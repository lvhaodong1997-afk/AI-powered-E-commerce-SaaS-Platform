# Meta 账号、作品数据与真实视频参数完善方案

状态：用户已于 2026-09-17 确认 P0，实现及聚焦回归验证完成。线上字段核验记录见同目录 `2026-09-17-meta-live-capabilities.md`。P1/P2 不包含在本次开发中。

**Goal:** 在现有 Instagram / Facebook 发布中心展示账号数据、已发布作品数据和真实视频参数，支持手动同步及后台定时同步。

**Architecture:** 保持 Spring Boot + Vue 架构，复用 Meta 授权、加密令牌、租户/公司权限、OSS 直传及现有任务调度模式。新增两个统计快照表与独立同步服务；视频探测复用 FFprobe，后台执行，不阻塞上传完成请求。

**Tech Stack:** Spring Boot、MyBatis-Plus、MySQL、Vue 3、TypeScript、Element Plus、OSS、FFprobe/FFmpeg。

## 一、需求理解

目标：

1. 已授权账号可查看粉丝数、平台支持的关注数、作品数和数据更新时间。
2. 图片/视频发布成功后可查看播放/浏览、点赞或反应、评论、分享，以及平台支持的收藏、触达等指标。
3. 上传视频显示真实时长、分辨率、帧率、编码和文件大小。
4. 用户无需填写 Meta metric 名称；正常页面展示业务数据，原始测试入口保留供排查。

首期覆盖已授权 Instagram 专业账号、Facebook Page，以及系统现存发布成功记录和后续发布记录。外部历史作品全量导入、个人 Facebook 账号统计、粉丝名单、关注名单不包含在首期。

### 已核对的现状

- 本地基线为 main / 67fa9c7；TkSocialProperties 默认 Graph API v25.0，实际线上版本在实施联调时核对，不凭默认值判断。
- Instagram 授权请求包含 followers_count、media_count，但授权结果映射、账号实体和 safeAccount 响应没有保留这些数值。
- GET /tk/social-account/insights 是账号分析测试入口，直接返回平台 JsonNode。
- TkSocialPlatformClient 已有 instagramMediaInsights 方法，但没有接到作品统计持久化和页面；Facebook 作品级统计尚需补充。
- 发布明细已有 externalMediaId、externalPostId、externalContainerId、publishUrl。统计必须选用平台已发布作品 ID，不能把上传容器 ID 当作作品 ID。
- OSS 直传完成只核对文件大小并采信浏览器元数据；metaPublishController.ts 固定提交 frameRate: 30。现有 TkSocialVideoInspector 只处理 byte[]，不适合直接加载 1 GiB 文件。
- 当前授权日期在页面可能显示毫秒时间戳，应与新增数据更新时间一起格式化。

## 二、需要增加的内容

### 产品功能

账号行增加粉丝数、关注数（如支持）、作品数（如支持）、最近数据更新时间、“同步数据”和同步状态。

发布明细增加“查看数据”“同步数据”，通过抽屉显示本次目标账号上的作品数据。相同上传文件发布到多个平台或账号，统计分别保存，不能存为一份 OSS 文件的公共播放量。

数值展示规则：平台真实返回 0 才显示 0；未获取、未产生数据、不支持、缺少权限、已过期分别提示。同步失败保留最近成功数据，标注更新时间和失败原因。

### 前端

- 在现有 MetaPublishPanel.vue 增加账号统计列；复用发布明细，新增 MetaMediaStatsDrawer.vue 展示作品指标、平台原始口径、统计范围与时间。
- 新增手动同步交互：点击后提交后台任务，禁用重复点击；页面轮询本系统结果，不循环直连 Meta。
- 上传状态分别显示“正在上传”“上传完成，检测中”“可发布”“检测失败”。只有检测完成的媒体才允许提交新发布任务。
- 删除默认 30fps；检测前显示“待检测”，使用服务端最终参数，时长和帧率按可读精度格式化。
- 日期复用项目时间格式工具，兼容现有毫秒时间戳响应。

