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
- 不复制令牌密文。历史连接在客户端重新授权前不能发布。不会为了“兼容”去读取或轮换工作台社交账号上的 refresh token，因为两端共享同一份 refresh 会在任一方刷新后互相失效。这不是无回归：已迁移但尚未重新授权的 OpenAPI 连接需要重新走授权。
- 同一 client 与同一 X 外部账号只有一行，数据库有唯一约束。多余重复行会被撤销、清空令牌密文和懒复制来源，并把 external_account_id 加上 #dup-{id} 后缀后软删，历史行保留。
- 社交表 platform 按 utf8mb4_bin 比较，写入常量是小写 x，迁移同时匹配 x 与 X。
- 撤销某条连接时，会同时处理该 client 下同一外部账号的其他活跃重复行，并清空 token cipher 与 source_social_account_id。

回滚：保留这些新表、字段和唯一约束，回退 JAR 即可。不要删除历史社交账号或已复制的连接行。已 tombstone 的重复行无需还原即可让 JAR 回退。

## 授权

1. POST /admin-api/tk/open/v1/x/auth/sessions，HMAC + x.auth。可选 returnUrl、clientState、endUserId。
2. 打开返回的 authorizeUrl 完成同意页，再跳转 X。OAuth state 是随机值并只存哈希，一次性且有 TTL，继续使用 PKCE S256。
3. 无 returnUrl：关闭页面，用 GET /admin-api/tk/open/v1/x/auth/sessions/{authSessionId} 轮询。轮询不消耗票据。
4. 有 returnUrl：平台只把已校验白名单地址加上一次性 ticket（失败时另有 error=auth_failed）。回跳不含 X token、refresh 或 HMAC secret。接入方后端用 HMAC 调用 POST /admin-api/tk/open/v1/x/auth/tickets/redeem 原子消费票据。重放、跨 client、过期都会拒绝。浏览器回跳重试不会重做 OAuth；未消费的同一票据可再次出现在回跳中。会话已进入 SUCCESS/FAILED 终态后不会再签发新票据；票据已被消费时，回跳最多带 error=already_completed，不会再发一张可用 ticket。
5. 服务端通知复用现有 authCallbackUrl 投递网关（签名、超时、重试、SSRF 防护、幂等），事件为 authorization.completed / authorization.failed。失败通知和授权会话查询返回受控的 `failReason`，例如 `AUTH_DENIED`、`AUTH_EXPIRED`、`CONSENT_MISMATCH`、`X_ACCOUNT_ALREADY_BOUND` 或 `CHANNEL_HTTP_429`；不含 token 或原始异常。

returnUrl 白名单在开放 API 管理页配置，精确匹配 scheme、host、port、path。拒绝 userinfo、fragment、query、非 HTTPS、后缀通配、自定义 scheme。不要把客户端传来的 query 透传到回跳地址。开发环境如需 http://127.0.0.1 或 http://localhost，必须显式打开 tk.x-open-api.allow-insecure-loopback-return-url（默认 false），且该地址仍须出现在该调用方白名单中。生产环境不要打开这个例外。

## 媒体与发布

发布不再接受任意 generationTaskId，也不拉取外部 URL。流程：

1. POST /admin-api/tk/open/v1/x/media/uploads（需要 x.publish）初始化本 client 的上传会话
2. 本地分片或 OSS 直传后，POST .../complete。完成时校验内容类型、大小、哈希、未过期，且状态必须仍是上传中。失败上传不能改成完成，也不能用外部 URL 或 object key 冒充归属
3. POST /admin-api/tk/open/v1/x/publish/tasks，请求体使用本 client 的 mediaId + connectionId，并携带 Idempotency-Key。相同幂等键且不同 payload 返回 409。查询任务同样按 client 隔离
4. 后台执行会再次校验 client、连接仍为该 client 的 AUTHORIZED、媒体仍可用。撤销后待执行任务会被拒绝。错误不会包装成成功

GET /admin-api/tk/open/v1/x/connections 列出该 HMAC 客户端自己的连接。DELETE /admin-api/tk/open/v1/x/connections/{connectionId} 只把该 client 的这条连接标为撤销，不调用 X 全局吊销，也不改其他 client 或工作台账号。同一 X 账号被不同 client 授权时，各有独立令牌副本；同一 client 对同一 X 账号重新授权会替换该连接上的令牌。若多个 client 仍共享同一个 X 开发者应用，平台侧撤销互不影响，但 X 平台自己轮换 refresh token 的行为属于残留风险。

