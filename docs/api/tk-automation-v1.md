# 自动出片 API v1

入口为 `https://clipforgestudio.fnn.net.cn/admin-api/tk/automation/v1`。使用已有账号 Token，继承当前账号的租户/素材/任务访问权限。它与 TikTok HMAC Open API 是两套鉴权入口，不能混用凭证。字段以本页及 [OpenAPI 契约](tk-platform-openapi.json) 为准，目标环境和权限由管理员确认。

首次接入先看 [快速开始](quickstart.md)，异常和重试看 [调用规则](calling-rules.md)。本页不包含千川上传、入计划接口。

## 1. 登录及保持会话

先调用 `GET /admin-api/system/tenant/get-id-by-name?name=<URL编码的租户名称>`，成功响应 `data` 为租户 ID，`null` 表示未找到。此查询不需已有 Token。

`POST /admin-api/system/auth/login`，JSON：`{"username":"YOUR_LOGIN_NAME","password":"YOUR_PASSWORD"}`。

必须使用实际的数字/字母登录账号，不能用显示昵称代替。用户名长度 4–30、只允许字母和数字；密码长度 4–16。响应 `data` 含 `userId`、`accessToken`、`refreshToken`、`expiresTime`（默认 Unix 毫秒时间戳）。请求需携带该用户所属租户的 `tenant-id`。如环境开启验证码，遵循原登录流程提交有效 `captchaVerification`，本功能不绕过验证码。

后续 API 请求头：

```http
Authorization: Bearer <accessToken>
tenant-id: <所属租户ID>
Content-Type: application/json
```

在 `expiresTime` 前调用 `POST /admin-api/system/auth/refresh-token?refreshToken=<URL编码后的刷新令牌>`，保存返回的新令牌和到期时间。刷新失效时重新授权登录。凭证保存在调用工具的密钥配置中。

当前账号需具有 `tk:generation:query`、`tk:generation:create`、`tk:material-library:query` 权限。管理员权限不能替代资源所属租户校验。

## 2. 出片顺序和接口

下表除登录外统一前缀 `/admin-api/tk/automation/v1`。

| 方法 | 路径 | 输入 | 返回 |
| --- | --- | --- | --- |
| GET | `/credits` | 无 | `tenantId,totalCredits,remainingCredits,frozenCredits,warningThreshold,lowBalance` |
| GET | `/material-libraries` | `name=芒果干&pageNo=1&pageSize=20` | `list,total`，列表包含素材库 `id,name,materialPurpose` 等 |
| GET | `/voices` | 无 | `name,ttsProvider,voiceCode,voiceProfileId,language`，仅可用音色 |
| GET | `/bgms` | 无 | 当前账号自己上传的可用曲目 `id,name,fileName,sourceType` |
| POST | `/tasks` | 下方 JSON | `taskId,status,progress,completed,failed` |
| GET | `/tasks/{taskId}` | 正整数任务号 | 状态、当前步骤、进度、失败原因，成功时可含 `downloadUrl` |
| GET | `/tasks/{taskId}/download` | 可选 `fileName=约定名称.mp4` | `taskId,fileName,downloadUrl` |

先通过素材库查询返回的真实 ID 选择“芒果干”；同名库有多个时明确选择，不固定示例 ID。当前版本使用电商手动文案模式和素材库全池随机混剪；素材需满足现有生成预检。积分查询是快照，提交时仍由原有服务原子冻结/结算积分，不够积分返回业务错误，创建或生成失败沿用现有退款逻辑。

### 2.1 查询参数

`credits`、`voices`、`bgms` 没有业务查询参数。`material-libraries` 支持：

| 参数 | 类型 | 默认/限制 | 说明 |
| --- | --- | --- | --- |
| `pageNo` | integer | 默认 1，最小 1 | 页码 |
| `pageSize` | integer | 默认 10，1–200 | 每页条数；遍历所有页 |
| `name` | string | 可选 | 名称筛选，例如芒果干 |
| `companyId` | int64 | 可选 | 公司筛选，仍受资源权限限制 |
| `category` | string | 可选 | 分类 |
| `materialPurpose` | string | 可选 | 用途，例如 `ECOMMERCE` |
| `status` | integer | 可选 | 素材库状态筛选 |

### 2.2 查询响应字段

下表均指成功响应中的 `data`；可选字段可能为 `null` 或省略，调用端应兼容。

| 接口 | data 结构 |
| --- | --- |
| 积分 | `tenantId,totalCredits,remainingCredits,frozenCredits,warningThreshold` 为整数，`lowBalance` 为布尔值；剩余积分不足时创建明确报错 |
| 素材库 | 对象：`list` 数组、`total` 总条数。每项含 `id,name,tenantId,companyId,category,scene,materialPurpose,tags,description,coverUrl,previewVideoUrl,videoCount,totalSize,defaulted,status,createTime`；时间默认 Unix 毫秒 |
| 音色 | 数组：每项含 `name,ttsProvider,voiceProfileId,voiceCode,language`。只返回启用且 READY 的租户音色及可用系统音色 |
| BGM | 数组：每项含 `id,name,fileName,sourceType`，仅当前账号可用的自有上传；按文件名或显示名识别曲目 |

