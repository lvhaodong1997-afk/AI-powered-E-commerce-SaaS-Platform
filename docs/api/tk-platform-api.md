# TK 自动混剪 SaaS — API 参考文档

| 项目 | 内容 |
| --- | --- |
| 文档版本 | v2.0 |
| 整理日期 | 2026-11-11 |
| 适用对象 | 需要通过 API 集成本平台的合作方 / 客户后端、本平台开发与运维 |
| 依据 | 仓库当前源码（`yudao-module-tk` 控制器、VO、鉴权过滤器、错误码常量）与 `docs/` 既有交付文档 |
| 交付物 | 本文（人读）、[`tk-platform-openapi.json`](tk-platform-openapi.json)（机读）、[`tk-platform-api.html`](tk-platform-api.html)（单页阅读版）、`build_html.py`（HTML 构建脚本） |

> **重要声明**：本文描述的是**当前源码已实现**的接口契约，不代表这些接口已完成部署、联调或通过生产验收。调用前须由双方确认目标环境连通性、客户端配置与权限开通状态。凡标记「规划中」的章节均**尚未实现**，不可作为联调依据。

---

## 0. 一分钟读懂：用 API 能操作什么

### 0.1 能力矩阵

| 能力 | 接口族 | 鉴权方式 | 当前状态 |
| --- | --- | --- | --- |
| TikTok 账号授权（浏览器 / 二维码 / 托管页） | `/tk/open/v1/tiktok/auth/**` | HMAC 签名 | 已实现 |
| TikTok 账号连接查询、资料刷新、解绑 | `/tk/open/v1/tiktok/connections/**` | HMAC 签名 | 已实现 |
| 视频上传（OSS 直传 / 分片） | `/tk/open/v1/tiktok/media/**` | HMAC 签名 | 已实现 |
| 视频发布、定时发布、取消、重试、状态查询 | `/tk/open/v1/tiktok/publish/**` | HMAC 签名 | 已实现 |
| 发布后视频指标（播放/点赞/评论/分享） | `/publish/tasks/{taskId}/metrics` | HMAC 签名 | 已实现 |
| 图片转文字（OCR） | `/tk/open/v1/ocr/**` | **免鉴权** | 已实现 |
| 文案改写、对标视频下载、视频文案时间轴提取 | `/admin-api/tk/open/**` | **免鉴权**（个别子接口需后台权限） | 已实现 |
| 自动混剪生成任务、素材库、配音、BGM、参考分析 | `/admin-api/tk/generation/**` 等 | **后台登录 Token + 菜单权限** | 仅后台可用，**无对外开放接口** |
| TikTok Shop、Facebook / Instagram 发布、账号统计 | `/admin-api/tk/tiktok-shop/**`、`/admin-api/tk/social-*` | **后台登录 Token + 菜单权限** | 仅后台可用，**无对外开放接口** |

### 0.2 必须先明确的边界

本项目目前**没有 C 端用户自助 API（`/app-api/**`）**。源码中 `yudao-module-tk` 不存在 `controller/app` 包，Nginx 也未转发 `/app-api/`。

因此「用户通过 API 操作本项目」在现阶段有两种可行路径：

1. **走开放 API（推荐给外部合作方）**：使用 `clientId` + `clientSecret` 做 HMAC 签名，可完成 TikTok 授权 → 视频上传 → 发布 → 查询 → 指标全链路。这是唯一面向第三方正式设计的接口族。
2. **走后台 API（仅建议内部系统集成）**：先调用 `/admin-api/system/auth/login` 换取 `accessToken`，再带 `Authorization: Bearer ...` 与 `tenant-id` 调用 `/admin-api/tk/**`。它绑定后台账号、租户和菜单权限，且启用了 API 加密，**不适合直接发给外部客户**。

> **结论**：如果业务诉求是「客户用 API 触发自动混剪并拿到成片」，当前开放 API **尚不覆盖**该能力，需要按第 11 节补齐。本文第 11 节给出可直接落地的接口设计草案。

---

## 1. 基础信息

### 1.1 服务地址

约定生产基地址（`docs/tiktok-open-api.md`、`docs/openapi/tiktok-open-api-c-party-integration.md` 中固定）：

```text
https://tkassetplant.fnn.net.cn
```

开放 API 推荐基地址：

```text
https://tkassetplant.fnn.net.cn/admin-api/tk/open/v1/tiktok
```

例如创建授权会话：

```text
POST https://tkassetplant.fnn.net.cn/admin-api/tk/open/v1/tiktok/auth/sessions
```

### 1.2 双路径注册与实际可达性

`tiktok` 与 `ocr` 两组开放控制器在源码中同时注册了两个路径：

```text
/admin-api/tk/open/v1/tiktok/**     <- 推荐，生产对外使用
/tk/open/v1/tiktok/**               <- 兼容路径
/admin-api/tk/open/v1/ocr/**        <- 推荐
/tk/open/v1/ocr/**                  <- 兼容路径
```

**注意**：`deploy/nginx/tk-auto-mix.conf` 只配置了 `location /admin-api/` 转发到后端 `127.0.0.1:48080`，**没有转发无 `/admin-api` 前缀的路径**。因此：

- 对外正式请求一律使用 `/admin-api/...` 前缀；
- 无前缀路径仅在直连后端服务或内部网关额外放通时可用，不要写进交付给第三方的正式文档；
- 签名原文中的 `REQUEST_TARGET` 必须包含实际使用的完整路径（含 `/admin-api`）。

### 1.3 通用约定

| 项目 | 约定 |
| --- | --- |
| 协议 | HTTPS、UTF-8 |
| 常规 Content-Type | `application/json` |
| 分片上传 Content-Type | `application/octet-stream` |
| OCR / 表单上传 | `multipart/form-data` |
| API 版本 | 路径中的 `v1`；不兼容变更须升版本路径，不得静默改变 v1 字段语义 |
| 时间格式 | ISO-8601，如 `2026-09-01T17:35:00`；带时区时如 `2026-09-26T18:00:00+08:00` |

### 1.4 资源编号（opaque ID）

以下编号由平台生成，调用方应视为不透明字符串，**不代表数据库主键**：

| 编号 | 用途 |
| --- | --- |
| `authSessionId` | TikTok 授权会话 |
| `connectionId` | TikTok 账号连接 |
| `uploadId` | 视频上传会话 |
| `mediaId` | 已完成上传的视频 |
| `taskId` | 发布任务 |
| `detailId` | 单账号发布明细 |
| `eventId` | 回调事件 |

调用方自己的业务编号通过 `externalAccountId`、`externalRequestId`、`clientState` 传递。所有平台资源编号**只在所属 `clientId` 范围内有效**，跨客户端不可查询、不可使用。

---

## 2. 鉴权

### 2.1 三套体系对照

| 体系 | 匹配路径 | 凭据 | 校验组件 |
| --- | --- | --- | --- |
| A. 开放 API HMAC | 含 `/tk/open/v1/tiktok/` | `clientId` / `clientSecret` | `TkOpenApiAuthenticationFilter` |
| B. 免登录工具接口 | `/tk/open/v1/ocr/**`、`/admin-api/tk/open/**` | 无 | 控制器 `@PermitAll` + `@TenantIgnore` |
| C. 后台会话 API | 其余 `/admin-api/**` | `accessToken` + `tenant-id` | `TokenAuthenticationFilter` + `@PreAuthorize` |

A 与 C **完全独立**：开放 API 不接受 `Bearer` Token，后台 API 也不接受 `X-TK-*` 签名。

### 2.2 A. 开放 API HMAC 签名

#### 2.2.1 客户端凭证

平台为每个外部调用方分配 `clientId`、`clientSecret`、`callbackSecret`：

- `clientSecret`：调用方请求平台时生成 HMAC 签名；
- `callbackSecret`：调用方校验平台回调签名（不接收回调时可不配置）；
- 密钥只允许保存在服务端环境变量或密钥管理系统，**禁止**下发到浏览器、移动端、前端代码或仓库；
- 每个调用方独立，不得跨客户端复用。

#### 2.2.2 请求头

