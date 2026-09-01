# TikTok 视频发布开放 API（多调用方通用版）

> 文档状态：接口草案，供外部调用方评审。当前仅输出文档，接口尚未开发。
>
> 版本：v1
>
> 适用范围：服务 A 为多个独立的外部应用提供 TikTok 授权、视频上传、视频发布和状态查询能力，所有外部应用使用同一套通用 API。

## 1. 服务定位

服务 A 是独立的 TikTok 发布平台，面向多个外部调用方提供统一能力，负责：

- TikTok OAuth 授权
- TikTok access token 和 refresh token 的加密保存
- TikTok 账号连接管理
- 视频文件上传和存储
- TikTok 发布任务执行
- 发布状态同步
- 发布结果回调

每个调用方应用负责：

- 自己的用户和业务数据
- 保存 A 方返回的外部资源编号
- 调用 A 方 API
- 接收并处理授权、发布状态回调

本 API 不涉及租户、公司或后台用户权限。请求和资源按照 `clientId` 隔离，不支持传入 `tenantId`、`companyId` 或 A 方数据库主键。

### 1.1 多调用方模型

A 方可以同时接入多个外部应用，例如应用 B、应用 C 和其他合作方。每个外部应用都是一个独立的 API 客户端：

```text
应用 B -> clientId_B -> B 的账号、媒体、任务和回调
应用 C -> clientId_C -> C 的账号、媒体、任务和回调
```

隔离规则：

- A 方为每个外部应用分配独立的 `clientId`、`clientSecret` 和 `callbackSecret`
- 一个 `clientId` 可以绑定多个 TikTok 账号
- 一个外部账号编号只在所属 `clientId` 范围内有效
- 不同 `clientId` 可以使用相同的 `externalAccountId`，互不冲突
- 一个客户端不能查询、修改或使用另一个客户端的资源
- 每个客户端独立配置回调地址、限流规则和状态
- A 方的 TikTok App 配置和 Token 存储由 A 方统一管理

调用方不需要了解 A 方内部的数据表、数据库编号或历史租户字段。

## 2. 基础信息

### 2.1 服务地址

当前项目生产前端采用同域 API，项目域名为 `tkassetplant.fnn.net.cn`，后端 API 前缀为 `/admin-api`。

```text
https://tkassetplant.fnn.net.cn/admin-api
```

本开放 API 的统一调用基地址为：

```text
https://tkassetplant.fnn.net.cn/admin-api/tk/open/v1/tiktok
```

本地开发或 A 方服务器内部调试地址为：

```text
http://localhost:48080/admin-api
```

`localhost` 仅代表调用请求发起机器自身，B 方、C 方等外部服务不能直接使用该地址调用 A 方。正式接入前，A 方需要将 `tkassetplant.fnn.net.cn` 解析到实际对外服务器，并通过 HTTPS 提供访问；API 前缀仍为 `/admin-api`。

所有接口使用 HTTPS 和 UTF-8 JSON。视频上传接口可能使用 `multipart/form-data` 或 OSS 表单直传。

### 2.2 API 版本

接口路径统一使用：

```text
/tk/open/v1/tiktok
```

示例完整路径：

```text
POST https://tkassetplant.fnn.net.cn/admin-api/tk/open/v1/tiktok/auth/sessions
```

### 2.3 外部资源编号

以下编号均为 A 方生成的 opaque ID，不代表数据库主键：

| 编号 | 用途 |
| --- | --- |
| `authSessionId` | TikTok 授权会话 |
| `connectionId` | TikTok 账号连接 |
| `uploadId` | 视频上传会话 |
| `mediaId` | 已完成的视频 |
| `taskId` | 发布任务 |
| `detailId` | 单个账号的发布明细 |

调用方自己的业务编号使用 `externalAccountId`、`externalRequestId` 等字段传递。外部编号只在当前 `clientId` 的命名空间内比较和查询。

## 3. 调用认证

### 3.1 客户端凭证

A 方为每个外部调用方单独创建一个 API 客户端，并分配：

```text
clientId
clientSecret
callbackSecret
```

`clientSecret` 和 `callbackSecret` 只在创建或重置时展示一次。调用方必须保存在服务端配置中，不得放入浏览器、移动端或前端代码。

客户端配置相互独立。应用 B 的凭证不能调用应用 C 的资源，应用 C 的回调密钥也不能验证应用 B 的回调。

