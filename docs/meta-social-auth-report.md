# Meta AUTH / PLATFORM 实施报告

日期：2026-09-15。范围为共享 checkout 的 AUTH/PLATFORM，已按后续指示把 Instagram VIDEO 和 Facebook VIDEO 提升为本次交付。未提交 Git、未部署、未真实授权或发布。SQL、媒体探测/存储、队列由主任务负责。

## 结果

实现独立 Instagram professional Login、短期至长期令牌交换、有效长期令牌续期；Facebook Login for Business config_id 用户登录、用户长期令牌、带任务权限过滤和安全游标分页的 Page 列表；加密授权会话、一次性 state CAS、租户/发起用户隔离、不可覆盖他人归属的绑定与安全账号返回。

最终兼容性补丁：Instagram 短期令牌响应兼容直接对象及 data 单元素对象数组，空/多元素/非对象包装直接拒绝，两种有效形状沿用相同身份与权限校验。此为防御性兼容，不声称包装形式已获官方文档确认；新增四项测试已纳入中央最终验证并通过。

账号正常状态统一为 AUTHORIZED。UNBOUND/DELETED 均显式清空凭据；删除在列表隐藏但保留主键及归属，同一所有者重新授权复用原行。令牌续期采用旧密文加 AUTHORIZED 条件写回，解绑或重授权后的旧请求不能恢复凭据。

Instagram VIDEO 使用 REELS 容器，IMAGE 使用 image_url；创建与发布分离，已有容器可恢复。Facebook VIDEO 使用 Page Reels start、CDN upload、finish、poll 四阶段，确认 ready 与 publishing complete 才成功。图文/纯文本继续使用 photos/feed；不把视频发送至照片接口。

生产传输采用直接 HttpURLConnection，避免 RestTemplate 的 DEBUG URI/请求体泄密。公开 OAuth 回调关闭应用 API 访问日志。权限代码 10/200 归为需修复权限后重新授权；写请求超时、5xx、不完整成功响应为 uncertain，禁止盲目重试。失败信息不包含原始响应或异常 cause。

## 与主任务的精确接口

```java
TkSocialAccountDO requireReadable(Long id);
String getValidToken(TkSocialAccountDO account);
void reportRejectedToken(Long accountId, String usedToken);

PublishResult advance(String platform, String externalAccountId,
    String accessToken, String mediaType, String text, String mediaUrl,
    String containerId, String platformStatus);

PublishResult reconcile(String platform, String externalAccountId,
    String accessToken, String containerId);
```

返回字段 status（WAITING/SUCCESS）、containerId、mediaId、postId、publishUrl、platformStatus。每个 WAITING 必须持久化阶段后再推进：

| 视频路径 | 持久化阶段 |
| --- | --- |
| Instagram | containerId + IN_PROGRESS/平台返回的未完成状态；FINISHED 后发布 |
| Facebook | videoId 存 containerId；FB_STARTED → FB_UPLOADED → FB_FINISH_SUBMITTED → 确认 PUBLISHED |

Facebook 上传地址从固定官方主机、配置版本和返回 videoId 重建，不需要新增 uploadUrl 字段。主队列手动重试仅对 IG_CONTAINER_ERROR、IG_CONTAINER_EXPIRED、FB_VIDEO_PROCESSING_FAILED、FB_VIDEO_ERROR 清除容器及阶段；其他错误保留检查点，UNKNOWN 不自动重新提交。

getValidToken 需要明确匹配的 TenantContextHolder，且不能处于 ignore 模式；后台 worker 应使用 TenantUtils.execute(tenantId, ...)。

reportRejectedToken 同样要求明确非忽略租户上下文，重新读取 AUTHORIZED 账号并比较当前解密令牌与实际发送令牌，仅匹配时以旧密文 CAS 标记 REAUTH_REQUIRED。已经重新授权、解绑或其他租户的账号不受旧请求拒绝影响。主 worker 仅在确实发送且得到 reauthRequired 时调用；另有四项回归测试覆盖这些条件。

## 官方资料与视频边界

新增 reconcile 为严格只读恢复，仅使用 GET。FB 与 advance 复用状态查询 helper，已发布时返回相同 ID/URL，处理中返回 WAITING/FB_RECONCILING；IG PUBLISHED 确认成功但不编造 mediaId/postId/URL，FINISHED 只返回 WAITING。终态错误码保持不变。主 worker 对 UNKNOWN+已知 ID 手动同步后应持续使用只读轮询模式，不得切回 advance。此次只调整平台客户端、新增平台测试及更新本报告，不修改 auth。新增七项测试已纳入中央最终验证并通过。

