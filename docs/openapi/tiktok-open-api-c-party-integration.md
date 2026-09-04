# A 方 TikTok Open API

## C 方授权发布对接开发文档

**文档版本：** v1.1
**适用对象：** C 方后端、前端和联调人员
**A 方生产服务：** `https://tkassetplant.fnn.net.cn`
**更新时间：** 2026-09-04

本文用于指导 C 方调用 A 方的 TikTok 授权、视频上传、视频发布和状态查询接口。C 方不需要访问 A 方数据库，也不需要了解 A 方内部实现。

## 1. 对接目标

C 方最终需要实现下面一条业务链路：

```text
C 方用户
  -> C 方创建授权会话
  -> 用户在浏览器完成 TikTok 授权
  -> C 方获得 connectionId
  -> C 方上传视频并获得 mediaId
  -> C 方创建发布任务并获得 taskId
  -> C 方接收回调或主动查询发布结果
```

A 方负责：

- TikTok OAuth 授权和二维码授权
- TikTok Token 的保存、刷新和失效处理
- TikTok 账号连接管理
- 视频文件接收或 OSS 直传
- TikTok 发布任务异步执行
- 发布状态同步和结果回调

C 方负责：

- C 方自己的用户、订单和业务数据
- 引导用户打开授权地址或展示二维码
- 保存 A 方返回的资源编号
- 在 C 方服务端调用 A 方 API
- 接收并幂等处理 A 方回调
- 向 C 方用户展示授权和发布状态

## 2. A 方需要先提供给 C 方的信息

A 方完成客户端配置后，向 C 方安全地提供以下信息：

| 配置项 | 用途 | 保存位置 |
| --- | --- | --- |
| `clientId` | 标识 C 方应用 | C 方服务端配置 |
| `clientSecret` | 签名 C 方发往 A 方的请求 | C 方服务端密钥管理系统 |
| `callbackSecret` | 校验 A 方发往 C 方的回调 | C 方服务端密钥管理系统 |
| A 方生产 API 地址 | 固定为本文第 3 节地址 | C 方服务端配置 |
| C 方授权回调地址 | 接收 `authorization.*` 事件 | A 方客户端配置 |
| C 方发布回调地址 | 接收 `publish.*` 事件 | A 方客户端配置 |
| C 方出口 IP | 配置 A 方 IP 白名单，可选 | A 方客户端配置 |

### 2.1 A 方后台“新增调用方”配置

A 方管理员在开放 API 管理页面为每个外部系统创建一个独立调用方。C 方、B 方和其他合作方必须分别创建，不能共用 `clientId` 或密钥。

建议按下面方式填写。带“示例”的内容需要替换为 C 方的真实信息：

| 后台字段 | 填写内容 | 是否必填 | 说明 |
| --- | --- | --- | --- |
| 调用方名称 | `C方视频发布系统`（示例） | 是 | 用于 A 方后台识别调用方 |
| 授权回调地址 | `https://c.example.com/webhooks/tiktok/authorization`（示例） | 建议填写 | C 方接收 `authorization.completed`、`authorization.failed` |
| 发布回调地址 | `https://c.example.com/webhooks/tiktok/publish`（示例） | 建议填写 | C 方接收 `publish.processing`、`publish.success`、`publish.failed` |
| 允许 IP | C 方调用 A 方 API 的公网出口 IP（`【待C方提供】`） | 可选 | 配置后仅允许这些 IP 调用；多个 IP 使用逗号分隔 |
| 权限 | 勾选 `auth`、`media`、`publish` | 是 | 完成授权、上传和发布闭环需要这三类权限 |
| 每分钟限额 | `120`（建议初始值） | 是 | 最终值按 C 方调用量确认 |
| 每日限额 | `10000`（建议初始值） | 是 | 最终值按 C 方调用量确认 |
| 状态 | 启用 | 是 | 联调前必须启用 |
| 备注 | `C方 TikTok 授权发布接口`（示例） | 否 | 记录合作方和用途 |

### 2.2 地址归属和禁止填写项

以下地址分别由不同系统使用，不能混填：

| 地址 | 由谁提供 | 使用位置 |
| --- | --- | --- |
| A 方 API 地址 | A 方 | C 方服务端请求地址 |
| A 方 TikTok OAuth 回调地址 | A 方 | TikTok 应用的 OAuth 配置 |
| C 方授权回调地址 | C 方 | A 方“新增调用方”页面 |
| C 方发布回调地址 | C 方 | A 方“新增调用方”页面 |

A 方固定的 TikTok OAuth 回调地址为：

```text
https://tkassetplant.fnn.net.cn/admin-api/tk/open/v1/tiktok/auth/callback
```

