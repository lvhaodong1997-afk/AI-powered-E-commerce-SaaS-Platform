# TikTok OSS PULL_FROM_URL Implementation Plan

> **For agentic workers:** Use `executing-plans` to implement this plan task-by-task after the user confirms development. Steps use checkbox syntax for tracking.

**Goal:** 将符合条件的阿里云 OSS 视频发布切换为自定义域名 + TikTok `PULL_FROM_URL`，消除应用服务器的视频下载、暂存和分片转发，并保持任务可追踪、可恢复。

**Architecture:** `tkpublish.fnn.net.cn` 通过 OSS 原生自定义域名直接提供视频。现有发布服务负责权限校验、源地址选择、提交初始化请求和异步结果同步；复用数据库任务、执行记录、调度器及现有鉴权体系。网页发布和 Open API 发布采用同一套视频源解析规则。

**Tech Stack:** Java / Spring、MyBatis / MySQL、现有 Redis 能力、阿里云 OSS、自定义域名 HTTPS、TikTok Content Posting API、Vue 3 / TypeScript。

## Global Constraints

- 本方案已获用户确认进入开发，用户补充：PULL_FROM_URL 优先，原流程做兜底；生产部署和真实发布另行授权。
- 最小修改，不改变已有架构，不破坏已有接口，优先复用代码。
- 保留用户已有未提交改动；开发时仅提交本方案涉及的差异。
- 保留原 OSS 地址、全局 OSS publicBaseUrl，以及已有 FILE_UPLOAD 任务的数据和恢复路径。
- 任何已进入远端初始化、已有 publish_id 或初始化结果不明确的执行，都不能通过切换传输方式重新初始化。
- 自定义域名 HTTPS 可用与 TikTok 实际发布成功分别验收。
- 后续实施先在本地完成和验证；生产部署、同步按用户后续授权执行。
- 用户追加约束：历史数据不用动；不增加历史 FAILED 记录的状态纠正/回写流程，也不批量补写新策略。

## 本地实施记录（2026-10-29）

已完成新任务的网页 / Open API 即时与定时 PULL 优先、初始化前 FILE_UPLOAD 兜底、加密源快照、已知 ID 防重复初始化、请求额度、到期状态查询与前端阶段显示。原 OSS URL 与全局存储配置未改。解析器使用 `resolve(originalUrl, accountId, verifiedProperty)` 返回实际 URL、大小和原因码；HEAD 成功结果在明确租户内合并并缓存 20 秒，最多 1024 条，无租户上下文时逐次探测。没有引入 ETag 版本表或跨节点元数据缓存，依靠不可覆盖 object key 的上线约束。

审查修复了灰度开关绕过、已知 ID 本地错误误判失败、恢复记录漏查、并发重试覆盖和旧响应覆盖新执行。新执行只有官方失败响应落库为 REMOTE_FAILED 后，才允许清除已有 ID 进入显式重试。历史状态不明确的记录按用户要求保持原样；本次不提供历史状态纠正流程。独立链接元数据更新保留成功任务的链接刷新能力。

验证：联合 22 类后端测试 254 项，252 通过，2 项既有鉴权/统计测试失败；已在原始 checkout 复现相同失败，未修改相关鉴权逻辑。后续重试和链接修复的 75 项聚焦测试全部通过。前端 2 项回归及完整 `ts:check` 通过；类型检查环境 Node 18 有版本提示。`git diff --check` 通过。

未执行生产同步、数据库迁移或真实 TikTok 发布。真实 MySQL 迁移重复执行/查询计划、共享 Redis 跨进程额度、TikTok App 域名验证和真实视频拉取仍为灰度验收项；本地 Docker 引擎不可用。下方原计划中的测试库、生产及真实发布勾选项不代表已完成，实际启用按运维说明执行。

## 一、需求理解

### 目标与当前基础

目标是解决原来 `OSS → 应用服务器 → TikTok` 导致的服务器带宽、磁盘和上传线程占用。新链路为：

