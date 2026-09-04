# TikTok 视频发布开放 API（多调用方通用版）

> 文档状态：按当前控制器、VO 和服务实现整理的调用方接口说明。
>
> 版本：v1
>
> 重要说明：本文描述的是当前源码已经实现的接口契约，不代表这些接口已经完成部署、联调或具备生产可用性。调用前仍需由双方确认目标环境连通性和客户端配置。
>
> 适用范围：服务 A 为多个独立外部应用提供 TikTok 授权、视频上传、视频发布和状态查询能力，所有外部应用使用同一套通用 API。

## 1. 服务定位

服务 A 是独立的 TikTok 发布平台，面向多个外部调用方提供统一能力，负责：

- TikTok OAuth 重定向授权和二维码授权
- TikTok access token 和 refresh token 的加密保存
- TikTok 账号连接管理
- OSS 直传或本地分片上传
- TikTok 发布任务异步执行
- 发布状态同步
- 授权和发布结果回调

每个调用方应用负责：

- 管理自己的用户和业务数据
- 保存 A 方返回的外部资源编号
- 在自己的服务端调用 A 方 API
- 引导用户完成 TikTok 授权
- 接收并处理授权、发布状态回调
- 对回调事件和发布请求做好幂等处理

本 API 的隔离边界只有 `clientId`。调用方只传递本 API 定义的业务字段和 opaque ID，不需要了解 A 方内部数据表或数据库编号。

### 1.1 多调用方模型

A 方可以同时接入应用 B、应用 C 和其他合作方。每个外部应用都是独立的 API 客户端：

```text
应用 B -> clientId_B -> B 的账号、媒体、任务和回调
应用 C -> clientId_C -> C 的账号、媒体、任务和回调
```

隔离规则：

- A 方为每个外部应用分配独立的 `clientId`、`clientSecret` 和 `callbackSecret`
- 一个 `clientId` 可以绑定多个 TikTok 账号
- 一个 `externalAccountId` 只在所属 `clientId` 范围内使用
- 不同 `clientId` 可以使用相同的 `externalAccountId`，互不冲突
- 一个客户端不能查询、修改或使用另一个客户端的资源
- 每个客户端独立配置权限、IP 白名单、限流、回调地址和启停状态
- A 方统一管理 TikTok App 配置和 Token 存储

### 1.2 外部文档边界

本文只描述外部调用方使用的 TikTok Open API。客户端创建、密钥重置、回调重放等管理后台接口不属于外部调用方 API 主体，不应由合作方直接调用。

## 2. 基础信息

### 2.1 服务地址

约定的生产基地址固定为：

```text
https://tkassetplant.fnn.net.cn/admin-api/tk/open/v1/tiktok
```

本文后续端点均相对于该基地址，例如：

```text
POST /auth/sessions
```

对应完整 URL：

```text
POST https://tkassetplant.fnn.net.cn/admin-api/tk/open/v1/tiktok/auth/sessions
```

该地址是接口契约中的目标地址，不构成已部署、已联调或已通过生产验收的声明。

除 TikTok OAuth 浏览器回调外，业务接口使用 HTTPS、UTF-8 和 JSON。`LOCAL` 模式的视频分片接口使用 `application/octet-stream`；`OSS` 模式由调用方向返回的 OSS 地址提交表单。

### 2.2 API 版本

当前 API 版本位于路径：

```text
/admin-api/tk/open/v1/tiktok
```

版本升级如果产生不兼容变化，应使用新的路径版本，不能静默改变 v1 已有字段含义。

### 2.3 外部资源编号

以下编号均由 A 方生成，属于 opaque ID，不代表数据库编号：

| 编号 | 用途 |
| --- | --- |
| `authSessionId` | TikTok 授权会话 |
| `connectionId` | TikTok 账号连接 |
| `uploadId` | 视频上传会话 |
| `mediaId` | 已完成的视频 |
| `taskId` | 发布任务 |
| `detailId` | 单个账号的发布明细 |
| `eventId` | 回调事件 |

调用方自己的业务编号使用 `externalAccountId`、`externalRequestId` 和 `clientState` 传递。所有 A 方资源编号只能在当前 `clientId` 范围内查询和操作。

### 2.4 机器可读文档与 SDK

- OpenAPI 3.0.3：[tiktok-open-api.json](openapi/tiktok-open-api.json)
- Java 示例：[tiktok-open-api-java.md](sdk/tiktok-open-api-java.md)
- Node.js 示例：[tiktok-open-api-node.md](sdk/tiktok-open-api-node.md)
- Python 示例：[tiktok-open-api-python.md](sdk/tiktok-open-api-python.md)

SDK 示例只用于展示签名和最小调用流程。客户端密钥必须从服务端环境变量或服务端密钥管理系统读取，不得写入浏览器、移动端、前端代码或代码仓库。