除 `GET /auth/callback`、`GET /auth/sessions/{id}/launch`、`GET /auth/sessions/{id}/launch/status` 外，所有 `/tk/open/v1/tiktok/**` 接口都需要签名：

```http
X-TK-Client-Id: client_example
X-TK-Timestamp: 1798761600
X-TK-Nonce: nonce_7f3c_example
X-TK-Request-Id: req_202609010001
X-TK-Signature: <Base64 HMAC-SHA256>
Content-Type: application/json
```

| 请求头 | 必填 | 说明 |
| --- | --- | --- |
| `X-TK-Client-Id` | 是 | 分配的客户端编号 |
| `X-TK-Timestamp` | 是 | Unix **秒级**时间戳，与服务端相差不超过 300 秒 |
| `X-TK-Nonce` | 是 | 每次请求唯一随机串，防重放窗口内不可重复 |
| `X-TK-Request-Id` | 否 | 1–128 位链路追踪编号；缺失或非法时服务端生成 |
| `X-TK-Signature` | 是 | 签名的标准 Base64 结果 |
| `Idempotency-Key` | 条件必填 | 创建 / 改期发布任务必填，≤128 字符 |

#### 2.2.3 签名算法

签名原文严格由 **5 行**组成，行间为单个 `\n`（LF），**最后一行不追加换行**：

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

```text
signature = Base64( HMAC-SHA256(clientSecret_UTF8, canonicalString_UTF8) )
```

必须遵守：

- `HTTP_METHOD` 大写（`GET` / `POST` / `PUT` / `DELETE`）；
- `REQUEST_TARGET` = 完整路径 + 原始查询字符串，**不含域名，包含 `/admin-api`**；
- 查询参数的顺序与编码必须与实际发送的 URL **完全一致**；
- JSON 摘要基于**实际发送的原始 UTF-8 字节**，签名后不得重新格式化 JSON；
- 二进制分片摘要基于该分片的原始字节；
- GET / 无请求体 DELETE 使用空字节数组 SHA-256：`e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`；
- HMAC key 只能是 `clientSecret` 原文，不得拼接 `clientId`、不得 Base64 解码、不得带引号或空白；
- 输出必须是**标准 Base64**，不是 Base64URL。

#### 2.2.4 签名参考实现（Node.js，仅服务端运行）

```js
import crypto from 'node:crypto';

const BASE = 'https://tkassetplant.fnn.net.cn';
const CLIENT_ID = process.env.TK_OPEN_API_CLIENT_ID;
const CLIENT_SECRET = process.env.TK_OPEN_API_CLIENT_SECRET;

const sha256Hex = (b) => crypto.createHash('sha256').update(b).digest('hex');

function sign(method, target, timestamp, nonce, bodyBytes) {
  const canonical = [
    method.toUpperCase(), target, timestamp, nonce, sha256Hex(bodyBytes)
  ].join('\n');
  return crypto.createHmac('sha256', CLIENT_SECRET)
    .update(canonical, 'utf8').digest('base64');
}

async function callOpenApi(method, path, payload, extraHeaders = {}) {
  const bodyBytes = payload === undefined ? Buffer.alloc(0)
    : Buffer.isBuffer(payload) ? payload
    : Buffer.from(JSON.stringify(payload), 'utf8');
  const timestamp = String(Math.floor(Date.now() / 1000));
  const nonce = crypto.randomUUID().replaceAll('-', '');
  const target = path;                       // 含 /admin-api 前缀与原始 query
  const res = await fetch(`${BASE}${path}`, {
    method,
    headers: {
      'X-TK-Client-Id': CLIENT_ID,
      'X-TK-Timestamp': timestamp,
      'X-TK-Nonce': nonce,
      'X-TK-Request-Id': crypto.randomUUID(),
      'X-TK-Signature': sign(method, target, timestamp, nonce, bodyBytes),
      ...(payload !== undefined ? {
        'Content-Type': Buffer.isBuffer(payload)
          ? 'application/octet-stream' : 'application/json'
      } : {}),
      ...extraHeaders
    },
    body: payload === undefined ? undefined : bodyBytes
  });
  const result = await res.json();
  if (!res.ok || result.code !== 0) {
    throw new Error(`${res.status} ${result.code}: ${result.msg} (requestId=${result.requestId})`);
  }
  return result.data;
}
```

Python、Java 的完整示例见 [`docs/sdk/`](../sdk/)：`tiktok-open-api-python.md`、`tiktok-open-api-java.md`、`tiktok-open-api-node.md`。

#### 2.2.5 签名之外的服务端检查

依次校验：请求头完整性 → 时间戳 → 客户端存在且启用 → 来源 IP 白名单 → 签名 → 模块权限（URI 含 `/media/` 需 `media`、含 `/publish/` 需 `publish`、其余需 `auth`）→ nonce 防重放 → 每分钟限流 → 每日配额。

| 限制 | 默认值 |
| --- | --- |
| 时间戳容差 | ±300 秒 |
| 签名请求体上限 | 8,388,608 bytes |
| LOCAL 默认分片大小 | 1,048,576 bytes |
| 每分钟调用 | 120 次 |
| 每日调用 | 10,000 次 |

以上均可按客户端配置。防重放与限流依赖 Redis，组件不可用时返回 `OPEN_API_GUARD_UNAVAILABLE`（503）。

### 2.3 B. 免登录工具接口

这些接口标注 `@PermitAll`（部分同时 `@TenantIgnore`），**不需要** `clientId`、签名、`Authorization` 或租户头：

| 接口 | 说明 |
| --- | --- |
| `POST /admin-api/tk/open/v1/ocr/image-to-text` | 图片文字识别 |
| `POST /admin-api/tk/open/copywriting/rewrite` | 文案改写 |
| `POST /admin-api/tk/open/video/download` | 对标视频解析下载 |
| `POST /admin-api/tk/open/video/transcript/extract` | 创建视频文案时间轴提取任务（免登录） |
| `GET /admin-api/tk/open/video/transcript/extract/{taskId}` | 查询提取任务（免登录） |

例外：`POST /admin-api/tk/open/video/transcript/extract-sync` 标注 `@PreAuthorize("...tk:reference:analyze")`，**需要后台登录与权限**。

`POST /tk/tiktok/webhook` 是平台接收 TikTok 官方 Content Posting Webhook 的端点，由 TikTok 调用，调用方不要主动请求；且该路径无 `/admin-api` 前缀，Nginx 未转发，生产可达性需单独确认。

### 2.4 C. 后台会话 API（仅供内部集成）

#### 2.4.1 登录

```http
POST /admin-api/system/auth/login
Content-Type: application/json
```

```json
{ "username": "admin", "password": "******" }
```

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `username` | 是 | 4–30 位字母数字 |
| `password` | 是 | 4–16 位 |
| `captchaVerification` | 条件 | 开启验证码时必填 |

响应 `data` 含 `accessToken`、`refreshToken`、`userId`、`expiresTime`。登录请求体**明文**发送（前端显式关闭 AES 加密）。

租户 ID 通过单独接口换取：

```http
GET /admin-api/system/tenant/get-id-by-name?name=<租户名>
```

#### 2.4.2 后续请求头

```http
Authorization: Bearer <accessToken>
tenant-id: <数字租户ID>
```

平台管理员跨租户访问时另加 `visit-tenant-id`。刷新令牌：`POST /admin-api/system/auth/refresh-token`；退出：`POST /admin-api/system/auth/logout`。

#### 2.4.3 后台 API 的两个使用障碍

1. **权限**：`/admin-api/tk/**` 绝大多数方法带 `@PreAuthorize("@ss.hasPermission('tk:xxx:query')")`，账号必须具备对应菜单权限，否则 403。
2. **API 加密**：`application.yaml` 中 `yudao.api-encrypt.enable: true`（AES）。前端在请求带 `isEncrypt` 标识时会对请求体加密并设置加密标识头，响应也会加密。纯外部脚本直接按明文 JSON 调用可能被后端解密逻辑拒绝。

因此**不建议**把后台 API 作为对外集成方案。若确需内部自动化调用，应先与运维确认加密开关与具体头名，或走第 11 节的开放 API 补齐方案。

### 2.5 客户端开通（平台侧）

