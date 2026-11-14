# GPT 剪辑工具出片需求 — API 适配评估与对接清单

> 评估对象：GPT 提出的 8 项必需能力 + 1 项加分能力
> 评估基准：当前仓库源码（2026-11-11）与 `docs/api/tk-platform-api.md` v2.0
> 结论性质：源码事实核对，非推测；**未修改任何业务代码**

---

## 0. 一句话结论

**可以点头，但不能只带着这 9 条去点头。**

我上一份文档里的 22 个 HMAC 开放端点，管的是「TikTok 授权 → 视频上传 → 发布 → 指标」，**这 9 项一项都不覆盖**。这 9 项能力在源码里几乎全部已经实现，但全部挂在 `/admin-api/tk/**` 的**后台管理 API** 下，需要后台账号、租户、菜单权限，且受 API 加密影响。

所以准确的说法是：

| 场景 | 结论 |
| --- | --- |
| 官方只肯给现有开放 API（我文档里那套） | **不满足，不能点头** |
| 允许工具用赵倩的后台账号直连 `/admin-api/tk/**` | **现在就能跑通 7 项**，2 项需绕、1 项（加分）做不了；仅适合内部自动化 |
| 官方愿意把后台生成能力包成 HMAC 开放接口（第 5 节清单） | **推荐，可点头**，工作量是「包装 + 鉴权改造」，不是重做业务 |

对接时务必把「**开放混剪生成接口**」写成明确要求，否则默认交付的就是那套只管发布的开放 API。

---

## 1. 九项需求逐条判定

图例：✅ 现成可用 · ⚠️ 有条件可用（需指定参数或自行绕过） · ❌ 当前不具备

| # | 需求 | 现有后台端点 | 判定 | 关键说明 |
| --- | --- | --- | --- | --- |
| 1 | 登录与账号（赵倩）、保持会话 | `POST /admin-api/system/auth/login`、`POST /admin-api/system/auth/refresh-token`、`GET /admin-api/system/tenant/get-id-by-name` | ⚠️ | 返回 `accessToken`、`refreshToken`、`expiresTime`；后续带 `Authorization: Bearer` 与 `tenant-id`。绑定个人账号 + 菜单权限 + AES 加密，不适合外部工具 |
| 2 | 查积分、不足明确报错 | `GET /admin-api/tk/credit/balance` | ✅ | 返回 `remainingCredits`、`frozenCredits`、`lowBalance`；创建任务前 `freezeForGenerationTask` 抛 `TK_CREDIT_NOT_ENOUGH`（1-060-000-029），**不会静默失败** |
| 3 | 列出素材库、指定「芒果干」 | `GET /admin-api/tk/material-library/page?name=芒果干` | ✅ | 返回 `id`、`name`、`scene`、`tags`、`videoCount`；把 `id` 作为 `libraryId` 传给生成接口 |
| 4 | 列出可用音色 | `GET /admin-api/tk/voice/minimax/options`、`GET /admin-api/tk/voice-profile/list` | ✅ | 返回 `value`／`label`／`voiceId`／`unavailable`；三个指定音色均在种子数据里。人群→音色优先级由工具侧判定，需过滤 `unavailable=true` |
| 5 | 列出「我的上传」BGM | `GET /admin-api/tk/bgm/list`（筛 `sourceType="USER"`）、`GET /system-list` | ⚠️ | 返回 `name`、`sourceType`、`style`、`fileUrl`，**没有独立 `fileName` 字段**，也没有「我的上传」专用端点；曲名池 01–12 靠 `name` 前缀匹配 |
| 6 | 一次传入全部生成参数 | `POST /admin-api/tk/generation/create`（配 `POST /precheck`） | ⚠️ | 口播、关翻译、音色、BGM、字幕五项、画幅帧率**全部有字段**；但「目标地区」无独立字段、「时长严格跟随口播」无开关、「40% 音量」需显式指定混音版本，详见第 3 节 |
| 7 | 查进度、步骤、成败与失败原因 | `GET /admin-api/tk/generation/get?id=`、`GET /admin-api/tk/generation/status-batch?ids=` | ✅ | 返回 `id`、`status`、`progress`、`currentStep`、`currentStepCode`、`currentStepCompleted`／`currentStepTotal`、`failCode`、`failReason`；批量最多 50 个 |
| 8 | 下载成片 | `GET /admin-api/tk/generation/get?id=` 的 `outputUrl` | ✅ | 返回带 `Expires` 与 `OSSAccessKeyId` 的 OSS 签名地址，`response-content-disposition` 已带建议文件名 `YYYYMMDD-创建人-序号.mp4`；工具也可自行按约定命名保存 |
| 9（加分） | 按标签抽画面（场景／产地／口感） | 无 | ❌ | 素材视频有 `tags` 自由文本，但混剪规划器只按 `segmentType`（S1–S7）和 `usagePhase` 选片，`/material-video/page` 也不支持 `tags` 筛选；只能走 `clipPlanMode="FULL_POOL_RANDOM"` 全库随机混剪 |