### 后端

建议接口（统一前缀 /admin-api）：

| 方法及路由 | 功能 |
| --- | --- |
| GET /tk/social-account/stats?id=账号ID | 查询最近账号数据、指标可用性和同步状态 |
| POST /tk/social-account/stats/sync?id=账号ID | 提交一次账号资料/统计刷新，重复点击合并 |
| GET /tk/social-publish/detail/stats?detailId=明细ID | 查询该目标作品的数据 |
| POST /tk/social-publish/detail/stats/sync?detailId=明细ID | 提交一次已发布作品数据刷新 |
| GET /tk/social-publish/media/get?id=媒体ID | 查询上传媒体真实参数、检测状态和失败原因 |

保留现有上传、授权、发布和原始 Insights 测试路由。给账号分页响应附加可选 stats 摘要，分页批量查询，避免每行额外数据库请求。

上传完成响应保持已有字段并返回媒体 id/status；浏览器元数据继续兼容接收，但仅作提示，不作为发布校验依据。升级前端以识别 PROCESSING 状态并轮询 media/get。部署需要前后端配套。

新增 TkSocialStatsService 和 TkSocialStatsJob；按项目现有模式使用有界执行器、数据库到期扫描、条件更新认领和过期租约恢复。统计查询和同步失败不得改变已成功发布的任务状态，也不得发起重新发布。

### 数据库

新增两个最新快照表，不把统计字段混入令牌管理表：

1. tk_social_account_stats：tenant_id、company_id、creator、social_account_id、platform；followers_count、following_count、media_count；metrics_json；last_success_time、last_attempt_time、sync_status、error_code、error_message、next_sync_time、retry_count、lease_token、lease_until。
2. tk_social_media_stats：tenant_id、company_id、creator、publish_detail_id、social_account_id、platform、external_media_id；views_count、likes_count、comments_count、shares_count、saves_count、reach_count；metrics_json；与账号表相同的同步控制字段。

所有计数字段使用可空 bigint。metrics_json 为规范化白名单结果，包含平台指标名、统计范围、单位、可用状态和各指标获取时间；不原样保存令牌或签名 URL。Facebook reactions 与 Instagram likes 不混为同一种无说明的口径。

唯一索引分别为 (tenant_id, social_account_id) 和 (tenant_id, publish_detail_id)。查询索引 (tenant_id, company_id)；调度索引 (sync_status, next_sync_time)、(sync_status, lease_until)。与实体既有租户及软删除规范一致，首期保留一行当前快照并通过 upsert 更新。

扩展 tk_social_media：metadata_status、metadata_source、video_codec、audio_codec、video_bitrate、audio_bitrate、audio_sample_rate、metadata_checked_at、metadata_error、probe_attempts、probe_next_time、probe_lease_token、probe_lease_until；复用现有宽高、时长、frame_rate、file_size。增加检测调度索引。

采用仅增量迁移，保留所有已有账号和发布记录。历史视频标记参数来源未知，不批量覆盖真实值；对未发布且来源未验证的 OSS 媒体重新探测后才放行。已发布作品查询运营数据不依赖重新检测文件。

P1 再新增按日快照表，用于粉丝趋势和作品增长曲线；不把“首次接入前的历史粉丝数”伪造成可回溯数据。

### 第三方服务

继续使用当前 Meta App、OSS 和 FFprobe/FFmpeg，无需增加一个应用。

- Instagram：沿用 Instagram Login。候选账号字段 followers_count、media_count；关注数的字段及当前登录模式支持先单独验证。已发布作品使用 media insights 及相应作品字段。
- Facebook：沿用 Page 授权，接入 Page 资料字段及当前版本适用的 Reels/视频统计接口；不直接把 Page 汇总 Insights 当作某条视频数据。
- 权限核验：Instagram 的 instagram_business_basic / instagram_business_manage_insights；Facebook 的 pages_read_engagement / read_insights 及 Page 的 ANALYZE 任务。发布权限保留，不为统计额外索要消息或评论管理权限。
- 开发前用已有授权做只读能力验证，记录请求版本、支持字段、指标口径及响应形状。当前官方开发文档访问遇到限流，尚未实测现有账号的关注数字段及完整指标集合，因此它们属于候选清单，不能承诺全部可用。
- Meta 官方 Postman 集合用于发布流程参考，Instagram 集合含 Facebook Login 示例；本系统不会据此更换现有 Instagram Login 或套用其权限名称。