```text
应用服务器 ── init / status / callback ── TikTok
                                          │
                                          │ HTTPS 拉取视频
                                          ▼
                              tkpublish.fnn.net.cn
                                          │ OSS 原生 CNAME
                                          ▼
                          tk-video-create（北京 OSS）
```

当前已知配置：

| 项目 | 当前情况 | 开发/上线处理 |
| --- | --- | --- |
| Bucket | tk-video-create，oss-cn-beijing | 作为允许映射的唯一源 Bucket |
| 自定义域名 | tkpublish.fnn.net.cn | 作为 TikTok 拉取域名 |
| ICP | 用户已完成备案 | 沿用 |
| HTTPS | 已安装证书；此前实测证书校验及样例 HEAD / Range 请求成功 | 上线前再次验证 |
| 证书维护 | 当前证书到期时间为 2027-01-27 14:12（北京时间）；尚未建立自动续期 | 建立到期提醒及续期部署流程 |
| TikTok 域名归属验证 | 尚未取得验证通过的证据 | 每个实际使用的 TikTok App 完成 URL Properties 验证 |
| 实际 TikTok 发布 | 尚未实测此新域名的完整拉取发布 | 白名单真实试发布验收 |

映射示例，保留对象路径及路径中的百分号编码：

```text
原地址：
https://tk-video-create.oss-cn-beijing.aliyuncs.com/TK/AI_Generator_Fast/final-video/1793253046949/1029-6.mp4

提交给 TikTok 的地址：
https://tkpublish.fnn.net.cn/TK/AI_Generator_Fast/final-video/1793253046949/1029-6.mp4
```

这条链路移除应用服务器的视频二进制转发；控制接口、元数据探测、日志仍有少量流量。OSS 公网下行费用仍然存在，北京 OSS 到 TikTok 的跨境网络表现需要实测。TikTok 文档提到入站速率可达 100 Mbps，不能将其视为每个任务的带宽保证。

### 代码核查结果

1. `TkOpenTiktokPublishService` 和 `TkTiktokPublishServiceImpl` 都已有 PULL 分支，可在原链路内改造。
2. 两条链路都会优先使用定时发布的本地文件；创建定时任务时也会提前落地视频。只修改域名配置，不能消除定时发布的下载。
3. 两处 PULL 请求目前仍从原始 `fileUrl` / `videoUrl` 取地址。源选择结果必须携带实际映射后的 URL。
4. Open API 执行记录已有 source、owner fencing、心跳、远端初始化保护、响应记录和 publish_id 恢复机制，应直接复用。
5. 网页链路通过状态 CAS 领取任务，但初始化异常仍可能走普通失败路径，需要补齐初始化结果不明确时的阻断保护。
6. 状态同步默认约两分钟，查询条件也使用过期时间筛选；单独降低定时器间隔不能达到及时同步效果。
7. 客户端当前只解析 `uploaded_bytes`，前端缺少 `PROCESSING_DOWNLOAD` 的明确展示。

## 二、需要增加的内容

| 模块 | 需要增加/调整 |
| --- | --- |
| 产品功能 | 符合条件的视频自动选用 PULL；即时、批量、定时发布一致；用户继续使用现有发布入口 |
| 前端 | 现有发布中心展示“排队中 / TikTok 拉取中 / 平台处理中 / 发布成功 / 失败或待核查”；不新增页面 |
| 后端 | 共享视频源解析、轻量预检、两条入口接入、定时任务策略固定、初始化保护、按到期时间同步状态 |
| 数据库 | 现有表新增可空字段，保存任务策略、实际源快照、初始化阶段和下次查询时间；复用现有执行记录 |
| 第三方 | TikTok App 验证自定义域名；保持 OSS HTTPS 直达及证书有效；核对对象生命周期和访问权限 |

## 三、实现方案

### 1. 统一源地址解析

新增 `TkTiktokPullSourceResolver`，供网页和 Open API 共用。调用方先完成现有租户、公司、账号及素材归属校验，解析器负责域名与对象身份验证。

建议接口契约（新建类型为该类内部不可变类型，避免分散文件）：

