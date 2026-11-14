# TikTok 授权、上传与发布接入

本页适用于已开通 TikTok 开放 API 客户端的服务端。自动出片使用账号 Token；本页使用独立的 `clientId`、`clientSecret` 和可选 `callbackSecret`，不能互换。千川上传和入计划不在此接口族内。

## 1. 开通与调用顺序

向管理员确认环境地址、客户端凭证、允许的模块（`auth`、`media`、`publish`）、IP 白名单、调用配额及可选回调地址。既有接入约定域名为 `https://tkassetplant.fnn.net.cn`，实际以开通环境为准。对外路径统一带 `/admin-api`。

| 步骤 | 接口 | 处理 |
| --- | --- | --- |
| 账号授权 | `POST /auth/sessions` | 传自己的 `externalAccountId`、`authMode`，取得 `authSessionId`、`launchUrl` |
| 用户确认 | 浏览器打开返回的 `launchUrl` | 用户完成 TikTok 授权；不要代造 OAuth `code/state` |
| 查询授权 | `GET /auth/sessions/{authSessionId}`、`GET /connections` | 确认授权成功，保存 `connectionId` 及调用方账号映射 |
| 准备媒体 | 公网 HTTPS 视频，或视频/图片上传会话 | 视频 URL 可用 quick-tasks；本地媒体走上传，图片需轮询转换为 READY |
| 创建发布 | `POST /publish/quick-tasks` 或 `POST /publish/tasks` | 视频/图文 tasks 均需 `Idempotency-Key`；quick-tasks 仅视频 |
| 查询结果 | `GET /publish/tasks/{taskId}`、`GET /publish/tasks/{taskId}/details` | 任务与单账号明细均需检查，API 受理不代表 TikTok 发布成功 |
| 发布指标 | `GET /publish/tasks/{taskId}/metrics` | 发布后查询播放、点赞等；与发布完成状态区分 |

表中相对路径统一以 `/admin-api/tk/open/v1/tiktok` 开头。每个需签名的请求同时携带四项 HMAC 头，不是任选其中一项。

托管页 `/auth/sessions/{id}/launch`、公开状态 `/auth/sessions/{id}/launch/status` 和平台 OAuth 回调 `/auth/callback` 不需要调用方 HMAC。服务端正式授权状态查询仍需签名。OAuth 回调由 TikTok 调用，不能手工触发来模拟授权。

## 2. HMAC 调用规则

```http
X-TK-Client-Id: <CLIENT_ID>
X-TK-Timestamp: <Unix秒级时间戳>
X-TK-Nonce: <每次请求新随机串>
X-TK-Signature: <标准Base64的HMAC-SHA256>
X-TK-Request-Id: <可选追踪编号>
```

签名原文严格为五行，使用单个 LF（`\n`）连接，末尾**不换行**：

```text
METHOD_UPPERCASE
REQUEST_TARGET
TIMESTAMP
NONCE
SHA256_HEX(BODY_BYTES)
```

`REQUEST_TARGET` 是实际发送的完整路径及原始查询串，包含 `/admin-api`，不含域名。查询参数顺序、URL 编码必须与发出的请求完全一致。先固定并签名实际发送的 UTF-8/二进制字节，再原样发送；签名后不可再次 JSON 序列化。无请求体时摘要对象为空字节数组。摘要为小写十六进制，最终签名为 `Base64(HMAC-SHA256(clientSecret_UTF8, canonical_UTF8))`，不是 Base64URL，也不是十六进制 HMAC。

默认时间容差 ±300 秒，实际限流、配额、时间窗以平台配置为准。重试时沿用同一业务幂等键，但必须重新生成时间戳、nonce 和签名。

## 3. Python 签名与请求示例

Python 3 标准库即可运行；凭证取自服务端环境变量，不写进代码。以下函数不包含自动重试。

