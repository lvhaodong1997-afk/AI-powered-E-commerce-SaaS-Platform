# Facebook 与 Instagram 发布接入

P0：Facebook **Page** 可发布纯文字、单张 JPEG 或单条 MP4 Reel；Instagram **专业账号**可发布单张 JPEG 或单条 MP4 Reel，可附文字。不支持个人 Facebook 动态、Instagram 纯文字、轮播、定时、外链素材或批量目标。

## 环境开通

每个平台默认关闭。服务端运行迁移 `tk_open_meta_openapi_mysql.sql`，配置 `tk.social`、`tk.meta-open-api.public-origin`（Meta 可访问的 HTTPS 源站）和 `tk.meta-open-api.private-root`（绝对路径，服务可写，非 Web 目录，多实例须共享）。确认网关和应用不记录 OAuth 或私有媒体的查询凭据后，再单独打开 `tk.meta-open-api.facebook-enabled` 或 `tk.meta-open-api.instagram-enabled`。

Meta App 配置复用现有回调 `/admin-api/tk/social-auth/{platform}/callback`。当前共用适配器要求 Facebook Business Login 配置包含 `pages_show_list`、`pages_read_engagement`、`pages_manage_posts`、`read_insights`，账号对所选 Page 有内容发布权限；Instagram Login 要求 `instagram_business_basic`、`instagram_business_content_publish`、`instagram_business_manage_insights`。面向非 App 角色的授权取决于 Meta 应用状态和相应权限审批。代码上线不等于平台已开通。

## 签名与隔离

前缀 `/admin-api/tk/open/v1/{platform}`，其中 `platform` 为 `facebook` 或 `instagram`。下表每个路径都必须追加唯一查询参数 `?workspaceId=<小写 UUID>`。SaaS 后端需先验证登录用户的工作区成员关系，不能用浏览器传来的工作区直接签名，也不能将 HMAC 密钥交给浏览器。

| 方法和相对路径 | 权限后缀 | 用法 |
| --- | --- | --- |
| `POST /auth/sessions` | `.auth` | 空请求体；打开 `data.authorizeUrl`，链接有效 10 分钟 |
| `GET /auth/sessions/{id}` | `.auth` | 轮询 `authSessionId`：`PENDING`、`EXCHANGING`、`PAGES_READY`、`SUCCESS`、`FAILED`、`EXPIRED` |
| `POST /auth/sessions/{id}/select-page` | `facebook.auth` | 仅 Facebook，在 `PAGES_READY` 后提交 `{"pageId":"123456"}`，返回连接 |
| `GET /connections` | `.auth` | 返回工作区内本平台连接，`AUTHORIZED` 才可发布 |
| `DELETE /connections/{id}` | `.auth` | 作废本工作区连接；不删除已发布内容 |
| `POST /media/uploads` | `.publish` | JSON：`contentType`、`fileSize`、`sha256`；返回上传计划 |
| `GET /media/uploads/{id}` | `.publish` | 查询 `UPLOADING`、`READY` 或 `EXPIRED` |
| `POST /media/uploads/{id}/chunks/{index}` | `.publish` | `application/octet-stream` 原始数据，每片独立签名；序号从 0 开始 |
| `POST /media/uploads/{id}/complete` | `.publish` | 空请求体；校验合并文件；`READY` 时返回 `mediaId` |
| `POST /publish/tasks` | `.publish` | 提交单连接、单素材或纯文字任务 |
| `GET /publish/tasks/{id}` | `.publish` | 依据 `taskId` 查询任务和结果 |

Facebook 返回 `pages[]`（`pageId`、`name`），须让用户确认 Page；Instagram 成功后直接返回 `connectionId`。素材、账号和任务不能跨客户端、工作区或平台复用。

沿用 `X-TK-Client-Id`、`X-TK-Timestamp`（秒）、`X-TK-Nonce`、`X-TK-Signature`。签名示例（Node.js）：

```js
import { createHash, createHmac, randomUUID } from 'node:crypto'

const target = `/admin-api/tk/open/v1/${platform}${path}?workspaceId=${workspaceId}`
const bytes = Buffer.from(JSON.stringify(payload), 'utf8') // 空请求用 Buffer.alloc(0)，分片直接用 Buffer
const timestamp = String(Math.floor(Date.now() / 1000))
const nonce = randomUUID()
const canonical = [method, target, timestamp, nonce, createHash('sha256').update(bytes).digest('hex')].join('\n')
const headers = {
  'X-TK-Client-Id': clientId,
  'X-TK-Timestamp': timestamp,
  'X-TK-Nonce': nonce,
  'X-TK-Signature': createHmac('sha256', clientSecret).update(canonical).digest('base64')
}
// 请求使用同一 target、method 和 bytes；JSON / 分片分别设置正确的 Content-Type。
```

## 素材和发布

`fileSize` 为字节数，`sha256` 为完整文件的小写十六进制摘要。JPEG 最大 8,000,000 字节，MP4 最大 100,000,000 字节；MP4 还需符合对应平台的 Reels 编码要求。本地上传每片 4 MiB（除尾片），会话有效 24 小时；重复上传同片同内容可重试，内容不同返回 `META_CHUNK_CONFLICT`。发布只接受 `READY` 的 `mediaId`。

```json
{
  "connectionId": "metaconn_0123456789abcdef0123456789abcdef",
  "mediaType": "IMAGE",
  "mediaId": "metamedia_0123456789abcdef0123456789abcdef",
  "text": "新品介绍",
  "idempotencyKey": "publish-20261128-001"
}
```

`mediaType` 为 `TEXT`、`IMAGE` 或 `VIDEO`。`TEXT` 仅 Facebook，必填非空 `text`，省略 `mediaId`。正文最多 2,200 个 UTF-16 单元。`idempotencyKey` 必须在签名 JSON 中；可选同名 HTTP 头 `Idempotency-Key` 必须与 JSON 一致。同工作区、平台和客户端内，同键同参返回已有任务，同键改参返回 HTTP 409 `META_IDEMPOTENCY_CONFLICT`。网络重试应使用新 nonce、原业务键。

响应采用 `{ "code": 0, "msg": "OK", "data": ..., "requestId": ... }`。轮询 `data.status`：`PENDING`、`PROCESSING` 为进行中；`SUCCESS` 是平台已确认；`FAILED`、`REAUTH_REQUIRED`、`UNKNOWN` 为终态。`UNKNOWN` **必须人工核对，不能自动换键重发**。平台结果包括 `providerContainerId`、`providerMediaId`、`providerPostId`、`publishUrl`（按平台响应可为空）。已配置的客户端会收到 `authorization.completed`、`publish.completed` 事件，按 `eventId` 去重，以查询为准。`503 META_OPEN_API_DISABLED` 表示目标环境未开通。
