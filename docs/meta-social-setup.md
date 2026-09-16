# Meta 社交账号及视频发布配置

本次交付包含 Instagram 专业账号直接登录、Facebook Login for Business 的 Page 选择，以及 Instagram Reels / Facebook Page Reels 视频发布。Facebook 图文和纯文本、Instagram 单图为补充能力。代码默认关闭；本次没有部署，也没有进行真实授权或发布。

## 开启前配置

先执行数据库增量脚本，再显式开启。以下放入服务端环境配置，真实密钥由环境变量或部署密钥管理系统注入，不写入仓库：

```yaml
tk:
  social:
    enabled: ${TK_SOCIAL_ENABLED:false}
    graph-version: ${TK_SOCIAL_GRAPH_VERSION:v25.0}
    encryption-key: ${TK_SOCIAL_ENCRYPTION_KEY:}
    session-ttl-minutes: 10
    proxy:
      enabled: ${TK_SOCIAL_PROXY_ENABLED:false}
      host: ${TK_SOCIAL_PROXY_HOST:}
      port: ${TK_SOCIAL_PROXY_PORT:0}
    instagram:
      app-id: ${TK_SOCIAL_INSTAGRAM_APP_ID:}
      app-secret: ${TK_SOCIAL_INSTAGRAM_APP_SECRET:}
      redirect-uri: ${TK_SOCIAL_INSTAGRAM_REDIRECT_URI:}
    facebook:
      app-id: ${TK_SOCIAL_FACEBOOK_APP_ID:}
      app-secret: ${TK_SOCIAL_FACEBOOK_APP_SECRET:}
      config-id: ${TK_SOCIAL_FACEBOOK_CONFIG_ID:}
      redirect-uri: ${TK_SOCIAL_FACEBOOK_REDIRECT_URI:}
```

加密密钥必须是随机 32 字节密钥的 Base64，保存后应备份。AES-GCM 使用随机 nonce 和租户/账号或租户/发起用户/会话作为认证上下文。直接替换密钥会导致已有凭据无法解密；当前版本不提供在线密钥轮换。不得复用或修改 TikTok 的加密配置。

API 版本可配置，示例版本并不代表对 Meta 最新版本的宣称。两个平台的 app-id/app-secret 独立配置，不假设 Instagram 产品凭据与 Facebook App 凭据相同。

`tk.social.proxy` 仅作用于本模块白名单内的 Meta HTTP 传输，不设置 JVM 或操作系统全局代理，因此不会改变 Gemini、DashScope、OSS 等其他客户端的网络出口。启用时必须同时配置 HTTP 代理主机和 1-65535 范围内的端口；未启用时 Meta 请求保持直连。

数据库脚本位于产品根目录的 `yudao-module-tk/src/main/resources/sql/tk_social_publish_center_mysql.sql`。它创建 5 张独立表，并在已有发布中心菜单下补充 6 个权限；不会自动授予用户角色或租户套餐。需在管理后台把 `tk:social-account:query/authorize/update` 和 `tk:social-publish:query/create/retry` 对应菜单加入目标租户套餐及操作角色，保留已有发布中心入口权限。使用系统成片选择器还需要 `tk:generation:query`。无已有发布中心父菜单时，权限插入不会执行，先恢复父菜单再重跑脚本。

视频探测及发布副本处理复用 `tk.generation.ffmpeg.ffprobe-path/ffmpeg-path` 或 `FFPROBE_PATH/FFMPEG_PATH`；必须安装可运行的 ffprobe 和 FFmpeg。文件存储需提供外网可读取的 HTTPS URL，并支持至少 24 小时的有效读取链接。服务端会把系统成片复制到独立的 `tk/{tenant}/{company}/social-media` 存储目录；不直接引用会被成片清理流程删除的源文件。不要对该目录套用 TikTok 素材清理规则。创建失败或并发幂等请求落败的成片快照会补偿清理；每小时扫描并回收超过 24 小时且没有任务引用的上传，以及中断的清理。媒体行锁协调任务创建与清理，已被任务引用的媒体会保留。

Nginx 和应用 multipart 大小应允许 100 MiB 视频及表单开销；媒体上传/成片复制需要足够的请求超时。应用现有基础配置允许 1GB 文件，本次不修改生产 Nginx 或服务配置。发布模块单实例同时准备一个媒体文件、两个平台执行线程，避免大视频无限并发分配内存。

## Instagram 专业账号直接登录

在 Meta 应用的 Instagram API with Instagram Login 产品中配置专业账号和 Instagram 专用凭据。此流程不要求绑定 Facebook Page，也不使用已退役的 Basic Display 或 TikTok refresh_token。

回调配置为：

```text
https://YOUR_API_DOMAIN/admin-api/tk/social-auth/instagram/callback
```