参考：

- https://www.postman.com/meta/instagram/documentation/6yqw8pt/instagram-api
- https://www.postman.com/meta/facebook/documentation/r56bjfd/facebook-api

## 三、实现方案

### 方案选择

1. 推荐：数据库保存最近快照，后台同步，页面直接读快照。响应稳定、能够展示失败前数据，符合现有后台任务架构。
2. 备选：每次打开页面实时请求 Meta。改动较少，但页面耗时、限流和偶发错误直接影响用户，且无法说明最后成功数据时间。
3. 暂不选：独立数据平台或完整历史仓库。当前需求不需要增加部署组件和维护成本。

### 实施顺序

第一步：能力验证。分别验证现有 IG 账号和 Page 的资料字段，以及一条已发布视频的指标；不做新的外部发布。以实际授权、Graph 版本、媒体类型建立指标清单。

第二步：账号同步及显示。授权成功后建立待同步快照；已有账号第一次进入页面按需初始化。统计失败不导致授权失败，账号展示可立即使用的最新值。

第三步：作品同步及显示。扫描本系统 SUCCESS 明细，使用正确外部 ID 建立一条独立统计快照。缺失 ID 的旧记录标明“缺少平台作品标识”，不猜测、不重新发视频。

第四步：真实视频参数。上传完成校验 OSS 会话归属、对象大小和对象身份，创建 PROCESSING 媒体；后台从服务端生成的受控 OSS 读地址获取文件并探测。优先尝试受限远程 Range 探测；需要本地处理时流式写临时文件，不转成 1 GiB byte[]。仅接受本系统签发的对象键，检测后绑定对象版本或 ETag，避免检测后替换。

复用现有音频兼容规则：需要修正的音频在独立发布副本中转换，原始文件保留；副本使用新对象键、二次探测和 1 GiB 大小校验。把 FFmpeg 处理重构为 Path 方式供该流程使用，不在请求线程下载或转换。

第五步：联调与交付。验证数据与同一统计范围的 Meta 返回一致，验证真实视频参数及故障场景；用户确认开发后按既有 main 工作方式执行，发布前数据库迁移、包校验、备份和回滚准备齐全。

### 文件范围

产品根目录：ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/。

后端 Java 基目录：yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/。

修改：

- service/social/auth/TkSocialAccountService.java：账号响应携带统计摘要，触发独立同步。
- service/social/auth/TkSocialProperties.java：统计与探测任务的启停、频率及资源限额。
- service/social/platform/TkSocialPlatformClient.java：补充账号资料、Facebook 作品统计，扩展 Insights 可用参数，不改变发布流程。
- controller/admin/social/TkSocialAccountController.java、TkSocialPublishController.java：统计查询、同步和媒体状态接口。
- service/social/TkSocialMediaService.java、TkSocialVideoInspector.java：完成上传、真实检测、流式/Path 处理、旧媒体兼容。
- dal/dataobject/social/TkSocialMediaDO.java、dal/mysql/social/TkSocialMediaMapper.java：检测数据及认领/完成条件更新。
- controller/admin/social/vo/TkSocialVideoUploadCompleteReqVO.java：兼容浏览器提示字段，真实结果由服务端产生。
- service/upload/TkUploadSessionService.java、TkOssObjectStorageService.java：限定修改为直传对象校验、会话完成幂等和独立发布副本清理。
- yudao-ui/yudao-ui-admin-vue3/src/api/tk/socialPublish/index.ts：新增统计与媒体状态契约。
- yudao-ui/yudao-ui-admin-vue3/src/views/tk/video-publish-center/components/MetaPublishPanel.vue、metaPublishController.ts：数据列、同步交互、日期显示、检测轮询。