由平台管理员在后台「开放 API 管理」中配置，对应源码 `/admin-api/tk/open-api/client/**` 与 `/admin-api/tk/open-api/operations/**`：

| 后台端点 | 用途 |
| --- | --- |
| `POST /admin-api/tk/open-api/client/create` | 新建调用方（返回一次性展示的密钥） |
| `PUT /admin-api/tk/open-api/client/update` | 修改回调地址、IP 白名单、限额 |
| `PUT /admin-api/tk/open-api/client/status` | 启停 |
| `POST /admin-api/tk/open-api/client/rotate-secret` | 轮换密钥（旧密钥立即失效） |
| `GET /admin-api/tk/open-api/client/get` / `page` | 查询 |
| `DELETE /admin-api/tk/open-api/client/delete` | 删除 |
| `GET /admin-api/tk/open-api/operations/usage` | 调用量 |
| `GET /admin-api/tk/open-api/operations/event/page` / `get` | 回调事件 |
| `POST /admin-api/tk/open-api/operations/event/replay` | 重放事件 |

新建时建议配置：权限勾选 `auth` + `media` + `publish`；每分钟 120 次、每日 10,000 次起；状态启用；IP 白名单填调用方公网出口 IP。

---

## 3. 通用响应与错误码

### 3.1 响应结构

开放 API 使用 `TkOpenApiResponse`，免登录/后台接口使用 `CommonResult`，两者字段一致：

```json
{ "code": 0, "msg": "OK", "data": {}, "requestId": "req_202609010001" }
```

```json
{ "code": "MEDIA_NOT_READY", "msg": "media is not ready", "data": null, "requestId": "req_..." }
```

调用方处理顺序：**先判 HTTP 状态 → 再判 `code === 0` → 才使用 `data`**。错误日志至少记录 HTTP 状态、`code`、`msg`、`requestId`，不记录密钥与 Token。

> 注意：`code` 在成功时为数字 `0`，失败时为字符串错误码；免登录工具接口失败时为数字错误码（如 `400`、`106000052`）。判断请统一用 `code !== 0` 而非类型。

### 3.2 HTTP 状态

| 状态 | 场景 |
| --- | --- |
| `200` | 成功 |
| `400` | 参数错误或业务状态不允许 |
| `401` | 认证失败 |
| `403` | IP / 权限策略拒绝 |
| `404` | 资源不存在或不属于当前客户端 |
| `409` | 幂等键冲突 |
| `413` | 请求体或视频超限 |
| `429` | 限流或日配额超限 |
| `500` | 服务内部错误 |
| `503` | 依赖服务、配置或防护组件暂不可用 |

### 3.3 幂等与重试

- 发布任务创建、快速发布、改期均需 `Idempotency-Key`（≤128 字符）；
- 幂等范围 = `clientId + Idempotency-Key`，记录有效期 **7 天**；
- 相同键 + 相同请求内容 → 返回原任务；相同键 + 不同内容 → `409 IDEMPOTENCY_KEY_CONFLICT`；
- 网络超时重试**必须复用原 key**，同时生成新的 `timestamp` 与 `nonce`；
- 每次请求的 `nonce` 必须唯一，签名重试也算新请求。

### 3.4 完整错误码（源码提取，共 47 项）

#### 认证与网关

| 错误码 | HTTP | 含义 |
| --- | --- | --- |
| `OPEN_API_AUTH_HEADER_MISSING` | 401 | 必要认证头缺失 |
| `OPEN_API_CLIENT_INVALID` | 401 | clientId 不存在或已禁用 |
| `OPEN_API_SIGNATURE_INVALID` | 401 | 签名错误 |
| `OPEN_API_TIMESTAMP_EXPIRED` | 401 | 时间戳无效或超出 300 秒 |
| `OPEN_API_NONCE_REPLAYED` | 401 | nonce 已使用 |
| `OPEN_API_SECRET_UNAVAILABLE` | 500 | 服务端无法加载客户端密钥 |
| `OPEN_API_IP_NOT_ALLOWED` | 403 | 来源 IP 不在允许范围 |
| `OPEN_API_PERMISSION_DENIED` | 403 | 缺少模块权限 |
| `OPEN_API_RATE_LIMITED` | 429 | 每分钟次数超限 |
| `OPEN_API_QUOTA_EXCEEDED` | 429 | 每日总量超限 |
| `OPEN_API_GUARD_UNAVAILABLE` | 503 | 防重放/限流组件暂不可用 |
| `OPEN_API_BODY_TOO_LARGE` | 413 | 签名请求体超过上限 |
| `OPEN_API_PARAMETER_INVALID` | 400 | 参数校验失败 |
| `OPEN_API_INTERNAL_ERROR` | 500 | 服务内部错误 |

#### 授权与连接

| 错误码 | 含义 |
| --- | --- |
| `AUTH_SESSION_NOT_FOUND` | 授权会话不存在或不属于当前客户端 |
| `CONNECTION_NOT_FOUND` | 账号连接不存在或不属于当前客户端 |
| `CONNECTION_NOT_AUTHORIZED` | 连接不是已授权状态 |
| `CONNECTION_CHANGED` | 连接已变化，请重试 |
| `INVALID_USERNAME` | username 必须为不带 `@` 的 TikTok 用户名 |
| `REAUTH_REQUIRED` | 需要重新授权 |
| `TOKEN_REFRESH_IN_PROGRESS` | Token 正在刷新，请稍后重试 |
| `TOKEN_REFRESH_INTERRUPTED` | Token 刷新被中断 |
| `TIKTOK_CONFIG_REQUIRED` | TikTok 应用配置不可用 |

#### 媒体上传

| 错误码 | 含义 |
| --- | --- |
| `MEDIA_NOT_FOUND` | 上传会话或视频不存在 |
| `MEDIA_NOT_READY` | 分片不完整 / OSS 对象暂不可用 / 定时发布时媒体未就绪 |
| `MEDIA_FILE_INVALID` | 扩展名、大小、分片或摘要校验失败 |
| `MEDIA_FILE_TOO_LARGE` | 视频超过 1,000,000,000 bytes |
| `MEDIA_UPLOAD_EXPIRED` | 上传会话已过期 |
| `MEDIA_UPLOAD_STATUS_INVALID` | 会话不处于 `UPLOADING` |
| `MEDIA_UPLOAD_FAILED` | 本地分片保存失败 |
| `MEDIA_SERVICE_UNAVAILABLE` | 快速发布所需远程媒体服务不可用 |
| `UPLOAD_MODE_INVALID` | 对 OSS 会话调用了本地分片接口 |
| `OSS_CONFIG_REQUIRED` | OSS 已启用但配置不完整 |

#### 发布、幂等与指标

| 错误码 | 含义 |
| --- | --- |
| `IDEMPOTENCY_KEY_REQUIRED` | 缺少 `Idempotency-Key` |
| `IDEMPOTENCY_KEY_INVALID` | 幂等键超过 128 字符 |
| `IDEMPOTENCY_KEY_CONFLICT` | 相同键对应不同请求内容 |
| `IDEMPOTENCY_RESULT_UNAVAILABLE` | 幂等记录存在但原任务不可读取 |
| `PUBLISH_TASK_NOT_FOUND` | 任务不存在或不属于当前客户端 |
| `PUBLISH_DETAIL_NOT_FOUND` | 明细不存在或不属于当前客户端 |
| `PUBLISH_RETRY_STATUS_INVALID` | 明细状态不允许重试 |
| `PUBLISH_RETRY_PUBLISH_ID_EXISTS` | 明细已有 TikTok publishId，不能重新初始化 |
| `PUBLISH_ALREADY_RUNNING` | 任务已发起远程执行，不能人工终止 |
| `PUBLISH_METRICS_MULTI_DETAIL_UNSUPPORTED` | 指标查询要求一个任务只对应一个账号 |
| `PUBLISH_METRICS_BATCH_INVALID` | `taskIds` 必须 1–50 个 |
| `PULL_ACCOUNT_POLICY_MIXED` | 直拉白名单内外账号请分开创建发布任务 |

#### 定时发布

