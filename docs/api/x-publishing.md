# X OpenAPI

X OpenAPI 按 HMAC clientId 隔离，和 TikTok 授权模式对齐。连接本身不再绑定租户、公司、负责人或 endUserId。endUserId 只作为接入方可选元数据保存，平台不把它当作隔离边界，也不宣称能阻止一个可信 HMAC 客户端代选其任意终端用户。

官方 X OAuth redirect_uri 仍然是平台后端的 TK_X_REDIRECT_URI（标准路径 /admin-api/tk/channel-publish/x/callback）。不要把它改成 C 端站点。浏览器完成页是可选的：创建授权会话时可传已登记的 returnUrl 和 clientState。未传 returnUrl 时，授权完成后仍是关闭页面并由接入方后端轮询会话。

## 开通

默认关闭。即使调用方已有 auth / media / publish，也不会自动获得 X。需要同时满足：

1. tk.x-open-api.enabled=true 或环境变量 TK_X_OPEN_API_ENABLED=true
2. 在开放 API 管理页为该调用方显式勾选 x.auth 和/或 x.publish
3. 平台级 X 发布已配置（TK_X_CLIENT_ID / TK_X_CLIENT_SECRET / TK_X_REDIRECT_URI 与令牌加密密钥）

tk.x-open-api.grants 已被忽略，保留只是为了旧配置还能启动。不要再靠 grants、tenant、owner 做访问控制。

## 迁移

在已执行 tk_open_x_upgrade_mysql.sql 和 tk_open_x_multiuser_upgrade_mysql.sql 之后，再执行可重复的 tk_open_x_client_isolation_upgrade_mysql.sql。该脚本：

- 增加调用方 return_url_whitelist 字段，以及专用的连接、授权会话、回跳票据、媒体和发布任务表
- 把历史上带 open_client_id 的已完成 X 会话复制为专用连接，不改工作台账号，不跨 client 重分配
- 不复制令牌密文。首次 OpenAPI 使用时才会把源社交账号中的令牌读出并写入专用连接的新加密上下文，之后刷新或撤销只改该连接，不回写工作台

回滚：保留这些新表和字段，回退 JAR 即可。不要删除历史社交账号或已复制的连接行。

## 授权

1. POST /admin-api/tk/open/v1/x/auth/sessions，HMAC + x.auth。可选 returnUrl、clientState、endUserId。
2. 打开返回的 authorizeUrl 完成同意页，再跳转 X。OAuth state 是随机值并只存哈希，一次性且有 TTL，继续使用 PKCE S256。
3. 无 returnUrl：关闭页面，用 GET /admin-api/tk/open/v1/x/auth/sessions/{authSessionId} 轮询。轮询不消耗票据。
4. 有 returnUrl：平台只把已校验白名单地址加上一次性 ticket（失败时另有 error=auth_failed）。回跳不含 X token、refresh 或 HMAC secret。接入方后端用 HMAC 调用 POST /admin-api/tk/open/v1/x/auth/tickets/redeem 原子消费票据。重放、跨 client、过期都会拒绝。浏览器回跳重试不会重做 OAuth；未消费的同一票据可再次出现在回跳中。
5. 服务端通知复用现有 authCallbackUrl 投递网关（签名、超时、重试、SSRF 防护、幂等），事件为 authorization.completed / authorization.failed。通知载荷和日志只有会话与连接标识，不含 token 或原始异常。

returnUrl 白名单在开放 API 管理页配置，精确匹配 scheme、host、port、path。拒绝 userinfo、fragment、query、非 HTTPS、后缀通配、自定义 scheme。不要把客户端传来的 query 透传到回跳地址。开发环境如需 http://127.0.0.1 或 http://localhost，必须显式打开 tk.x-open-api.allow-insecure-loopback-return-url（默认 false），且该地址仍须出现在该调用方白名单中。生产环境不要打开这个例外。

## 媒体与发布

发布不再接受任意 generationTaskId，也不拉取外部 URL。流程：

1. POST /admin-api/tk/open/v1/x/media/uploads（需要 x.publish）初始化本 client 的上传会话
2. 本地分片或 OSS 直传后，POST .../complete。完成时校验内容类型、大小、哈希、未过期，且状态必须仍是上传中。失败上传不能改成完成，也不能用外部 URL 或 object key 冒充归属
3. POST /admin-api/tk/open/v1/x/publish/tasks，请求体使用本 client 的 mediaId + connectionId，并携带 Idempotency-Key。相同幂等键且不同 payload 返回 409。查询任务同样按 client 隔离
4. 后台执行会再次校验 client、连接仍为该 client 的 AUTHORIZED、媒体仍可用。撤销后待执行任务会被拒绝。错误不会包装成成功

GET /admin-api/tk/open/v1/x/connections 列出该 HMAC 客户端自己的连接。DELETE /admin-api/tk/open/v1/x/connections/{connectionId} 只把该 client 的这条连接标为撤销，不调用 X 全局吊销，也不改其他 client 或工作台账号。同一 X 账号被不同 client 授权时，各有独立令牌副本；同一 client 对同一 X 账号重新授权会替换该连接上的令牌。若多个 client 仍共享同一个 X 开发者应用，平台侧撤销互不影响，但 X 平台自己轮换 refresh token 的行为属于残留风险。

没有 open_client_id 的工作台账号不会被自动收编进 OpenAPI。工作台 X 发布路径保持不变。

## 验收要点

- 跨 client 的连接、媒体、任务、票据一律拒绝
- 已知他户 generationTaskId 被拒绝
- 旧 client 连接不跨挂；同 client 可有多个 X 账号
- state / ticket 过期、重放、跨 client、returnUrl 白名单绕过均拒绝
- 无 returnUrl 时仍是关页 + 轮询
- 同幂等键不同 payload 返回 409；撤销后待执行任务拒绝；未授权或停用 client 拒绝
- 去掉 grants 后，原 auth/publish 客户端不会自动获得 X
- 工作台发布不回归；不做真实发帖或真实撤销用户授权