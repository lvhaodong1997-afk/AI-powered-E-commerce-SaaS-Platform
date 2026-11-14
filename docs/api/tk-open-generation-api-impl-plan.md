# 开放生成接口实施方案与阻塞处置

> 版本：1.0 · 日期：2026-11-11
> 背景：对接方反馈三条阻塞——① 开放的混剪接口还没做出来；② 未发放 clientId 和密钥；③ 工作电脑连不上域名。
> 本文基于当前源码逐条给出解法，并给出第 ① 条的实现蓝图。**本文为内部文档，未改任何业务代码。**

---

## 0. 三条阻塞的判定与责任

| # | 阻塞 | 判定 | 解法 | 谁来做 |
| --- | --- | --- | --- | --- |
| ① | 开放混剪接口没做出来 | **属实**，源码只有 TikTok 发布族（`/tk/open/v1/tiktok/**`），生成能力全在后台 API | 按第 2 节蓝图开发 `/tk/open/v1/generation/**` 接口族 | 本方开发（本文方案） |
| ② | 没给 clientId 和密钥 | **发放机制已存在但从未执行** | 用超管账号调 `POST /admin-api/tk/open-api/client/create`，见第 4 节 SOP | 本方运营/管理员，5 分钟 |
| ③ | 工作电脑连不上域名 | 与接口无关，属网络/部署问题 | 按第 5 节排查清单逐项确认；同时注意 client 表有 `allowedIps` 白名单，IP 不在白名单也会 403 | 双方配合 |

注意 ②③ 不依赖 ①：即使生成接口未开发，也可以先发 clientId（权限只给 `auth`，或不发）并把域名打通，让对方先把签名、连通性调通。

---

## 1. 现有基础设施盘点（已确认可用）

| 组件 | 位置 | 状态 |
| --- | --- | --- |
| HMAC 签名过滤器 | `framework/openapi/TkOpenApiAuthenticationFilter` | 可用，但 `shouldNotFilter` 只拦 `/tk/open/v1/tiktok/` |
| 鉴权服务 | `TkOpenApiAuthenticationService` | 可用：clientId 校验、IP 白名单、签名、限流（默认 120/分钟）、日配额（默认 10000） |
| 调用方表 | `tk_open_api_client`（`TkOpenApiClientDO`） | **无 tenant_id 列**，见设计决策 D1 |
| 调用方管理 | `TkOpenApiClientAdminController`（`/tk/open-api/client/**`，`@PreAuthorize super_admin`） | 可用：create 一次性返回 clientId/`tksec_`/`tkcb_` 三把密钥，支持 rotate、启停、删 |
| 权限模型 | `permissions` 列 + `requiredPermission(uri)` | 只支持 `auth` / `media` / `publish`，**无 `generation`** |
| 回调框架 | `TkOpenApiCallbackService` + 事件表 | 可复用，新增 generation 事件类型即可 |
| 请求日志 | `tk_open_api_request_log` | 自动记录，排错可用 |
| 生成业务 | `TkGenerationTaskService`（precheck/create/get/status-batch）、`TkCreditService.freezeForGenerationTask` | 后台 API 完整，需包装开放层 |

---

## 2. 设计决策（需拍板后开工）

### D1 clientId ↔ 租户绑定（最关键）

生成任务按租户隔离并扣积分（`freezeForGenerationTask(tenantId)`），而 `tk_open_api_client` 没有 `tenant_id`。TikTok 族按 `clientId` 隔离数据，与此不同。

**已拍板（2026-11-11）：采用独立映射表。** 新增 `tk_open_api_client_tenant`（`client_id` + `tenant_id`，支持一个 client 绑多个租户，含 `is_default` / `status` 字段）。鉴权成功后按 client 查映射：仅一条时自动作为该请求租户；多条时取 `is_default=1` 的租户（将来做代运营再扩展请求级 `X-TK-Tenant` 头）。`tenantId` 放进 `TkOpenApiPrincipal` / `TkOpenApiContext`，开放生成服务层用它调用 `TkGenerationTaskService` 与积分服务。当前对接场景只需单条映射，表结构已预留扩展。

### D2 新增 `generation` 权限（3 处改动）