### 3.2 请求头

除 TikTok 浏览器回调接口外，其他接口都需要以下请求头：

```http
Content-Type: application/json
X-TK-Client-Id: client_xxx
X-TK-Timestamp: 1798761600
X-TK-Nonce: 7f3c...
X-TK-Request-Id: req_202608310001
X-TK-Signature: base64-signature
```

上传分片接口的 `Content-Type` 为 `multipart/form-data`，签名仍然使用实际请求体的 SHA-256 摘要。

### 3.3 签名规则

签名算法：`HMAC-SHA256`。

签名原文按以下格式拼接，每行之间使用 `\n`，最后一行不追加换行：

```text
HTTP_METHOD
REQUEST_TARGET
TIMESTAMP
NONCE
SHA256_HEX(BODY)
```

示例：

```text
POST
/admin-api/tk/open/v1/tiktok/publish/tasks
1798761600
7f3c...
8f434346648f...
```

计算方式：

```text
signature = Base64(HMAC-SHA256(clientSecret, canonicalString))
```

约定：

- `HTTP_METHOD` 使用大写，例如 `POST`、`GET`、`DELETE`
- `REQUEST_TARGET` 包含路径和查询字符串，不包含域名
- JSON 使用发送前的原始字节计算摘要
- 空请求体的 SHA-256 是空字符串的 SHA-256
- `X-TK-Timestamp` 使用 Unix 秒
- 服务端允许的时间偏差：`【待确认，建议 300 秒】`
- `X-TK-Nonce` 必须唯一，服务端会短期保存，重复使用会失败

### 3.4 认证失败

认证失败不会进入业务处理，常见错误码：

| 错误码 | 含义 |
| --- | --- |
| `OPEN_API_CLIENT_INVALID` | clientId 不存在或已禁用 |
| `OPEN_API_SIGNATURE_INVALID` | 签名不正确 |
| `OPEN_API_TIMESTAMP_EXPIRED` | 时间戳超出允许范围 |
| `OPEN_API_NONCE_REPLAYED` | nonce 已使用 |
| `OPEN_API_RATE_LIMITED` | 超过调用频率 |

## 4. 通用响应

成功响应：

```json
{
  "code": 0,
  "msg": "OK",
  "data": {},
  "requestId": "req_202608310001"
}
```

失败响应：

```json
{
  "code": "MEDIA_NOT_READY",
  "msg": "视频尚未完成上传",
  "data": null,
  "requestId": "req_202608310001"
}
```

建议同时使用 HTTP 状态码和业务错误码：

| HTTP 状态 | 场景 |
| --- | --- |
| `200` | 查询或操作成功 |
| `201` | 资源创建成功 |
| `400` | 参数错误或业务状态不允许 |
| `401` | 认证失败 |
| `404` | 资源不存在或不属于当前 `clientId` |
| `409` | 幂等键冲突或资源状态冲突 |
| `413` | 视频超过大小限制 |
| `429` | 请求频率过高 |
| `500` | A 方内部错误 |
| `503` | A 方或 TikTok 服务暂时不可用 |

## 5. 推荐调用流程

```text
1. 创建授权会话
2. 用户浏览器打开 authorizeUrl
3. TikTok 回调 A 方
4. A 方保存 Token，创建 connectionId
5. 当前调用方查询授权状态或接收自己的回调
6. 创建视频上传会话
7. 上传视频到 OSS 或 A 方分片接口
8. 完成上传，获得 mediaId
9. 创建发布任务，获得 taskId
10. 查询任务或接收发布状态回调
```

## 6. 授权接口

### 6.1 创建授权会话

```http
POST /tk/open/v1/tiktok/auth/sessions
```

请求：

```json
{
  "externalAccountId": "account_10001",
  "authMode": "REDIRECT",
  "clientState": "order_or_page_state_001"
}
```

字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `externalAccountId` | string | 是 | 调用方自己的账号编号 |
| `authMode` | string | 是 | 当前支持 `REDIRECT` |
| `clientState` | string | 否 | 调用方透传状态，不用于 A 方安全校验 |

响应：

```json
{
  "code": 0,
  "msg": "OK",
  "data": {
    "authSessionId": "auth_xxx",
    "authorizeUrl": "https://www.tiktok.com/v2/auth/authorize/?...",
    "status": "WAITING",
    "expireTime": "2026-08-31T18:00:00+08:00"
  },
  "requestId": "req_xxx"
}
```