```python
import base64
import hashlib
import hmac
import json
import os
import time
import uuid
import urllib.error
import urllib.request

BASE = os.environ["TK_OPEN_API_BASE_URL"].rstrip("/")
CLIENT_ID = os.environ["TK_OPEN_API_CLIENT_ID"]
CLIENT_SECRET = os.environ["TK_OPEN_API_CLIENT_SECRET"]

def call_open_api(method, target, payload=None, extra_headers=None):
    # target须已经正确编码，包含/admin-api及查询串。
    method = method.upper()
    body = (b"" if payload is None else payload if isinstance(payload, bytes)
            else json.dumps(payload, ensure_ascii=False,
                            separators=(",", ":")).encode("utf-8"))
    timestamp = str(int(time.time()))
    nonce = uuid.uuid4().hex
    canonical = "\n".join([
        method, target, timestamp, nonce, hashlib.sha256(body).hexdigest()
    ])
    signature = base64.b64encode(hmac.new(
        CLIENT_SECRET.encode("utf-8"), canonical.encode("utf-8"), hashlib.sha256
    ).digest()).decode("ascii")
    headers = dict(extra_headers or {})
    headers.update({
        "X-TK-Client-Id": CLIENT_ID,
        "X-TK-Timestamp": timestamp,
        "X-TK-Nonce": nonce,
        "X-TK-Request-Id": uuid.uuid4().hex,
        "X-TK-Signature": signature,
    })
    if payload is not None:
        headers["Content-Type"] = ("application/octet-stream"
                                   if isinstance(payload, bytes) else "application/json")
    request = urllib.request.Request(BASE + target, headers=headers,
        data=body if payload is not None else None, method=method)
    # 禁止把签名头带到重定向后的其它域名；环境地址应直接指向HTTPS API。
    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, req, fp, code, msg, hdrs, newurl):
            return None
    opener = urllib.request.build_opener(NoRedirect)
    try:
        with opener.open(request, timeout=30) as response:
            result = json.load(response)
    except urllib.error.HTTPError as error:
        raise RuntimeError(f"HTTP {error.code}; request failed") from error
    if result.get("code") != 0:
        raise RuntimeError(f'{result.get("code")}: {result.get("msg")} '
                           f'(requestId={result.get("requestId")})')
    return result["data"]

# 创建授权会话后，在用户浏览器打开返回的launchUrl完成授权。
session = call_open_api("POST", "/admin-api/tk/open/v1/tiktok/auth/sessions", {
    "externalAccountId": "YOUR_STABLE_ACCOUNT_ID", "authMode": "AUTO"
})
```

有查询参数时，用 `urllib.parse.urlencode` **先**构造 `target`，再将同一字符串交给函数，不要签名一个顺序却发送另一个顺序。

## 4. Node.js 签名与请求示例

使用支持原生 `fetch` 的 Node.js（如 Node.js 18+），保存为 `.mjs`。同样只从环境变量读取凭证。

```javascript
import crypto from 'node:crypto';

const BASE = process.env.TK_OPEN_API_BASE_URL.replace(/\/$/, '');
const CLIENT_ID = process.env.TK_OPEN_API_CLIENT_ID;
const SECRET = process.env.TK_OPEN_API_CLIENT_SECRET;

async function callOpenApi(method, target, payload, extraHeaders = {}) {
  method = method.toUpperCase();
  const body = payload === undefined ? Buffer.alloc(0)
    : Buffer.isBuffer(payload) ? payload : Buffer.from(JSON.stringify(payload), 'utf8');
  const timestamp = String(Math.floor(Date.now() / 1000));
  const nonce = crypto.randomUUID().replaceAll('-', '');
  const digest = crypto.createHash('sha256').update(body).digest('hex');
  const canonical = [method, target, timestamp, nonce, digest].join('\n');
  const signature = crypto.createHmac('sha256', SECRET)
    .update(canonical, 'utf8').digest('base64');
  const response = await fetch(BASE + target, {
    method, redirect: 'error', signal: AbortSignal.timeout(30000),
    headers: {
      ...extraHeaders,
      'X-TK-Client-Id': CLIENT_ID, 'X-TK-Timestamp': timestamp,
      'X-TK-Nonce': nonce, 'X-TK-Request-Id': crypto.randomUUID(),
      'X-TK-Signature': signature,
      ...(payload === undefined ? {} : {'Content-Type':
        Buffer.isBuffer(payload) ? 'application/octet-stream' : 'application/json'})
    },
    body: payload === undefined ? undefined : body
  });
  const result = await response.json();
  if (!response.ok || result.code !== 0) {
    throw new Error(`${response.status} ${result.code}: ${result.msg} (requestId=${result.requestId})`);
  }
  return result.data;
}

const session = await callOpenApi('POST', '/admin-api/tk/open/v1/tiktok/auth/sessions', {
  externalAccountId: 'YOUR_STABLE_ACCOUNT_ID', authMode: 'AUTO'
});
```