1. `TkOpenApiClientAdminService.SUPPORTED_PERMISSIONS` 加 `"generation"`；
2. `TkOpenApiAuthenticationFilter.requiredPermission`：URI 含 `/generation/` → 返回 `"generation"`；
3. `shouldNotFilter` 从 `"/tk/open/v1/tiktok/"` 放宽为 `"/tk/open/v1/"`。

### D3 计费与错误语义

复用 `freezeForGenerationTask` / `refundByLogId`。积分不足把 `TK_CREDIT_NOT_ENOUGH`(1_060_000_029) 转成开放层字符串码 `GENERATION_CREDIT_NOT_ENOUGH`（400），message 带所需积分数。`/credits/balance` 直接读 `remainingCredits / frozenCredits / lowBalance`。

### D4 幂等

创建/改期/重试/取消统一要求 `Idempotency-Key` 头（≤128 字符），服务端以 `clientId + Idempotency-Key` 唯一约束落 `tk_open_api_idempotency` 表（记录响应快照，冲突返回 409 `IDEMPOTENCY_KEY_CONFLICT`），超时重试复用原响应。`externalRequestId` 作为业务对账字段保留。

### D5 参数契约（对应评估文档的 4 个坑，必须按新契约实现，不得沿用后台默认行为）

| 新契约字段 | 行为 |
| --- | --- |
| `bgmVolume` | 开放层校验 0–1，**内部强制走 `audioMixVersion=2` 通路**，任务详情回显实际生效音量 |
| `durationMode` | 枚举 `FIXED`（现行为）/ `FOLLOW_AUDIO`（新增：成片时长=口播音频时长，不按 `referenceDuration` 补画面） |
| `targetRegion` | 新增独立字段（如 `CN`），关闭翻译时也落库并在任务详情回显 |
| `materialTags` | 新增可选入参；同时给素材分页加 `tags` 筛选；规划器按标签过滤后再走 S1–S7 / 全库随机 |

### D6 数据隔离与安全

- 所有查询以 `clientId`（或绑定租户）过滤，opaque 任务号；
- 成片地址复用 `refreshGeneratedAssetReadUrl` 签名 URL（带过期 + 建议文件名）；
- 回调签名沿用 `callbackSecret`，事件类型新增 `generation.succeeded` / `generation.failed`。

---

## 3. 接口与文件改动清单

### P0（最小可用集，8 个端点）

| 方法 | 路径（`/admin-api/tk/open/v1/generation`） | 包装的后台能力 |
| --- | --- | --- |
| GET | `/credits/balance` | 积分余额查询 |
| GET | `/material-libraries` | 素材库分页（name/tags/videoCount） |
| GET | `/voices` | MiniMax 音色 options（过滤 unavailable 回显） |
| GET | `/bgm`（`sourceType=USER`） | BGM 列表，**Resp 增加 fileName** |
| POST | `/tasks/precheck` | 预检（积分/素材时长/字幕可行性） |
| POST | `/tasks` | 创建生成任务（含 D5 新字段） |
| GET | `/tasks/{taskId}` | 状态、当前步骤、失败原因、音量回显 |
| GET | `/tasks/{taskId}/result` | 成片签名地址 + 建议文件名 + 字幕降级原因 |

### P1（补全，4 个端点 + 回调）

`/material-libraries/{id}/tags`、`/tasks/{id}/retry`、`/tasks/{id}/cancel`、`/tasks/{id}/publish`（打通现有发布族）、`generation.succeeded/failed` 回调推送。

### 文件改动

**修改**（2 个）：`TkOpenApiAuthenticationFilter`（D2）、`TkOpenApiClientAdminService`（D2 权限；映射表的维护入口可并入现有 client 管理 Controller 或单独新增）。

**新增**：
- `controller/open/generation/TkOpenGenerationController`（P0 查询类）+ `TkOpenGenerationTaskController`（任务类）+ `vo/` 包；
- `service/open/generation/TkOpenGenerationQueryService`、`TkOpenGenerationTaskService`（clientId→租户解析、参数映射、幂等、错误码转换）；
- SQL：`tk_open_api_client_tenant` 映射表（D1）；`tk_open_api_idempotency` 表（`client_id` + `idempotency_key` 唯一索引）；
- 测试：鉴权/权限、幂等冲突、积分不足转换、`FOLLOW_AUDIO` 行为。

