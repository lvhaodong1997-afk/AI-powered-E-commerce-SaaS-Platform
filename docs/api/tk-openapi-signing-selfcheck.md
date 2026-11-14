# 开放 API 签名与连通性自检指南

> 版本：1.0 · 日期：2026-11-11 · 面向：已领取 `clientId` 与密钥的对接方
> 目的：在生成接口上线前，先把**签名鉴权**和**网络连通性**调通，避免两件事混在一起排查。

---

## 1. 接入信息

| 项目 | 值 |
| --- | --- |
| Base URL | `https://tkassetplant.fnn.net.cn` |
| 签名请求路径前缀 | 必须包含 `/admin-api`（如 `/admin-api/tk/open/v1/tiktok/connections`） |
| 请求体类型 | `application/json`（GET 无请求体） |
| 时间戳容差 | ±300 秒（Unix 秒级，请确保本机时钟准确，建议开启 NTP） |
| 频控 | 默认 120 次/分钟、10000 次/日，超出返回 429 |

每个请求携带以下请求头：

| 头 | 说明 |
| --- | --- |
| `X-TK-Client-Id` | 分配的调用方 ID（`client_` 开头） |
| `X-TK-Timestamp` | Unix 秒级时间戳 |
| `X-TK-Nonce` | 每次请求唯一的随机串（建议 UUID），短时间重复会被拒绝 |
| `X-TK-Signature` | 按下文算法计算的签名（Base64） |
| `X-TK-Request-Id` | 可选，链路追踪编号（≤128 字符，`A-Za-z0-9._:-`） |

---

## 2. 签名算法

1. 对请求体原始字节计算 `SHA256`，转小写十六进制（GET/HEAD 无请求体时，对空字符串计算）。
2. 拼接 5 行规范化串（`\n` 分隔，顺序固定）：

```
canonical =
  HTTP_METHOD（大写）          \n
  REQUEST_TARGET              \n
  TIMESTAMP                   \n
  NONCE                       \n
  SHA256_HEX(BODY)
```

其中 `REQUEST_TARGET` = 路径（**必须含 `/admin-api` 前缀**）+ 原始查询串（有 `?a=b` 则一并带上，不做 URL 重编码）。

3. 以 `clientSecret` 为密钥计算 `HMAC-SHA256`，结果转 Base64，即 `X-TK-Signature`。

### Node.js 示例

```js
const crypto = require('crypto');

function sign(method, requestTarget, timestamp, nonce, body, clientSecret) {
  const bodyBuf = Buffer.from(body ?? '', 'utf8');
  const bodyHash = crypto.createHash('sha256').update(bodyBuf).digest('hex');
  const canonical = [
    method.toUpperCase(), requestTarget, timestamp, nonce, bodyHash
  ].join('\n');
  return crypto.createHmac('sha256', clientSecret)
    .update(canonical, 'utf8').digest('base64');
}
```

### curl 示例（自检端点）

```bash
CLIENT_ID="client_xxxx"          # 我方发放
CLIENT_SECRET="tksec_xxxx"       # 我方发放
BASE="https://tkassetplant.fnn.net.cn"
TARGET="/admin-api/tk/open/v1/tiktok/connections"
TS="$(date +%s)"
NONCE="$(uuidgen)"

SIG="$(node -e "
const c=require('crypto');
const canonical=['GET','$TARGET','$TS','$NONCE',
  c.createHash('sha256').update('').digest('hex')].join('\n');
console.log(c.createHmac('sha256','$CLIENT_SECRET')
  .update(canonical,'utf8').digest('base64'));
")"

curl -sS "$BASE$TARGET" \
  -H "X-TK-Client-Id: $CLIENT_ID" \
  -H "X-TK-Timestamp: $TS" \
  -H "X-TK-Nonce: $NONCE" \
  -H "X-TK-Signature: $SIG" \
  -H "X-TK-Request-Id: selfcheck-1"
```