调用方将 `authorizeUrl` 在用户浏览器中打开。不得在服务端模拟用户完成授权，也不得截取授权 code。

### 6.2 TikTok 授权回调

```http
GET /tk/open/v1/tiktok/auth/callback
```

该接口由 TikTok 访问，不由调用方主动调用。

处理规则：

1. A 方校验 `state`
2. 校验授权会话是否存在和过期
3. 使用授权 code 换取 Token
4. 加密保存 access token 和 refresh token
5. 获取 TikTok 账号资料
6. 创建或更新账号连接
7. 回调当前 `clientId` 配置的授权事件地址

TikTok 的 redirect URI 固定配置在 A 方，不允许调用方每次请求时传入任意 redirect URI。

### 6.3 查询授权会话

```http
GET /tk/open/v1/tiktok/auth/sessions/{authSessionId}
```

响应：

```json
{
  "code": 0,
  "msg": "OK",
  "data": {
    "authSessionId": "auth_xxx",
    "externalAccountId": "account_10001",
    "connectionId": "conn_xxx",
    "accountName": "@example",
    "status": "SUCCESS",
    "failReason": null
  },
  "requestId": "req_xxx"
}
```

授权状态：

```text
WAITING
SUCCESS
FAILED
EXPIRED
```

### 6.4 查询账号连接

```http
GET /tk/open/v1/tiktok/connections
```

可选查询参数：

```text
externalAccountId=account_10001
status=AUTHORIZED
```

响应：

```json
{
  "code": 0,
  "msg": "OK",
  "data": [
    {
      "connectionId": "conn_xxx",
      "externalAccountId": "account_10001",
      "accountName": "@example",
      "authStatus": "AUTHORIZED",
      "tokenStatus": "NORMAL",
      "lastAuthTime": "2026-08-31T17:00:00+08:00"
    }
  ],
  "requestId": "req_xxx"
}
```

### 6.5 解绑账号

```http
POST /tk/open/v1/tiktok/connections/{connectionId}/disconnect
```

成功后，该连接不能继续创建发布任务。是否同时调用 TikTok 撤销授权：`【待确认】`。

## 7. 视频上传接口

### 7.1 创建上传会话

```http
POST /tk/open/v1/tiktok/media/uploads
```

请求：

```json
{
  "fileName": "video.mp4",
  "fileSize": 104857600,
  "contentType": "video/mp4",
  "sha256": "8f434346648f..."
}
```

限制：

- 支持格式：`mp4`、`mov`、`webm`
- 视频大小上限：当前规划为 `1GB`，最终以 A 方部署配置为准
- `sha256` 用于上传完成后的文件校验

OSS 直传响应：

```json
{
  "code": 0,
  "msg": "OK",
  "data": {
    "uploadId": "upload_xxx",
    "uploadMode": "OSS",
    "uploadUrl": "https://oss.example.com",
    "objectKey": "tk/open/2026/08/upload_xxx/video.mp4",
    "fields": {
      "key": "...",
      "policy": "...",
      "OSSAccessKeyId": "...",
      "Signature": "..."
    },
    "expireTime": "2026-08-31T18:30:00+08:00"
  },
  "requestId": "req_xxx"
}
```

当前调用方使用响应中的 `uploadUrl` 和 `fields` 直接上传文件，不把视频先上传到调用方再转发到 A 方。

### 7.2 查询上传进度

```http
GET /tk/open/v1/tiktok/media/uploads/{uploadId}
```

响应：

```json
{
  "code": 0,
  "msg": "OK",
  "data": {
    "uploadId": "upload_xxx",
    "uploadMode": "OSS",
    "status": "UPLOADING",
    "fileSize": 104857600,
    "uploadedSize": 104857600,
    "uploadedChunks": []
  },
  "requestId": "req_xxx"
}
```

状态：

```text
UPLOADING
COMPLETED
FAILED
CANCELLED
EXPIRED
```

### 7.3 完成上传

```http
POST /tk/open/v1/tiktok/media/uploads/{uploadId}/complete
```

请求：

```json
{
  "fileSize": 104857600,
  "sha256": "8f434346648f...",
  "coverTimestampMs": 1200
}
```

响应：