| 错误码 | 含义 |
| --- | --- |
| `SCHEDULE_TIME_INVALID` | `scheduledAt` 缺失或不是合法 ISO-8601（含时区偏移不匹配） |
| `SCHEDULE_REGION_INVALID` | 不支持的 `regionCode` |
| `SCHEDULE_MODE_UNSUPPORTED` | 定时发布仅支持 `DIRECT_POST` |
| `SCHEDULE_ACCOUNT_COUNT_INVALID` | 定时发布要求恰好 1 个账号 |
| `SCHEDULE_NOT_MUTABLE` | 仅未执行的定时任务可改期 |
| `SCHEDULE_ALREADY_RUNNING` | 定时任务已开始执行 |

---

## 4. 授权与连接 API

基地址：`/admin-api/tk/open/v1/tiktok`。除标注外均需 HMAC 签名。

### 4.1 创建授权会话

```http
POST /auth/sessions
```

```json
{
  "externalAccountId": "account_10001",
  "authMode": "AUTO",
  "clientState": "page_state_001"
}
```

| 字段 | 类型 | 必填 | 限制 | 说明 |
| --- | --- | --- | --- | --- |
| `externalAccountId` | string | 是 | ≤128 | 调用方自己的账号编号，稳定且唯一 |
| `authMode` | string | 是 | `AUTO` / `REDIRECT` / `QR_CODE` | 推荐 `AUTO` |
| `clientState` | string | 否 | ≤512 | 透传状态，不参与 OAuth 安全校验 |

会话有效期 **15 分钟**。响应 `data`：

| 字段 | 说明 |
| --- | --- |
| `authSessionId` / `externalAccountId` / `clientState` / `authMode` | 会话标识与回显 |
| `authorizeUrl` | TikTok 浏览器授权地址（`QR_CODE` 时为 `null`） |
| `qrcodeUrl` | TikTok 二维码数据地址（不是图片） |
| `qrcodeImageUrl` | 平台生成的二维码图片，可直接作 `<img src>`（`data:image/png;base64,...`） |
| `launchUrl` | 平台托管授权页，推荐在用户浏览器打开 |
| `status` | 初始 `WAITING` |
| `expireTime` | 过期时间 |

### 4.2 托管授权页（公开，无需签名）

```http
GET /auth/sessions/{authSessionId}/launch          -> HTML
GET /auth/sessions/{authSessionId}/launch/status   -> JSON
```

托管页展示授权入口与二维码，每 3 秒轮询公开状态接口。这两个端点不需要签名；服务端正式状态查询仍应使用带签名的 `GET /auth/sessions/{authSessionId}`。

### 4.3 TikTok OAuth 回调（平台内部）

```http
GET /auth/callback?code=...&state=...
```

由 TikTok 浏览器重定向访问，平台校验 `state`、换取并加密保存 Token、创建连接、生成授权回调事件。**调用方不要构造或调用**。平台固定 redirect URI：

```text
https://tkassetplant.fnn.net.cn/admin-api/tk/open/v1/tiktok/auth/callback
```

### 4.4 查询授权会话

```http
GET /auth/sessions/{authSessionId}
```

`data` 字段：`providerIdentity`（TikTok 主体 SHA-256，非原始 openId）、`authSessionId`、`externalAccountId`、`clientState`、`connectionId`、`accountName`、`username`、`avatarUrl`、`bioDescription`、`profileDeepLink`、`verified`、`followerCount`、`followingCount`、`likesCount`、`videoCount`、`statsUpdatedAt`、`status`、`failReason`、`expireTime`。

| `status` | 含义 |
| --- | --- |
| `WAITING` | 等待用户操作 |
| `SUCCESS` | 授权成功，返回 `connectionId` |
| `FAILED` | 失败，查看 `failReason` |
| `EXPIRED` | 会话过期，需重新创建 |

建议轮询间隔 3 秒，超过 `expireTime` 停止。

### 4.5 查询账号连接

```http
GET /connections?externalAccountId=account_10001&status=AUTHORIZED&username=example_user
```

三个查询参数均可选。`data` 为数组，元素含：`providerIdentity`、`connectionId`、`externalAccountId`、`accountName`、`username`、`avatarUrl`、`bioDescription`、`profileDeepLink`、`verified`、`followerCount`、`followingCount`、`likesCount`、`videoCount`、`statsUpdatedAt`、`authStatus`、`tokenStatus`、`lastAuthTime`、`videoRegion`（`status` / `locationCreated` / `countryName` / `sourceVideoId` / `sourceVideoUrl` / `fetchedAt`）。

**Token 永不通过接口返回。**

### 4.6 刷新账号资料

```http
POST /connections/{connectionId}/profile/refresh
```

返回单个 `ConnectionResp`。用于重新拉取头像、粉丝数、地区等资料。

### 4.7 解绑账号

```http
POST /connections/{connectionId}/disconnect
```

返回 `data: true`。连接置为 `DISCONNECTED`、Token 置 `REVOKED`、清空本地 Token、阻止后续发布。当前实现**不调用** TikTok 撤销授权接口。

---

## 5. 视频上传 API

基地址：`/admin-api/tk/open/v1/tiktok/media`。均需 HMAC 签名。

### 5.1 创建上传会话

```http
POST /uploads
```