### 新增错误码（开放层字符串码）

`GENERATION_CREDIT_NOT_ENOUGH`(400)、`GENERATION_LIBRARY_NOT_FOUND`(404)、`GENERATION_VOICE_UNAVAILABLE`(400)、`GENERATION_BGM_NOT_FOUND`(404)、`GENERATION_TASK_NOT_FOUND`(404)、`GENERATION_TASK_NOT_CANCELLABLE`(409)、`GENERATION_DUPLICATE_REQUEST`(409)、`GENERATION_TAGS_UNSUPPORTED`(400，未实现标签时明确报错而非忽略)。

---

## 4. clientId 发放 SOP（阻塞②，当天可完成）

前置：操作者有 `super_admin` 角色的后台账号；服务已部署到目标环境。

1. `GET /admin-api/system/tenant/get-id-by-name?name=<租户名>` 换租户 ID（仅登录时需要）。
2. 登录拿 `accessToken` 后调：

```http
POST /admin-api/tk/open-api/client/create
Authorization: Bearer <accessToken>
tenant-id: <租户ID>
Content-Type: application/json

{
  "clientName": "GPT-出片工具-对接方",
  "authCallbackUrl": null,
  "publishCallbackUrl": "https://<对方回调地址>",
  "allowedIps": "<对方出口IP或网段>",
  "permissions": "auth",
  "rateLimitPerMinute": 120,
  "dailyQuota": 10000,
  "status": 0,
  "remark": "出片工具对接；生成接口上线后追授 generation"
}
```

响应**一次性**返回 `clientId`（`client_` 前缀）、`clientSecret`（`tksec_`）、`callbackSecret`（`tkcb_`）。
3. 交付方式（对方也要求了：不走聊天明文）：密码管理器共享链接、或加密压缩包（密码走电话/另一个渠道）二选一。
4. 生成接口上线后：`PUT /update` 把 `permissions` 改为 `auth,generation`。
5. 密钥泄露应急：`POST /rotate-secret?type=CLIENT`，旧密钥立即失效。

> 若对方只需要先调通签名与连通性，权限先给 `auth` 即可；`/admin-api/tk/open-api/client/page`、`/get` 可随时核对配置。

---

## 5. 域名连通性排查清单（阻塞③）

按顺序逐项确认，**不需要改生产配置**，先收集信息：

1. 对方侧：`ping` / `nslookup tkassetplant.fnn.net.cn` 是否解析；`curl -v https://tkassetplant.fnn.net.cn/admin-api/tk/open/v1/generation/credits/balance` 看卡在哪一步（DNS / TCP / TLS / HTTP）。把出口公网 IP 一并给我方。
2. 我方 DNS：域名解析是否正确、是否只做了内网/hosts 解析。
3. 我方 Nginx（`deploy/nginx/tk-auto-mix.conf`）：`server_name` 是否含该域名、证书是否有效、是否只监听内网。
4. 我方防火墙/安全组：443 是否对公网（或对方出口 IP）开放。
5. 双方确认后：把对方出口 IP 加进 `tk_open_api_client.allowedIps`（否则签名正确也会 403 `OPEN_API_IP_NOT_ALLOWED`）。
6. 若域名必须走内网/VPN：明确告知对方接入方式（专线/白名单 IP/跳板），并写进对接文档。

---

## 6. 工作量与顺序建议

| 阶段 | 内容 | 前置 |
| --- | --- | --- |
| 第 0 天 | 发 clientId（§4）+ 域名排查（§5） | 无，与开发并行 |
| P0 开发 | D1/D2 改造 + 8 个端点 + 幂等表 + 错误码 + 测试 | D1 租户绑定方案确认 |
| P0 验收 | `mvn -pl yudao-module-tk -am` 通过；用真实 clientId 端到端跑通 查积分→选库→选音色→选 BGM→precheck→create→轮询→取成片 | 部署到目标环境 |
| P1 | tags、retry、cancel、publish、回调 | P0 上线 |

验收口径：对接方全程不碰网页，凭 clientId + 签名完成一次完整出片；任一失败可读到字符串错误码与原因。