查询串先用 `new URLSearchParams(...)` 编码，生成一次完整 `target` 后签名及发送同一个字符串；没有请求体的 GET 不要发送空 JSON 对象。

## 5. 上传本地视频

1. 签名调用 `POST /media/uploads`，传 `fileName`、`fileSize`，可选 `contentType`、完整文件 `sha256`。文件扩展名允许 mp4/mov/webm，大小需大于 0 且不超过 1,000,000,000 字节。
2. 读取 `uploadMode`，由服务端选择，调用方不能指定。
3. `OSS` 模式：向返回的 `uploadUrl` 提交 multipart 表单，包含 `key=objectKey`、`policy=fields.policy`、`OSSAccessKeyId=fields.ossAccessKeyId`、`Signature=fields.signature`、视频 `file`；如有摘要再提交 `x-oss-meta-sha256`。此存储请求不带平台 HMAC 或账号 Token。
4. `LOCAL` 模式：按返回的 `chunkSize` 和 `totalChunks`，调用 `PUT /media/uploads/{uploadId}/chunks/{chunkIndex}`，`Content-Type: application/octet-stream`，对实际分片字节签名；索引从 **0** 开始，除最后一片外每片长度严格等于 `chunkSize`。
5. 用 `GET /media/uploads/{uploadId}` 核对进度，再签名调用 `POST /media/uploads/{uploadId}/complete`，JSON 必须传与创建时相同的 `fileSize`。若创建时传摘要，完成时回传同一 `sha256`；`coverTimestampMs` 可选。保存返回的 `mediaId`，确保状态 `READY`。

上传会话、分片、字段映射和完整响应以 [API 总览第 5 节](tk-platform-api.md) 及 [OpenAPI](tk-platform-openapi.json) 为准。上传完成不等于视频已发布。

### 图片（PHOTO）上传与转换

图片复用相同上传/发布路由，没有新增图片专用路径。创建上传时加 `mediaType=PHOTO`；省略仍为 VIDEO。支持 JPEG/WebP/PNG/GIF/HEIC/HEIF，单张 1 字节至 20MiB（20,971,520 字节），原文件 MIME 与扩展名匹配。

```json
{"mediaType":"PHOTO","fileName":"product.heic","fileSize":718114,"contentType":"image/heic"}
```

OSS 上传还必须把 `fields.xOssObjectAcl` 映射为 multipart 字段 `x-oss-object-acl=private`，把 `fields.contentType` 映射为 `Content-Type` 表单字段，视频/图片 `file` 都放在表单最后。不要把私有原图 URL 直接用于发布。LOCAL 使用返回的分片大小，PHOTO 为 1MiB，索引从 0 开始。

PHOTO complete 返回 `PROCESSING`，后台转换为适配发布的 JPEG，尚不能直接发布。每 2–5 秒查询上传状态，只有 `READY` 才读取 `mediaId`；`FAILED` 查看 `failReason`。在会话有效期内可再次 complete 重试，保留原文件长度、字节和已绑定摘要，不得换成另一张图；重试 SHA 可省略，提供时仍须相同。轮询超时保留 `uploadId` 继续查询，不重复创建会话。CANCELLED/EXPIRED 不可发布。

## 6. 创建发布、查询与重试

已有公网 HTTPS 视频时调用 quick-tasks；`videoUrl` 必须为平台可访问的公网地址，拒绝内网、回环、云元数据地址，链接须保持足够有效期。自动出片临时链接用于此处前也要满足这些要求。

```javascript
const task = await callOpenApi('POST', '/admin-api/tk/open/v1/tiktok/publish/quick-tasks', {
  externalAccountId: 'YOUR_AUTHORIZED_ACCOUNT_ID',
  videoUrl: 'https://cdn.example.com/your-video.mp4',
  fileName: 'your-video.mp4', title: '视频标题', caption: '发布文案',
  postMode: 'DIRECT_POST', privacyLevel: 'PUBLIC_TO_EVERYONE',
  externalRequestId: 'YOUR_BUSINESS_ORDER_ID'
}, {'Idempotency-Key': 'YOUR_UNIQUE_BUSINESS_KEY'});
```