该地址只需要配置到 A 方的 TikTok 应用中，不要填入 A 方后台的“授权回调地址”或“发布回调地址”字段。

C 方回调地址必须满足：

- 使用 C 方自己的公网 HTTPS 域名。
- 由 C 方后端接收 `POST` JSON 请求。
- 不能填写 `localhost`、内网 IP、A 方域名或 TikTok 官方 OAuth 地址。
- C 方更换回调域名后，应先通知 A 方更新调用方配置。

允许 IP 的填写规则：

- 填写 C 方服务端访问 A 方时的公网出口 IP，不是 A 方服务器 IP。
- 暂不确定时可以留空，表示不启用 IP 限制；正式环境建议配置。
- 如果 C 方通过多个公网出口访问，需要全部加入白名单。

### 2.3 凭证交付

A 方点击保存后，将在结果窗口中向管理员展示以下凭证：

| 凭证 | 用途 | 交给 C 方 |
| --- | --- | --- |
| `clientId` | 标识 C 方调用方 | 是 |
| `clientSecret` | C 方生成请求 HMAC 签名 | 是 |
| `callbackSecret` | C 方验证 A 方回调签名 | 是 |

注意：

- 三项凭证只交付给 C 方后端负责人，不发送到浏览器或前端页面。
- `clientSecret` 和 `callbackSecret` 只在创建或轮换时展示，关闭窗口后不能依赖再次查看。
- 轮换调用密钥会使旧的 `clientSecret` 立即失效。
- 轮换回调密钥会使旧的 `callbackSecret` 立即失效。
- 文档、工单、聊天记录和日志中不得记录完整密钥。

安全要求：

- `clientSecret` 和 `callbackSecret` 只能放在 C 方后端，不能放到浏览器、移动端、前端 JavaScript 或小程序包中。
- C 方前端只能调用 C 方自己的后端，由 C 方后端代为调用 A 方。
- 文档、日志、异常信息中不得输出完整密钥。
- C 方更换出口 IP 或回调域名时，应先通知 A 方更新客户端配置。

## 3. 服务地址和接口清单

### 3.1 生产基地址

```text
https://tkassetplant.fnn.net.cn/admin-api/tk/open/v1/tiktok
```

例如：

```text
POST https://tkassetplant.fnn.net.cn/admin-api/tk/open/v1/tiktok/auth/sessions
```

### 3.2 接口清单

| 模块 | 方法 | 路径 | C 方用途 |
| --- | --- | --- | --- |
| 授权 | `POST` | `/auth/sessions` | 创建授权会话 |
| 授权 | `GET` | `/auth/sessions/{authSessionId}` | 查询授权状态 |
| 授权 | `GET` | `/connections` | 查询已授权账号 |
| 授权 | `POST` | `/connections/{connectionId}/disconnect` | 解绑账号 |
| TikTok 回调 | `GET` | `/auth/callback` | A 方接收 TikTok 回调，C 方不调用 |
| 媒体 | `POST` | `/media/uploads` | 创建视频上传会话 |
| 媒体 | `GET` | `/media/uploads/{uploadId}` | 查询上传状态 |
| 媒体 | `PUT` | `/media/uploads/{uploadId}/chunks/{chunkIndex}` | LOCAL 模式上传分片 |
| 媒体 | `POST` | `/media/uploads/{uploadId}/complete` | 完成上传并获取 `mediaId` |
| 媒体 | `DELETE` | `/media/uploads/{uploadId}` | 取消上传 |
| 发布 | `POST` | `/publish/tasks` | 创建异步发布任务 |
| 发布 | `GET` | `/publish/tasks/{taskId}` | 查询任务汇总状态 |
| 发布 | `GET` | `/publish/tasks/{taskId}/details` | 查询每个账号的发布明细 |
| 发布 | `POST` | `/publish/details/{detailId}/retry` | 重试失败明细 |

除 `/auth/callback` 外，所有接口都必须带 A 方请求签名。

## 4. 通用请求认证

### 4.1 请求头

```http
X-TK-Client-Id: ${clientId}
X-TK-Timestamp: 1798761600
X-TK-Nonce: 5b8c7c9d0a2e4f1a
X-TK-Request-Id: c-request-202609020001
X-TK-Signature: ${signature}
```

| 请求头 | 必填 | 规则 |
| --- | --- | --- |
| `X-TK-Client-Id` | 是 | A 方分配的 `clientId` |
| `X-TK-Timestamp` | 是 | Unix 秒级时间戳，与 A 方服务器相差不超过 300 秒 |
| `X-TK-Nonce` | 是 | 每次请求唯一，不能复用 |
| `X-TK-Request-Id` | 否 | C 方链路追踪编号，最长 128 字符 |
| `X-TK-Signature` | 是 | Base64 编码的 HMAC-SHA256 签名 |

