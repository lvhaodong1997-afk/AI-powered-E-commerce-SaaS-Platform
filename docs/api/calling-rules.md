# API 调用规则

## 选择正确的接口与凭证

| 用途 | 路径 | 必须携带 |
| --- | --- | --- |
| 查询租户 ID | `/admin-api/system/tenant/get-id-by-name` | 租户名称；无需已有 Token |
| 登录、刷新 | `/admin-api/system/auth/login`、`/refresh-token` | `tenant-id`；登录凭证或刷新令牌 |
| 自动出片 | `/admin-api/tk/automation/v1/**` | `Authorization: Bearer <accessToken>` **与** `tenant-id` |
| TikTok 授权、上传、发布 | `/admin-api/tk/open/v1/tiktok/**` | `X-TK-Client-Id`、`X-TK-Timestamp`、`X-TK-Nonce`、`X-TK-Signature` 四项 HMAC 头；公开端点除外 |

两套凭证不可互换。所有 URL 使用管理员确认的环境与 `/admin-api` 前缀。产品出片默认地址为 `https://clipforgestudio.fnn.net.cn`；既有 TikTok 客户端按开通域名接入，不要假设两个域名共享租户、权限或凭证。

## 登录、租户和权限

使用实际登录用户名，不能用显示昵称代替。租户查找返回 `data=null` 时停止并联系管理员。登录、刷新、出片必须使用该账号所属租户，不能通过更换 `tenant-id` 访问其他资源。

环境开启验证码时，按现有登录流程取得 `captchaVerification`；此接口不提供验证码绕过、免密登录或永久会话。保存 `accessToken`、`refreshToken`、`expiresTime`（平台默认 Unix 毫秒时间戳），在到期前刷新并替换返回的两种令牌。刷新失败重新登录。并行请求共享一个刷新过程，避免同时刷新导致令牌互相覆盖。

账号需有 `tk:generation:query`、`tk:generation:create`、`tk:material-library:query`。权限和资源归属分别校验；有菜单权限不代表可访问别人的任务、素材或 BGM。BGM 查询与创建仅允许当前账号可用的自有上传。

## JSON、响应与错误处理

常规请求采用 HTTPS、UTF-8、`Content-Type: application/json`。出片请求直接发送 JSON；无需套用 HMAC，也不要从网页实现推断所有接口都要 AES 加密。字段省略时才使用默认值；不要用 `null` 代替必填值或默认值。

账号接口响应形如 `{"code":0,"msg":"","data":{...}}`。同时检查 HTTP 状态、JSON 解析结果和 `code`；**HTTP 200 不等于成功**。`code=0` 只代表本次接口成功，创建后仍须查询异步结果。错误时保留 `code`、`msg`、任务号和发生时间用于排错，不记录 Token 或完整登录请求。

| 情况 | 调用方处理 |
| --- | --- |
| HTTP 401 或业务 `code=401` | 刷新令牌；刷新失败重新登录。查询可在刷新后再试，创建先确认是否已受理 |
| HTTP 403 或业务 `code=403` | 检查账号权限、租户和资源归属；不要自动切换账号/租户 |
| HTTP 400 或非零参数错误码 | 读取 `msg`，修正字段后再调用；错误码并不全部等于 HTTP 状态 |
| `code=1060000029` | 积分不足；停止创建并提示补充积分 |
| 下载业务 `code=409` | 尚未成功或暂无成片；先查任务，成功且有结果后再获取链接 |
| 查询响应 `code=0`，但 `failed=true` | 异步生成失败；读取 `failCode`、`failReason`，停止等待 |
| 非 JSON / 网络超时 / 5xx | 区分查询与创建；保留上下文，按下述重试规则处理 |

## 资源选择与参数限制

素材库 `pageNo` 从 1 起，默认 1；`pageSize` 默认 10，范围 1–200。用 `total` 遍历所有页，不可因第一页未找到而认定不存在。名称不是 ID，同名结果需明确选择。

音色通过实时列表取得：`voiceProfileId` 与非空 `voiceCode` 二选一，并一并提交该项的 `ttsProvider`；MiMo 必须用 `voiceProfileId`。示例编码/ID 不代表用户环境已有资源。

口播最长 3000 字符，正文只放可直接朗读的文本。按实际口播时长结束，系统硬上限最大 500 秒，**当前环境配置可能更低**。不要通过提交 500 秒脚本推断保证可生成；超过有效上限或无法识别时长会失败。当前仅支持 CN、关闭翻译、1080×1920、30fps 和固定字幕样式，完整限制见 [自动出片接口](tk-automation-v1.md)。

积分查询为快照，创建时由现有积分服务再次校验并冻结/结算；查询余额后并不保证下一次创建一定成功。不要把 HTTP 响应成功当作字幕、配音及画面验收通过。

## 异步任务与重试

创建后立即持久化 `taskId`、标题、提交时间及调用方业务编号。建议每 3–5 秒查询，网络异常时增加间隔；设置整体等待期限，到期提示“仍在处理”，保留任务号以便继续查询。不要因本地等待超时自动创建新任务。

`completed=true` 对应 `SUCCESS`，`failed=true` 对应 `FAILED`，其他状态继续等待。`progress` 是阶段进度，不能仅凭 100 判断成功。逐字字幕是否完成需结合 `subtitleKaraokeStatus` 和 `subtitleKaraokeReason`：降级信息可能与成片成功同时存在，诊断为空也不等于逐字字幕验收通过。

| 操作 | 自动重试规则 |
| --- | --- |
| 出片资源查询、任务查询、获取下载信息 | 临时网络错误可以退避重试；永久权限/参数错误先修正 |
| **出片 `POST /automation/v1/tasks`** | **不支持 `Idempotency-Key`，不得盲重试**；响应丢失后先在生成记录中按标题及时间核对，必要时联系管理员 |
| TikTok 创建/改期发布 | 使用各接口要求的 `Idempotency-Key`；同一业务重试保持原幂等键和相同参数，重新生成时间戳/nonce/签名 |
| 存储文件下载 | 临时异常可重试；签名过期重新获取地址，再按约定名保存 |

自动出片目前没有生成 Webhook、标签抽画面、创建幂等或自动重试接口。TikTok 发布回调属于另一接口族，不能用来监听出片完成。

## 下载与密钥管理

下载信息接口返回 JSON 中的 `downloadUrl`，不是文件流。临时链接的有效期依存储配置；本接口未承诺固定分钟数或独立 `expiresAt` 字段。尽快按原 URL 下载；失效后重新获取。获取任务结果也会检查当前账号资源权限。

文件名可选，默认 `video-{taskId}.mp4`；最长 128 字符、以 `.mp4` 结尾，拒绝路径、控制字符以及 `/ \ : " < > | ? *`。查询参数正确 URL 编码。

凭证仅保存在调用端的密钥配置，示例及 Postman 导出时清除本地密码/Token。刷新 URL 和签名下载链接也应视为敏感信息。向存储链接下载不携带平台 `Authorization`，HMAC 签名所用 `clientSecret` 不下发浏览器。