```java
// 调用方已验证素材归属；metadata 来自服务端素材记录或 OSS HEAD。
PullDecision resolve(String originalUrl, SourceMetadata metadata, PullPolicy policy);

// PullDecision 包含：eligible、pullUrl、objectKey、fileSize、etag、reasonCode。
// 返回 eligible=false 只表示不适用本方案，不代表允许立即重试远端发布。
```

解析规则：

- 必须使用 URI 解析，并对 host 做规范化后的精确匹配；禁止字符串包含判断或整串 replace。
- 允许的源 host 为 `tk-video-create.oss-cn-beijing.aliyuncs.com`，目标 host 为 `tkpublish.fnn.net.cn`。
- 输入为源 host 时，仅替换 scheme/authority，保留 raw path；已是目标 host 时保持对象路径。
- 拒绝 userinfo、非标准端口、重定向依赖、非法路径，以及越过当前素材归属边界的对象。
- 首版处理当前已可匿名读取的自有 OSS 对象。带签名的原始 URL 不能直接替换 host 后保留签名；该类素材沿用现有上传方式。
- 其他不适用的素材在远端初始化之前沿用现有 FILE_UPLOAD 路径。
- 保留现有 URL Property 的 Domain / URL Prefix 验证语义；此次映射规则精确限定到上述域名。

### 2. 元数据预检，不完整下载

- 优先复用素材已有 fileSize、mimeType，以及 OSS `headObject` 返回的 contentLength / etag。
- 对首次使用的目标链接验证 HTTPS、HTTP 200、文件大小和可读取性；小范围 Range 仅用于必要的内容检查。
- HTTP 客户端关闭自动重定向；复用现有安全远程 URL 校验，连接/读取设置有限超时。
- 探测按对象身份与 etag 短期合并，多个账号发布同一视频不重复探测整份内容。
- 时长、大小、格式、用户隐私设置及 creator info 继续执行现有 TikTok 发布校验。
- 用户确认后的执行规则：PULL 预检失败记录原因，并在远端初始化之前回退原 FILE_UPLOAD；远端结果不明确或已取得 publish_id 时保持原执行并核查。

### 3. 两条发布入口接入

源选择结果携带实际 pullUrl，构造请求时只使用这个结果：

```json
{
  "source_info": {
    "source": "PULL_FROM_URL",
    "video_url": "https://tkpublish.fnn.net.cn/TK/AI_Generator_Fast/final-video/1793253046949/1029-6.mp4"
  }
}
```

保留现有 post_info。PULL 分支不传 video_size、chunk_size、total_chunk_count，不要求响应携带 upload_url。获取 publish_id 后立即持久化并释放执行线程；后续由状态同步或回调推进。

Open API 保留现有 owner token、响应记录和 beginRemoteInit 顺序。网页链路在明细中增加最小初始化阶段标记，在外部请求前持久化 `INIT_IN_FLIGHT`，拿到 publish_id 后切换为 `INIT_CONFIRMED`。超时、断线或响应落库失败进入 `RECOVERY_REQUIRED`，禁止普通失败重试按钮重新初始化。

### 4. 定时发布保存对象引用

- 新建任务时固定 `transfer_policy`：`FILE_UPLOAD` 或 `OSS_PULL_V1`；`LEGACY` / 空值专用于历史兼容，避免新任务借旧 PULL 分支绕过开关。
- 新的 OSS_PULL_V1 定时任务保存源对象引用和元数据，跳过 `prepareForScheduledPublish` / `scheduledMediaService.persist` 的本地落地。
- 到点执行时再校验目标地址；首版链接不依赖短期签名。
- 历史已落地任务继续按原策略执行，避免切换中的行为漂移。
- 应用内删除、清理及取消逻辑检查该对象是否仍被其他待执行/处理中任务引用；最后一个相关任务终态后再按现有保留期清理。
- 素材删除与任务创建采用同一素材的锁或删除资格标记，防止“刚检查无引用、随后又创建任务”的竞争。
- OSS 生命周期规则必须覆盖“计划等待时间 + 拉取时间 + 故障核查保留期”。仍可能拉取的对象不可被提前删除或覆盖。