没有 open_client_id 的工作台账号不会被自动收编进 OpenAPI。工作台 X 发布路径保持不变。旧的社交表 OpenAPI 在途任务（openXConnectionId 指向旧授权会话）不会再被 Worker 继续执行，以免绕过新连接的撤销或误用工作台令牌；任务行不会被自动取消，需接入方改用 mediaId + connectionId 重试或由运营人工处理。

## 验收要点

- 跨 client 的连接、媒体、任务、票据一律拒绝
- 已知他户 generationTaskId 被拒绝
- 旧 client 连接不跨挂；同 client 可有多个 X 账号
- state / ticket 过期、重放、跨 client、returnUrl 白名单绕过均拒绝
- 无 returnUrl 时仍是关页 + 轮询
- 同幂等键不同 payload 返回 409；撤销后待执行任务拒绝；未授权或停用 client 拒绝
- 去掉 grants 后，原 auth/publish 客户端不会自动获得 X
- 工作台发布路径保持不变，但不要把“历史 OpenAPI 连接可继续用工作台令牌”当成无回归
- 旧社交表 OpenAPI 在途任务会被拒绝执行且不自动取消；回调重放在票据消费后不再签发新票
- 不做真实发帖或真实撤销用户授权
## Workspace-scoped consumers

A SaaS backend can include one `workspaceId=<lowercase UUID>` in the raw query
string of every signed X business request. Derive it from an authenticated
workspace on the caller's backend. Include the query in the existing HMAC target
and keep `endUserId` as optional metadata. Browser consent and X callbacks use
scope stored with the session; they do not accept a workspace from the browser.

The provider requires the same client and workspace for session and ticket
reads, connections and revocation, uploads and media, and publish tasks. Session
and connection responses include `workspaceId` for scoped rows. An X account
already connected through the same client cannot be reassigned by OAuth in a
different workspace. Idempotency keys remain unique per client; a retry cannot
return a task from a different workspace. Requests without `workspaceId` retain
the legacy API and can access only legacy resources. Legacy connections are not
automatically assigned to a workspace, even if `endUserId` matches. Review any
legacy connections before switching the caller to workspace scope.

Apply `tk_open_x_workspace_isolation_mysql.sql` after client isolation and before
starting the updated server. Verify a signed scoped session echoes the expected
workspace, a second scope cannot read it, and both X permissions are granted to
the existing client before enabling the caller. No real post is required for
release verification. Once scoped rows exist, disable X OpenAPI before any rollback
to a server version that predates workspace isolation.

### Bring forward a verified legacy connection

If the caller has no existing connections, skip this step. Otherwise, pause
legacy submissions from that caller and let its accepted X jobs finish. For each
legacy connection, take the workspace UUID and both the stable upstream
connection ID and X user ID from the caller's protected binding table. Require a
single matching binding in the caller database. An old `endUserId`, a display
name, or a new OAuth attempt is not proof of ownership. If the stable ID is
unavailable, resolve the mapping with the caller's operator before proceeding.

After applying the workspace schema, the ClipForge database operator can assign
that specific connection using the following guarded transaction. Bind the four
values through the database client; the placeholders denote bind parameters.
Keep the original connection ID and token ciphertext. If any check fails, roll
back and leave the row unassigned.

```sql
START TRANSACTION;
SELECT id, workspace_id FROM tk_open_x_connection
WHERE client_id = :client_id AND connection_id = :connection_id
  AND external_account_id = :x_user_id AND deleted = b'0'
FOR UPDATE;
-- Require exactly one row and workspace_id IS NULL.
UPDATE tk_open_x_connection AS c
SET workspace_id = :verified_workspace_uuid
WHERE c.client_id = :client_id AND c.connection_id = :connection_id
  AND c.external_account_id = :x_user_id AND c.deleted = b'0'
  AND c.workspace_id IS NULL
  AND NOT EXISTS (
    SELECT 1 FROM tk_open_x_publish_task AS t
    WHERE t.client_id = c.client_id AND t.connection_id = c.connection_id
      AND t.deleted = b'0' AND t.status IN ('PENDING', 'PROCESSING')
  );
SELECT ROW_COUNT();
-- Commit only if exactly one row changed; otherwise roll back.
```

Do not change the scope of legacy tasks or media. Verify the adopted connection
appears only in the assigned workspace and refresh the caller's connection list.
