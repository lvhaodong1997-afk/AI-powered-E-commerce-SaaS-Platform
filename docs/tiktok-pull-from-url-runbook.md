# TikTok 自定义域名 PULL 发布运维说明

## 行为

新建任务优先尝试自有 OSS 视频直拉；域名、账号白名单、对象地址或 HEAD 预检不符合条件时，在调用 TikTok 初始化之前走原 FILE_UPLOAD 流程。原素材 URL 与全局 OSS publicBaseUrl 保持不变。

- 原 Bucket：`tk-video-create.oss-cn-beijing.aliyuncs.com`。
- 拉取域名：`https://tkpublish.fnn.net.cn`，使用 OSS 原生自定义域名绑定和 DNS CNAME。
- 网页和 Open API 共用解析器；新定时任务可只保存对象引用，历史任务按原策略执行。
- 新任务创建时记录 `transfer_policy`：OSS_PULL_V1 或强制 FILE_UPLOAD；LEGACY / 空值只保留历史行为。OSS_PULL_V1 实际执行时再次预检，失败仍可回退旧上传。
- 已有 publish_id 或初始化结果不明确时，停止源切换和自动重发，继续状态查询或进入待核查。
- TikTok 明确失败的任务可通过已有重试入口创建下一次执行；正在拉取的任务不能凭本地超时自行重新发布。
- 用户确认历史数据不做修复或迁移。历史 FAILED 且仍有 publish_id、缺少远端失败证据的记录保持原样，禁止清空 ID 后重发；本次不增加历史状态纠正流程。新执行只有收到官方失败状态并持久化 REMOTE_FAILED，才允许重试已有 ID 的失败任务。

## 上线前置条件

1. 当前代码未自行修改生产；先在测试环境执行迁移并验证。
2. 每个实际使用的 TikTok App 的 URL Properties 必须验证该域名或覆盖它的属性。备案、证书和 CNAME 不代替 TikTok 归属验证。
3. 目标 HTTPS URL 返回视频本身，HEAD 为 200、Content-Length 为有效大小；不经过 301/302、登录页面或挑战页。
4. 使用已有可匿名读取的自有 OSS 对象。自有 OSS URL 带已知 V1 / V4 签名、STS 参数或 response-content-disposition 时，解析器只提取原始对象路径，生成自定义域名下的无参数地址，再以匿名 HEAD 预检；原签名不透传，不依赖其有效期。私有对象匿名预检失败时沿用 FILE_UPLOAD，不修改对象 ACL。
5. 对象 key 不覆盖，OSS 生命周期覆盖定时等待、拉取及故障核查时间。TikTok 下载期间持续保持可访问；外部手工删除和 OSS 生命周期不受应用事务控制。
6. 共享 Redis/Redisson 可用，账号 open_id 与实际应用身份完整，用于多个入口及节点共享请求额度。
7. 证书持续有效。此前颁发证书到期时间为 2027-01-27 14:12（北京时间），上线时重新核对实际证书；续期需同步到 OSS 域名 HTTPS 配置。

## 数据库与配置

先备份，再在目标数据库执行产品源根下：

`yudao-module-tk/src/main/resources/sql/tk_tiktok_pull_from_url_upgrade_mysql.sql`

这是新增可空字段与索引的幂等迁移，不重写历史任务。全新安装按原顺序建立 Open API、网页发布、attempt 表，再执行该迁移。升级脚本需在独立 MySQL 测试库重复执行并核对实际查询计划后用于生产。

在现有 TIKTOK 配置中按租户/实际应用设置：

| key | 值 | 说明 |
| --- | --- | --- |
| verified-pull-domain | tkpublish.fnn.net.cn | 必须与该应用已验证属性一致；也可使用正确的 HTTPS URL Prefix |
| pull-from-url-enabled | false，灰度时改 true | 默认关闭，新任务策略选择开关 |
| pull-origin-host | tk-video-create.oss-cn-beijing.aliyuncs.com | 可映射的精确源 host |
| pull-base-url | https://tkpublish.fnn.net.cn | HTTPS 根地址 |
| pull-account-allowlist | 首轮账号的数字 ID，多个用英文逗号 | 空名单不启用；明确填 `*` 才对所有账号启用 |

网页入口白名单使用 tk_tiktok_account.id，Open API 使用 tk_open_tiktok_connection.id；两个入口的数字 ID 属于不同表，存在相同值时会同时命中，灰度前核对名单。域名验证由实际使用 App 决定，不能将一个 App 的验证结果套用到另一 App。

HTTP 初始化上限为每用户 access token 每分钟 6 次，状态查询上限为每分钟 30 次。实现按实际 App key + open_id 建共享额度，令牌刷新不重置额度。状态同步采用单次 HTTP 请求，失败交给后续调度退避。

正常状态扫描每 10 秒执行，单任务按持久化的轮询间隔逐步采用 10 / 20 / 30 / 60 秒并加入约 10% 抖动，实际查询还受额度与恢复退避约束。网页状态同步的批量事务已移除，领取与结果写入使用短条件更新，避免跨网络请求持有整批行锁。初始化状态不明且没有 publish_id 的记录，超过 15 分钟只标记为待核查，不自动重发。