### 5. 状态同步和错误处理

- 新增正常状态查询的 `next_poll_time`，与恢复用 `next_reconcile_time` 区分。
- 调度器建议每 5～10 秒扫描到期任务，单任务使用 10 / 20 / 30 / 60 秒退避并加入抖动；受账号总额度约束时继续延后。
- 网页调度器中的定时激活、待处理恢复与平台状态查询分别控制扫描频率，避免缩短查询周期后顺带重复扫描全部业务。
- 解析并透传 `downloaded_bytes`。仅在总大小已知且 TikTok 返回有效字节数时展示进度；下载完成仍继续等待平台处理。
- PULL 的正常 `PLATFORM_PROCESSING` 不占本地上传租约，不因上传心跳停止被 watchdog 当成断线上传。
- 保留 FILE_UPLOAD 的 PROCESSING_UPLOAD / uploaded_bytes 和续传规则。
- 终态写入采用现有条件更新与终态服务；查询、回调、恢复并发时不能重复结算或将终态回退。

错误边界：

| 情况 | 处理 |
| --- | --- |
| 初始化前不符合 PULL 条件 | 沿用现有 FILE_UPLOAD 策略 |
| 已选 PULL 但对象预检失败 | 记录原因，在远端初始化前回退原 FILE_UPLOAD |
| url_ownership_unverified | 阻断该 App 新的 PULL 初始化并提示运维修复域名验证 |
| 初始化超时、连接中断、结果不明确 | RECOVERY_REQUIRED；不切换方式，不盲目重发 |
| 已取得 publish_id | 对该 publish_id 查询/接收回调，不重新 init |
| 查询 429 或暂时网络错误 | 按额度及响应退避，保留处理中状态 |
| TikTok 明确返回 FAILED / video_pull_failed | 旧执行保持失败；预检通过后通过现有显式重试入口新建一次执行 |
| 拉取耗时接近或超过一小时 | 继续核查平台状态；不凭本地计时推断可重发 |
| SEND_TO_USER_INBOX | 表示送达用户收件箱，沿用上传模式语义，不显示为已直接发布 |

## 四、技术方案

### 配置

复用 `tk_api_key_config`，provider 为 TIKTOK，按现有租户配置作用域读取。下列新增 key 需随实现加入配置说明，当前尚不存在：

| key | 建议值 | 用途 |
| --- | --- | --- |
| verified-pull-domain（已有） | tkpublish.fnn.net.cn | 与实际 TikTok App 已验证的 URL Property 对应 |
| pull-from-url-enabled（新增） | false | 控制新建任务是否采用新策略 |
| pull-origin-host（新增） | tk-video-create.oss-cn-beijing.aliyuncs.com | 精确限制可映射源 |
| pull-base-url（新增） | https://tkpublish.fnn.net.cn | 提交给 TikTok 的 URL 基地址 |
| pull-account-allowlist（新增） | 空 | 首轮按账号灰度；空名单不启用新策略 |

若账号由不同 TikTok App/credential profile 授权，按该账号实际使用的 App 判断域名验证状态及请求额度。不能因为一个 App 验证通过，就让其他 App 自动启用。

### 数据库增量

全部新增字段允许为空，兼容已有数据；业务读取时显式处理历史空值。新增一个幂等升级脚本，并同步全新安装表结构。

| 表 | 新增字段 | 说明 |
| --- | --- | --- |
| tk_open_tiktok_publish_task、tk_tiktok_publish_task | transfer_policy varchar(32) | 固定新老任务源策略；空值为 LEGACY |
| tk_open_tiktok_publish_attempt | pull_url_cipher text | 保存本次实际源 URL；source / size 复用已有 upload_source / file_size |
| tk_open_tiktok_publish_detail | next_poll_time datetime、downloaded_bytes bigint | 正常轮询调度及拉取进度 |
| tk_tiktok_publish_detail | transfer_source varchar(32)、pull_url_cipher text、source_file_size bigint | 网页链路执行源快照 |
| tk_tiktok_publish_detail | init_phase varchar(32)、init_started_at datetime | 网页链路初始化边界和未知结果保护 |
| tk_tiktok_publish_detail | next_poll_time datetime、downloaded_bytes bigint | 正常轮询调度及拉取进度 |