**合计：5 项 ✅、3 项 ⚠️、1 项 ❌。**

---

## 2. 逐项证据与参数落点

### 2.1 登录与会话

`POST /admin-api/system/auth/login` 请求体 `{ "username": "...", "password": "..." }`（明文，开启验证码时补 `captchaVerification`），响应 `data` 含 `accessToken`、`refreshToken`、`userId`、`expiresTime`。租户 ID 由 `GET /admin-api/system/tenant/get-id-by-name?name=<租户名>` 换取。会话续期用 `POST /admin-api/system/auth/refresh-token?refreshToken=...`。

赵倩账号需具备：`tk:generation:create`、`tk:generation:query`、`tk:material-library:query`、`tk:credit:balance` 所需的 `tk:dashboard:query`（三者之一即可）。

> 源码位置：`TkCreditController.java` 第 34 行；`TkGenerationTaskController.java` 第 103／112／119 行。

### 2.2 积分与失败语义

`TkCreditBalanceRespVO` 字段：`tenantId`、`totalCredits`、`remainingCredits`、`frozenCredits`、`warningThreshold`、`lowBalance`。
创建任务时 `TkGenerationTaskServiceImpl.createGenerationTask` 先跑预检，再调用 `creditService.freezeForGenerationTask(tenantId)`；积分不足抛 `TK_CREDIT_NOT_ENOUGH = 1_060_000_029`「租户积分余额不足，本次操作需要 {} 积分，请联系客服充值」，任务不会落库。异常时会 `refundByLogId` 回滚冻结。

### 2.3 素材库与「芒果干」

`TkMaterialLibraryPageReqVO` 支持 `companyId`、`name`、`category`、`materialPurpose`、`status` 过滤；`TkMaterialLibraryRespVO` 返回 `id`、`name`、`category`、`scene`、`tags`、`videoCount`、`totalSize`、`coverUrl`。指定「芒果干」= 按 `name` 查出 `id`，再作为必填 `libraryId` 提交。

### 2.4 音色

`TkMiniMaxVoiceOptionRespVO` 字段：`value`、`label`、`voiceId`、`model`、`language`、`country`、`gender`、`previewUrl`、`favorite`、`isDefault`、`unavailable`。指定音色在 `yudao-module-tk/src/main/resources/sql/tk_minimax_voice_catalog_upgrade_mysql.sql`：

| 前端名称 | voiceId |
| --- | --- |
| 温柔学姐 | `Chinese (Mandarin)_Gentle_Senior` |
| 沉稳高管 | `Chinese (Mandarin)_Reliable_Executive` |
| 霸道青年音色-beta | `male-qn-badao-jingpin` |

提交时传 `voiceCode`（取 `value` 或 `voiceId`，按后台实际映射）或自定义音色的 `voiceProfileId`。

### 2.5 已上传 BGM 与人群池

`TkBgmAssetRespVO` 字段：`id`、`name`、`sourceType`、`style`、`fileUrl`、`duration`、`format`、`status`、`canDelete`、`referencedPresetCount`。`TkBgmAssetServiceImpl.uploadUserBgm` 写入 `sourceType="USER"`，系统曲为 `"SYSTEM"`。

⚠️ 两个必须提前说清的点：
1. **没有 `fileName` 字段**。上传接口的 `name` 是调用方自定义名称，`fileUrl` 里含对象文件名。若工具要求「至少返回文件名」，需官方补字段，或约定用 `name`。
2. **人群池不是接口能力**。「温馨居家 01–12」「品质感 01–11」「办公室下午茶 01–12」「活泼日常 01–11」是运行时数据命名，官方不保证已按此命名入库；工具侧按 `name` 前缀匹配并限定同池随机，属于客户端规则。

### 2.6 生成任务的完整参数落点

`TkGenerationTaskCreateReqVO`（`POST /admin-api/tk/generation/create`）与需求的对应关系：

