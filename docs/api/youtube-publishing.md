# YouTube OpenAPI 接入指南

## 开通与签名

生产基地址：`https://tkassetplant.fnn.net.cn`。以下接口统一加前缀 `/admin-api/tk/open/v1/youtube`。

- 由运营为调用方 HMAC 客户端开通 `youtube.auth` 与 `youtube.publish`。两套凭据不可混用：ClipForge 签名密钥仅存在 SaaS 后端，Google OAuth 凭据和刷新令牌仅存在 ClipForge 后端。
- 每个签名请求都必须有 `?workspaceId=<小写 UUID>`。SaaS 后端应从当前登录用户的工作区权限中取得该值，不相信浏览器传入的归属。连接、上传和任务不跨客户端或工作区共享。
- 请求头：`X-TK-Client-Id`、`X-TK-Timestamp`（Unix 秒）、`X-TK-Nonce`（每次唯一）、`X-TK-Signature`。签名原文依次为 HTTP 方法、原始路径及查询串、时间戳、Nonce、原始请求体 SHA-256 小写十六进制。用 LF 连接，无末尾换行；签名为 HMAC-SHA256 的 Base64。GET 计算空字节的哈希，分片计算实际二进制字节的哈希。
- 业务响应为 `{code,msg,data,requestId}`，只有 `code=0` 表示调用成功。浏览器跳转与回调不走 HMAC。

## 频道授权与连接

| 方法与路径 | 请求与结果 |
| --- | --- |
| `POST /auth/sessions` | 可选 `endUserId`（仅业务备注）。返回 `authSessionId`、`launchUrl`、`expireTime` 和 `PENDING`。 |
| `GET /auth/sessions/{sessionId}/launch` | 浏览器直接打开返回的完整 `launchUrl`，其一次性票据有效期 10 分钟。不要记录或拼接该 URL。 |
| `GET /auth/callback` | Google 浏览器回调；部署也可复用已登记的工作台回调。C 端不直接请求此接口。 |
| `GET /auth/sessions/{sessionId}` | 轮询 `PENDING`、`EXCHANGING`、`SUCCESS`、`FAILED` 或 `EXPIRED`。成功后保存 `connectionId`，失败读取 `errorCode`。 |
| `GET /connections` | 返回当前工作区连接数组，含 `connectionId`、`channelId`、`channelTitle`、`workspaceId`、`status`。 |
| `POST /connections/{connectionId}/profile/refresh` | 无请求体，刷新并返回频道资料。 |
| `DELETE /connections/{connectionId}` | 解除本工作区绑定并清除已存令牌。不会解除 Google 端其他应用或工作区的授权。 |

用户在 Google 选择一个 YouTube 频道，授权完成后返回 SaaS 页面；SaaS 后端每 3 至 5 秒查询会话。连接状态为 `AUTHORIZED` 才可发布；`EXPIRED`、`REVOKED` 需要重新授权。授权测试模式、频道资格和公开发布可用性由 Google 控制台及 YouTube 决定。

## 视频上传

| 方法与路径 | 请求与结果 |
| --- | --- |
| `POST /media/uploads` | `fileName`（MP4）、`fileSize`（字节）、可选 `contentType`（`video/mp4`）、`sha256`（64 位十六进制，推荐）。返回 `uploadId`、`uploadMode`、`expireTime` 和上传参数。 |
| `GET /media/uploads/{uploadId}` | 返回 `status`、`fileSize`、`uploadedSize`、`uploadMode`、`mediaId`。 |
| `POST /media/uploads/{uploadId}/chunks/{chunkIndex}` | LOCAL 模式，`application/octet-stream`，索引从 0 开始，按 `chunkSize` 和 `totalChunks` 切片，最后一片可较短。 |
| `POST /media/uploads/{uploadId}/complete` | 提交 `fileSize` 和初始化时的 `sha256`（如有），校验通过返回 `status=READY` 和 `mediaId`。 |

OSS 模式：向 `uploadUrl` 发送 multipart 表单，依次放入 `key=objectKey`、`policy`、`OSSAccessKeyId=fields.accessKeyId`、`Signature=fields.signature`、`success_action_status=200`、`x-oss-meta-sha256=fields.sha256`（存在时），最后放 `file`。成功后调用 `complete`。不要把 ClipForge 的签名头发给 OSS，也不要接受任意外链代替视频上传。

## 提交发布任务

`POST /publish/tasks` 必填 JSON 字段 `idempotencyKey`（1 至 128 个可见 ASCII 字符，随请求体签名）。可同时传 `Idempotency-Key` 请求头，但值必须一致。请求示例：

```json
{
  "idempotencyKey": "saas-video-request-001",
  "connectionId": "ytconn_example",
  "mediaId": "media_example",
  "title": "示例标题",
  "description": "示例说明",
  "privacyStatus": "private",
  "madeForKids": false,
  "containsSyntheticMedia": false
}
```

标题限 100 字符；描述可省略，限 5000 UTF-8 字节；两者不含尖括号。`privacyStatus` 为 `private`、`unlisted` 或 `public`，响应里的该字段为请求值。`madeForKids` 必填；`containsSyntheticMedia` 默认 `false`。首期为立即提交单条视频，分类固定为 `22`；不支持定时发布、标签、缩略图或播放列表。

`GET /publish/tasks/{taskId}` 返回 `taskId`、`workspaceId`、`connectionId`、`mediaId`、`status`、`platformStatus`、`privacyStatus`、`youtubeVideoId`、`publishUrl`、`retryable`、`retryCount`、`errorCode`、`uploadedBytes`。拿到 `taskId` 仅表示受理；推荐每 5 至 15 秒查询，`SUCCESS` 表示 YouTube 处理完成，不表示已对公众可见。

| 状态或错误 | C 端处理 |
| --- | --- |
| `PENDING` | 继续查询；`YT_UPLOAD` 表示平台上传中，`YT_PROCESSING` 表示平台处理中。 |
| `SUCCESS` | 显示 `youtubeVideoId` 和 `publishUrl`。 |
| `FAILED` | 展示 `errorCode`。仅当 `retryable=true` 时允许 `POST /publish/tasks/{taskId}/retry`，最多 3 次，沿用原任务、断点或视频 ID。 |
| `UNKNOWN` | 上传结果不确定或断点过期，先查 YouTube Studio，禁止自动创建新任务或重试。 |
| `IDEMPOTENCY_KEY_CONFLICT` | 原键已用于不同参数或工作区。超时应保留原参数和原键，用新的时间戳和 Nonce 重新签名。 |
| `YOUTUBE_CONNECTION_DISABLED` | 重新授权同一工作区和频道，再按 `retryable` 决定是否重试原任务。 |
| `YOUTUBE_WORKSPACE_INVALID` | 补充正确的已签名 `workspaceId`。 |
| `YOUTUBE_OPEN_API_DISABLED` / `OPEN_API_PERMISSION_DENIED` | 联系管理员核对开关或当前客户端权限。 |

已配置发布回调的客户端可接收现有签名回调 `publish.completed` / `publish.failed`，新负载包含 `platform=youtube`，按 `eventId` 去重并按 `status` 判断。回调只作通知，查询接口为最终准据。

## 上线条件

更改 Google OAuth redirect URI 时须在 Google 控制台登记完全一致的 HTTPS 地址。首次联调先授权测试频道，并经用户确认后使用 `private` 做真实上传验证。测试模式或未完成适用的审核时，不承诺公开视频发布。