## 3. 调用认证

### 3.1 客户端凭证

A 方为每个外部调用方分配：

```text
clientId
clientSecret
callbackSecret
```

- `clientSecret` 用于调用方请求 A 方接口时生成 HMAC 签名
- `callbackSecret` 用于调用方校验 A 方发出的回调签名
- 密钥只应保存在调用方服务端环境变量或服务端密钥管理系统中
- 客户端凭证按 `clientId` 独立，不能跨客户端复用

### 3.2 请求头

除 `GET /auth/callback` 外，其他接口都需要签名认证：

```http
X-TK-Client-Id: client_example
X-TK-Timestamp: 1798761600
X-TK-Nonce: nonce_7f3c_example
X-TK-Request-Id: req_202609010001
X-TK-Signature: <Base64 HMAC-SHA256 result>
```

其中：

| 请求头 | 必填 | 说明 |
| --- | --- | --- |
| `X-TK-Client-Id` | 是 | A 方分配的客户端编号 |
| `X-TK-Timestamp` | 是 | Unix 秒级时间戳 |
| `X-TK-Nonce` | 是 | 每次请求唯一的随机串 |
| `X-TK-Request-Id` | 否 | 1-128 位跟踪编号；缺失或格式不合法时由服务端生成 |
| `X-TK-Signature` | 是 | 请求签名 |

JSON 请求使用：

```http
Content-Type: application/json
```

本地分片请求使用：

```http
Content-Type: application/octet-stream
```

### 3.3 签名规则

签名算法：`HMAC-SHA256`，结果使用标准 Base64 编码。