建议 C 方所有请求都生成自己的 `X-TK-Request-Id`，并在 C 方日志中保存该编号。

### 4.2 签名原文

签名原文由 5 行组成，行之间是一个 LF 换行符，最后一行不追加换行：

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
5b8c7c9d0a2e4f1a
8f434346648f6b96df89dda901c5176b10a6d83961dd3c1ac88b59b2dc327aa4
```

签名计算：

```text
signature = Base64(HMAC-SHA256(clientSecret, canonicalString))
```

必须遵守：

- `HTTP_METHOD` 使用大写，例如 `GET`、`POST`、`PUT`、`DELETE`。
- `REQUEST_TARGET` 包含 `/admin-api`、完整路径和原始查询字符串，不包含域名。
- 查询参数顺序和编码必须与实际发出的 URL 完全一致。
- JSON 签名前先生成 UTF-8 字节，签名使用的字节必须和实际发送的字节完全相同。
- 二进制分片直接对原始分片字节计算 SHA-256。
- 没有请求体时，对空字节计算 SHA-256。
- 不要对 JSON 先签名、再重新格式化后发送。

### 4.3 Node.js 签名调用模板

以下代码只能运行在 C 方服务端。示例使用 Node.js 18+ 的 `fetch`：

```js
import crypto from 'node:crypto';

const BASE = 'https://tkassetplant.fnn.net.cn/admin-api/tk/open/v1/tiktok';
const CLIENT_ID = process.env.TK_OPEN_API_CLIENT_ID;
const CLIENT_SECRET = process.env.TK_OPEN_API_CLIENT_SECRET;

function sha256(bytes) {
  return crypto.createHash('sha256').update(bytes).digest('hex');
}

function sign(method, target, timestamp, nonce, bodyBytes) {
  const canonical = [
    method.toUpperCase(),
    target,
    timestamp,
    nonce,
    sha256(bodyBytes)
  ].join('\n');
  return crypto.createHmac('sha256', CLIENT_SECRET)
    .update(canonical, 'utf8')
    .digest('base64');
}