新增：

- service/social/TkSocialStatsService.java、TkSocialStatsJob.java：统计同步及调度。
- service/social/TkSocialMetricCatalog.java：按平台、登录方式、版本和媒体类型映射实测指标。
- service/social/TkSocialMediaInspectJob.java：独立探测任务，不占用发布任务线程池。
- dal/dataobject/social/TkSocialAccountStatsDO.java、TkSocialMediaStatsDO.java。
- dal/mysql/social/TkSocialAccountStatsMapper.java、TkSocialMediaStatsMapper.java。
- controller/admin/social/vo/TkSocialAccountStatsRespVO.java、TkSocialMediaStatsRespVO.java、TkSocialSyncRespVO.java。
- yudao-module-tk/src/main/resources/sql/tk_social_stats_upgrade_mysql.sql。
- yudao-ui/yudao-ui-admin-vue3/src/views/tk/video-publish-center/components/MetaMediaStatsDrawer.vue。
- 对应统计同步、平台指标映射、探测任务和前端状态测试文件。

删除文件：无。不增加独立微服务，不修改 TikTok 接口和统计逻辑。

## 四、技术方案

### 架构与数据流

账号：授权/手动刷新 → 账号待同步记录 → 独立统计任务 → Meta 资料及统计接口 → 当前快照 → 账号行。

作品：本系统发布明细 SUCCESS → 作品待同步记录 → 平台已发布作品 ID → Meta 作品接口 → 该明细的统计快照 → 数据抽屉。

视频：浏览器直传 OSS → 完成接口校验并建立 PROCESSING 记录 → 后台 FFprobe/必要的音频处理 → READY 或检测失败 → 页面允许发布或提示修正。

### 统一结果契约

统计响应包含 objectId、platform、syncStatus、lastSuccessTime、lastAttemptTime、nextSyncTime、errorCode、errorMessage，以及 metrics。

单个 metrics 项包含 key、value（可空）、unit、sourceMetric、scope/period、availability、fetchedAt。availability 区分 AVAILABLE、PENDING、UNSUPPORTED、PERMISSION_REQUIRED、ERROR；旧数据是否过期独立标注。不同指标部分成功时保留各自获取时间，不能使用整次请求时间给失败指标刷新时间戳。

同步接口返回 accepted、syncStatus、nextPollAfterSeconds，不把“任务已接收”当作“平台数据已更新”。重复请求合并，读取接口不产生外部发布副作用。

### 默认调度策略（可配置）

- 账号：授权成功后排队获取一次，之后每 6 小时刷新。
- 已发布作品：首次延迟 5 分钟；发布首日每 30 分钟一次，第 2–7 天每 6 小时一次，第 8–30 天每天一次；更早作品默认手动刷新。
- 页面手动同步：同一对象 60 秒冷却；相同对象进行中的任务合并。全局并发初始为 2，并设每轮批量上限；根据平台 usage / Retry-After 降速。
- 网络错误、429、5xx 使用退避和随机抖动；缺少权限、字段不支持、对象删除不进行高频重试。解绑或授权失效时暂停对应自动同步。
- 请求参数按平台映射，包括必要的 metric_type、period、since/until；不继续使用一个通用 metric+period 拼接去覆盖所有接口。
- 仅在短事务中认领或落库；平台 HTTP 请求不占用数据库事务。租约条件更新防止旧请求覆盖新同步结果。

### 媒体检测约束

并发初始为 1，单文件最高 1,073,741,824 字节；控制临时磁盘空间、进程超时和失败重试上限，不能一次性读入 JVM 堆。保留当前图片上传行为。

探测来源由服务端对象键及 OSS 配置派生，拒绝前端任意 URL。FFprobe/FFmpeg 参数用参数数组传递，避免 shell 拼接；禁用不必要协议。检测失败清理临时文件，OSS 原始对象按现有保留策略处理，重试期间不得误删。