| 需求 | 字段 | 取值 |
| --- | --- | --- |
| 完整口播正文 | `promptText` | 原文；配 `scriptMode="MANUAL"` |
| 关闭翻译 | `manualTranslationEnabled` | `false` |
| 目标地区中国大陆 | `targetLanguage` | `"zh-cn"`（映射国家 `CN`）；**无独立 `regionCode`** |
| 指定素材库 | `libraryId` | 第 3 项查到的 ID，必填 |
| 音色 | `voiceCode` 或 `voiceProfileId` | 第 4 项选定值 |
| BGM | `bgmEnabled`、`bgmAssetId` | `true` + 第 5 项选定 ID |
| BGM 音量 40% | `bgmVolume`、`audioMixVersion` | `0.4` **且必须** `audioMixVersion=2` |
| 智能字幕开关 | `subtitleEnabled` | `true` |
| 特大字号 | `subtitleFontSize` | `"extra_large"` |
| 标准白字黑边 | `subtitleStyle` | `"classic_white"`（也是默认值） |
| 智能避让 | `subtitlePositionMode` | `"smart_safe"`（也是默认值） |
| 关键词高亮 | `subtitleKeywordEnabled` | `true` |
| 逐字卡点 | `subtitleKaraokeEnabled` | `true` |
| 时长 | `referenceDuration` | 8–500 秒任意整数，默认 15 |
| 画面方向 | `clipPlanMode` | `"FULL_POOL_RANDOM"`（全库随机）或 `"SEGMENTED"`（按 S1–S7 结构） |
| 竖屏 1080×1920 | 无需传参 | 渲染服务固定 `scale=1080:1920` |
| 30 帧 | 无需传参 | 渲染服务固定 `-r 30` |

源码依据：`DefaultTkVideoRenderService.java` 第 38–39 行与第 175／194／210 行；`TkGenerationTaskServiceImpl.java` 第 487–497 行；`TkVideoDurationSupport.java` 第 5–7 行；`DefaultTkAssSubtitleRenderService.java` 第 450 行（`extra_large`）、`DefaultTkSubtitleLayoutService.java` 第 25 行（`smart_safe`）。

**口播正文不会被污染**：`DefaultTkScriptGenerationService.generateScript` 在手动电商模式下直接 `return new TkGeneratedScript(标题, task.getPromptText().trim(), ...)`，不拼画像、结构说明、素材库标签。但注意 `manualOriginalText` 有 `@Size(max=3000)` 校验，正文只放 `promptText`，不要同时塞 `manualOriginalText`。

### 2.7 进度与失败

`TkGenerationTaskStatusRespVO`：`id`、`title`、`status`、`progress`、`outputUrl`、`failReason`、`failCode`、`currentStep`、`currentStepCode`、`currentStepCompleted`／`currentStepTotal`、`heartbeatTime`、`stepStartedAt`／`stepFinishedAt`。状态枚举含 `PENDING`、`PRECHECKED`、`ANALYZING`、`SCRIPT_READY`、`VOICE_READY`、`MATERIAL_MATCHING`、`MATERIAL_MATCHED`、`SUBTITLE_TIMELINE_READY`、`VISUAL_ANALYZED`、`CLIP_PLANNED`、`RENDERING`、`EXPORTING`、`SUCCESS`、`FAILED`。

### 2.8 成片下载

`TkGenerationTaskController.refreshOutputUrls` 调用 `TkGenerationOutputStorageServiceImpl.refreshGeneratedAssetReadUrl` 重新签发：URL 带 `OSSAccessKeyId`、`Expires`、`Signature`、`response-content-disposition`（已含建议文件名）。若未配置 `readUrlExpireSeconds`，返回不带签名的公开地址 —— 这点要向官方确认，避免拿到会过期或长期公开的地址。

---

## 3. 四个必须提前钉死的坑

### 3.1 BGM 40% 会被静默降到 30%（最危险）

`TkAudioMixSupport.normalizeVolume(volume, version)`：
- `audioMixVersion` 为 `null` 或 `1`（默认）→ `Math.max(0.01, Math.min(0.30, volume))`，**0.4 被截成 0.30，无报错**；
- `audioMixVersion=2` → `Math.max(0, Math.min(1, volume))`，0.4 生效。

前端 `audioSettings.mjs` 保存预设时固定发 `audioMixVersion: 2`，但直接调 API 若漏传就会静默降级。**对接清单里必须写死「创建任务必须传 `audioMixVersion=2`」**，并要求官方在响应里回显实际生效音量。