async function callA(method, path, payload, extraHeaders = {}) {
  const bodyBytes = payload === undefined
    ? Buffer.alloc(0)
    : Buffer.isBuffer(payload)
      ? payload
      : Buffer.from(JSON.stringify(payload), 'utf8');
  const timestamp = String(Math.floor(Date.now() / 1000));
  const nonce = crypto.randomUUID().replaceAll('-', '');
  const target = `/admin-api/tk/open/v1/tiktok${path}`;
  const response = await fetch(`${BASE}${path}`, {
    method,
    headers: {
      'X-TK-Client-Id': CLIENT_ID,
      'X-TK-Timestamp': timestamp,
      'X-TK-Nonce': nonce,
      'X-TK-Request-Id': crypto.randomUUID(),
      'X-TK-Signature': sign(method, target, timestamp, nonce, bodyBytes),
      ...(payload !== undefined
        ? { 'Content-Type': Buffer.isBuffer(payload)
            ? 'application/octet-stream' : 'application/json' }
        : {}),
      ...extraHeaders
    },
    body: payload === undefined ? undefined : bodyBytes
  });
  const result = await response.json();
  if (!response.ok || result.code !== 0) {
    throw new Error(`${response.status} ${result.code}: ${result.msg}`);
  }
  return result.data;
}
```

C 方使用其他语言时，必须保持同样的字节和换行规则。

## 5. 统一响应格式

成功响应：

```json
{
  "code": 0,
  "msg": "OK",
  "data": {},
  "requestId": "c-request-202609020001"
}
```

失败响应：

```json
{
  "code": "MEDIA_NOT_READY",
  "msg": "media is not ready",
  "data": null,
  "requestId": "c-request-202609020001"
}
```

C 方处理规则：

1. 先判断 HTTP 状态码。
2. 再判断 JSON 中的 `code` 是否为 `0`。
3. 只有 HTTP 成功且 `code=0` 时，才使用 `data`。
4. 错误日志至少记录 HTTP 状态码、`code`、`msg`、`requestId`，不要记录密钥和 Token。

## 6. 授权对接

### 6.1 推荐的 REDIRECT 授权流程

#### 第一步：C 方服务端创建授权会话

```http
POST /auth/sessions
Content-Type: application/json
```

```json
{
  "externalAccountId": "c-user-10001",
  "authMode": "REDIRECT",
  "clientState": "c-order-202609020001"
}
```

字段说明：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `externalAccountId` | 是 | C 方自己的账号编号，同一个 C 方内应保持稳定 |
| `authMode` | 是 | `REDIRECT` 或 `QR_CODE` |
| `clientState` | 否 | C 方透传状态，例如用户 ID、页面状态或业务单号 |

响应：

```json
{
  "code": 0,
  "msg": "OK",
  "data": {
    "authSessionId": "auth_example",
    "externalAccountId": "c-user-10001",
    "clientState": "c-order-202609020001",
    "authMode": "REDIRECT",
    "authorizeUrl": "https://www.tiktok.com/v2/auth/authorize/?...",
    "qrcodeUrl": null,
    "status": "WAITING",
    "expireTime": "2026-09-02T11:00:00"
  },
  "requestId": "c-request-202609020001"
}
```

#### 第二步：C 方引导用户打开 `authorizeUrl`

C 方前端打开 A 方返回的 `authorizeUrl`。用户在 TikTok 页面完成授权后，TikTok 会回调 A 方固定地址：

```text
https://tkassetplant.fnn.net.cn/admin-api/tk/open/v1/tiktok/auth/callback
```

C 方不需要调用该地址，也不需要自己接收 TikTok 的 `code`。

#### 第三步：C 方获得 `connectionId`

授权完成后，C 方有两种方式获知结果：

- 推荐：接收 A 方的 `authorization.completed` 或 `authorization.failed` 回调。
- 兜底：轮询 `GET /auth/sessions/{authSessionId}`。

### 6.2 查询授权状态

```http
GET /auth/sessions/{authSessionId}
```

成功响应：

```json
{
  "code": 0,
  "msg": "OK",
  "data": {
    "authSessionId": "auth_example",
    "externalAccountId": "c-user-10001",
    "clientState": "c-order-202609020001",
    "connectionId": "conn_example",
    "accountName": "TikTok Account",
    "status": "SUCCESS",
    "failReason": null,
    "expireTime": "2026-09-02T11:00:00"
  },
  "requestId": "c-request-202609020002"
}
```

授权状态：

| 状态 | C 方处理 |
| --- | --- |
| `WAITING` | 继续等待，不创建发布任务 |
| `SUCCESS` | 保存 `connectionId`，可以进入上传和发布 |
| `FAILED` | 展示 `failReason`，允许用户重新授权 |
| `EXPIRED` | 授权会话失效，需要重新创建会话 |

建议轮询间隔：3 秒。授权会话有效期为 15 分钟，超过 `expireTime` 不要继续轮询。

### 6.3 QR_CODE 授权

创建会话时改为：

```json
{
  "externalAccountId": "c-user-10001",
  "authMode": "QR_CODE",
  "clientState": "c-order-202609020001"
}
```

A 方返回 `qrcodeUrl`。C 方展示二维码，并在 `status=WAITING` 时轮询同一个授权会话接口。二维码被确认后，查询结果会变为 `SUCCESS` 并返回 `connectionId`。

### 6.4 查询已授权账号

```http
GET /connections?externalAccountId=c-user-10001&status=AUTHORIZED
```

参数均可选：

| 参数 | 说明 |
| --- | --- |
| `externalAccountId` | C 方账号编号 |
| `status` | 连接状态，例如 `AUTHORIZED` |

响应中的 `connectionId` 是后续发布必须使用的账号编号。A 方不会返回 TikTok access token。

### 6.5 解绑账号

```http
POST /connections/{connectionId}/disconnect
```

成功后该连接变为不可发布状态。C 方应将本地账号状态同步为“已解绑”，再次发布前必须重新授权。

## 7. 视频上传对接

### 7.1 创建上传会话

```http
POST /media/uploads
Content-Type: application/json
```

```json
{
  "fileName": "demo.mp4",
  "fileSize": 104857600,
  "contentType": "video/mp4",
  "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
}
```

字段：

| 字段 | 必填 | 规则 |
| --- | --- | --- |
| `fileName` | 是 | 扩展名必须是 `mp4`、`mov` 或 `webm` |
| `fileSize` | 是 | 大于 0，当前上限为 1,000,000,000 bytes |
| `contentType` | 否 | 推荐填写正确 MIME 类型 |
| `sha256` | 否 | 推荐填写完整视频文件的 64 位十六进制 SHA-256 |

服务端决定返回 `OSS` 或 `LOCAL`，C 方不能自行指定模式。上传会话默认有效期为 24 小时。

### 7.2 OSS 模式

当响应中：

```json
{
  "uploadMode": "OSS",
  "uploadUrl": "https://oss.example.com",
  "objectKey": "tk/open-api/client/date/upload_example.mp4",
  "fields": {
    "policy": "...",
    "ossAccessKeyId": "...",
    "signature": "...",
    "xOssMetaSha256": "..."
  }
}
```

C 方直接向 `uploadUrl` 发起 `multipart/form-data`，表单字段如下：

| 表单字段 | 值 |
| --- | --- |
| `key` | `objectKey` |
| `policy` | `fields.policy` |
| `OSSAccessKeyId` | `fields.ossAccessKeyId` |
| `Signature` | `fields.signature` |
| `x-oss-meta-sha256` | `fields.xOssMetaSha256`，不为空时填写 |
| `file` | 视频文件 |

注意：

- OSS 表单上传地址不使用 A 方 `X-TK-*` 签名头。
- 返回的 OSS 字段是短期表单字段，不是 C 方长期 OSS 密钥。
- OSS 返回 2xx 后，C 方仍必须调用 A 方的 `complete` 接口。
- 如果创建时填写了 `sha256`，完成上传时必须再次填写同一个值。

### 7.3 LOCAL 模式

当响应中：

```json
{
  "uploadId": "upload_example",
  "uploadMode": "LOCAL",
  "chunkSize": 1048576,
  "totalChunks": 100,
  "uploadUrl": null,
  "objectKey": null,
  "fields": null,
  "expireTime": "2026-09-03T10:00:00"
}
```

按 `chunkIndex=0` 开始逐片调用：

```http
PUT /media/uploads/{uploadId}/chunks/0
Content-Type: application/octet-stream
```

请求体就是原始二进制分片，不是 JSON，也不是 multipart：

```text
videoBytes[0 : chunkSize]
```

规则：

- `chunkSize` 和 `totalChunks` 以本次响应为准。
- `chunkIndex` 从 `0` 开始。
- 除最后一片外，分片大小必须严格等于 `chunkSize`。
- 最后一片等于剩余字节数。
- 每一个分片请求都必须单独生成新的时间戳、nonce 和签名。
- 分片签名的 body hash 必须对这一个分片的原始字节计算。
- 当前默认分片大小为 1 MiB，不能自行改成更大的分片；如果 A 方返回其他值，以返回值为准。

如果 C 方中途失败，可以先调用 `GET /media/uploads/{uploadId}` 查看已上传大小，然后按 C 方本地记录继续上传未完成分片。当前查询接口只返回 `uploadedSize`，不返回分片编号列表，建议 C 方自己持久化已成功的分片索引。

### 7.4 查询上传状态

```http
GET /media/uploads/{uploadId}
```

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
  "requestId": "c-request-202609020010"
}
```