两张 detail 表为正常到期查询增加组合索引，候选顺序为 `(status, next_poll_time, id)`；将租户、软删除条件纳入真实 SQL 的 EXPLAIN 检查后确定最终索引。原 next_reconcile_time、link_next_retry_time 继续保留各自用途。

实际 URL 复用现有加密能力保存；接口、日志、业务事件仅输出 host、脱敏路径或对象标识，不输出访问令牌、签名参数或私钥。ETag 用于预检及短期缓存；首版以不可覆盖的 object key 固定对象身份，不新增独立对象版本表。

### 请求额度和并发

Direct Post init 官方限制为每用户 access token 每分钟 6 次；状态查询为每用户 access token 每分钟 30 次。实现以稳定的 App + 用户身份聚合额度，令牌刷新不重新获得一份额度，网页与 Open API 共享预算。

排队使用已有持久化任务；短初始化执行与状态轮询分别限流。多节点运行时复用现有共享协调设施原子领取预算，不能仅靠进程内计数。若尚未部署共享预算，则首轮限制为单调度节点，不宣称支持多节点总额度控制。

## 五、开发任务拆解

以下路径均相对于产品源根目录：

`C:/Users/lhd/Documents/TK自动混剪SaaS产品/ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/`

Java 主源码根为 `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/`；测试根为 `yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/`。

删除文件：无。

### Task 1：共享映射与预检

**目标：** 能准确决定是否采用 PULL，并返回可提交的自定义域名 URL 和实际文件大小。

**文件：**

- 新增：`service/tiktok/TkTiktokPullSourceResolver.java`。
- 复用：`service/config/TkApiKeyConfigServiceImpl.java` 的现有配置读取接口，策略默认值在解析器中定义。
- 复用：`service/upload/TkOssObjectStorageClient.java` 的 headObject、现有安全远程 URL 校验器。
- 新增测试：测试根下 `service/tiktok/TkTiktokPullSourceResolverTest.java`。

- [ ] 先编写用例：原 OSS 地址正确映射；目标域名地址保持不变；百分号编码不二次编码；伪造 host、userinfo、签名 URL、非自有对象被拒绝或判为不适用。
- [ ] 用 Mock HTTP 返回 200 / 302 / 403 / 超时和已知 Content-Length，确认预检不跟随重定向、不下载完整文件。
- [ ] 实现 resolve 契约和轻量探测，保持全局 OSS publicBaseUrl 不变。
- [ ] 验证映射样例输出为本文的新地址，fileSize 等于真实对象元数据；失败原因可区分配置、权限和网络。

### Task 2：兼容性数据库迁移与任务策略

**目标：** 历史任务保持原行为，新任务可以固定 PULL 策略，执行源和初始化边界可持久化。

**文件：**

- 新增：`yudao-module-tk/src/main/resources/sql/tk_tiktok_pull_from_url_upgrade_mysql.sql`。
- 修改：同目录 `tk_tiktok_open_api_mysql.sql`、`tk_tiktok_publish_center_mysql.sql`。
- 全新安装按原流程执行同目录 `tk_tiktok_publish_attempt_upgrade_mysql.sql` 创建 attempt 表后，再执行新升级脚本；既有升级脚本保持原样。
- 修改 Java 主源码根下：
  - `dal/dataobject/openapi/TkOpenTiktokPublishTaskDO.java`
  - `dal/dataobject/openapi/TkOpenTiktokPublishAttemptDO.java`
  - `dal/dataobject/openapi/TkOpenTiktokPublishDetailDO.java`
  - `dal/dataobject/TkTiktokPublishTaskDO.java`
  - `dal/dataobject/TkTiktokPublishDetailDO.java`