素材库的 `tags` 是已有标签文本，不代表本版支持通过生成参数按标签抽取画面。

## 3. 音色及 BGM 选择

音色列表若返回 `voiceProfileId`，提交该 ID 和对应 `ttsProvider`；系统音色提交 `voiceCode` 和对应 `ttsProvider`。两个音色标识只能选一个。MiMo 使用返回的 `voiceProfileId`。名称缺失时按下表回退；连回退音色也没有则停止，不随意换音色。

| 人群 | 音色优先顺序 | BGM 同人群池 |
| --- | --- | --- |
| 精致妈妈 | 温柔学姐 | 温馨居家 01–12 |
| 新锐白领 | 温柔学姐 | 办公室下午茶 01–12 |
| 资深中产 | 沉稳高管 → 温柔学姐 | 品质感 01–11 |
| 小镇青年 | 霸道青年音色-beta → 温柔学姐 | 活泼日常 01–11 |

由调用工具依据 `name` / `fileName` 识别同人群、正确编号的曲目池，再随机选一首并提交 `bgmAssetId`。若没有匹配曲目，停止并提示缺少 BGM；不要借用其他人群音乐。服务端不会从正文推断人群或自动跨池选曲。BGM 必须是当前账号自有上传。

## 4. 创建任务示例

以下 ID / voiceCode 均需替换为查询到的真实值。

```json
{
  "libraryId": 123,
  "title": "芒果干_精致妈妈_01",
  "scriptText": "这里填写完整、可直接朗读的口播正文。",
  "ttsProvider": "MINIMAX",
  "voiceCode": "查询到的温柔学姐编码",
  "bgmAssetId": 456,
  "bgmVolume": 0.4,
  "translationEnabled": false,
  "targetRegion": "CN",
  "durationMode": "FOLLOW_NARRATION",
  "width": 1080,
  "height": 1920,
  "fps": 30,
  "subtitleEnabled": true,
  "subtitleFontSize": "extra_large",
  "subtitleStyle": "classic_white",
  "subtitlePositionMode": "smart_safe",
  "subtitleKeywordEnabled": true,
  "subtitleKaraokeEnabled": true
}
```

正文必填，最长3000字符，直接用于配音，不调用 AI 改写；人群画像和结构说明不要写入正文。可选 `title` 最长128字符，不会混入口播。字幕等参数省略时使用示例默认值；必填字段为 `libraryId,scriptText,ttsProvider,bgmAssetId` 和一个音色标识。音量范围为0–1，`0.4` 表示40%，使用混音版本2，不使用旧版30%上限。

### 4.1 创建参数

| 字段 | 类型 | 必填 | 默认与限制 |
| --- | --- | --- | --- |
| `libraryId` | int64 | 是 | 正整数，来自素材库列表 |
| `scriptText` | string | 是 | 非空白，最长 3000 字符 |
| `title` | string | 否 | 最长 128 字符；建议用来核对提交记录 |
| `ttsProvider` | string | 是 | `MINIMAX` / `DASHSCOPE` / `MIMO`，与选定音色一致 |
| `voiceProfileId` | int64 | 二选一 | 正整数；MiMo 必须用此字段 |
| `voiceCode` | string | 二选一 | 系统音色编码，非空白；与 `voiceProfileId` 只提交一个 |
| `bgmAssetId` | int64 | 是 | 正整数，当前账号自有上传 BGM |
| `bgmVolume` | number | 否 | 默认 `0.4`，范围 0–1 |
| `translationEnabled` | boolean | 否 | 默认且仅允许 `false` |
| `targetRegion` | string | 否 | 默认且仅允许 `CN` |
| `durationMode` | string | 否 | 默认且仅允许 `FOLLOW_NARRATION` |
| `width` / `height` / `fps` | integer | 否 | 默认且仅允许 `1080` / `1920` / `30` |
| `subtitleEnabled` | boolean | 否 | 默认 `true` |
| `subtitleFontSize` | string | 否 | 默认且仅允许 `extra_large` |
| `subtitleStyle` | string | 否 | 默认且仅允许 `classic_white` |
| `subtitlePositionMode` | string | 否 | 默认且仅允许 `smart_safe` |
| `subtitleKeywordEnabled` | boolean | 否 | 默认 `true` |
| `subtitleKaraokeEnabled` | boolean | 否 | 默认 `true`；质量需查询诊断字段 |

省略可选字段才使用默认值；不要显式传 `null` 来启用默认值。使用租户音色时删除 `voiceCode`，改传列表返回的 `voiceProfileId` 与 `ttsProvider`。