展示源视频与发布副本差异（发生音频修正时）。没有检测结果时帧率为未知，绝不填默认 30fps。

## 五、开发任务拆解

### Task 1：确认平台能力与字段映射

目标：确定现有两个授权账号真正支持的字段及作品指标。

文件：TkSocialPlatformClient.java、新增 TkSocialMetricCatalog.java、对应平台客户端测试。

实现：只读核验当前 Graph 版本、有效权限、Page ANALYZE；分别请求资料字段和一条已发布视频指标。关注数单独验证，不让可选字段失败拖垮账号基本资料。保存脱敏请求/响应测试样例，不保存 Token。

验收：指标清单明确来源、单位、统计范围、请求参数和不可用原因；能区分 0、空、权限不足、指标废弃。

### Task 2：账号最新快照闭环

目标：在账号行看见真实粉丝数和可用的关注数、作品数。

文件：统计迁移 SQL、TkSocialAccountStatsDO/Mapper、TkSocialStatsService、账号 Controller/Service、socialPublish/index.ts、MetaPublishPanel.vue。

实现：数据表幂等创建、读取快照、手动提交刷新、授权后首次同步。读取权限复用 tk:social-account:query，触发同步使用 tk:social-account:update；全部核对租户、公司及账号归属。

验收：正常获取、有效零值、不支持字段、重新授权、跨租户拒绝、同步失败保留旧数据；旧账号无须解绑重绑即可初始化快照。

### Task 3：已发布作品数据闭环

目标：每个发布明细有独立作品统计。

文件：TkSocialMediaStatsDO/Mapper、TkSocialStatsService、TkSocialPublishController、平台客户端及新增 MetaMediaStatsDrawer.vue。

实现：只处理 SUCCESS 且有合法作品标识的记录，核对 ID 与已授权账号关联；分页初始化已有成功记录。视频和图片按实际媒体类型选指标。只读查询复用 tk:social-publish:query，手动刷新同权限加防抖/配额，不赋予重发权限。

验收：同文件双平台发布统计互不覆盖；IG 容器 ID 不被当作作品 ID；Page 视频使用正确接口；缺 ID、删除、未产生数据各自明确展示，绝不触发 publish。

### Task 4：后台定时同步与故障恢复

目标：账号和作品数据自动更新且可恢复。

文件：TkSocialStatsJob、统计 Mapper、TkSocialProperties 和任务测试。

实现：到期批量扫描、有界执行、数据库租约、手动任务合并、退避、停用账号跳过。统计执行器和发布执行器分开。

验收：重复点击不重复执行；多实例只能认领一次；重启后恢复；429 降速；旧租约结果不能覆盖新结果；统计失败不修改发布状态。

### Task 5：真实视频参数与安全检测

目标：去掉虚假帧率，在保持 OSS 直传和 1 GiB 上限的基础上验证实际文件。

文件：TkSocialMediaService、TkSocialVideoInspector、TkSocialMediaInspectJob、媒体 DO/Mapper、上传服务、metaPublishController.ts 和既有社交视频测试。

实现：完成接口校验归属/对象并幂等创建检测任务；后台受限读入或流式落盘、FFprobe、需要时生成兼容发布副本；前端轮询并在 READY 前禁止发布。兼容历史记录及旧上传路由。

验收：实际 24/25/29.97/30/60fps 样本；伪造浏览器参数无效；损坏 MP4、越界大小、异租户对象、重复 complete、探测超时和临时文件清理；大文件内存不随整个文件大小增长。

### Task 6：页面、联调与发布准备

目标：业务可读且回归发布流程。

文件：MetaPublishPanel.vue、MetaMediaStatsDrawer.vue、metaPublishController.ts、tests/meta-social-publish.test.cjs、相关后端测试。

实现：日期格式、最新数据与过期状态、指标口径说明；运行改动对应的后端测试、前端类型检查、相关前端测试和生产构建。只读使用已有已发布视频进行平台对照，不为了验收自动发布测试内容。