```json
{
  "code": 0,
  "msg": "OK",
  "data": {
    "mediaId": "media_xxx",
    "status": "READY",
    "fileName": "video.mp4",
    "fileSize": 104857600
  },
  "requestId": "req_xxx"
}
```

### 7.4 取消上传

```http
DELETE /tk/open/v1/tiktok/media/uploads/{uploadId}
```

## 8. 发布接口

### 8.1 创建发布任务

```http
POST /tk/open/v1/tiktok/publish/tasks
```

请求头增加：

```http
Idempotency-Key: publish_order_001
```

请求：

```json
{
  "connectionIds": [
    "conn_xxx"
  ],
  "mediaId": "media_xxx",
  "title": "视频标题",
  "caption": "视频文案",
  "postMode": "DIRECT_POST",
  "privacyLevel": "PUBLIC_TO_EVERYONE",
  "allowComment": true,
  "allowDuet": false,
  "allowStitch": false,
  "commercialContent": false,
  "brandContent": false,
  "aigcContent": true,
  "externalRequestId": "business_order_001"
}
```

字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `connectionIds` | string[] | 是 | 已授权的 TikTok 账号连接 |
| `mediaId` | string | 是 | 已完成上传的视频 |
| `title` | string | 否 | 发布标题 |
| `caption` | string | 否 | 发布文案，长度以 A 方校验为准 |
| `postMode` | string | 是 | `DIRECT_POST` 或 `UPLOAD_TO_INBOX` |
| `privacyLevel` | string | 是 | TikTok 支持的隐私级别 |
| `externalRequestId` | string | 否 | 调用方业务编号 |

响应：

```json
{
  "code": 0,
  "msg": "OK",
  "data": {
    "taskId": "task_xxx",
    "status": "PENDING",
    "accountCount": 1,
    "successCount": 0,
    "failedCount": 0,
    "pendingCount": 1,
    "createTime": "2026-08-31T17:30:00+08:00"
  },
  "requestId": "req_xxx"
}
```

该接口只负责创建任务并返回，不等待 TikTok 实际完成发布。

相同 `clientId + Idempotency-Key` 必须使用相同请求内容。相同请求重复提交时，返回原任务；相同幂等键但请求内容不同，返回 `IDEMPOTENCY_KEY_CONFLICT`。

### 8.2 查询发布任务

```http
GET /tk/open/v1/tiktok/publish/tasks/{taskId}
```

响应：

```json
{
  "code": 0,
  "msg": "OK",
  "data": {
    "taskId": "task_xxx",
    "status": "SUCCESS",
    "mediaId": "media_xxx",
    "accountCount": 1,
    "successCount": 1,
    "failedCount": 0,
    "pendingCount": 0,
    "failReason": null,
    "createTime": "2026-08-31T17:30:00+08:00",
    "updateTime": "2026-08-31T17:35:00+08:00"
  },
  "requestId": "req_xxx"
}
```

任务状态：

```text
PENDING
PROCESSING
SUCCESS
FAILED
PARTIAL_SUCCESS
```

### 8.3 查询发布明细

```http
GET /tk/open/v1/tiktok/publish/tasks/{taskId}/details
```

响应：

```json
{
  "code": 0,
  "msg": "OK",
  "data": [
    {
      "detailId": "detail_xxx",
      "connectionId": "conn_xxx",
      "accountName": "@example",
      "status": "SUCCESS",
      "tiktokStatus": "PUBLISHED",
      "publishId": "publish_xxx",
      "publishUrl": "https://www.tiktok.com/@example/video/123",
      "failReason": null,
      "updateTime": "2026-08-31T17:35:00+08:00"
    }
  ],
  "requestId": "req_xxx"
}
```

### 8.4 重试失败明细

```http
POST /tk/open/v1/tiktok/publish/details/{detailId}/retry
```

只有 `FAILED` 状态的明细允许重试。重试后状态变为 `PENDING`，由 A 方异步处理。

## 9. 状态回调

### 9.1 回调地址

每个调用方在 A 方配置自己的回调地址。回调地址必须使用 HTTPS，并且由 A 方审核或配置白名单。A 方只向产生事件的 `clientId` 配置的地址发送回调，不会跨客户端发送事件。

回调包括：

- 授权完成
- 授权失败
- 发布处理中
- 发布成功
- 发布失败
- 部分成功

### 9.2 回调请求头

```http
Content-Type: application/json
X-TK-Event-Id: evt_xxx
X-TK-Timestamp: 1798761600
X-TK-Signature: callback-signature
```