- [ ] 在独立测试数据库执行升级两次，第二次无重复列/索引错误。
- [ ] 回归历史空字段记录，确保按 LEGACY 执行；持久化加密 URL 后可被执行服务读取，列表接口不返回密文或明文源快照。
- [ ] 为新字段补齐 DO 映射和任务创建时的策略固定；不批量修改历史任务。
- [ ] 检查新旧代码切换的兼容性：先加列，再部署读写逻辑，最后启用策略；数据库回滚保留新增列。

### Task 3：两条入口完成 PULL 初始化

**目标：** 即时发布不再下载或上传符合条件的视频，初始化后立即释放执行线程。

**文件：**

- 修改：`service/open/tiktok/TkOpenTiktokPublishService.java`。
- 修改：`service/open/tiktok/TkOpenTiktokPublishAttemptService.java`。
- 修改：`service/tiktok/TkTiktokPublishServiceImpl.java`。
- 修改：`dal/mysql/TkTiktokPublishDetailMapper.java`，补充网页初始化阶段的条件更新。
- 测试：`service/open/tiktok/TkOpenTiktokPublishExecutionTest.java`、`service/open/tiktok/TkOpenTiktokPublishAttemptServiceTest.java`、`service/tiktok/TkTiktokPublishServiceImplTest.java`。

- [ ] 添加初始化请求断言：source 为 PULL_FROM_URL、video_url 为目标域名、没有分片参数；仅返回 publish_id 也被接受。
- [ ] 添加传输调用断言：符合条件时 OSS 完整下载、临时文件创建和 uploadVideoChunks 调用次数均为 0。
- [ ] 将 UploadSource 扩充为携带实际 pullUrl 和 size；payload 使用该 URL，停止从原始素材 URL 重取。
- [ ] Open API 沿用 beginRemoteInit 和响应持久化；网页增加 INIT_IN_FLIGHT → INIT_CONFIRMED / RECOVERY_REQUIRED 的条件更新。
- [ ] 注入 init 超时、保存响应失败、重复任务投递，断言每次执行最多跨越一次不明确的远端初始化边界；已有 publish_id 不重复初始化。
- [ ] PULL 直接进入平台处理，不写 UPLOAD_PENDING，不进入上传恢复线程；保留原 FILE_UPLOAD 成功及失败回归用例。

### Task 4：定时任务和对象保留

**目标：** 定时发布同样消除本地视频缓存，且到点时源对象仍可读取。

**文件：**

- 修改：`service/open/tiktok/TkOpenTiktokMediaService.java`。
- 修改：`service/open/tiktok/TkOpenTiktokPublishService.java`。
- 修改：`service/tiktok/TkTiktokPublishServiceImpl.java`。
- 修改：`service/tiktok/TkTiktokScheduledMediaService.java`。
- 测试：`service/open/tiktok/TkOpenTiktokMediaServiceTest.java`、`service/tiktok/TkTiktokScheduledMediaServiceTest.java`、`service/tiktok/TkTiktokPublishServiceImplTest.java`。

- [ ] 创建 OSS_PULL_V1 定时任务，断言不调用本地 persist / download，重启后仍能通过对象引用执行。
- [ ] 回归已有 scheduledLocalPath 的 LEGACY 任务；其 FILE_UPLOAD 路径保持有效。
- [ ] 在素材删除和定时清理入口增加活动引用检查；同对象被两个任务使用时，取消其中一个不能删除源对象。
- [ ] 模拟到点前对象被外部删除/替换，预检明确失败且未调用 init；上线核对 OSS 生命周期规则及对象不可覆盖约定。

### Task 5：异步状态、限流与恢复

**目标：** 正常拉取快速呈现，异常状态可核查，多入口不突破用户请求额度。

**文件：**