签名原文严格由 5 行组成，每行之间使用 `\n`，最后一行不追加换行：

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
nonce_7f3c_example
8f434346648f6b96df89dda901c5176b10a6d83961dd3c1ac88b59b2dc327aa4
```

计算方式：

```text
signature = Base64(HMAC-SHA256(clientSecret, canonicalString))
```

约定：

- `HTTP_METHOD` 必须大写
- `REQUEST_TARGET` 包含 `/admin-api`、完整路径和原始查询字符串，不包含域名
- 查询参数的顺序和编码必须与实际发送的 URL 完全一致
- JSON 摘要基于实际发送的原始 UTF-8 字节，不能签名后再次格式化 JSON
- 二进制分片摘要基于实际发送的分片字节
- GET 和无请求体 DELETE 使用空字节数组的 SHA-256
- 时间戳允许与服务端时间相差最多 300 秒
- `nonce` 在防重放窗口内不能重复使用
- 默认签名请求体上限为 8,388,608 bytes；当前默认本地分片为 1,048,576 bytes

### 3.4 OAuth 回调例外

```http
GET /auth/callback
```

该地址由 TikTok 浏览器重定向访问，不走外部客户端 HMAC 过滤器。服务端通过 `state` 查找并校验授权会话。调用方不应主动构造或调用该端点。

### 3.5 权限、IP、限流和配额

通过签名后，服务端还会检查：

- 客户端是否启用
- 来源 IP 是否在该客户端允许范围内
- 客户端是否具有当前模块的 `auth`、`media` 或 `publish` 权限
- nonce 是否重复
- 每分钟调用次数
- 每日调用总量

限流和每日配额可按客户端配置；未配置时服务端默认值分别为每分钟 120 次、每日 10,000 次。

### 3.6 认证相关错误

| 错误码 | HTTP 状态 | 含义 |
| --- | --- | --- |
| `OPEN_API_AUTH_HEADER_MISSING` | 401 | 必要认证头缺失 |
| `OPEN_API_CLIENT_INVALID` | 401 | `clientId` 不存在或已禁用 |
| `OPEN_API_SIGNATURE_INVALID` | 401 | 请求签名错误 |
| `OPEN_API_TIMESTAMP_EXPIRED` | 401 | 时间戳无效或超过 300 秒 |
| `OPEN_API_NONCE_REPLAYED` | 401 | nonce 已使用 |
| `OPEN_API_IP_NOT_ALLOWED` | 403 | 来源 IP 不在允许范围 |
| `OPEN_API_PERMISSION_DENIED` | 403 | 当前客户端缺少模块权限 |
| `OPEN_API_RATE_LIMITED` | 429 | 每分钟调用次数超限 |
| `OPEN_API_QUOTA_EXCEEDED` | 429 | 每日调用总量超限 |
| `OPEN_API_BODY_TOO_LARGE` | 413 | 签名请求体超过上限 |
| `OPEN_API_GUARD_UNAVAILABLE` | 503 | 防重放或限流组件暂时不可用 |

## 4. 通用响应

### 4.1 成功响应

当前控制器创建、查询、取消、解绑和重试成功时均返回 HTTP `200`。

```json
{
  "code": 0,
  "msg": "OK",
  "data": {},
  "requestId": "req_202609010001"
}
```

### 4.2 失败响应

```json
{
  "code": "MEDIA_NOT_READY",
  "msg": "media is not ready",
  "data": null,
  "requestId": "req_202609010001"
}
```

调用方应同时记录 HTTP 状态、业务 `code`、`msg` 和 `requestId`。

| HTTP 状态 | 场景 |
| --- | --- |
| `200` | 操作或查询成功 |
| `400` | 参数错误或业务状态不允许 |
| `401` | 认证失败 |
| `403` | IP 或权限策略拒绝 |
| `404` | 资源不存在或不属于当前客户端 |
| `409` | 幂等键冲突 |
| `413` | 请求体或视频超过限制 |
| `429` | 调用频率或每日配额超限 |
| `500` | 服务内部错误 |
| `503` | 依赖服务、配置或防护组件暂时不可用 |

## 5. 推荐调用流程

### 5.1 重定向授权流程

```text
1. 调用 POST /auth/sessions，authMode=REDIRECT
2. 在用户浏览器打开 authorizeUrl
3. 用户在 TikTok 完成授权
4. TikTok 重定向到 GET /auth/callback
5. A 方校验 state、换取并加密保存 Token
6. 调用方轮询授权会话或接收授权回调
7. 获得 connectionId
```

### 5.2 二维码授权流程

```text
1. 调用 POST /auth/sessions，authMode=QR_CODE
2. 向用户展示 qrcodeUrl
3. 用户扫码并确认
4. 调用方轮询 GET /auth/sessions/{authSessionId}
5. 查询接口同步检查二维码状态
6. 授权成功后获得 connectionId
```

### 5.3 上传和发布流程

```text
1. 创建上传会话
2. 根据 uploadMode 选择 OSS 直传或 LOCAL 分片
3. 调用 complete 完成上传并获得 mediaId
4. 使用 Idempotency-Key 创建发布任务
5. 获得 taskId
6. 查询任务汇总状态和发布明细
7. 对 FAILED 明细按需重试
8. 同时可通过回调接收结果，以查询接口作为补偿
```

## 6. 授权接口

### 6.1 创建授权会话

```http
POST /auth/sessions
Content-Type: application/json
```

请求：

```json
{
  "externalAccountId": "account_10001",
  "authMode": "REDIRECT",
  "clientState": "page_state_001"
}
```

字段：

| 字段 | 类型 | 必填 | 限制 | 说明 |
| --- | --- | --- | --- | --- |
| `externalAccountId` | string | 是 | 最大 128 字符 | 调用方自己的账号编号 |
| `authMode` | string | 是 | `REDIRECT` 或 `QR_CODE` | 授权模式 |
| `clientState` | string | 否 | 最大 512 字符 | 调用方透传状态，不参与 OAuth 安全校验 |

授权会话有效期固定为 15 分钟。

#### 6.1.1 REDIRECT 响应

```json
{
  "code": 0,
  "msg": "OK",
  "data": {
    "authSessionId": "auth_example",
    "externalAccountId": "account_10001",
    "clientState": "page_state_001",
    "authMode": "REDIRECT",
    "authorizeUrl": "https://www.tiktok.com/v2/auth/authorize/?...",
    "qrcodeUrl": null,
    "status": "WAITING",
    "expireTime": "2026-09-01T18:00:00"
  },
  "requestId": "req_example"
}
```

调用方必须在用户浏览器中打开 `authorizeUrl`，不得在服务端模拟用户授权或截取授权 code。

#### 6.1.2 QR_CODE 响应

```json
{
  "code": 0,
  "msg": "OK",
  "data": {
    "authSessionId": "auth_example",
    "externalAccountId": "account_10001",
    "clientState": "page_state_001",
    "authMode": "QR_CODE",
    "authorizeUrl": null,
    "qrcodeUrl": "https://www.tiktok.com/qr/example",
    "status": "WAITING",
    "expireTime": "2026-09-01T18:00:00"
  },
  "requestId": "req_example"
}
```

调用方展示 `qrcodeUrl`，并轮询授权会话。每次查询处于 `WAITING` 的二维码会话时，服务端会向 TikTok 查询二维码状态；确认成功后完成 Token 交换并建立账号连接。

### 6.2 TikTok 授权回调

```http
GET /auth/callback?code=...&state=...
```

错误回调也可能携带：

```text
error
error_description
state
```

处理规则：

1. 校验 `state` 是否存在
2. 根据 `state` 查找未过期授权会话
3. 处理 TikTok 返回的 `error` 或授权 code
4. 使用 code 换取 Token
5. 加密保存 access token 和 refresh token
6. 获取 TikTok 账号资料
7. 创建或更新当前客户端的账号连接
8. 更新授权会话状态
9. 生成授权结果回调事件
10. 返回简单 HTML 页面，提示用户可关闭窗口

TikTok redirect URI 由 A 方固定配置，调用方不能在创建会话时传入任意 redirect URI。

### 6.3 查询授权会话

```http
GET /auth/sessions/{authSessionId}
```

响应：

```json
{
  "code": 0,
  "msg": "OK",
  "data": {
    "authSessionId": "auth_example",
    "externalAccountId": "account_10001",
    "clientState": "page_state_001",
    "connectionId": "conn_example",
    "accountName": "TikTok Account",
    "status": "SUCCESS",
    "failReason": null,
    "expireTime": "2026-09-01T18:00:00"
  },
  "requestId": "req_example"
}
```

授权状态：

| 状态 | 含义 |
| --- | --- |
| `WAITING` | 等待用户操作 |
| `SUCCESS` | 授权成功，响应中包含 `connectionId` |
| `FAILED` | 授权失败，查看 `failReason` |
| `EXPIRED` | 授权会话已过期 |

### 6.4 查询账号连接

```http
GET /connections?externalAccountId=account_10001&status=AUTHORIZED
```

查询参数均可选：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `externalAccountId` | string | 按调用方账号编号过滤 |
| `status` | string | 按连接授权状态过滤，例如 `AUTHORIZED` |

响应：

```json
{
  "code": 0,
  "msg": "OK",
  "data": [
    {
      "connectionId": "conn_example",
      "externalAccountId": "account_10001",
      "accountName": "TikTok Account",
      "username": "example_user",
      "avatarUrl": "https://example.invalid/avatar.png",
      "authStatus": "AUTHORIZED",
      "tokenStatus": "NORMAL",
      "lastAuthTime": "2026-09-01T17:00:00"
    }
  ],
  "requestId": "req_example"
}
```

Token 不会通过接口返回。

### 6.5 解绑账号

```http
POST /connections/{connectionId}/disconnect
```

当前实现会：

- 将连接状态设置为 `DISCONNECTED`
- 将 Token 状态设置为 `REVOKED`
- 清空本地保存的访问 Token 和刷新 Token
- 阻止该连接继续创建新的发布任务

当前实现没有在此接口中调用 TikTok 撤销授权接口。

响应：

```json
{
  "code": 0,
  "msg": "OK",
  "data": true,
  "requestId": "req_example"
}
```

## 7. 视频上传接口

### 7.1 创建上传会话

```http
POST /media/uploads
Content-Type: application/json
```

请求：

```json
{
  "fileName": "video.mp4",
  "fileSize": 104857600,
  "contentType": "video/mp4",
  "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
}
```

字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `fileName` | string | 是 | 文件扩展名必须是 `mp4`、`mov` 或 `webm` |
| `fileSize` | integer | 是 | 必须大于 0，默认上限为 1,000,000,000 bytes |
| `contentType` | string | 否 | 缺失时按扩展名生成 `video/{extension}` |
| `sha256` | string | 否 | 文件 SHA-256 十六进制摘要；提供后会在完成上传时校验 |

上传会话默认有效期为 24 小时。服务端根据配置返回 `OSS` 或 `LOCAL`，调用方不能自行指定 `uploadMode`。

#### 7.1.1 OSS 模式响应

```json
{
  "code": 0,
  "msg": "OK",
  "data": {
    "uploadId": "upload_example",
    "uploadMode": "OSS",
    "chunkSize": null,
    "totalChunks": null,
    "uploadUrl": "https://example.invalid",
    "objectKey": "tk/open-api/client_example/20260901/upload_example.mp4",
    "fields": {
      "policy": "<short-lived policy>",
      "ossAccessKeyId": "<short-lived form identifier>",
      "signature": "<short-lived form signature>",
      "xOssMetaSha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    },
    "expireTime": "2026-09-02T17:00:00"
  },
  "requestId": "req_example"
}
```

调用方向 `uploadUrl` 提交 OSS 表单时使用：

| OSS 表单字段 | 取值来源 |
| --- | --- |
| `key` | `objectKey` |
| `policy` | `fields.policy` |
| `OSSAccessKeyId` | `fields.ossAccessKeyId` |
| `Signature` | `fields.signature` |
| `x-oss-meta-sha256` | `fields.xOssMetaSha256`，创建时提供 SHA-256 才使用 |
| `file` | 视频文件 |

上述返回值是单次上传所需的短期表单字段，不是调用方长期凭据。OSS 上传完成后仍必须调用 `complete`。

#### 7.1.2 LOCAL 模式响应

```json
{
  "code": 0,
  "msg": "OK",
  "data": {
    "uploadId": "upload_example",
    "uploadMode": "LOCAL",
    "chunkSize": 1048576,
    "totalChunks": 100,
    "uploadUrl": null,
    "objectKey": null,
    "fields": null,
    "expireTime": "2026-09-02T17:00:00"
  },
  "requestId": "req_example"
}
```

`chunkSize` 和 `totalChunks` 以响应为准。当前默认分片大小为 1,048,576 bytes。

### 7.2 上传本地分片

该接口只适用于 `uploadMode=LOCAL`：

```http
PUT /media/uploads/{uploadId}/chunks/{chunkIndex}
Content-Type: application/octet-stream
```

- `chunkIndex` 从 `0` 开始
- 除最后一片外，每片字节数必须严格等于返回的 `chunkSize`
- 最后一片字节数等于文件剩余字节数
- 请求体就是原始分片字节，不使用 multipart 包装
- HMAC body hash 必须基于这段原始分片字节计算
- 对 OSS 会话调用该接口会返回 `UPLOAD_MODE_INVALID`
- 上传会话必须处于 `UPLOADING`；其他状态返回 `MEDIA_UPLOAD_STATUS_INVALID`（HTTP 400）

响应：

```json
{
  "code": 0,
  "msg": "OK",
  "data": true,
  "requestId": "req_example"
}
```

### 7.3 查询上传进度

```http
GET /media/uploads/{uploadId}
```

响应：

```json
{
  "code": 0,
  "msg": "OK",
  "data": {
    "uploadId": "upload_example",
    "mediaId": null,
    "uploadMode": "LOCAL",
    "fileSize": 104857600,
    "uploadedSize": 52428800,
    "status": "UPLOADING"
  },
  "requestId": "req_example"
}
```

当前响应不返回分片编号列表。常见状态：

| 状态 | 含义 |
| --- | --- |
| `UPLOADING` | 上传中 |
| `READY` | 已完成并生成 `mediaId` |
| `CANCELLED` | 已取消 |

上传会话过期且仍处于 `UPLOADING` 时，查询或操作会返回 `MEDIA_UPLOAD_EXPIRED`。

### 7.4 完成上传

```http
POST /media/uploads/{uploadId}/complete
Content-Type: application/json
```

上传会话必须处于 `UPLOADING`；其他状态返回 `MEDIA_UPLOAD_STATUS_INVALID`（HTTP 400）。

请求：

```json
{
  "fileSize": 104857600,
  "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
  "coverTimestampMs": 1200
}
```

字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `fileSize` | integer | 是 | 必须与创建上传会话时一致 |
| `sha256` | string | 条件必填 | 创建时提供 SHA-256 后，这里必须提供相同值 |
| `coverTimestampMs` | integer | 否 | 封面时间点，毫秒 |

LOCAL 模式会检查：

- 所有分片是否存在
- 合并后的文件长度是否一致
- 创建时提供 SHA-256 时，合并文件摘要是否一致

OSS 模式会执行 HEAD 元数据查询并检查：

- 对象是否存在且可访问
- `Content-Length` 是否等于创建时的 `fileSize`
- 创建时提供 SHA-256 时，对象的 `x-oss-meta-sha256` 是否一致

校验成功后响应：

```json
{
  "code": 0,
  "msg": "OK",
  "data": {
    "mediaId": "media_example",
    "uploadId": "upload_example",
    "fileName": "video.mp4",
    "fileSize": 104857600,
    "contentType": "video/mp4",
    "status": "READY"
  },
  "requestId": "req_example"
}
```

### 7.5 取消上传

```http
DELETE /media/uploads/{uploadId}
```

上传会话必须处于 `UPLOADING`；其他状态返回 `MEDIA_UPLOAD_STATUS_INVALID`（HTTP 400）。LOCAL 模式会删除临时分片目录；OSS 模式会在对象存储已配置且存在 `objectKey` 时删除对象。上传记录状态更新为 `CANCELLED`。

响应：

```json
{
  "code": 0,
  "msg": "OK",
  "data": true,
  "requestId": "req_example"
}
```

## 8. 发布接口

### 8.1 创建发布任务

```http
POST /publish/tasks
Content-Type: application/json
Idempotency-Key: publish_order_001
```

`Idempotency-Key` 必填，长度不能超过 128 字符。

请求：

```json
{
  "connectionIds": [
    "conn_example"
  ],
  "mediaId": "media_example",
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

| 字段 | 类型 | 必填 | 限制或默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `connectionIds` | string[] | 是 | 1-20 个 | 已授权的 TikTok 账号连接 |
| `mediaId` | string | 是 | 状态必须为 `READY` | 已完成上传的视频 |
| `title` | string | 否 | 最大 512 字符 | 发布标题 |
| `caption` | string | 否 | 最大 2200 字符 | 发布文案 |
| `postMode` | string | 是 | `DIRECT_POST` 或 `UPLOAD_TO_INBOX` | 发布模式 |
| `privacyLevel` | string | 是 | 非空 | TikTok 隐私级别 |
| `allowComment` | boolean | 否 | 默认 `true` | 允许评论 |
| `allowDuet` | boolean | 否 | 默认 `false` | 允许合拍 |
| `allowStitch` | boolean | 否 | 默认 `false` | 允许拼接 |
| `commercialContent` | boolean | 否 | 默认 `false` | 商业内容标记 |
| `brandContent` | boolean | 否 | 默认 `false` | 品牌内容标记 |
| `aigcContent` | boolean | 否 | 默认 `true` | AI 生成内容标记 |
| `externalRequestId` | string | 否 | 最大 128 字符 | 调用方业务编号 |

响应：

```json
{
  "code": 0,
  "msg": "OK",
  "data": {
    "taskId": "task_example",
    "mediaId": "media_example",
    "externalRequestId": "business_order_001",
    "status": "PENDING",
    "accountCount": 1,
    "successCount": 0,
    "failedCount": 0,
    "pendingCount": 1,
    "failReason": null,
    "createTime": "2026-09-01T17:30:00",
    "updateTime": "2026-09-01T17:30:00"
  },
  "requestId": "req_example"
}
```

任务创建后异步执行，不等待 TikTok 完成发布。

幂等规则：

- 幂等范围为当前 `clientId + Idempotency-Key`
- 幂等记录有效期为 7 天
- 相同键和相同请求内容返回原任务
- 相同键但请求内容不同返回 `IDEMPOTENCY_KEY_CONFLICT`
- 请求内容哈希基于服务端序列化后的完整发布请求

### 8.2 查询发布任务

```http
GET /publish/tasks/{taskId}
```

响应：

```json
{
  "code": 0,
  "msg": "OK",
  "data": {
    "taskId": "task_example",
    "mediaId": "media_example",
    "externalRequestId": "business_order_001",
    "status": "SUCCESS",
    "accountCount": 1,
    "successCount": 1,
    "failedCount": 0,
    "pendingCount": 0,
    "failReason": null,
    "createTime": "2026-09-01T17:30:00",
    "updateTime": "2026-09-01T17:35:00"
  },
  "requestId": "req_example"
}
```

任务状态：

| 状态 | 含义 |
| --- | --- |
| `PENDING` | 已创建，等待处理 |
| `PROCESSING` | 至少一个明细仍在处理 |
| `SUCCESS` | 全部成功 |
| `FAILED` | 全部失败 |
| `PARTIAL_SUCCESS` | 部分成功、部分失败 |

### 8.3 查询发布明细

```http
GET /publish/tasks/{taskId}/details
```

响应：

```json
{
  "code": 0,
  "msg": "OK",
  "data": [
    {
      "detailId": "detail_example",
      "taskId": "task_example",
      "connectionId": "conn_example",
      "accountName": "TikTok Account",
      "status": "SUCCESS",
      "tiktokStatus": "PUBLISH_COMPLETE",
      "publishId": "publish_example",
      "publishUrl": null,
      "failReason": null,
      "retryCount": 0,
      "updateTime": "2026-09-01T17:35:00"
    }
  ],
  "requestId": "req_example"
}
```

`tiktokStatus` 保存 TikTok 返回或本地流程产生的状态，调用方应以明细 `status` 和任务汇总 `status` 作为主要业务判断依据。

### 8.4 重试失败明细

```http
POST /publish/details/{detailId}/retry
```

只有 `FAILED` 状态的明细允许重试。重试后：

- 明细 `status` 变为 `PENDING`
- `tiktokStatus` 变为 `RETRY_PENDING`
- 清空旧的 `publishId` 和 `failReason`
- `retryCount` 加 1
- 重新汇总任务状态并异步提交

响应：

```json
{
  "code": 0,
  "msg": "OK",
  "data": true,
  "requestId": "req_example"
}
```

## 9. 状态回调

### 9.1 回调地址和事件范围

每个客户端可分别配置授权回调地址和发布回调地址。A 方只向事件所属 `clientId` 的回调地址发送事件。

当前事件类型包括：

- `authorization.completed`
- `authorization.failed`
- `publish.processing`
- `publish.success`
- `publish.failed`

回调地址必须使用 HTTPS，并通过服务端的回调 URL 安全校验。

### 9.2 回调请求头和签名

```http
Content-Type: application/json
X-TK-Event-Id: evt_example
X-TK-Timestamp: 1798761600
X-TK-Signature: <Base64 HMAC-SHA256 result>
```

回调签名原文严格由 3 行组成：

```text
EVENT_ID
TIMESTAMP
SHA256_HEX(BODY)
```

计算方式：

```text
signature = Base64(HMAC-SHA256(callbackSecret, canonicalString))
```

调用方必须使用收到的原始请求体字节计算 SHA-256，并使用服务端保存的 `callbackSecret` 验证签名。

### 9.3 授权成功事件

```json
{
  "eventId": "evt_auth_example",
  "eventType": "authorization.completed",
  "authSessionId": "auth_example",
  "connectionId": "conn_example",
  "externalAccountId": "account_10001",
  "accountName": "TikTok Account",
  "status": "AUTHORIZED",
  "failReason": null,
  "clientState": "page_state_001",
  "occurredAt": "2026-09-01T17:00:00+08:00"
}
```

### 9.4 授权失败事件

```json
{
  "eventId": "evt_auth_example",
  "eventType": "authorization.failed",
  "authSessionId": "auth_example",
  "connectionId": null,
  "externalAccountId": "account_10001",
  "accountName": null,
  "status": "FAILED",
  "failReason": "Authorization failed",
  "clientState": "page_state_001",
  "occurredAt": "2026-09-01T17:00:00+08:00"
}
```

### 9.5 发布状态事件

```json
{
  "eventId": "evt_publish_example",
  "eventType": "publish.success",
  "taskId": "task_example",
  "detailId": "detail_example",
  "connectionId": "conn_example",
  "externalRequestId": "business_order_001",
  "status": "SUCCESS",
  "publishId": "publish_example",
  "publishUrl": null,
  "failReason": null,
  "occurredAt": "2026-09-01T17:35:00+08:00"
}
```

### 9.6 回调超时和重试

当前实现参数：

- 单次 HTTP 回调超时：10 秒
- 成功条件：HTTP 2xx
- 最大投递次数：8 次，包括首次投递
- 失败后的重试间隔：1、5、15、30、60、180、360 分钟
- 达到最大次数仍失败后，事件状态变为 `FAILED`

调用方应：

1. 先校验回调签名
2. 使用 `eventId` 做幂等
3. 快速返回 HTTP 2xx
4. 将耗时业务处理放入自己的异步队列
5. 以任务和明细查询接口作为回调丢失时的补偿

回调按至少一次语义处理，同一 `eventId` 可能被投递多次。

## 10. 主要错误码

### 10.1 授权和连接

| 错误码 | 含义 |
| --- | --- |
| `AUTH_SESSION_NOT_FOUND` | 授权会话不存在或不属于当前客户端 |
| `AUTHORIZATION_FAILED` | TikTok 授权失败 |
| `TIKTOK_CONFIG_REQUIRED` | TikTok 应用配置不可用 |
| `CONNECTION_NOT_FOUND` | 账号连接不存在或不属于当前客户端 |
| `CONNECTION_NOT_AUTHORIZED` | 账号连接不是已授权状态 |

授权会话自然过期后通过状态返回 `EXPIRED`；继续操作已过期上传会话时使用 `MEDIA_UPLOAD_EXPIRED`。

### 10.2 视频上传

| 错误码 | 含义 |
| --- | --- |
| `MEDIA_NOT_FOUND` | 上传会话或视频不存在 |
| `MEDIA_NOT_READY` | 本地分片不完整或 OSS 对象暂时不可用 |
| `MEDIA_FILE_INVALID` | 扩展名、大小、分片或摘要校验失败 |
| `MEDIA_FILE_TOO_LARGE` | 视频超过 1,000,000,000 bytes 上限 |
| `MEDIA_UPLOAD_EXPIRED` | 上传会话已过期 |
| `MEDIA_UPLOAD_STATUS_INVALID` | 上传会话不处于 `UPLOADING`，不能上传分片、完成或取消 |
| `MEDIA_UPLOAD_FAILED` | 本地分片保存失败 |
| `UPLOAD_MODE_INVALID` | 对 OSS 会话调用本地分片接口 |
| `OSS_CONFIG_REQUIRED` | OSS 上传已启用但配置不完整 |

### 10.3 发布

| 错误码 | 含义 |
| --- | --- |
| `IDEMPOTENCY_KEY_REQUIRED` | 缺少 `Idempotency-Key` |
| `IDEMPOTENCY_KEY_INVALID` | 幂等键超过 128 字符 |
| `IDEMPOTENCY_KEY_CONFLICT` | 相同幂等键对应不同请求内容 |
| `IDEMPOTENCY_RESULT_UNAVAILABLE` | 幂等记录存在但原任务不可读取 |
| `PUBLISH_TASK_NOT_FOUND` | 发布任务不存在或不属于当前客户端 |
| `PUBLISH_DETAIL_NOT_FOUND` | 发布明细不存在或不属于当前客户端 |
| `PUBLISH_RETRY_STATUS_INVALID` | 明细当前状态不允许重试 |

### 10.4 通用错误

| 错误码 | 含义 |
| --- | --- |
| `OPEN_API_PARAMETER_INVALID` | 请求参数校验失败 |
| `OPEN_API_INTERNAL_ERROR` | 服务内部错误 |
| `OPEN_API_SECRET_UNAVAILABLE` | 服务端无法加载客户端签名密钥 |

## 11. 外部调用方接入检查清单

### 11.1 客户端和安全

- [ ] 已获得独立的 `clientId`、`clientSecret` 和 `callbackSecret`
- [ ] 密钥只保存在服务端环境变量或服务端密钥管理系统
- [ ] 已实现 5 行请求 canonical string
- [ ] 已使用实际请求字节计算 SHA-256
- [ ] 已实现 HMAC-SHA256 和标准 Base64
- [ ] 已使用 Unix 秒时间戳，并保证服务器时钟同步
- [ ] 已为每次请求生成唯一 nonce
- [ ] 已记录和传递合法的 `X-TK-Request-Id`
- [ ] 已处理 401、403、413、429 和 503

### 11.2 授权

- [ ] 已选择并实现 `REDIRECT` 或 `QR_CODE` 流程
- [ ] 重定向授权只在用户浏览器中打开 `authorizeUrl`
- [ ] 二维码授权展示 `qrcodeUrl` 并轮询会话
- [ ] 已处理 `WAITING`、`SUCCESS`、`FAILED`、`EXPIRED`
- [ ] 已保存 `authSessionId` 和 `connectionId`
- [ ] 已处理解绑后的连接不可发布

### 11.3 上传

- [ ] 已支持服务端返回 `OSS` 或 `LOCAL` 两种模式
- [ ] LOCAL 分片使用 `PUT` 和 `application/octet-stream`
- [ ] LOCAL 分片按返回的 `chunkSize` 切割，索引从 0 开始
- [ ] OSS 表单正确映射 `objectKey` 和 `fields`
- [ ] 已计算完整文件 SHA-256
- [ ] 已调用 `complete`，并等待返回 `READY`
- [ ] 已处理 1,000,000,000 bytes 上限和上传会话过期

### 11.4 发布和查询

- [ ] 每次创建发布任务都发送 `Idempotency-Key`
- [ ] 同一业务重试复用相同幂等键和相同请求内容
- [ ] 已保存 `taskId` 和 `detailId`
- [ ] 已查询任务汇总和逐账号明细
- [ ] 只对 `FAILED` 明细调用重试接口
- [ ] 已处理 `PARTIAL_SUCCESS`

### 11.5 回调

- [ ] 已使用原始请求体校验 3 行回调签名
- [ ] 已使用 `eventId` 做回调幂等
- [ ] 回调处理可在 10 秒内返回 2xx
- [ ] 已按最多 8 次投递设计重复事件处理
- [ ] 已使用查询接口作为回调补偿

### 11.6 上线前验证

- [ ] 已确认目标环境中 API 基地址可访问
- [ ] 已确认客户端状态、权限和 IP 白名单配置正确
- [ ] 已完成 REDIRECT 或 QR_CODE 真实授权联调
- [ ] 已分别验证实际启用的 OSS 或 LOCAL 上传模式
- [ ] 已完成上传、发布、查询全链路联调
- [ ] 已验证回调地址、签名、超时和重试行为
- [ ] 已准备密钥轮换和异常告警方案

## 12. 接口清单与交付物

### 12.1 已实现的外部端点

| 模块 | 方法 | 路径 |
| --- | --- | --- |
| 授权 | POST | `/auth/sessions` |
| 授权 | GET | `/auth/sessions/{authSessionId}` |
| 连接 | GET | `/connections` |
| 连接 | POST | `/connections/{connectionId}/disconnect` |
| OAuth | GET | `/auth/callback` |
| 上传 | POST | `/media/uploads` |
| 上传 | GET | `/media/uploads/{uploadId}` |
| 上传 | PUT | `/media/uploads/{uploadId}/chunks/{chunkIndex}` |
| 上传 | POST | `/media/uploads/{uploadId}/complete` |
| 上传 | DELETE | `/media/uploads/{uploadId}` |
| 发布 | POST | `/publish/tasks` |
| 发布 | GET | `/publish/tasks/{taskId}` |
| 发布 | GET | `/publish/tasks/{taskId}/details` |
| 发布 | POST | `/publish/details/{detailId}/retry` |

### 12.2 使用顺序

1. 先阅读本文，理解鉴权、资源隔离、授权、上传、发布和回调行为
2. 使用 OpenAPI JSON 导入 API 工具或生成客户端骨架
3. 参考对应语言 SDK 示例实现请求签名
4. 在目标环境完成真实授权、上传、发布和回调联调
5. 只有在部署、配置、连通性和端到端验证全部完成后，才能据实确认具体环境的可用状态