回调签名使用 `callbackSecret`，签名原文：

```text
EVENT_ID
TIMESTAMP
SHA256_HEX(BODY)
```

### 9.3 授权完成事件

```json
{
  "eventId": "evt_auth_xxx",
  "eventType": "authorization.completed",
  "connectionId": "conn_xxx",
  "externalAccountId": "account_10001",
  "accountName": "@example",
  "status": "AUTHORIZED",
  "occurredAt": "2026-08-31T17:00:00+08:00"
}
```

### 9.4 发布状态事件

```json
{
  "eventId": "evt_publish_xxx",
  "eventType": "publish.success",
  "taskId": "task_xxx",
  "detailId": "detail_xxx",
  "connectionId": "conn_xxx",
  "externalRequestId": "business_order_001",
  "status": "SUCCESS",
  "publishId": "publish_xxx",
  "publishUrl": "https://www.tiktok.com/@example/video/123",
  "failReason": null,
  "occurredAt": "2026-08-31T17:35:00+08:00"
}
```

### 9.5 回调处理要求

每个调用方应当：

1. 先校验回调签名
2. 使用 `eventId` 做幂等
3. 快速返回 HTTP 2xx
4. 将业务处理放入自己的异步队列
5. 回调失败时使用任务查询接口补偿

A 方回调采用至少一次投递，每个调用方不得假设事件只会收到一次。回调事件的幂等范围为当前 `clientId + eventId`。

## 10. 主要错误码

| 错误码 | 含义 |
| --- | --- |
| `AUTH_SESSION_NOT_FOUND` | 授权会话不存在 |
| `AUTH_SESSION_EXPIRED` | 授权会话已过期 |
| `AUTHORIZATION_FAILED` | TikTok 授权失败 |
| `CONNECTION_NOT_FOUND` | 账号连接不存在 |
| `CONNECTION_NOT_AUTHORIZED` | TikTok 账号未授权或授权失效 |
| `MEDIA_NOT_FOUND` | 视频不存在 |
| `MEDIA_NOT_READY` | 视频尚未完成上传 |
| `MEDIA_FILE_INVALID` | 视频格式、大小或摘要校验失败 |
| `PUBLISH_PARAMETER_INVALID` | 发布参数不合法 |
| `PUBLISH_TASK_NOT_FOUND` | 发布任务不存在 |
| `PUBLISH_DETAIL_NOT_FOUND` | 发布明细不存在 |
| `PUBLISH_RETRY_STATUS_INVALID` | 当前状态不允许重试 |
| `IDEMPOTENCY_KEY_CONFLICT` | 幂等键对应的请求内容不一致 |
| `TIKTOK_TOKEN_INVALID` | TikTok Token 无效，需要重新授权 |
| `TIKTOK_API_ERROR` | TikTok API 调用失败 |
| `OPEN_API_RATE_LIMITED` | 超过调用频率 |

## 11. 外部调用方接入检查清单

- [ ] 已由 A 方创建独立的 `clientId`
- [ ] 已获得本客户端的 `clientSecret` 和 `callbackSecret`
- [ ] 已实现 HMAC 签名
- [ ] 已实现时间戳和 nonce
- [ ] 已保存 `authSessionId`、`connectionId`、`mediaId`、`taskId`
- [ ] 已实现 TikTok 授权跳转
- [ ] 已实现授权状态查询或授权回调
- [ ] 已实现 OSS 表单直传
- [ ] 已实现发布任务幂等
- [ ] 已实现发布状态查询
- [ ] 已实现回调验签和事件幂等
- [ ] 已处理 Token 失效后的重新授权
- [ ] 已处理 TikTok 发布超时和失败状态

## 12. 待双方确认事项

以下内容在 A 方开放给每个调用方前需要确认：

1. A 方正式域名和 API 前缀
2. HMAC 时间戳允许偏差
3. 每个 `clientId` 的限流规则
4. TikTok redirect URI
5. 当前调用方的授权回调地址
6. 当前调用方的发布状态回调地址
7. 回调重试次数和间隔
8. 是否支持 QR 授权
9. 是否支持 `UPLOAD_TO_INBOX`
10. 视频大小和格式最终限制
11. 是否允许一个 `clientId` 绑定多个 TikTok 账号
12. 解绑时是否同步撤销 TikTok 授权