### 7.5 完成上传并获取 `mediaId`

所有 OSS 直传或 LOCAL 分片完成后，调用：

```http
POST /media/uploads/{uploadId}/complete
Content-Type: application/json
```

```json
{
  "fileSize": 104857600,
  "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
  "coverTimestampMs": 1200
}
```

成功响应：

```json
{
  "code": 0,
  "msg": "OK",
  "data": {
    "mediaId": "media_example",
    "uploadId": "upload_example",
    "fileName": "demo.mp4",
    "fileSize": 104857600,
    "contentType": "video/mp4",
    "status": "READY"
  },
  "requestId": "c-request-202609020011"
}
```

只有 `status=READY` 且存在 `mediaId` 时，C 方才能创建发布任务。

### 7.6 取消上传

```http
DELETE /media/uploads/{uploadId}
```

仅 `UPLOADING` 状态可以取消。C 方放弃上传时建议调用该接口，避免保留无用的临时文件或 OSS 对象。

## 8. 视频发布对接

### 8.1 创建异步发布任务

```http
POST /publish/tasks
Content-Type: application/json
Idempotency-Key: c-publish-202609020001
```

请求示例：

```json
{
  "connectionIds": ["conn_example"],
  "mediaId": "media_example",
  "title": "C 方视频标题",
  "caption": "C 方视频文案",
  "postMode": "DIRECT_POST",
  "privacyLevel": "PUBLIC_TO_EVERYONE",
  "allowComment": true,
  "allowDuet": false,
  "allowStitch": false,
  "commercialContent": false,
  "brandContent": false,
  "aigcContent": true,
  "externalRequestId": "c-business-order-202609020001"
}
```

字段：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `connectionIds` | 是 | 已授权连接编号，1-20 个 |
| `mediaId` | 是 | `complete` 返回且状态为 `READY` 的媒体编号 |
| `title` | 否 | 最长 512 字符 |
| `caption` | 否 | 最长 2200 字符 |
| `postMode` | 是 | `DIRECT_POST` 或 `UPLOAD_TO_INBOX` |
| `privacyLevel` | 是 | TikTok 隐私级别，建议使用 A 方/TikTok 允许的值 |
| `allowComment` | 否 | 默认 `true` |
| `allowDuet` | 否 | 默认 `false` |
| `allowStitch` | 否 | 默认 `false` |
| `commercialContent` | 否 | 默认 `false` |
| `brandContent` | 否 | 默认 `false` |
| `aigcContent` | 否 | 默认 `true` |
| `externalRequestId` | 否 | C 方业务编号，最长 128 字符 |