本版本仅支持关闭翻译、中国大陆、跟随口播、竖屏1080×1920和30fps；提供其他值会返回参数错误。字幕样式固定为示例值，开关可设为 false。当前不提供标签抽画面、固定秒数或其他输出规格接口。

系统先取得完整配音时长规划素材，最终合成按实际配音时长结束，不强制15、29或35秒（编码可能有帧/音频采样精度偏差）。系统硬上限最大500秒，当前环境配置可能更低；超过有效上限或无法识别配音时长时，任务明确失败，避免静默截断。

## 5. 进度、失败和下载

建议每3–5秒查询一次任务，遇到临时网络错误退避重试查询。`completed=true` 对应 `SUCCESS`，`failed=true` 对应 `FAILED`；其余状态继续等待。`progress` 是处理阶段进度，不能仅凭100判断成功。

```json
{
  "code": 0,
  "msg": "",
  "data": {
    "taskId": 789,
    "status": "FAILED",
    "progress": 100,
    "currentStep": "生成失败",
    "currentStepCode": "FAILED",
    "completed": false,
    "failed": true,
    "failCode": "具体失败代码",
    "failReason": "可读失败原因",
    "downloadUrl": null
  }
}
```

成功后调用 `/tasks/789/download?fileName=<URL编码的约定名称.mp4>`，下载返回地址中的字节并保存为 `fileName`。文件名最长128字符，不能含路径、换行等控制字符。地址沿用现有存储签名/有效期，过期后重新调用下载接口获取。输出文件下载必须以已授权任务为来源。

任务响应还包含 `subtitleKaraokeStatus,subtitleKaraokeReason`，透传现有字幕质量诊断；如逐字卡点降级，调用方需明确提示，不能把仅成功开启请求开关视为已完成逐字字幕验收。

### 5.1 任务响应字段

创建与查询任务都返回 `TaskResp`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `taskId` | int64 | 任务号，创建后立即保存 |
| `status` | string | `SUCCESS` 成功、`FAILED` 失败；其余表示处理中 |
| `progress` | integer | 阶段进度，可能为空；不代表媒体验收 |
| `currentStep` / `currentStepCode` | string | 当前步骤说明/代码，可能为空 |
| `completed` / `failed` | boolean | 终态标记 |
| `failCode` / `failReason` | string | 失败代码/原因，非失败时可能为空 |
| `subtitleKaraokeStatus` / `subtitleKaraokeReason` | string | 逐字字幕诊断，可能为空或表示降级 |
| `downloadUrl` | string | 成功时可能提供的临时成片地址；也可通过下载接口获取 |

`taskId` 路径参数必须为正整数。任务归属会再次检查，不能查询其他账号无权访问的任务。

### 5.2 下载响应示例

```json
{
  "code": 0,
  "msg": "",
  "data": {
    "taskId": 789,
    "fileName": "video-789.mp4",
    "downloadUrl": "https://storage.example.invalid/video-789.mp4?signature=EXAMPLE"
  }
}
```

上方 URL 仅示意，不可用于下载。省略 `fileName` 默认 `video-{taskId}.mp4`；自定义名必须以 `.mp4` 结尾，拒绝控制字符及 `/ \ : " < > | ? *`。成功后访问 `data.downloadUrl` 获取文件；不要向存储地址转发平台 Token。

## 6. 错误和联调边界

成功以响应 JSON 的 `code=0` 为准，不仅检查 HTTP 状态。原平台部分业务错误通过 HTTP 200 内的非零 code 返回；令牌、权限、参数和业务错误均读取 `code,msg`。`1060000029` 表示积分不足；失败任务原因通过 `failCode,failReason` 获取。未完成任务获取下载地址返回业务 `code=409`。

首次登录还受原有验证码配置约束；本接口不提供免密、绕过验证码或永久会话。没有新增账号绑定的 API Key/HMAC 剪辑鉴权。

当前创建接口不支持 `Idempotency-Key` 去重。创建响应超时/丢失时不要盲目再次 POST，以免重复生成/扣分；可通过现有 `/admin-api/tk/generation/page` 按任务标题及时间核对，确认没有创建后再提交。查询和下载信息接口可重试。标签、幂等和 Webhook 可后续扩展。

目标租户的素材、音色、BGM及语音/字幕/存储配置应在接入时确认。接口提交成功后仍应检查最终成片及字幕诊断，不要将示例资源名视为每个环境都已配置。

## 7. 调用示例与可导入文件

[快速开始](quickstart.md) 提供登录、资源查询、创建、查询、下载及刷新 cURL；[Postman 集合](tk-automation.postman_collection.json) 配合 [环境模板](tk-automation.postman_environment.json) 导入。默认 `allowPaidCreate=false`，只读查询与登录不触发生成；核对参数并显式开启后才允许发送一次创建请求。

自动化脚本见 [Python/Node.js 示例](examples/README.md)，完整接口契约见 [API 总览](tk-platform-api.md)、[OpenAPI JSON](tk-platform-openapi.json)。