已读取 [Meta 官方 Facebook Postman 集合](https://www.postman.com/meta/facebook/documentation/r56bjfd/facebook-api)、[Instagram 集合](https://www.postman.com/meta/workspace/instagram/documentation/23987686-9386f468-7714-490f-9bfc-9442db5c8f00) 和 [Instagram Login 说明](https://www.postman.com/meta/instagram/folder/6raa77c/instagram-api-with-instagram-login)。

保守范围已告知主任务：Facebook Reels 9:16、至少 540×960、4–60 秒、至少 23 FPS；Instagram Reels 3–900 秒、23–60 FPS、宽度最高 1920，9:16 为推荐，不伪造强制比例上下限。本交付进一步收敛为 100MB MP4 H264/AAC，主任务负责探测校验。官方集合部分媒体示例较旧，因此不将这些条件声称为 Meta 所有现行产品最大范围。未实现常规 Page /videos 横屏路径。

直接 Meta developer 登录/交换/续期/Reels 文档本次返回 429。实际 App 控制台、config_id 令牌类型、审核权限、长期交换/续期、真实视频发布均待配置后验收。代码不请求 publish_video。

## 测试与核验状态

最终验证通过。已读取权威记录 [Meta 视频发布本地验证](meta-social-verification.md)：中央后端验证共 120 项通过，0 失败、0 错误、0 跳过，其中 91 项 Meta 测试、29 项已有 TikTok 回归。全部包含最终只读 reconciliation、包装令牌响应、reportRejectedToken 四项回归、H2 解绑后 CAS 测试及 Meta 专用代理测试。

按用户要求先写测试，中央 RED 已确认缺少新类型，最终 GREEN 由主任务统一执行。本分工未运行 Maven；此次仅更新报告，没有修改源码或重跑测试。真实授权、权限审核和发布仍需配置应用与账号后验收，本地测试不代表已真实发布。

测试类：

- TkSocialPlatformClientTest：IG 图像容器恢复、已发布不可重提、Page 分页/任务过滤、独立 IG 凭据、长期交换/刷新、错误分类。
- TkSocialVideoPlatformTest：IG REELS 参数、处理中/终态失败；FB 四阶段恢复、真实发布成功判断、finish 结果不确定、不误走照片接口。
- TkSocialReconcileTest：所有请求强制断言 GET 且 mutation=false；IG FINISHED 不发布、PUBLISHED 不编造媒体 ID；FB finish 结果未知后的成功确认、发布前/处理中仅等待；两平台终态错误及 GET 临时失败分类。
- TkSocialHttpTransportTest：真实传输方法/请求头/编码在 HTTP connection stub 上验证，关闭重定向、资源关闭、5xx/权限/无效响应/IO 错误脱敏，应用 DEBUG 日志无测试令牌。
- TkSocialAuthSecurityTest：加密随机 nonce/AAD/篡改、跨用户会话、过期/重放/错平台、跨租户后台令牌、篡改选页、安全返回、同所有者复用、过期令牌拒绝刷新、续期 CAS 失败不恢复账号。
- TkSocialSessionPersistenceTest：H2 运行真实 Mapper SQL，证明一次性消费、过期拒绝、完成/过期清空载荷及解绑后续期 CAS 写入失败。
- TkSocialAuthControllerSecurityTest：回调允许匿名、忽略外部租户头要求、禁用敏感查询日志；轮询需授权权限。

配置和操作说明在 docs/meta-social-setup.md。限定变更路径的格式检查已通过。最新验证结果与复现命令以 [docs/meta-social-verification.md](meta-social-verification.md) 为准。

## 新增文件

Java 路径相对 yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/：

- service/social/auth/TkSocialProperties.java
- service/social/auth/TkSocialTokenCipher.java
- service/social/auth/TkSocialAuthService.java
- service/social/auth/TkSocialAccountService.java
- service/social/platform/TkSocialPlatformClient.java
- service/social/platform/TkSocialHttpTransport.java
- service/social/platform/TkSocialPlatformException.java
- controller/admin/social/TkSocialAuthController.java
- controller/admin/social/TkSocialAccountController.java
- dal/dataobject/social/TkSocialAccountDO.java
- dal/dataobject/social/TkSocialAuthSessionDO.java
- dal/mysql/social/TkSocialAccountMapper.java
- dal/mysql/social/TkSocialAuthSessionMapper.java

测试位于 yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/service/social/auth/ 与 platform/，名称见上。文档为真实工作区 docs/meta-social-setup.md 和 docs/meta-social-auth-report.md，没有使用 undefined/docs。