视频已上传并 READY 时，改用 `POST /publish/tasks`，传 `connectionIds`（1–20 个）、`mediaId`、`postMode` 和 `privacyLevel`，同样携带幂等键。定时发布只允许一个连接；定时时区、计划和改期规则见 API 总览第 7 节。

图文同样用 `POST /publish/tasks`：传 `mediaType=PHOTO`、1–35 个**有序**的 `photoMediaIds`，不能同时传视频 `mediaId`。`photoCoverIndex` 默认 0，必须小于图片数量；图片 ID 必须是当前客户端的 READY 媒体，不能使用后台网页的数值 ID。标题≤90、描述≤4000 个 UTF-16 代码单元，emoji 通常占两个；不支持启用 `allowDuet/allowStitch`。不接收远程图片 URL quick-publish。

```javascript
const photoTask = await callOpenApi('POST', '/admin-api/tk/open/v1/tiktok/publish/tasks', {
  mediaType: 'PHOTO', connectionIds: ['conn_example'],
  photoMediaIds: ['media_first', 'media_second'], photoCoverIndex: 0,
  postMode: 'DIRECT_POST', privacyLevel: 'SELF_ONLY',
  title: '商品展示', caption: '商品细节与使用场景', autoAddMusic: true
}, {'Idempotency-Key': 'YOUR_UNIQUE_PHOTO_BUSINESS_KEY'});
```

PHOTO DIRECT_POST 需 `video.publish`，并检查 creator-info 隐私选项；`autoAddMusic` 仅此模式有效，表示 TikTok 自动配乐。UPLOAD_TO_INBOX 需 `video.upload`，用户仍需在 TikTok 完成发布，不可启用自动配乐或定时投递收件箱；两种模式仍都提交 `privacyLevel`。幂等摘要包含图片顺序、封面及音乐，改变这些值须使用新业务 key。

保存发布 `taskId`，建议每 3–5 秒查询任务与明细，或结合回调补偿查询。发布明细失败时先读取失败原因，再按接口契约重试失败明细。传输失败后的同业务创建重试保留原幂等键与参数，更新 nonce/时间戳/签名；不能把此规则用于没有幂等能力的自动出片创建。

## 7. 接收状态回调

管理员可为客户端配置 HTTPS 授权/发布回调 URL，不配置时可通过轮询完成流程。回调包含 `X-TK-Event-Id`、`X-TK-Timestamp`、`X-TK-Signature`。签名用独立 **callbackSecret**，原文为三行，末尾不换行：

```text
EVENT_ID
TIMESTAMP
SHA256_HEX(收到的原始BODY字节)
```

接收方必须在解析/重新序列化前保留原始字节，计算标准 Base64 HMAC-SHA256，使用恒定时间比较函数验签。验证时间窗口（建议不超过 300 秒）、请求头与 JSON 的 `eventId` 一致，并按 `eventId` 幂等落库；重复事件也要正确应答。

授权事件为 `authorization.completed` / `authorization.failed`，发布事件为 `publish.processing` / `publish.success` / `publish.failed` / `publish.cancelled`。失败授权的空 `connectionId` 不能保存为有效连接。

当前回调为至少一次投递，HTTP 2xx 视为成功；单次超时 10 秒，最多投递 8 次（含首次）。先校验与持久化，再快速返回 2xx，耗时业务放异步队列；仍应通过状态查询补偿回调丢失。这些回调不包括自动出片进度。

图文回调新增 `mediaType,photoCount`（VIDEO 回调 photoCount 为 0），接收方兼容新增字段。图文成功以平台任务/账号明细为准，分享链接可能为空；PHOTO metrics 返回 `UNAVAILABLE` 不表示发布失败。

## 8. 常见排错

验签失败依次核对：客户端/环境是否正确 → 秒与毫秒是否混淆 → 完整路径是否含 `/admin-api` → query 编码/顺序是否一致 → 实际字节是否被 JSON 重排 → 最后是否多了 LF → HMAC 是否标准 Base64。权限、IP 白名单、nonce 重放及配额错误按响应 `code/msg/requestId` 排查，不打印密钥。

完整字段、错误码、取消/改期/指标等接口见 [API 总览](tk-platform-api.md) 与 [OpenAPI JSON](tk-platform-openapi.json)。