HEAD 成功结果在单进程、明确租户上下文内缓存 20 秒，最多 1024 条；同租户同目标 URL 的并发探测合并。每次仍检查开关、白名单和 URL Property，失败不缓存。无租户上下文时逐次 HEAD；多节点不共享该元数据缓存。对象必须使用不可覆盖的 key，不能依赖这段缓存证明拉取期间对象未改变。

### 带查询参数的素材地址

网页与 Open API 的任务创建、执行预检共用相同解析规则。仅允许移除以下精确参数名：OSSAccessKeyId、Expires、Signature、security-token、x-oss-signature-version、x-oss-credential、x-oss-date、x-oss-expires、x-oss-signature、x-oss-security-token、x-oss-additional-headers、response-content-disposition。路径编码原样保留，数据库中的素材原始地址保持不变，供原上传流程使用。

versionId、x-oss-process、response-content-type 及其他未知参数均不移除，判为 UNSUPPORTED_QUERY_PARAMETERS 并使用 FILE_UPLOAD；重复参数、编码后的参数名、缺少值或异常分隔符同样不进入 PULL。配置中的 pull-base-url 和已验证 URL Prefix 仍禁止携带查询参数。

定时任务创建时先完成匿名 HEAD，确认当前可直拉后才省略本地素材准备；匿名不可读或预检失败时，立即按旧流程保存素材并固定 FILE_UPLOAD，避免到点时原签名过期。到点执行 PULL 时再次预检。匿名读取成功不代表对象会一直存在，仍需遵守对象不可覆盖和生命周期保留约定。

PULL 资格以无参数目标地址的匿名访问结果为准；原地址有签名不代表对象必须私有。无参数地址返回 403、重定向、非视频类型、异常大小或探测失败时，在 TikTok 初始化前回退 FILE_UPLOAD。已经取得 publish_id 或初始化结果不明确时，仍禁止切换来源重新初始化。新规则只影响后续创建任务，不改写历史 transfer_policy 或重新发布已成功任务。

## 灰度步骤

1. 保持开关 false，部署数据库兼容代码并回归一条旧 FILE_UPLOAD 任务。
2. 验证目标域名及对应 App URL Property，设置一个账号的白名单后开启。
3. 使用获授权的测试素材与发布设置，依次测试即时、定时、同素材多账号发布。
4. 记录任务 ID、publish_id、实际传输方式、失败原因及最终平台状态；敏感 URL 与令牌脱敏。
5. 检查即时和定时的合格 PULL 路径没有完整视频 GET、临时视频文件、分片 PUT；初始化无 upload_url 应正常工作。
6. 测试预检失败回退、查询 429、初始化超时、服务重启和回调重复投递；状态不回退，初始化不重复。
7. 与同类视频旧流程对比排队时间、总耗时、应用服务器流量/磁盘/线程和 OSS 出网成本，再扩大账号范围。

HTTPS 可访问、单元测试通过及 API 初始化成功，均不代替真实发布验收。Direct Post 必须到达 PUBLISH_COMPLETE；SEND_TO_USER_INBOX 仅表示送达草稿收件箱。

## 常见情况

| 现象 | 检查 |
| --- | --- |
| 仍走 FILE_UPLOAD | 新任务的策略、开关、白名单 ID、源 host、查询参数是否受支持、URL Property 配置及匿名预检原因码 |
| url_ownership_unverified | 核对授权该账号的实际 App、Domain/URL Prefix 和提交地址，不只检查网站域名配置 |
| PROCESSING_DOWNLOAD | TikTok 正在拉取；保持对象可访问，等待状态查询或回调 |
| RECOVERY_REQUIRED | 初始化可能已经被接受；保留任务与源对象，查询已有 publish_id，禁止清空后直接重发 |
| 历史 FAILED 带 publish_id，重试被拒绝 | 历史本地失败不代表官方失败；按用户要求保留历史记录，本次不批量改写或自动重发 |
| video_pull_failed | 检查 OSS 请求日志、证书、对象访问权限及跨境连通性；确认平台失败后再走显式重试 |
| 状态查询延后 | 检查共享 Redis、应用/用户身份、请求额度和 next_poll_time，不通过连续手动查询绕过额度 |

## 回滚

关闭 pull-from-url-enabled 会停止新任务选择新策略，旧流程继续提供发布能力。已有 PULL 任务继续跟踪原 publish_id；已固定 OSS_PULL_V1 的待执行任务在执行时重新判断配置，必要时在初始化前使用原流程。

保留新增数据库字段，避免兼容代码回滚因缺列失败。不要重建状态不明的执行，不删除仍在处理或核查中的源对象。

## 验证记录范围

本地验证记录见开发交付结果。MySQL 真实迁移、生产 Redis 并发、TikTok App 属性验证和真实视频发布须分别记录，不能用 H2/Mock 或本地 HTTPS 探测替代。