```json
{
  "fileName": "video.mp4",
  "fileSize": 104857600,
  "contentType": "video/mp4",
  "sha256": "0123...cdef"
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `fileName` | string | 是 | 扩展名仅 `mp4` / `mov` / `webm` |
| `fileSize` | integer | 是 | > 0，上限 1,000,000,000 bytes |
| `contentType` | string | 否 | 缺省按扩展名生成 `video/{ext}` |
| `sha256` | string | 否 | 完整文件 SHA-256 十六进制；提供后 `complete` 时必须回传相同值 |

会话默认有效期 **24 小时**。`uploadMode` 由服务端根据配置决定（`OSS` 或 `LOCAL`），调用方不能指定。

`data` 字段：`uploadId`、`uploadMode`、`chunkSize`、`totalChunks`、`uploadUrl`、`objectKey`、`fields`、`expireTime`。

**OSS 模式**：`fields` 含 `policy`、`ossAccessKeyId`、`signature`、`xOssMetaSha256`。向 `uploadUrl` 提交 `multipart/form-data` 时映射为：

| 表单字段 | 取值 |
| --- | --- |
| `key` | `objectKey` |
| `policy` | `fields.policy` |
| `OSSAccessKeyId` | `fields.ossAccessKeyId` |
| `Signature` | `fields.signature` |
| `x-oss-meta-sha256` | `fields.xOssMetaSha256`（创建时提供 SHA-256 才使用） |
| `file` | 视频文件 |

OSS 表单地址**不使用** `X-TK-*` 签名头；返回字段是短期表单凭据，不是长期 OSS 密钥。上传完仍必须调用 `complete`。

**LOCAL 模式**：按 `chunkSize`（默认 1,048,576 bytes）与 `totalChunks` 逐片上传。

### 5.2 上传本地分片

```http
PUT /uploads/{uploadId}/chunks/{chunkIndex}
Content-Type: application/octet-stream
```

- `chunkIndex` 从 **0** 开始；
- 除最后一片外，每片字节数必须严格等于 `chunkSize`；
- 请求体是原始分片字节，**不是** multipart；
- 每一片单独生成新的 `timestamp` / `nonce` / 签名，body hash 基于该片字节；
- 对 OSS 会话调用返回 `UPLOAD_MODE_INVALID`；会话非 `UPLOADING` 返回 `MEDIA_UPLOAD_STATUS_INVALID`。

响应 `data: true`。

### 5.3 查询上传进度

```http
GET /uploads/{uploadId}
```

`data`：`uploadId`、`mediaId`、`uploadMode`、`fileSize`、`uploadedSize`、`status`。

| `status` | 含义 |
| --- | --- |
| `UPLOADING` | 上传中 |
| `READY` | 已完成并生成 `mediaId` |
| `CANCELLED` | 已取消 |

当前响应**不返回分片编号列表**，调用方应自行持久化已成功的分片索引。

### 5.4 完成上传

```http
POST /uploads/{uploadId}/complete
```

```json
{ "fileSize": 104857600, "sha256": "0123...cdef", "coverTimestampMs": 1200 }
```

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `fileSize` | 是 | 必须与创建会话时一致 |
| `sha256` | 条件 | 创建时提供过则此处必填且必须相同 |
| `coverTimestampMs` | 否 | 封面时间点，毫秒 |

LOCAL 会校验全部分片、合并长度和摘要；OSS 会 HEAD 检查对象存在、`Content-Length` 与 `x-oss-meta-sha256`。成功后 `data` 含 `mediaId`、`uploadId`、`fileName`、`fileSize`、`contentType`、`status: "READY"`。**只有 `READY` 且取得 `mediaId` 才能发布。**

### 5.5 取消上传

```http
DELETE /uploads/{uploadId}
```

仅 `UPLOADING` 可取消。LOCAL 删除临时分片目录；OSS 在配置完整时删除对象。返回 `data: true`。

---

## 6. 发布任务 API

基地址：`/admin-api/tk/open/v1/tiktok/publish`。均需 HMAC 签名。

### 6.1 快速发布（推荐，视频已有公网 HTTPS 地址）

```http
POST /quick-tasks
Idempotency-Key: c-quick-publish-202609020001
```

```json
{
  "externalAccountId": "account_10001",
  "videoUrl": "https://cdn.example.com/videos/demo.mp4",
  "fileName": "demo.mp4",
  "contentType": "video/mp4",
  "coverTimestampMs": 1200,
  "title": "视频标题",
  "caption": "视频文案",
  "postMode": "DIRECT_POST",
  "privacyLevel": "PUBLIC_TO_EVERYONE",
  "externalRequestId": "business_order_001",
  "scheduledAt": "2026-09-26T18:00:00+08:00",
  "regionCode": "CN"
}
```

| 字段 | 必填 | 限制 / 默认值 |
| --- | --- | --- |
| `externalAccountId` | 是 | ≤128，平台据此定位唯一已授权连接 |
| `videoUrl` | 是 | ≤2048，必须是平台可访问的**公网 HTTPS** 地址；拒绝 HTTP、回环、内网、云元数据地址 |
| `fileName` | 否 | ≤512，扩展名 `mp4` / `mov` / `webm`，缺省从 URL 推断或 `video.mp4` |
| `contentType` | 否 | ≤128 |
| `coverTimestampMs` | 否 | 封面时间点，毫秒 |
| `title` | 否 | ≤512 |
| `caption` | 否 | ≤2200 |
| `postMode` | 否 | `DIRECT_POST`（默认）/ `UPLOAD_TO_INBOX` |
| `privacyLevel` | 否 | ≤64，默认 `PUBLIC_TO_EVERYONE` |
| `allowComment` / `allowDuet` / `allowStitch` | 否 | 默认 `true` / `false` / `false` |
| `commercialContent` / `brandContent` | 否 | 默认 `false` |
| `aigcContent` | 否 | 默认 `true` |
| `externalRequestId` | 否 | ≤128，调用方业务单号 |
| `scheduledAt` / `regionCode` | 否 | 定时发布，见第 7 节 |

一次调用完成「创建媒体 + 准备媒体 + 创建发布任务」，仍为**异步**：接口立即返回 `taskId`。

### 6.2 创建发布任务

```http
POST /tasks
Idempotency-Key: publish_order_001
```

```json
{
  "connectionIds": ["conn_example"],
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
  "externalRequestId": "business_order_001",
  "scheduledAt": "2026-09-26T18:00:00+08:00",
  "regionCode": "CN"
}
```

| 字段 | 必填 | 限制 |
| --- | --- | --- |
| `connectionIds` | 是 | 1–20 个已授权连接；**定时发布只允许 1 个** |
| `mediaId` | 是 | 状态必须为 `READY` |
| `postMode` | 是 | `DIRECT_POST` / `UPLOAD_TO_INBOX` |
| `privacyLevel` | 是 | 非空 |
| `title` / `caption` | 否 | ≤512 / ≤2200 |
| 其余开关字段 | 否 | 同 6.1 |
| `scheduledAt` / `regionCode` | 否 | ≤64 / ≤16，见第 7 节 |

`data` 为 `TaskResp`：`taskId`、`mediaId`、`externalRequestId`、`status`、`accountCount`、`successCount`、`failedCount`、`pendingCount`、`failReason`、`scheduledAt`、`regionCode`、`regionName`、`scheduleTimezone`、`scheduleUtcTime`、`scheduleBeijingTime`、`scheduleEpochMillis`、`scheduleUtcOffset`、`scheduleStatus`、`canReschedule`、`canCancel`、`createTime`、`updateTime`。

### 6.3 查询任务

```http
GET /tasks/{taskId}
```

`data` 同 `TaskResp`。

| `status` | 含义 |
| --- | --- |
| `PENDING` | 已创建，等待处理 |
| `PROCESSING` | 至少一个明细处理中 |
| `SUCCESS` | 全部成功 |
| `FAILED` | 全部失败 |
| `PARTIAL_SUCCESS` | 部分成功 |
| `CANCELLED` | 已取消（配合 `scheduleStatus` / `canCancel` 判断） |

### 6.4 查询发布明细

```http
GET /tasks/{taskId}/details
```

`data` 为数组，元素：`phase`、`uploadSource`、`lastError`、`nextReconcileTime`、`callbackStatus`、`callbackHttpStatus`、`detailId`、`taskId`、`connectionId`、`accountName`、`status`、`tiktokStatus`、`publishId`、`publishUrl`、`failReason`、`retryCount`、`updateTime`。

业务判断以明细 `status` 与任务汇总 `status` 为准；`tiktokStatus` 仅作补充；`publishUrl` 可能为空，不能把 `publishId` 当作公开视频地址。

### 6.5 改期定时发布

```http
POST /tasks/{taskId}/reschedule
Idempotency-Key: reschedule_order_001
```

```json
{ "scheduledAt": "2026-09-27T20:00:00+08:00", "regionCode": "CN" }
```

仅未开始执行的定时任务可改期，否则 `SCHEDULE_NOT_MUTABLE` / `SCHEDULE_ALREADY_RUNNING`。返回 `TaskResp`。

### 6.6 取消发布任务

```http
POST /tasks/{taskId}/cancel
```

任务已发起远程执行时返回 `PUBLISH_ALREADY_RUNNING`。取消会触发 `publish.cancelled` 回调。返回 `TaskResp`。

### 6.7 查询视频指标

```http
GET /tasks/{taskId}/metrics
```

只需提交 `taskId`，平台按 `clientId + taskId` 查找明细并调用 TikTok `video.query`。`data`：`taskId`、`detailId`、`connectionId`、`publishId`、`publicPostId`、`publishUrl`、`metricsStatus`、`viewCount`、`likeCount`、`commentCount`、`shareCount`、`metricsLastSyncTime`、`metricsFailReason`。

| `metricsStatus` | 处理 |
| --- | --- |
| `WAITING_PUBLISH` / `WAITING_PUBLIC` / `SYNCING` | 延迟后重试 |
| `AVAILABLE` | 读取四项计数 |
| `REAUTH_REQUIRED` | 引导用户重新授权同一 `externalAccountId` |
| `UNAVAILABLE` | 视频暂不可查询（如草稿箱） |
| `FAILED` | 记录 `metricsFailReason`，人工处理 |

- 只返回播放、点赞、评论、分享四项，**不返回收藏数**；
- 计数为 `null` 表示暂不可用，**不能当作 0 入账**；
- 一个 `taskId` 只支持一个发布明细，多账号任务返回 `PUBLISH_METRICS_MULTI_DETAIL_UNSUPPORTED`；
- 建议任务 `SUCCESS` 后首次调用，之后每 5–15 分钟查询一次，不要高频轮询。

### 6.8 批量查询视频指标

```http
POST /tasks/metrics/batch
```

```json
{ "taskIds": ["task_1", "task_2"] }
```

`taskIds` 为 1–50 个，否则 `PUBLISH_METRICS_BATCH_INVALID`。响应 `data`：`requestedCount`、`resultCount`、`items`（每项结构同 6.7）。

### 6.9 重试失败明细

```http
POST /details/{detailId}/retry
```

仅 `FAILED` 明细可重试。重试后状态回到 `PENDING`、`tiktokStatus=RETRY_PENDING`、清空旧 `publishId` 与 `failReason`、`retryCount + 1`。已有 TikTok publishId 的明细返回 `PUBLISH_RETRY_PUBLISH_ID_EXISTS`。返回 `data: true`。

---

## 7. 定时发布规则

在创建任务或改期时传 `scheduledAt` + `regionCode` 即进入定时发布。

| 约束 | 规则 |
| --- | --- |
| 任务类型 | 仅 `DIRECT_POST`（`SCHEDULE_MODE_UNSUPPORTED`） |
| 账号数量 | 恰好 1 个（`SCHEDULE_ACCOUNT_COUNT_INVALID`） |
| 媒体 | 必须已 `READY`（`MEDIA_NOT_READY`） |
| 时间格式 | ISO-8601。可带偏移（`2026-09-26T18:00:00+08:00`，偏移必须与所选地区一致），或不带偏移（`2026-09-26T18:00:00`，按地区本地时间解释） |
| 默认地区 | `CN`（`Asia/Shanghai`） |

支持的 `regionCode`（大小写不敏感，不支持返回 `SCHEDULE_REGION_INVALID`）：

| 代码 | 地区 / 时区 | 代码 | 地区 / 时区 |
| --- | --- | --- | --- |
| `CN` | 中国区 `Asia/Shanghai` | `JP` | 日本区 `Asia/Tokyo` |
| `US` | 美区 `America/Los_Angeles` | `KR` | 韩国区 `Asia/Seoul` |
| `DE` | 德区 `Europe/Berlin` | `SG` | 新加坡区 `Asia/Singapore` |
| `UK` | 英国区 `Europe/London` | `AE` | 阿联酋区 `Asia/Dubai` |
| `FR` | 法国区 `Europe/Paris` | `AU` | 澳洲区 `Australia/Sydney` |
| `CA` | 加拿大区 `America/Toronto` | `NZ` | 新西兰区 `Pacific/Auckland` |
| `BR` | 巴西区 `America/Sao_Paulo` | | |

`TaskResp` 会同时返回本地时间、UTC 时间、北京时间、epoch 毫秒与 UTC 偏移，便于调用方核对。`canReschedule` / `canCancel` 表示当前是否允许改期/取消。

---

## 8. 状态回调

### 8.1 事件类型

| 事件 | 触发 |
| --- | --- |
| `authorization.completed` | 授权成功 |
| `authorization.failed` | 授权失败 |
| `publish.processing` | 发布处理中 |
| `publish.success` | 单账号发布成功 |
| `publish.failed` | 单账号发布失败 |
| `publish.cancelled` | 任务取消 |

每个客户端可分别配置授权回调地址与发布回调地址，必须为 HTTPS 并通过服务端 URL 安全校验。回调是**可选能力**：不配置时轮询授权会话、连接列表、任务与明细即可完成全流程。

### 8.2 回调签名

```http
Content-Type: application/json
X-TK-Event-Id: evt_example
X-TK-Timestamp: 1798761600
X-TK-Signature: <Base64 HMAC-SHA256>
```

签名原文为 **3 行**，行间单个 LF，末尾无换行：

```text
EVENT_ID
TIMESTAMP
SHA256_HEX(BODY)
```

```text
signature = Base64( HMAC-SHA256(callbackSecret, canonicalString) )
```

调用方必须用**收到的原始请求体字节**计算摘要，并自行校验时间戳窗口（建议 ≤300 秒）、`eventId` 幂等、请求头与 JSON 中 `eventId` / `eventType` 一致。

### 8.3 事件体示例

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

授权事件另含 `authSessionId`、`externalAccountId`、`accountName`、`clientState`、`status`、`failReason`。失败事件 `connectionId` 为 `null`，不可保存为有效连接。

### 8.4 投递策略

| 项目 | 当前实现 |
| --- | --- |
| 单次超时 | 10 秒 |
| 成功条件 | HTTP 2xx |
| 最大投递 | 8 次（含首次） |
| 重试间隔 | 1、5、15、30、60、180、360 分钟 |
| 最终失败 | 事件状态 `FAILED`，可在后台 `operations/event/replay` 重放 |

回调为**至少一次**语义，同一 `eventId` 可能投递多次。接收方应：校验签名 → `eventId` 幂等落库 → 快速返回 2xx → 耗时处理放入自己的异步队列 → 用查询接口作为回调丢失的补偿。

---

## 9. 免登录工具接口

> 本节接口返回 `CommonResult`（同样为 `code` / `msg` / `data`），不需要任何鉴权头。除特别说明外路径均带 `/admin-api` 前缀。

### 9.1 图片转文字（OCR）

```http
POST /admin-api/tk/open/v1/ocr/image-to-text
Content-Type: multipart/form-data
```

| 项目 | 规则 |
| --- | --- |
| 文件字段 | `file`（固定） |
| 数量 | 单张 |
| 大小 | ≤20 MB |
| 格式 | JPG / JPEG / PNG / WEBP |

```bash
curl -X POST "https://tkassetplant.fnn.net.cn/admin-api/tk/open/v1/ocr/image-to-text" \
  -F "file=@/path/to/image.png"