建议 C 方把 `externalRequestId` 设置为自己的订单号或发布单号，把 `Idempotency-Key` 设置为一次发布请求的唯一键。

### 8.2 幂等规则

`Idempotency-Key` 是必填请求头，最长 128 字符。

幂等范围为：

```text
clientId + Idempotency-Key
```

处理规则：

- 相同 `clientId`、相同 `Idempotency-Key`、相同请求内容：返回原任务。
- 相同 `clientId`、相同 `Idempotency-Key`、不同请求内容：返回 `409 IDEMPOTENCY_KEY_CONFLICT`。
- 幂等记录有效期为 7 天。
- 网络超时后，C 方应使用原来的 `Idempotency-Key` 重试，不要立即生成新 key。
- 只有确认是新业务发布时，才生成新的 key。

### 8.3 发布响应

```json
{
  "code": 0,
  "msg": "OK",
  "data": {
    "taskId": "task_example",
    "mediaId": "media_example",
    "externalRequestId": "c-business-order-202609020001",
    "status": "PENDING",
    "accountCount": 1,
    "successCount": 0,
    "failedCount": 0,
    "pendingCount": 1,
    "failReason": null,
    "createTime": "2026-09-02T10:30:00",
    "updateTime": "2026-09-02T10:30:00"
  },
  "requestId": "c-request-202609020020"
}
```

创建任务只表示 A 方已接受任务，不表示 TikTok 已经发布成功。

### 8.4 查询任务汇总状态

```http
GET /publish/tasks/{taskId}
```

任务状态：

| 状态 | 含义 | C 方处理 |
| --- | --- | --- |
| `PENDING` | 任务已创建，等待处理 | 等待 |
| `PROCESSING` | 至少一个账号仍在处理 | 等待 |
| `SUCCESS` | 所有账号发布成功 | 订单成功 |
| `FAILED` | 所有账号发布失败 | 订单失败，可查看明细 |
| `PARTIAL_SUCCESS` | 部分成功、部分失败 | 订单部分成功，逐条查看明细 |

### 8.5 查询发布明细