- 修改：`service/tiktok/TkTiktokApiClient.java`。
- 修改：`service/open/platform/TkOpenPublishPlatformAdapter.java`、`service/open/platform/TkOpenTiktokPlatformAdapter.java`。
- 修改：`service/open/tiktok/TkOpenTiktokPublishStatusJob.java`、`service/tiktok/TkTiktokPublishStatusSyncJob.java`。
- 修改：`dal/mysql/openapi/TkOpenTiktokPublishDetailMapper.java`、`dal/mysql/TkTiktokPublishDetailMapper.java`。
- 修改：两条发布服务的状态推进及现有请求额度处理；复用当前 Redis/协调组件。
- 测试：`service/tiktok/TkTiktokApiClientTest.java`、`service/open/tiktok/TkOpenTiktokPublishStatusJobTest.java`、`service/open/tiktok/TkOpenTiktokPublishTerminalServiceTest.java`，以及两条发布服务的执行测试。

- [ ] 解析包含 PROCESSING_DOWNLOAD / downloaded_bytes 的官方响应样例，保留旧构造器及 FILE_UPLOAD 语义。
- [ ] 使用可控时钟测试 next_poll_time，未到期不查、到期领取、429 延后、并发领取不会形成重复查询风暴。
- [ ] 验证网页与 Open API 对同一 App + 用户共享 init / status 预算；刷新 access token 不清空预算。
- [ ] 注入延迟回调、查询先成功后收到旧响应，断言终态不回退、不重复结算。
- [ ] 覆盖 PULL 平台处理中本地心跳已停止的情况：watchdog 不重新 init、不安排分片续传。

### Task 6：发布中心呈现与完整验收

**目标：** 用户能看清拉取与处理状态，并完成真实发布验证。

**文件：**

- 修改：`controller/admin/tiktok/vo/TkTiktokPublishDetailRespVO.java`，按需追加可空 downloadedBytes / sourceFileSize 响应字段。
- 修改：`yudao-ui/yudao-ui-admin-vue3/src/api/tk/videoPublishCenter/index.ts`。
- 修改：`yudao-ui/yudao-ui-admin-vue3/src/views/tk/video-publish-center/index.vue`。
- 新增：`docs/tiktok-pull-from-url-runbook.md`，记录验证、灰度、证书维护、失败排查与回滚。

- [ ] 展示 PROCESSING_DOWNLOAD 对应“TikTok 拉取中”；字段缺失时显示阶段，不伪造百分比。
- [ ] 展示下载结束后的平台处理状态，只有 Direct Post 的 PUBLISH_COMPLETE 显示发布成功。
- [ ] 单账号即时发布、同视频多账号、定时发布各完成一轮真实验收；使用用户明确授权的测试素材和发布设置。
- [ ] 记录实际 publish_id、最终状态、耗时以及服务端传输指标，脱敏保存证据。

### 实施时的验证命令

以下命令是后续开发使用，本方案阶段不执行构建或发布。

从产品源根目录运行后端聚焦测试：