### 3.2 「时长跟随口播结束即停」目前是「取较大值」，不是严格相等

`DefaultTkGenerationPipelineService.resolveEffectiveTargetDuration` 计算 `effectiveDuration = Math.max(targetDuration, ceil(bodyAudioDuration))`，即配音比设定时长长时会自动延长（8–500 秒范围内），**不会强制 15／29／35 秒**；但若口播短于 `referenceDuration`（默认 15 秒），视频仍会被补到设定时长。后端另有下限 8 秒、上限 500 秒的钳制。

要严格「口播结束即停」，二选一：
- 工具侧把 `referenceDuration` 设为下限 `8`，让口播时长几乎总是胜出（近似满足）；
- 要求官方新增 `durationMode="FOLLOW_AUDIO"`，渲染时不再按 `targetDuration` 补齐。

### 3.3 「目标地区中国大陆」没有独立字段

`TkGenerationTaskCreateReqVO` 只有 `targetLanguage`（`zh-cn` → 国家 `CN`），没有 `regionCode`。且 `TkGenerationRouteConfigSupport.withManualTranslationConfig` 在**关闭翻译时会移除** `manualTranslationTargetLanguage` 与 `manualTranslationTargetCountry`，地区信息不落库。若「目标地区」是硬性要求，需要官方新增 `targetRegion` 字段并在任务详情里回显。

### 3.4 逐字卡点可能降级

`subtitleKaraokeEnabled=true` 只是开关，实际效果受 ASR 时间轴质量影响，任务上有 `subtitleKaraokeStatus`、`subtitleKaraokeReason` 两个回读字段。工具应在任务完成后检查这两个字段，不能只看开关。

### 3.5（加分项）按标签抽画面确实没有

素材视频 `tags` 字段在上传时可写入，`TkMaterialVideoRespVO` 也会返回，但：
- `TkMaterialVideoPageReqVO` 只有 `libraryId`、`fileName`、`status`、`usagePhase`、`segmentType`，**没有 `tags` 筛选**；
- `TkGenerationTaskCreateReqVO` **没有任何标签筛选参数**；
- `DefaultTkClipPlannerService` 只按 `TkMaterialSegmentTypeEnum`（`S1_HOOK` 黄金3秒、`S2_PAIN` 痛点场景、`S3_REVEAL` 产品亮相、`S4_DEMO` 使用演示、`S5_PROOF` 效果证明、`S6_DETAIL` 细节特写、`S7_LIFESTYLE` 场景融入、`GENERAL`）与 `usagePhase`（`ATTENTION`／`PRODUCT_SHOW`／`RESULT_EFFECT`／`GENERAL`）选片。

结论：**「场景／产地／口感」这类业务标签目前无法控制画面方向**，只能退回到「从芒果干库里自行混剪」。要补，需官方加 `materialTags` 入参 + 规划器按标签过滤 + 素材分页按标签检索。

---

## 4. 后台直连版：可直接落地的请求示例

> 仅用于内部验证，不代表对外交付契约。所有 `id` 需由前置查询取得。

```http
POST /admin-api/tk/generation/create
Authorization: Bearer <accessToken>
tenant-id: <租户ID>
Content-Type: application/json
```

```json
{
  "libraryId": 12,
  "title": "芒果干-精致妈妈-01",
  "scriptMode": "MANUAL",
  "promptText": "这里放完整口播正文，不要写人群画像和结构说明",
  "manualTranslationEnabled": false,
  "targetLanguage": "zh-cn",
  "materialPurpose": "ECOMMERCE",
  "voiceEnabled": true,
  "voiceCode": "Chinese (Mandarin)_Gentle_Senior",
  "bgmEnabled": true,
  "bgmAssetId": 34,
  "bgmVolume": 0.4,
  "audioMixVersion": 2,
  "referenceDuration": 8,
  "clipPlanMode": "FULL_POOL_RANDOM",
  "subtitleEnabled": true,
  "subtitleStyle": "classic_white",
  "subtitleFontSize": "extra_large",
  "subtitlePositionMode": "smart_safe",
  "subtitleKeywordEnabled": true,
  "subtitleKaraokeEnabled": true
}
```

响应 `data` 为 `Long` 类型任务号。随后轮询 `GET /admin-api/tk/generation/get?id=<taskId>`，待 `status="SUCCESS"` 后取 `outputUrl` 下载，并按约定文件名保存；同时检查 `subtitleKaraokeStatus`／`subtitleKaraokeReason`。

