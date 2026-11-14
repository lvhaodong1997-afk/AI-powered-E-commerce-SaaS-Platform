# GPT 自动出片 API v1

接口已于 2026-11-12 部署到产品服务器，入口为 `https://clipforgestudio.fnn.net.cn/admin-api/tk/automation/v1`。新路由使用已有账号 Token，继承当前账号的租户/素材/任务访问权限。它与 TikTok HMAC Open API 是两套鉴权入口，不能混用凭证。部署记录见 [部署验收记录](tk-automation-v1-deployment-2026-11-12.md)。

## 1. 登录及保持会话

`POST /admin-api/system/auth/login`，JSON：`{"username":"实际登录账号","password":"账号密码"}`。

“赵倩”可能是昵称，必须使用该用户实际的数字/字母登录账号，不能直接假定昵称可登录。响应 `data` 含 `userId`、`accessToken`、`refreshToken`、`expiresTime`。请求需携带该用户所属租户的 `tenant-id`。如环境开启验证码，遵循原登录流程提交有效 `captchaVerification`，本功能不绕过验证码。

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

兼容端点：`GET /admin-api/tk/bgm/user-list` 也可查询自有上传 BGM；网页原 `/tk/bgm/list` 接口行为不变。

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

本版本仅支持关闭翻译、中国大陆、跟随口播、竖屏1080×1920和30fps；提供其他值会返回参数错误。字幕样式固定为示例值，开关可设为 false。当前不提供标签抽画面、固定秒数或其他输出规格接口。

系统先取得完整配音时长规划素材，最终合成按实际配音时长结束，不强制15、29或35秒（编码可能有帧/音频采样精度偏差）。超过当前后端配置上限（最大500秒）或无法识别配音时长时，任务明确失败，避免静默截断。

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

## 6. 错误和联调边界

成功以响应 JSON 的 `code=0` 为准，不仅检查 HTTP 状态。原平台部分业务错误通过 HTTP 200 内的非零 code 返回；令牌、权限、参数和业务错误均读取 `code,msg`。`1060000029` 表示积分不足；失败任务原因通过 `failCode,failReason` 获取。未完成任务获取下载地址返回业务 `code=409`。

首次登录还受原有验证码配置约束；本接口不提供免密、绕过验证码或永久会话。没有新增账号绑定的 API Key/HMAC 剪辑鉴权。

当前创建接口不支持 `Idempotency-Key` 去重。创建响应超时/丢失时不要盲目再次 POST，以免重复生成/扣分；可通过现有 `/admin-api/tk/generation/page` 按任务标题及时间核对，确认没有创建后再提交。查询和下载信息接口可重试。标签、幂等和 Webhook 可后续扩展。

真实账号“赵倩”、素材“芒果干”、各人群音色/BGM是否已配置，以及语音、字幕、FFmpeg、OSS的完整媒体链路，需在部署后的测试环境实际联调；单元/控制器测试不能代替真实成片验收。

## 7. 本次验证记录

2026-11-12 本地离线 Maven 构建完成编译，聚焦回归执行 135 项：134 项通过，1 项既有字幕测试失败。新增自动化 API、账号权限、资源筛选、积分错误、下载、时长模式及既有生成/混音相关测试通过。两处时长修复（请求复制保留模式、原生开头计入总时长）已经代码复核。

未通过项为 `DefaultTkAssSubtitleRenderServiceTest.highlightingPreservesChineseAndEnglishWrapping`，单独执行也可复现：测试辅助方法只取第一条 `Dialogue`，既有英文渲染会拆成多条事件，导致断言缺少末尾 `today`。该渲染器及测试与 Git HEAD 一致，本次未修改，不能声称全部回归通过。初次实现阶段未进行生产部署、真实账号登录或完整媒体出片验收。

后续按用户指令同步远端主分支并部署：基础版本 `25363c20`；发布回归 381 项中 380 项通过、1 项跳过，未纳入上述既有字幕失败测试。服务器版本、校验值、接口鉴权及公网检查结果见部署验收记录。仍未使用真实账号提交收费生成任务。