```powershell
& '..\..\..\.runtime\apache-maven-3.9.10\bin\mvn.cmd' -pl yudao-module-tk -am '-Dtest=TkTiktokPullSourceResolverTest,TkOpenTiktokPublishExecutionTest,TkOpenTiktokMediaServiceTest,TkOpenTiktokPublishAttemptServiceTest,TkOpenTiktokPublishStatusJobTest,TkOpenTiktokPublishTerminalServiceTest,TkTiktokApiClientTest,TkTiktokPublishServiceImplTest,TkTiktokScheduledMediaServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

预期：所选测试无失败，相关模块 BUILD SUCCESS；新增 mapper/迁移测试随对应任务加入所选列表。网页源发生改动后，从前端目录运行：

```powershell
& '..\..\..\..\..\.runtime\npm-global\node_modules\.bin\pnpm.cmd' ts:check
```

预期：无本次修改引入的类型错误；若出现既有错误，记录原始基线与本次差异。数据库迁移在独立测试库重复执行并检查列、索引及查询计划。

## 六、风险分析

| 维度 | 风险 | 对策 |
| --- | --- | --- |
| 性能 | 国内 OSS 跨境拉取仍可能慢；状态查询增多 | 真实试发布测量；共享用户额度、到期扫描、抖动与退避 |
| 安全 | 私有签名失效、外部 URL 滥用、敏感 URL 日志泄露 | 首版限定已有可读取自有对象；精确源校验；沿用租户权限；加密/脱敏 |
| 数据 | 初始化超时后重复发布；拉取时对象被清理 | 持久化初始化边界；已知 publish_id 仅查状态；对象引用与生命周期保护 |
| 兼容性 | 历史定时任务、FILE_UPLOAD 续传、网页入口行为变化 | 历史空值按 LEGACY；新任务固定策略；两条入口分别回归 |
| 扩展性 | 不同 App 验证不一致；多节点额度叠加 | 按实际 App 路由判断验证与预算；复用共享协调设施 |
| 运维 | 证书到期或域名/对象权限变化 | 上线前 HTTPS 探测、到期维护、拉取失败率监测 |

## 七、开发优先级、灰度与验收

### P0：形成可安全发布的闭环

Task 1～6 中的源映射、配置开关、两条入口、定时任务、初始化保护、正常状态跟踪、额度控制、对象保留和真实试发布必须一起交付。前端至少能展示拉取阶段和准确终态。

### P1：体验与运行效率

精确下载进度、元数据探测合并、按源/账号/App 的耗时与失败指标、查询批次公平性、证书续期自动化。下载字节解析可在 P0 随客户端改造完成，精确百分比展示可后置。

### P2：按测量结果进一步优化

若北京 OSS 的真实 video_pull_failed 比例或拉取耗时不达标，再评估境外 OSS / CDN 回源、私有对象自定义域名签名和按区域源路由；保持源解析接口可扩展。

### 灰度顺序

1. 校验 TikTok App 域名归属、HTTPS、目标对象及生命周期；保留现有客户端发布权限和创作者交互流程。
2. 数据库加列 → 部署默认关闭的兼容代码 → 配置单 App/单账号白名单。
3. 依次验证即时、定时和多账号任务，再按账号扩大范围；并发提升以用户额度和源端表现为上限。
4. 回滚先停止新任务采用 OSS_PULL_V1；已固定策略的待执行任务及已初始化任务继续原策略和状态同步。必要时取消尚未初始化的任务后按旧策略重建，禁止重建状态不明的执行。

### 验收条件

- 相同 object key 映射准确；原 OSS 地址仍然可用，全局存储配置未被改写。
- 新的合格即时、定时任务均提交 PULL_FROM_URL；服务端完整 GET、临时视频文件、分片 PUT 为 0。
- 只有 publish_id、没有 upload_url 的初始化响应正常进入异步状态同步。
- 任务重投、服务重启、延迟回调、429 和网络超时不造成重复初始化或终态回退。
- 每个真实试发布记录均核对最终 TikTok 状态；Direct Post 到达 PUBLISH_COMPLETE 才算发布验收通过。
- 历史 FILE_UPLOAD 与旧定时任务回归通过。
- 对照相同大小/同类视频，比较排队时间、初始化耗时、拉取/处理耗时、总耗时 p95、服务器带宽/线程/磁盘以及 OSS 出网量；以实测说明收益。

## 八、等待确认

方案确认后，从共享源解析和兼容迁移开始，依次完成两条发布入口、定时任务、异步状态及真实试发布；开发阶段提供聚焦 diff 和验证结果。生产发布另按用户授权执行。

是否开始开发？

## 官方依据

- [TikTok Media Transfer Guide](https://developers.tiktok.com/docs/en/content-posting-api-media-transfer-guide)：HTTPS、无重定向、所属 App 的 URL 归属验证、整个下载过程保持可访问以及一小时下载超时。
- [TikTok Direct Post](https://developers.tiktok.com/docs/en/content-posting-api-reference-direct-post)：PULL_FROM_URL 请求结构、upload_url 适用范围与初始化请求额度。
- [TikTok Get Post Status](https://developers.tiktok.com/docs/en/content-posting-api-reference-get-video-status)：PROCESSING_DOWNLOAD、downloaded_bytes、发布终态、失败原因及状态请求额度。
- [TikTok Content Sharing Guidelines](https://developers.tiktok.com/docs/en/content-sharing-guidelines)：对已保存在服务端的视频使用 PULL_FROM_URL 的建议。