```

```json
{ "code": 0, "msg": "", "data": { "text": "识别出的文字" } }
```

常见失败：`400` 图片为空 / 格式不支持 / 超过 20MB；`106000052` 模型服务暂不可用。业务逻辑不落盘图片与历史结果，图片仅在本次请求中发送给视觉模型服务。

### 9.2 文案改写

```http
POST /admin-api/tk/open/copywriting/rewrite
```

```json
{ "copywriting": "原始文案", "prompt": "改成更口语化的短视频口播" }
```

| 字段 | 必填 | 限制 |
| --- | --- | --- |
| `copywriting` | 是 | ≤20000 字符 |
| `prompt` | 是 | ≤4000 字符 |

```json
{ "code": 0, "msg": "", "data": { "copywriting": "改写后的文案" } }
```

### 9.3 对标视频下载 / 解析

```http
POST /admin-api/tk/open/video/download
```

```json
{ "sourceUrl": "https://...", "libraryId": 123 }
```

`sourceUrl` 必填、≤2048、必须以 `http://` 或 `https://` 开头；`libraryId` 可选。响应 `data`：`sourceUrl`、`resolvedVideoUrl`、`coverUrl`、`videoDuration`、`resolution`。

### 9.4 视频文案时间轴提取

| 方法 | 路径 | 鉴权 |
| --- | --- | --- |
| POST | `/admin-api/tk/open/video/transcript/extract` | 免登录，创建异步任务 |
| GET | `/admin-api/tk/open/video/transcript/extract/{taskId}` | 免登录，查询任务 |
| POST | `/admin-api/tk/open/video/transcript/extract-sync` | **需后台权限** `tk:reference:analyze`，同步等待结果 |

创建请求体：`sourceUrl`（必填，≤2048，`http(s)://` 开头）、`targetLanguage`（可选）。`taskId` 为 `Long` 数字类型。

### 9.5 TikTok Webhook（平台侧）

```http
POST /tk/tiktok/webhook
TikTok-Signature: ...
```

由 TikTok 官方回调，平台校验签名后处理发布状态。调用方不要主动调用；该路径无 `/admin-api` 前缀，生产可达性需单独确认 Nginx 配置。

---

## 10. 后台管理 API 索引（需登录）

> 以下均为 `/admin-api/**`，需 `Authorization: Bearer <accessToken>` + `tenant-id` + 对应 `@PreAuthorize` 菜单权限，并受 API 加密影响。**不属于对外交付接口**，此处仅作内部集成与能力盘点索引。字段级契约请以 Swagger / 源码 VO 为准。