精确匹配后台配置的 HTTPS URL。所需权限为 instagram_business_basic、instagram_business_content_publish。开发模式下使用应用允许的测试者/专业账号；向应用外用户开放前，按 Meta 应用控制台要求完成相应权限审核及发布状态配置。

服务端依次执行：

1. Instagram OAuth 授权码交换短期令牌。
2. graph.instagram.com/access_token 的 ig_exchange_token 长期交换。
3. 验证专业账号身份及已授予权限，按实际 expires_in 保存期限。
4. 有效长期令牌临近到期时，以 ig_refresh_token 调用 refresh_access_token。至少授权 24 小时且未过期才尝试刷新；过期要求重新授权。每小时维护任务也会检查临近到期账号。

可访问的 [Meta 官方 Postman Instagram Login 说明](https://www.postman.com/meta/instagram/folder/6raa77c/instagram-api-with-instagram-login) 确认专业账号、独立 Login 路径及上述新权限名。直接 [Business Login 文档](https://developers.facebook.com/docs/instagram-platform/instagram-api-with-instagram-login/business-login/)、[长期交换](https://developers.facebook.com/docs/instagram-platform/reference/access_token/) 和 [续期文档](https://developers.facebook.com/docs/instagram-platform/reference/refresh_access_token/) 在本次读取时返回 429；真实应用交换及续期尚待配置后验收。

## Facebook Login for Business 与 Page

在 Facebook App 配置 Login for Business，并创建返回用户访问令牌的配置，将其 config_id 填入上述配置。不要选择仅系统用户令牌的配置来替代本交付的用户登录链路。

回调：

```text
https://YOUR_API_DOMAIN/admin-api/tk/social-auth/facebook/callback
```

配置并审核 pages_show_list、pages_read_engagement、pages_manage_posts 权限。代码不请求 publish_video。Facebook 授权 URL 使用 config_id 和 response_type=code，不把 Instagram 直接登录权限混入 Facebook Login。

授权码换用户令牌，再执行 fb_exchange_token 长期用户令牌交换。读取 /me/permissions，分页读取 /me/accounts 的 id、name、access_token、tasks。只接受 CREATE_CONTENT 或 PROFILE_PLUS_CREATE_CONTENT 的 Page，并只允许绑定本次服务端候选列表中的 ID。分页使用 after 游标重建官方地址，绝不访问响应里的任意 next URL。

选页前，Page 凭据仅在带期限的会话密文中保存；不返回前端。用户令牌在候选列表生成后从临时载荷中移除。Page token 缺少固定到期时间不等于永久有效：使用前会检查令牌对应的 App、Page 身份、权限及有效性，验证缓存最长 15 分钟。debug_token 返回期限时保存实际期限，失效要求重新授权。

[Meta 官方 Facebook Postman 集合](https://www.postman.com/meta/facebook/documentation/r56bjfd/facebook-api) 提供用户到 Page 令牌、tasks、Reels 分阶段接口及上传头示例；[Login for Business 开发者文档](https://developers.facebook.com/docs/facebook-login/facebook-login-for-business/) 本次返回 429。App 配置选项及审核结果应在真实控制台验收，不以本地测试替代。

## 本次视频兼容范围

服务端执行 MP4 文件探测、独立存储快照及入队前校验。交付文件上限为 100 MiB、MP4、H.264/AAC；视频码率不超过 25Mbps。音频不符合 AAC 48kHz、128kbps 以内时，仅在发布副本中转换为 AAC 48kHz / 96kbps 并重新检查，保留视频流及原始成片。这兼容系统当前 44.1kHz / 192kbps 的配音成片；不会修改现有生成器。视频画面尺寸、时长或帧率不合规时仍返回提示，不自动裁剪或重编码视频。

| 平台 | 本次应校验的视频条件 | 发布路径 |
| --- | --- | --- |
| Instagram | 3–900 秒；23–60 FPS；宽度不超过 1920；9:16 为推荐而非本次已证实的强制条件 | media_type=REELS + video_url → 容器状态 → media_publish |
| Facebook | 4–60 秒；23–60 FPS；9:16；至少 540×960 | Page video_reels start → rupload CDN 上传 → finish → 状态查询 |

Instagram 官方集合同时列出 AAC 48kHz、视频最高 25Mbps、音频最高 128kbps、文件最高 1GB；这些资料见 [Meta Instagram 集合 Reels 部分](https://www.postman.com/meta/workspace/instagram/documentation/23987686-9386f468-7714-490f-9bfc-9442db5c8f00)。其中 Reels 参数位于 Facebook Login 示例部分，本交付保守复用于直接 Instagram Login 的 Reels，不将未核实的宽高比最小/最大值编造成官方限制。

Facebook 集合中的媒体条件及部分示例版本较旧，本交付使用其明确可证实的保守范围。横屏生成视频不宣称支持 Facebook Reels；入队前应提示调整视频。未实现未经本次 primary docs 核实的常规 Page /videos 路径。

## 队列接入契约

```java
PublishResult advance(String platform, String externalAccountId,
    String accessToken, String mediaType, String text, String mediaUrl,
    String containerId, String platformStatus);
```

每个 WAITING 都持久化 containerId、platformStatus 后再推进。每次调用最多执行一个发布写阶段：

- IG：创建容器后 WAITING；已有容器只轮询；FINISHED 才 media_publish。ERROR/EXPIRED 失败；已 PUBLISHED 且本地没有帖子 ID 时进入人工核验，不能再次发布。
- Facebook VIDEO：FB_STARTED → FB_UPLOADED → FB_FINISH_SUBMITTED；最终仅当 video_status=ready 且 publishing_phase.status=complete 才 SUCCESS。上传 success=true 或 finish success=true 都不是已发布证明。
- FB video ID 存 containerId。CDN 上传地址从固定官方 rupload.facebook.com/video-upload/{version}/{videoId} 构造，上传 URL 不需要额外持久化。Authorization 为 OAuth Page token，视频地址放 file_url 请求头。
- 写请求超时、5xx、无有效响应标记 uncertain；主队列进入 UNKNOWN，不自动重发。读取状态的临时失败可重试，明确限流拒绝可按队列策略重试。失败信息只保留安全分类和错误码，不回传 Meta 原始错误文本。
- IG_CONTAINER_ERROR、IG_CONTAINER_EXPIRED、FB_VIDEO_PROCESSING_FAILED、FB_VIDEO_ERROR 是明确终态失败。用户重试时主队列应清空旧 containerId/platformStatus 再创建新容器；权限或网络错误不能套用此重建规则，UNKNOWN 不允许盲目重发。

媒体地址来自独立发布存储，不接受最终用户传入任意 URL。确认的发布结果不会因后续 permalink 查询失败而退回失败重发。任务创建使用租户、创建者和请求幂等键去重；一个任务下的每个账号独立记录结果。

队列每 5 秒扫描已提交任务；领取明细使用 5 分钟租约，领取后重新读取最新进度。结果写入同时校验租约标识，并与主任务计数在同一事务提交。平台处理通常每 15 秒查询，最多 240 次；明确可重试的临时失败最多自动重试 3 次。工作进程中断、写请求结果不明或处理超时进入 UNKNOWN，禁止自动及手工重发。

界面的“核对状态”会为已有容器/视频 ID 的 UNKNOWN 安排独立只读查询：Facebook 检查最终发布阶段，Instagram 检查容器状态。查询成功可恢复 SUCCESS；明确确认处理失败才转 FAILED。查询期间始终保留 UNKNOWN，权限不足也不会退回可重发状态；重新授权后可再次核对。Instagram 容器已发布但本地缺少媒体 ID 时，只确认发布成功，不伪造帖子 ID 或链接。没有任何远端 ID 时无法可靠自动对账，需要人工到平台核验。

## 会话、归属与运行要求

OAuth state 为 32 字节随机数，数据库保存 SHA-256 摘要；PENDING → PROCESSING 通过带 TTL 的条件更新消费一次。公开回调根据 state 决定租户，不信任 URL 的租户或用户参数。轮询、选页及绑定同时检查发起用户与租户。

回调使用 PermitAll 和 TenantIgnore；当前框架会把 TenantIgnore 控制器加入无租户头可访问列表。回调关闭应用 API 访问日志，防止授权码/state 进入业务日志。部署时还需保证反向代理/链路追踪不记录回调查询串、OAuth 交换 URL、Authorization 或 file_url 以外的敏感令牌内容；本次未改服务器配置。

生产 HTTP 传输直接使用 HttpURLConnection，不经过 RestTemplate 的 URI/请求体 DEBUG 日志。POST 使用固定长度流式发送、关闭重定向并限制响应大小。HTTP 10/200 权限拒绝需要修复应用审核或 Page 权限后重新授权，不能靠重复发布解决。

账号状态为 AUTHORIZED、REAUTH_REQUIRED、UNBOUND、DELETED。解绑或删除都会显式清空密文和令牌期限。删除保留同一主键和原归属但在列表隐藏；同一租户、公司、创建者重新授权时复用记录，避免布尔软删除唯一键冲突。其他创建者不能通过重新授权夺取绑定。

会话完成/过期清空临时密文，分钟级维护清理过期载荷。启用前应确保 Spring 定时任务处于运行状态。长期令牌刷新写回用旧密文条件更新，防止旧请求恢复已解绑账号。

## 验收边界

本地测试覆盖协议参数、阶段恢复、HTTP 请求头、错误分类、数据隔离和会话 CAS。主任务统一运行 Maven，避免共享构建目录并发。真实 App ID、Secret、config_id、权限审核、专业账号和 Page 准备后，还需真实浏览器 OAuth、撤销/到期处理、单账号视频发布及双平台独立结果验收；本次没有调用真实发布接口。