> 建议先调 `POST /admin-api/tk/generation/precheck` 传同一份 body，读 `errors[]` 与 `warnings[]`（含素材时长不足、分段素材不足等提示），避免生成到一半才失败。

---

## 5. 建议提给官方的开放 API 清单（正式对接版）

沿用现有 HMAC 签名、`Idempotency-Key`、回调与 `clientId` 隔离模型，新增 `/admin-api/tk/open/v1/generation` 接口族：

| 方法 | 建议路径 | 对应需求 |
| --- | --- | --- |
| `GET` | `/credits/balance` | 2 查积分 |
| `GET` | `/material-libraries` | 3 列出素材库（含 `name`、`tags`、视频数） |
| `GET` | `/material-libraries/{id}/tags` | 9 列出可用画面标签（加分项） |
| `GET` | `/voices` | 4 列出音色（含 `unavailable`） |
| `GET` | `/bgm?sourceType=USER` | 5 只列「我的上传」，**必须返回 `fileName`** |
| `POST` | `/tasks/precheck` | 6 提交前预检（积分、素材时长、字幕可行性） |
| `POST` | `/tasks` | 6 一次传入全部生成参数（新增 `targetRegion`、`durationMode`、`materialTags`） |
| `GET` | `/tasks/{taskId}` | 7 任务号、状态、当前步骤、失败原因 |
| `GET` | `/tasks/{taskId}/result` | 8 成片签名下载地址 + 建议文件名 + 字幕降级原因 |
| `POST` | `/tasks/{taskId}/retry` | 7 失败重试 |
| `POST` | `/tasks/{taskId}/cancel` | 7 取消排队任务 |
| `POST` | `/tasks/{taskId}/publish` | 打通现有第 6 节发布 API（成片直发） |

配套硬要求（写进对接清单，避免静默降级）：

1. `bgmVolume` 原样生效并在详情里回显，不得钳到 0.30；
2. 新增 `durationMode="FOLLOW_AUDIO"`，成片时长严格等于口播音频时长；
3. 新增 `targetRegion`（如 `CN`），并在任务详情里回显；
4. BGM 列表返回 `fileName` 字段；
5. 积分不足返回独立错误码与所需积分数，不得静默失败；
6. 新增 `materialTags` 入参与标签检索，让「场景／产地／口感」能控制画面方向；
7. 任务完成回调 `generation.success` 携带成片地址，`generation.failed` 携带 `failCode`／`failReason`；
8. 生成按 `clientId` 隔离与计费，任务号用 opaque ID，成片地址为带过期时间的签名 URL。

---

## 6. 对接时直接问官方的 6 个问题

1. 这 9 项是走**现有开放 API**、走**后台账号直连**，还是愿意**新增生成类开放接口**？
2. 若走后台直连：赵倩账号的菜单权限、租户 ID、AES 加密开关与加密头名由谁提供？Token 过期后是否允许工具自动 refresh？
3. `bgmVolume=0.4` 能否原样生效？是否默认 `audioMixVersion=2`？
4. 能否保证「成片时长 = 完整口播结束」，口播短于设定时长时不补画面？
5. BGM「我的上传」列表是否返回原始文件名？人群池曲目当前是否已按 01–12 命名入库？
6. 是否提供按业务标签（场景／产地／口感）抽画面的能力？若没有，是否接受工具侧全库随机混剪作为兜底？

---

## 7. 最终判定

- **清单本身没问题**，按实际出片顺序组织，可以直接作为对接基线；
- **但按现状「直接点头」会踩坑**：现有开放 API 覆盖率为 0，9 项全在后台 API 下；
- **推荐动作**：带着第 5 节的开放接口清单 + 第 6 节的 6 个问题去对接，把「包装开放接口」作为前提条件。若官方只接受内部直连，则先用第 4 节做一次端到端验证，再谈长期方案。

---

> 本文是 `docs/api/tk-platform-api.md` 第 11 节「能力缺口与 API 化建议」的实测补充：第 11 节提出的是设计草案，本文给出了每一项需求对应的**已实现后台端点、字段落点与剩余缺口**。

> **对外发送提示**：本文含源码路径、内部端点、账号权限与内部研判，仅供内部使用。发给对接方请改用 [`tk-generation-api-requirements.html`](tk-generation-api-requirements.html)（或同名 `.md`）——纯净需求规格版，不含内部信息。若对方是要接入现有 TikTok 发布能力，则发 `tk-platform-api.html` + `tk-platform-openapi.json`。