| 模块 | 类级路径 | 主要端点 |
| --- | --- | --- |
| 登录与权限 | `/admin-api/system/auth` | `POST /login`、`/logout`、`/refresh-token`、`GET /get-permission-info` |
| 租户 | `/admin-api/system/tenant` | `GET /get-id-by-name?name=` |
| 混剪生成任务 | `/admin-api/tk/generation` | `POST /precheck`、`/create`、`/create-batch`、`/create-with-opening`、`/translate-script`、`/audio-export`、`/voice-preview`、`/retry`、`/opening/session/*`、`/opening/chunk`、`GET /page`、`/page-summary`、`/status-batch`、`/get` |
| 生成批次 | `/admin-api/tk/generation-batch` | `GET /page`、`/get`、`POST /retry-failed` |
| 生成路由 | `/admin-api/tk/generation-route` | `GET /page`、`/get`、`/history/page`、`/statistics`、`PUT /update` |
| 素材视频 | `/admin-api/tk/material-video` | `POST /upload`、`PUT /usage-phase`、`PUT /segment-type`、`GET /get`、`/page`、`/segment-summary`、`DELETE /delete`、`/delete-batch` |
| 素材库 | `/admin-api/tk/material-library` | `POST /create`、`PUT /update`、`GET /get`、`/page`、`DELETE /delete` |
| 素材分片上传 | `/admin-api/tk/upload` | `POST /material-video/session/create`、`/chunk`、`/session/complete`、`GET /material-video/session/{uploadId}`、`DELETE /material-video/session/{uploadId}` |
| 参考分析 | `/admin-api/tk/reference` | `POST /analyze`、`/video/download`、`/{id}/script-options/regenerate`、`GET /latest`、`/{id}`、`/page`、`/status-batch` |
| 配音 / 音色 | `/admin-api/tk/voice-profile` | `POST /create`、`/mimo-design`、`/mimo-clone`、`/retry`、`GET /list`、`PUT /enabled`、`/enabled-batch`、`/tags`、`DELETE /delete`、`/delete-batch` |
| 音色选项 / 收藏 | `/admin-api/tk/voice/minimax`、`/admin-api/tk/voice/favorite` | `GET /options`、`POST /favorite`、`DELETE /favorite`；收藏为类级路径上的 `GET/POST/DELETE` |
| BGM | `/admin-api/tk/bgm` | `GET /list`、`/system-list`、`/reference-count`、`/references`、`POST /upload`、`DELETE /delete` |
| 受众音频预设 | `/admin-api/tk/audio-audience-preset` | `GET /list`、`/get`、`POST /save`、`DELETE /reset` |
| TikTok 发布（后台） | `/admin-api/tk/tiktok-publish` | 媒体会话全套、`POST /create`、`/reschedule`、`/cancel`、`/retry`、`/link/sync`、`/publish-url/register`、`/status/sync`、`GET /task-page`、`/detail-page`、`/post-page` |
| TikTok 账号/授权/统计 | `/admin-api/tk/tiktok-account`、`/tk/tiktok-auth`、`/tk/tiktok-account-stats`、`/tk/tiktok-account-group`、`/tk/tiktok-content-display`、`/tk/video-publish-center`、`/tk/open-tiktok-publish/queue` | 分页、绑定/解绑、二维码授权、统计同步、发布队列 |
| TikTok Shop | `/admin-api/tk/tiktok-shop`、`/tk/tiktok-shop/media` | 能力查询、授权会话、商品、发布任务与确认、媒体会话 |
| Facebook / Instagram | `/admin-api/tk/social-auth`、`/tk/social-account`、`/tk/social-publish`、`/tk/social-account/stats` | 授权回调、账号、发布任务、指标同步 |
| 公司 / 积分 / 看板 | `/admin-api/tk/company`、`/tk/credit`、`/tk/dashboard`、`/tk/business-log` | 公司 CRUD、余额与充值、8 个看板接口、业务日志（需 `super_admin`） |
| 开放平台管理 | `/admin-api/tk/open-api/client`、`/tk/open-api/operations` | 见 2.5 |

---

## 11. 能力缺口与 API 化建议（规划中，尚未实现）

### 11.1 现状差距

外部调用方目前只能「上传/指定一个视频并发布到 TikTok」，**无法通过开放 API 使用本平台最核心的自动混剪能力**：创建混剪任务、查询生成进度、下载成片、管理素材库、选择配音与 BGM。这些能力全部位于第 10 节的后台 API 之下，绑定后台账号、租户、菜单权限和 AES 加密。

如果业务目标是「客户通过 API 完成：上传素材 → 生成混剪视频 → 拿成片 → 一键发布」，当前链路是**断开的**：调用方只能自己先生成好视频，再用 `quick-tasks` 交给平台发布。

### 11.2 建议补齐的开放 API（设计草案）

沿用现有 HMAC 签名、幂等键、回调与 `clientId` 隔离模型，建议新增 `/admin-api/tk/open/v1/generation` 接口族：

| 方法 | 建议路径 | 用途 |
| --- | --- | --- |
| `POST` | `/materials/uploads` | 创建素材上传会话（复用 `media` 的 OSS / LOCAL 双模式） |
| `POST` | `/materials/uploads/{id}/complete` | 完成上传，返回 `materialId` |
| `GET` | `/materials` | 查询当前客户端素材列表与状态 |
| `DELETE` | `/materials/{materialId}` | 删除素材 |
| `POST` | `/tasks` | 创建混剪任务（脚本、素材、音色、BGM、时长、目标语言、片头等） |
| `GET` | `/tasks/{generationId}` | 查询任务状态、进度、失败原因 |
| `GET` | `/tasks/{generationId}/result` | 获取成片下载地址（建议短期签名 URL + 过期时间） |
| `POST` | `/tasks/{generationId}/retry` | 重试失败任务 |
| `POST` | `/tasks/{generationId}/cancel` | 取消排队中任务 |
| `GET` | `/options` | 可用音色、BGM、受众预设、目标语言等枚举 |
| `POST` | `/tasks/{generationId}/publish` | 成片直接转为发布任务，打通第 6 节 |

配套要求：

- 新增模块权限 `generation`，并在 `TkOpenApiAuthenticationService` 的 URI→权限映射中注册；
- 生成任务与素材按 `clientId` 隔离，编号使用 opaque ID，不暴露数据库主键；
- 复用 `Idempotency-Key`（创建生成任务）和回调事件（`generation.processing` / `generation.success` / `generation.failed`）；
- 成片下载 URL 必须是带过期时间的签名地址，不能返回永久公开链接；
- 积分/配额按 `clientId` 计量，避免跨租户扣费；
- 生成是长耗时任务，务必异步 + 轮询 + 回调，不要设计为同步等待。

> 本节为**建议**，不是已实现契约。落地前需单独做技术方案、数据库隔离与灰度验证。

---

## 12. 端到端调用示例

### 12.1 最简流程（推荐，视频已有公网 HTTPS 地址）

```text
1. 服务端  POST /auth/sessions                authMode=AUTO
2. 浏览器  打开 data.launchUrl                 用户完成 TikTok 授权（无需签名）
3. 服务端  GET  /auth/sessions/{id}           轮询至 status=SUCCESS，保存 connectionId
4. 服务端  POST /publish/quick-tasks          传 externalAccountId + videoUrl + Idempotency-Key
5. 服务端  GET  /publish/tasks/{taskId}       轮询至终态
           GET  /publish/tasks/{taskId}/details  逐账号结果
6. 服务端  GET  /publish/tasks/{taskId}/metrics  发布成功后取四项指标
```

该流程**不需要**实现二维码、OAuth 回调、OSS 表单、LOCAL 分片，也不需要配置回调地址。

### 12.2 完整上传流程（视频在调用方本地）

```text
1. POST /auth/sessions (AUTO) -> launchUrl -> 轮询得 connectionId
2. 计算视频 fileSize 与完整 sha256
3. POST /media/uploads -> 得到 uploadMode
   OSS   : 按 fields 向 uploadUrl 提交 multipart/form-data
   LOCAL : 按 chunkSize 逐片 PUT /media/uploads/{id}/chunks/{i}（每片独立签名）
4. POST /media/uploads/{id}/complete -> status=READY，保存 mediaId
5. POST /publish/tasks（Idempotency-Key、connectionIds、mediaId、发布参数）
6. 轮询 GET /publish/tasks/{taskId} 与 /details
7. FAILED 明细可 POST /publish/details/{detailId}/retry
8. 可选：配置回调接收 publish.* 事件，并以查询接口兜底
```

### 12.3 curl 示例（占位值需替换）