```http
GET /publish/tasks/{taskId}/details
```

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
      "updateTime": "2026-09-02T10:35:00"
    }
  ],
  "requestId": "c-request-202609020021"
}
```

C 方业务判断优先使用明细 `status`，`tiktokStatus` 仅作为 TikTok 或 A 方内部状态补充信息。`publishUrl` 可能为空，不能把 `publishId` 当作公开视频地址。

### 8.6 重试失败明细

```http
POST /publish/details/{detailId}/retry
```

只有明细 `status=FAILED` 时允许重试。重试成功返回后，C 方重新查询任务和明细，不需要重新上传媒体。

## 9. A 方结果回调

### 9.1 C 方需要实现的回调接口

C 方需要提供并交给 A 方配置两个 HTTPS 地址：

```text
POST https://c.example.com/webhooks/tiktok/authorization
POST https://c.example.com/webhooks/tiktok/publish
```

实际路径由 C 方决定，但必须满足：

- 使用 HTTPS。
- 接收 `Content-Type: application/json`。
- 能读取原始请求体字节。
- 校验签名后快速返回 2xx。
- 根据 `eventId` 幂等处理重复事件。

### 9.2 回调请求头

```http
Content-Type: application/json
X-TK-Event-Id: evt_example
X-TK-Timestamp: 1798761600
X-TK-Signature: ${callbackSignature}
```

### 9.3 回调签名

回调签名原文是 3 行：

```text
EVENT_ID
TIMESTAMP
SHA256_HEX(BODY)
```

计算方式：

```text
signature = Base64(HMAC-SHA256(callbackSecret, canonicalString))
```

Node.js 校验示例：

```js
function verifyCallback(eventId, timestamp, signature, rawBody) {
  const canonical = [
    eventId,
    timestamp,
    sha256(rawBody)
  ].join('\n');
  const expected = crypto.createHmac('sha256', process.env.TK_OPEN_API_CALLBACK_SECRET)
    .update(canonical, 'utf8')
    .digest('base64');
  return crypto.timingSafeEqual(
    Buffer.from(expected, 'utf8'),
    Buffer.from(signature || '', 'utf8')
  );
}
```

C 方还应自行检查：

- `eventId`、`timestamp`、`signature` 是否存在。
- 回调时间是否在合理窗口内，建议不超过 300 秒。
- `eventId` 是否已经处理过。
- JSON 中的 `eventId`、`eventType` 是否和请求头一致。

### 9.4 授权成功事件

```json
{
  "eventId": "evt_auth_example",
  "eventType": "authorization.completed",
  "authSessionId": "auth_example",
  "connectionId": "conn_example",
  "externalAccountId": "c-user-10001",
  "accountName": "TikTok Account",
  "status": "AUTHORIZED",
  "failReason": null,
  "clientState": "c-order-202609020001",
  "occurredAt": "2026-09-02T10:20:00+08:00"
}
```

C 方收到后：

1. 以 `eventId` 做幂等判断。
2. 校验 `externalAccountId` 对应的是 C 方自己的用户。
3. 保存 `connectionId`。
4. 将 C 方账号状态更新为已授权。
5. 返回 HTTP 200。

### 9.5 授权失败事件

```json
{
  "eventId": "evt_auth_failed_example",
  "eventType": "authorization.failed",
  "authSessionId": "auth_example",
  "connectionId": null,
  "externalAccountId": "c-user-10001",
  "accountName": null,
  "status": "FAILED",
  "failReason": "Authorization failed",
  "clientState": "c-order-202609020001",
  "occurredAt": "2026-09-02T10:20:00+08:00"
}
```

C 方收到后将授权会话标记为失败，并展示 `failReason`。不要把失败事件中的 `connectionId=null` 保存为有效连接。

### 9.6 发布状态事件

发布事件类型：

- `publish.processing`
- `publish.success`
- `publish.failed`

成功示例：

```json
{
  "eventId": "evt_publish_example",
  "eventType": "publish.success",
  "taskId": "task_example",
  "detailId": "detail_example",
  "connectionId": "conn_example",
  "externalRequestId": "c-business-order-202609020001",
  "status": "SUCCESS",
  "publishId": "publish_example",
  "publishUrl": null,
  "failReason": null,
  "occurredAt": "2026-09-02T10:35:00+08:00"
}
```

失败示例：

```json
{
  "eventId": "evt_publish_failed_example",
  "eventType": "publish.failed",
  "taskId": "task_example",
  "detailId": "detail_example",
  "connectionId": "conn_example",
  "externalRequestId": "c-business-order-202609020001",
  "status": "FAILED",
  "publishId": null,
  "publishUrl": null,
  "failReason": "TikTok publish failed",
  "occurredAt": "2026-09-02T10:35:00+08:00"
}
```

C 方必须按 `detailId` 更新单账号发布状态，再按 C 方业务规则汇总订单状态。回调事件可能重复，不能以重复回调再次创建订单或再次触发发布。

### 9.7 回调响应和重试

C 方回调端点在完成签名校验并持久化事件后，返回任意 2xx 即表示接收成功：

```http
HTTP/1.1 200 OK
```

A 方当前回调行为：

- 单次回调超时约 10 秒。
- HTTP 2xx 视为成功。
- 最多投递 8 次，包括首次投递。
- 重试间隔依次为 1、5、15、30、60、180、360 分钟。
- C 方返回非 2xx、超时或签名校验失败时，A 方会按策略重试。

因此 C 方回调接口应先落库、快速返回，再异步执行业务处理。建议保存完整原始 body、`eventId`、事件类型、接收时间和处理结果。

## 10. C 方完整实现伪代码

```text
1. C 方将系统名称、两个 HTTPS 回调地址和公网出口 IP 提供给 A 方
2. A 方在开放 API 管理页面创建 C 方调用方，并启用 `auth`、`media`、`publish` 权限
3. A 方将 `clientId`、`clientSecret`、`callbackSecret` 安全交给 C 方
4. C 方后端保存三项凭证
5. A 方配置 C 方授权回调地址和发布回调地址
6. C 方调用 POST /auth/sessions
7. C 方前端打开 data.authorizeUrl，或展示 data.qrcodeUrl
8. C 方接收授权回调，或轮询 GET /auth/sessions/{authSessionId}
9. status=SUCCESS 后保存 connectionId
10. C 方计算视频 fileSize 和完整 sha256
11. C 方调用 POST /media/uploads
12. uploadMode=OSS：按返回 fields 向 uploadUrl 直传
13. uploadMode=LOCAL：按 chunkSize 上传全部二进制分片
14. 调用 POST /media/uploads/{uploadId}/complete
15. 确认 data.status=READY，保存 mediaId
16. 生成 Idempotency-Key
17. 调用 POST /publish/tasks
18. 保存 taskId
19. 接收 publish.* 回调，或轮询任务和明细
20. SUCCESS：更新 C 方订单为成功
21. FAILED：展示失败原因，必要时调用 retry
22. PARTIAL_SUCCESS：按 detailId 分别处理账号结果
```

## 11. 错误码处理建议

| HTTP | 错误码示例 | C 方处理 |
| --- | --- | --- |
| `400` | `OPEN_API_PARAMETER_INVALID`、`MEDIA_NOT_READY` | 修正请求或等待资源状态 |
| `400` | `MEDIA_FILE_INVALID` | 重新检查文件名、大小、SHA-256、分片大小 |
| `401` | `OPEN_API_AUTH_HEADER_MISSING` | 检查请求头和签名生成 |
| `401` | `OPEN_API_SIGNATURE_INVALID` | 检查原始 URL、body 字节、时间戳和密钥 |
| `401` | `OPEN_API_TIMESTAMP_EXPIRED` | 校准 C 方服务器时间 |
| `401` | `OPEN_API_NONCE_REPLAYED` | 每次请求生成新 nonce，原请求超时重试时也要新 nonce |
| `403` | `OPEN_API_IP_NOT_ALLOWED` | 联系 A 方配置 C 方出口 IP |
| `403` | `OPEN_API_PERMISSION_DENIED` | 联系 A 方确认 `auth/media/publish` 权限 |
| `404` | `CONNECTION_NOT_FOUND`、`MEDIA_NOT_FOUND` | 检查资源编号是否属于当前 C 方 |
| `409` | `IDEMPOTENCY_KEY_CONFLICT` | 不要复用该 key 发送不同发布内容 |
| `413` | `MEDIA_FILE_TOO_LARGE` | 压缩视频或调整文件大小 |
| `429` | `OPEN_API_RATE_LIMITED` | 按退避策略重试，不要高频轮询 |
| `429` | `OPEN_API_QUOTA_EXCEEDED` | 等待配额恢复或联系 A 方 |
| `503` | `TIKTOK_CONFIG_REQUIRED`、`OSS_CONFIG_REQUIRED` | 联系 A 方检查平台或存储配置 |
| `503` | `AUTHORIZATION_FAILED`、`MEDIA_UPLOAD_FAILED` | 记录错误，按业务规则重试或重新授权 |

不要对所有错误都无限重试：

- 签名、参数、权限和资源编号错误，应先修正后再请求。
- 网络超时、502/503、429 可以有限退避重试。
- 发布创建超时必须复用原 `Idempotency-Key`。
- TikTok 已产生发布 ID 但状态暂未确定时，不要立即重复创建新的发布任务。

## 12. C 方联调验收清单

### 12.1 A 方配置

- [ ] 已获得 C 方专用 `clientId`、`clientSecret`、`callbackSecret`
- [ ] 后台调用方名称已填写且能明确对应 C 方系统
- [ ] C 方客户端已开启 `auth`、`media`、`publish` 权限
- [ ] C 方出口 IP 已加入白名单，或双方确认不限制 IP
- [ ] C 方授权回调地址已配置为 C 方自己的公网 HTTPS 接口
- [ ] C 方发布回调地址已配置为 C 方自己的公网 HTTPS 接口
- [ ] 未将 A 方 TikTok OAuth 回调地址填入 C 方回调字段
- [ ] 每分钟限额和每日限额已按 C 方调用量确认
- [ ] 调用方状态已启用
- [ ] TikTok App 的 OAuth 配置已包含 A 方固定 redirect URI

### 12.2 C 方请求

- [ ] 所有 A 方业务接口都带 4 个 `X-TK-*` 必填认证头
- [ ] 签名使用的是实际发送的 request target
- [ ] 签名使用的是实际发送的原始 body 字节
- [ ] 每次请求使用唯一 nonce
- [ ] C 方服务器时间准确
- [ ] 发布请求始终带 `Idempotency-Key`
- [ ] 超时重试时复用原 `Idempotency-Key`

### 12.3 授权发布闭环

- [ ] REDIRECT 授权能够打开 TikTok 授权页面
- [ ] 授权成功后能够获得 `connectionId`
- [ ] 授权失败和过期能够展示明确状态
- [ ] 能够查询已授权连接
- [ ] OSS 或 LOCAL 上传路径至少完成一条联调
- [ ] `complete` 返回 `READY` 和 `mediaId`
- [ ] 能够创建 `DIRECT_POST` 或 `UPLOAD_TO_INBOX` 任务
- [ ] 能够查询任务和明细状态
- [ ] 能够接收并校验授权回调签名
- [ ] 能够接收并校验发布回调签名
- [ ] 重复回调不会重复处理
- [ ] 失败明细能够调用 retry

## 13. 机器可读文档和示例

A 方仓库同时提供：

- OpenAPI 3.0.3：`docs/openapi/tiktok-open-api.json`
- Node.js 示例：`docs/sdk/tiktok-open-api-node.md`
- Python 示例：`docs/sdk/tiktok-open-api-python.md`
- Java 示例：`docs/sdk/tiktok-open-api-java.md`

本文是面向 C 方项目落地的流程文档；字段和接口发生疑问时，以 OpenAPI JSON 和 A 方实际返回为准，并通过 `requestId` 与 A 方联调定位问题。