预期结果：HTTP 200，响应体 `{"code":0,"msg":"","data":[...],"requestId":"..."}`（未连接过账号时 `data` 为空数组）。

> 说明：生成类接口（`/open/v1/generation/**`）尚在开发，自检阶段先用上面这个已上线的只读接口验证签名与连通性；接口族上线后算法、请求头完全一致，无需改动签名代码。

---

## 3. 错误码排错表（鉴权与网关层）

响应体形如 `{"code":"OPEN_API_XXX","msg":"...","requestId":"..."}`，HTTP 状态码配合判断：

| code | HTTP | 原因与处理 |
| --- | --- | --- |
| `OPEN_API_AUTH_HEADER_MISSING` | 401 | 四个鉴权头有缺失，逐个核对 |
| `OPEN_API_CLIENT_INVALID` | 401 | clientId 错误或被禁用，与我方核对 |
| `OPEN_API_TIMESTAMP_EXPIRED` | 401 | 时间戳超出 ±300 秒或格式错误：校准 NTP、确认是秒级 |
| `OPEN_API_NONCE_REPLAYED` | 401 | nonce 短时间内重复：每次请求生成新的随机串 |
| `OPEN_API_SIGNATURE_INVALID` | 401 | 签名不匹配，按第 4 节顺序排查 |
| `OPEN_API_IP_NOT_ALLOWED` | 403 | 出口 IP 不在我方白名单：把出口 IP 发给我方更新 |
| `OPEN_API_PERMISSION_DENIED` | 403 | 该路径所需权限未授予：与我方核对 permissions 配置 |
| `OPEN_API_BODY_TOO_LARGE` | 413 | 签名请求体超过上限 |
| `OPEN_API_RATE_LIMITED` | 429 | 超过分钟频控，稍后重试 |
| `OPEN_API_QUOTA_EXCEEDED` | 429 | 超过日配额，联系我方调整 |
| `OPEN_API_GUARD_UNAVAILABLE` / `OPEN_API_SECRET_UNAVAILABLE` | 503 | 我方服务组件暂不可用，带 `requestId` 联系我方 |

排错顺序建议：先看 HTTP 状态码，再对 code；401 里最常见的是 `OPEN_API_SIGNATURE_INVALID`。

### `OPEN_API_SIGNATURE_INVALID` 排查顺序

1. `REQUEST_TARGET` 是否带了 `/admin-api` 前缀、查询串是否与实际请求完全一致；
2. 时间戳是否秒级、是否与签名时用的值相同；
3. body 是否与计算哈希时的字节完全一致（不要先格式化再发送）；
4. 是否用了 `callbackSecret` 当 `clientSecret`（两者不同）；
5. 换一个新 nonce 重试一次，排除重放缓存。

---

## 4. 连通性分层自检

签名调不通时，先分层定位是「网络不通」还是「签名不对」：

```bash
# 1. DNS
nslookup tkassetplant.fnn.net.cn

# 2. TCP/TLS/HTTP（这一步不需要签名）
curl -v "https://tkassetplant.fnn.net.cn/admin-api/tk/open/v1/tiktok/connections"
#    能收到 401 JSON（带 code）说明网络与证书已通，剩下的只是签名问题
#    卡在 DNS / TCP / TLS 阶段则是网络问题，把 curl -v 全文输出发给我方

# 3. 加签名重试（第 2 节示例）
```

网络问题一般出在：公司出口防火墙、代理、DNS 解析、或我方安全组未对公网开放。请把**出口公网 IP** 提供给我方，用于两件事：网络放行核对 + 调用方 IP 白名单（`allowedIps`）配置。

---

## 5. 联调约定

- 所有请求带 `X-TK-Request-Id`；反馈问题时附上 `requestId`、时间点、完整请求头（**隐去签名值**）。
- 写操作携带 `Idempotency-Key` 头（≤128 字符），超时重试复用同一值。
- 密钥泄露时立即告知我方轮换（旧密钥即时失效），不要在聊天工具中明文传递密钥。