```bash
# 空请求体 SHA-256 固定值
BODY_HASH="e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
TS=$(date +%s)
NONCE=$(openssl rand -hex 16)
TARGET="/admin-api/tk/open/v1/tiktok/connections?externalAccountId=account_10001"
CANON=$(printf 'GET\n%s\n%s\n%s\n%s' "$TARGET" "$TS" "$NONCE" "$BODY_HASH")
SIG=$(printf '%s' "$CANON" | openssl dgst -sha256 -hmac "$TK_OPEN_API_CLIENT_SECRET" -binary | base64)

curl -s "https://tkassetplant.fnn.net.cn${TARGET}" \
  -H "X-TK-Client-Id: $TK_OPEN_API_CLIENT_ID" \
  -H "X-TK-Timestamp: $TS" \
  -H "X-TK-Nonce: $NONCE" \
  -H "X-TK-Request-Id: req_$(date +%s)" \
  -H "X-TK-Signature: $SIG"
```

> shell 中 `printf '%s'` 不会补尾部换行，符合 canonical「末尾无换行」要求。带 JSON 体时把 `$BODY_HASH` 换成实际 body 文件的 `sha256sum` 结果，且请求必须发送**同一份字节**。

---

## 13. 接入检查清单与排错

### 13.1 上线前检查

- [ ] 已获得独立 `clientId` / `clientSecret`，接收回调时另有 `callbackSecret`
- [ ] 密钥只保存在服务端环境变量或密钥管理系统
- [ ] 后台已启用 `auth` / `media` / `publish` 权限、IP 白名单、限流与配额、客户端状态
- [ ] 已实现 5 行 canonical string，末尾无换行，方法大写
- [ ] 签名使用实际发送的 `REQUEST_TARGET`（含 `/admin-api` 与原始 query）和原始 body 字节
- [ ] 服务器时钟同步，每次请求生成唯一 `nonce`
- [ ] 创建发布任务必带 `Idempotency-Key`，超时重试复用原 key
- [ ] 已处理 401 / 403 / 409 / 413 / 429 / 503 与回调 `eventId` 幂等
- [ ] 已确认目标环境 `/admin-api/` 基地址可达（不要使用无前缀兼容路径）
- [ ] 已完成真实授权、实际启用的上传模式、发布、查询、回调全链路联调

### 13.2 `OPEN_API_SIGNATURE_INVALID` 排查顺序

1. `X-TK-Client-Id` 是否与当前 `clientSecret` 配对；
2. `REQUEST_TARGET` 是否含 `/admin-api`、完整路径与原始查询串；
3. 查询参数顺序、编码与实际 URL 是否一致；
4. JSON 是否用签名前生成的同一份 UTF-8 字节发送；
5. 空请求体是否使用固定空串 SHA-256；
6. canonical 是否恰好 5 行、行间单个 LF、末尾无换行；
7. HMAC key 是否为 `clientSecret` 原文，结果是否标准 Base64。

排查时向平台提供 `X-TK-Request-Id` 即可，**不要提交完整密钥或 Token**。

### 13.3 重试策略

- 签名、参数、权限、资源编号错误：先修正再请求，不要盲目重试；
- 网络超时、502 / 503 / 429：有限退避重试；
- 发布创建超时：复用原 `Idempotency-Key`，换新 `timestamp` / `nonce`；
- TikTok 已产生 publishId 但状态未定：不要重复创建发布任务，先查明细。

---

## 14. 附录

### 14.1 开放 API 端点总表（源码已实现，共 22 个）

| 模块 | 方法 | 路径（相对 `/admin-api/tk/open/v1/tiktok`） | 签名 |
| --- | --- | --- | --- |
| 授权 | POST | `/auth/sessions` | 是 |
| 授权 | GET | `/auth/sessions/{authSessionId}` | 是 |
| 授权 | GET | `/auth/sessions/{authSessionId}/launch` | 否（HTML） |
| 授权 | GET | `/auth/sessions/{authSessionId}/launch/status` | 否 |
| 授权 | GET | `/auth/callback` | 否（TikTok 调用） |
| 连接 | GET | `/connections` | 是 |
| 连接 | POST | `/connections/{connectionId}/profile/refresh` | 是 |
| 连接 | POST | `/connections/{connectionId}/disconnect` | 是 |
| 媒体 | POST | `/media/uploads` | 是 |
| 媒体 | GET | `/media/uploads/{uploadId}` | 是 |
| 媒体 | PUT | `/media/uploads/{uploadId}/chunks/{chunkIndex}` | 是 |
| 媒体 | POST | `/media/uploads/{uploadId}/complete` | 是 |
| 媒体 | DELETE | `/media/uploads/{uploadId}` | 是 |
| 发布 | POST | `/publish/tasks` | 是 |
| 发布 | POST | `/publish/quick-tasks` | 是 |
| 发布 | GET | `/publish/tasks/{taskId}` | 是 |
| 发布 | POST | `/publish/tasks/{taskId}/reschedule` | 是 |
| 发布 | POST | `/publish/tasks/{taskId}/cancel` | 是 |
| 发布 | GET | `/publish/tasks/{taskId}/details` | 是 |
| 发布 | GET | `/publish/tasks/{taskId}/metrics` | 是 |
| 发布 | POST | `/publish/tasks/metrics/batch` | 是 |
| 发布 | POST | `/publish/details/{detailId}/retry` | 是 |

> 与既有 `docs/openapi/tiktok-open-api.json`（17 个 path、18 个 method+path 端点）相比，源码新增 4 个端点：`/connections/{id}/profile/refresh`、`/publish/tasks/{id}/reschedule`、`/publish/tasks/{id}/cancel`、`/publish/tasks/metrics/batch`；另新增 `publish.cancelled` 回调事件。`TaskResp` / `DetailResp` / `ConnectionResp` 也增加了定时发布与账号资料字段。本文与同目录 `tk-platform-openapi.json` 已补齐以上差异。

### 14.2 仓库内相关文档

| 文件 | 用途 |
| --- | --- |
| `docs/tiktok-open-api.md` | A 方内部接口说明 v1.2（TikTok 开放 API 主说明） |
| `docs/openapi/tiktok-open-api-c-party-integration.md` | C 方联调主文档 v1.4（含后台配置、验收清单） |
| `docs/openapi/tiktok-open-api.json` | 原有 OpenAPI 3.0.3（17 端点） |
| `docs/openapi/image-to-text-open-api.md` | OCR 接口单独说明 |
| `docs/sdk/tiktok-open-api-{java,node,python}.md` | 三语言签名与调用示例 |
| `docs/api/tk-platform-api.md` | **本文**：全平台 API 参考 |
| `docs/api/tk-platform-openapi.json` | 机读版（22 开放端点 + 免登录工具接口） |
| `docs/api/tk-platform-api.html` | 单页阅读版（离线可打开，支持目录筛选与深浅色切换） |
| `docs/api/build_html.py` | 由本文重新生成 HTML 的构建脚本：`python docs/api/build_html.py` |
| `docs/api/tk-generation-api-fit-assessment.md` | 剪辑工具出片 9 项需求的 API 适配评估与对接清单（本文第 11 节的实测补充） |

### 14.3 源码核对索引

| 内容 | 路径（相对 `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/`） |
| --- | --- |
| 开放 API 控制器 | `controller/open/tiktok/`、`controller/open/ocr/` |
| 请求 / 响应 VO | `controller/open/tiktok/vo/`、`controller/open/ocr/vo/`、`controller/open/copywriting/vo/` |
| HMAC 过滤器与签名 | `framework/openapi/TkOpenApiAuthenticationFilter.java`、`TkOpenApiSigner.java`、`TkOpenApiAuthenticationService.java`、`TkOpenApiRedisAccessGuard.java` |
| 统一响应 | `framework/openapi/TkOpenApiResponse.java` |
| 业务逻辑 | `service/open/tiktok/`、`service/open/api/TkOpenApiCallbackService.java`、`service/open/tiktok/TkOpenTiktokScheduleTimeService.java` |
| 错误码 | `framework/openapi/TkOpenApiException.java` 及各 Service 中的 `TkOpenApiException.*` 调用 |
| 后台控制器 | `controller/admin/**` |
| Nginx 转发规则 | `deploy/nginx/tk-auto-mix.conf` |