验收：两个平台各一条现有视频及各一个账号的数据与同次 Meta API 返回一致；页面展示与数据库快照一致。实际 1 GiB 上传单独记录验证结果，边界单测不能替代真实传输验证。

部署：确认开发后，依用户已选的 main 工作方式提交推送；目标服务器增量迁移、备份构建产物、SHA-256 校验、原子切换、服务及公网检查。回滚保留新增表字段，旧版本代码继续运行，不删除用户数据。

## 六、风险分析

| 方面 | 风险与处理 |
| --- | --- |
| 性能 | 不随每次页面加载请求 Meta；分批同步、并发上限、限流退避；视频探测独立后台任务，控制内存和磁盘 |
| 安全 | 后端保管 Token；接口核对租户、公司和对象归属；不接收任意抓取 URL；日志/样例不含令牌或签名 |
| 数据 | 平台更新存在延迟；区分累计值和周期值、likes 与 reactions；失败保留旧值并显示每项更新时间；不虚构缺失的 0 |
| 兼容性 | 当前登录方式、Graph 版本和媒体类型可能限制关注数/Insights；单项能力降级，不破坏已授权状态和发布链路 |
| 扩展性 | 独立指标映射与统计表；后续增加历史作品导入、按日快照及趋势，无须重写发布服务 |
| 发布 | 先做增量迁移，再部署配套前后端；保留旧接口和回滚版本，保护其他项目 |

## 七、开发优先级

P0：实测字段能力、账号粉丝/可用关注/作品数、单作品核心指标、手动及定时同步、错误/空值区分、真实视频检测、日期格式、租户隔离及回归验证。

P1：每日快照、粉丝变化趋势、平台支持的观看时长等扩展指标、指定时间范围分析、账号历史作品分页导入。

P2：Excel 导出、跨账号报表、作品排行、异常增长提醒。

## 八、等待确认

建议按 P0 一次完成，P1/P2 暂不实施。Instagram 关注数和各平台可选指标以现有授权实际支持为准；不支持时必须明确显示原因，不能填假值。

已获用户确认，按 main 提交、推送及部署流程执行。

## 实施对照与发布说明

- 实际统计实现为 `TkSocialStatsCollector/Policy/Service/Worker`，通过 `TkSocialStatsRepository` 显式限定租户及公司，复用通用快照对象；账号与作品仍分别存储在两个统计表。未引入新的业务架构或更改既有发布状态。
- 两份增量脚本为 `tk_social_stats_upgrade_mysql.sql` 和 `tk_social_media_probe_upgrade_mysql.sql`，需先执行迁移，再部署前后端。统计默认关闭，部署时仅为本项目设置 `TK_SOCIAL_STATS_ENABLED=true`；其他授权配置保持原值。
- 视频上传保留 OSS 直传及 1,073,741,824 字节上限。检测返回 `PROCESSING`，实际参数通过 `media/get` 获取；视频只有 `READY + VERIFIED` 才能新建发布。检测器容量繁忙只延后处理，不消耗失败次数。
- 统计任务有独立执行器及数据库租约；重新授权不会抢占正在执行的同步，完成后再同步一次。令牌过期暂停任务，缺权限保留已有数据及其原始采集时间。
- 本地验证：后端 26 个相关测试类共 180 项通过，零失败、零跳过；前端 53 项逻辑测试和类型检查通过，生产构建成功。覆盖真实 FFprobe、音频归一化、租约竞争、权限隔离、重启恢复和历史记录兼容。
- 验证边界：未执行真实 1 GiB 文件端到端传输；当前 Instagram 账号没有可访问作品，无法完成正向线上作品指标对照。未为测试新发任何作品。平台空值、权限不足或对象不可访问均不得伪造为零。
- 进程在生成 OSS 发布副本后、数据库确认前崩溃，可能遗留未引用对象；为避免误删已提交副本，本次不自动删除提交状态不明确的对象，后续需补充未引用对象清理。
