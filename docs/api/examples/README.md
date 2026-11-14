# 自动出片完整调用示例

提供两份功能等价的服务端示例，任选其一：`automation.py`（Python 3.10+，标准库）或 `automation.mjs`（Node.js 20+，内置 fetch），不需要安装第三方包。

这两份示例调用账号 Token 接口 `/admin-api/tk/automation/v1`。TikTok 发布接口另用 HMAC 签名，不能混用凭证。示例不会上传或发布到 TikTok。

## 1. 准备账号和环境

请先开通自己的账号和租户，并准备可访问的电商素材库、可用音色和当前账号自己上传的 BGM。账号需要 `tk:generation:query`、`tk:generation:create`、`tk:material-library:query` 权限。

`BASE_URL` 只填域名，不附加 `/admin-api`。`TENANT_ID` 是租户 ID，不是账号 ID；可向平台取得，或通过 `GET /admin-api/system/tenant/get-id-by-name?name=<URL编码租户名>` 查询。不得照抄别人的资源 ID 或租户 ID。

Linux / macOS 环境变量示例：

```bash
export BASE_URL='https://clipforgestudio.fnn.net.cn'
export TENANT_ID='你的租户ID'
export TK_ACCESS_TOKEN='你的访问Token'
export TK_REFRESH_TOKEN='你的刷新Token'
export TK_TOKEN_FILE="$HOME/.clipforge/your-tenant-session.json"
```

PowerShell 环境变量示例：

```powershell
$env:BASE_URL = 'https://clipforgestudio.fnn.net.cn'
$env:TENANT_ID = '你的租户ID'
$env:TK_ACCESS_TOKEN = '你的访问Token'
$env:TK_REFRESH_TOKEN = '你的刷新Token'
$env:TK_TOKEN_FILE = Join-Path $env:USERPROFILE '.clipforge/your-tenant-session.json'
```

账号登录方式：不设置 `TK_ACCESS_TOKEN` / `TK_REFRESH_TOKEN`，改为设置 `TK_USERNAME`、`TK_PASSWORD`；原环境开启验证码时，还必须提供按原验证码流程获取的有效 `TK_CAPTCHA_VERIFICATION`。脚本不会绕过验证码，也不保证首次登录能无人值守完成。

`.env.example` 只是变量清单，两份脚本都不自动读取 `.env`。请通过操作系统或你自己的密钥管理工具设置环境变量，不要将真实凭证写入脚本。

登录及 Token 刷新会保存最新会话到 `TK_TOKEN_FILE`（默认 `~/.clipforge/session.json`），便于下次继续使用。不同租户或域名必须使用不同文件。POSIX 新文件使用 `0600` 权限；Windows 请限制该目录的 ACL。不要将 Token 文件、真实 `.env` 或生成的视频提交到 Git。已有环境变量优先于文件：首次保存后应移除旧的 `TK_ACCESS_TOKEN` / `TK_REFRESH_TOKEN` 变量，后续从会话文件读取，避免反复使用已轮换凭证。

## 2. 查询自己能用的资源

```bash
python automation.py resources --library-name 芒果干
# 或：
node automation.mjs resources --library-name 芒果干
```

命令返回积分、素材库 `materialLibraries`、音色 `voices` 和 BGM `bgms`。素材库列表自动分页；超过示例上限时需按名称筛选。此命令不提交生成任务。

- 从素材库选择正确 `libraryId`；同名库有多个时确认自己要使用的 ID。
- 音色返回 `voiceCode` 时提交它及对应 `ttsProvider`；返回 `voiceProfileId` 时提交该 ID 及对应 `ttsProvider`，并删除 `voiceCode`。二者只能选一个；MiMo 必须用 `voiceProfileId`。
- 从当前账号自有上传曲目中选择 `bgmAssetId`。示例会检查该 ID 是否在当前账号可用列表中，但不会从口播正文推断人群、自动随机选曲或跨人群回退。

人群选择规则（由调用方选择，非平台硬编码）：

| 人群 | 音色优先顺序 | BGM 同人群池 |
| --- | --- | --- |
| 精致妈妈 | 温柔学姐 | 温馨居家 01–12 |
| 新锐白领 | 温柔学姐 | 办公室下午茶 01–12 |
| 资深中产 | 沉稳高管 → 温柔学姐 | 品质感 01–11 |
| 小镇青年 | 霸道青年音色-beta → 温柔学姐 | 活泼日常 01–11 |

按 `name` / `fileName` 确认正确曲目后，在同人群池里选一首。缺少指定音色、回退音色或匹配 BGM 时应停止并补齐配置；不要借用其他人群的曲目。多人群批量任务应由调用方为每条任务分别保存资源选择和任务记录。

## 3. 准备请求并先本地校验

复制 `request.example.json` 为 `request.json`，替换所有 `REPLACE_WITH_...` 内容。`libraryId`、`bgmAssetId`、`voiceProfileId` 要填 JSON 数字（无引号），不是示例字符串。填写自己的完整口播正文，最长 3000 字；可选 `title` 最长 128 字，不会进入口播。

```bash
python automation.py create --request request.json --state mango-01.json --output mango-01.mp4 --dry-run
# 或：
node automation.mjs create --request request.json --state mango-01.json --output mango-01.mp4 --dry-run
```

`--dry-run` 只校验本地参数，不登录、不调用接口、不验证资源权限、不创建任务或扣积分。当前规格为无翻译、CN、完整口播时长、1080×1920/30fps；BGM 默认 `0.4` 表示 40%。接口暂不提供标签抽画面、幂等提交或 Webhook。

## 4. 显式提交生成任务

确认口播、音色、BGM、积分后，执行不带 `--dry-run` 的 `create`。**这一步会创建真实生成任务，沿用平台积分冻结和结算规则。**

```bash
python automation.py create --request request.json --state mango-01.json --output mango-01.mp4
# 或（不要对同一任务再运行另一份脚本 create）：
node automation.mjs create --request request.json --state mango-01.json --output mango-01.mp4
```

流程为：验证可用资源 → 独占创建任务记录 → POST 一次生成接口 → 保存 `taskId` → 查询进度 → 获取下载地址 → 流式写入 MP4。两份程序都判断 JSON `code=0`，不会把 HTTP 200 单独当作成功。积分不足、无权限或生成失败会停止并显示业务错误。

任务记录在 POST 前以 `SUBMITTING` 状态落盘，因此即使请求超时、响应丢失或进程中断，也不会自动重新提交。已经存在的 `--state` 文件一律拒绝再次 `create`。每条确定的新任务请使用不同的 `--state`；不要为绕过保护而删除不明状态的旧文件。

对于明确的 401 鉴权拒绝，程序最多刷新一次 Token 后重发；创建请求遇到网络错误、5xx 或业务失败不会自动重试。查询遇到其他错误也会停止，保留记录供继续操作。需要更复杂的查询重试时，可在自己的应用中做有限指数退避，不能把该规则应用到响应不明的创建请求。

## 5. 查询续跑与下载

默认每 3 秒查询，最多等待 900 秒。此间隔是建议值，不代表服务端额定限流或并发承诺。`SUCCESS` 且 `completed=true` 才下载；失败展示 `failCode` / `failReason`。逐字字幕质量诊断会原样显示，不能只因提交开关为 true 就认定字幕质量验收通过。

```bash
python automation.py resume --state mango-01.json --output mango-01.mp4 --timeout 900 --poll-interval 5
# 或：
node automation.mjs resume --state mango-01.json --output mango-01.mp4 --timeout 900 --poll-interval 5
```

`resume` 只查询已有任务并下载，绝不会创建新任务。等待超时、暂时网络错误或程序重启后，使用它继续。轮询等待时长不是服务器任务的超时配置；单次 HTTP 请求另有网络超时，结束可能略晚于轮询等待期限。

如果记录只有 `SUBMITTING` 没有 `taskId`，先在网页生成记录或现有 `/admin-api/tk/generation/page` 中按标题、提交时间核对任务；确认对应任务后，将它的真实 `taskId` 加入记录，再 `resume`。无法确认时联系平台，不能盲目再 POST。

下载使用单独 HTTP 请求，不携带平台 `Authorization` 或 `tenant-id`，并拒绝重定向。生产地址和下载地址要求 HTTPS（只允许本机测试使用 HTTP）。文件先写入 `<output>.part`，成功后改名；已存在的 MP4 不会被覆盖。下载中断后检查并移除本次残留 `.part` 文件，再 `resume` 获取新的下载地址。不能把“获取到下载地址”当作文件已经保存成功。

## 6. 验证范围

示例在本地 HTTP 服务上验证提交、鉴权刷新、资源匹配、进度与下载，未使用真实账号进行收费生成。接入自己的环境时，还需确认素材库、音色、BGM 和生产权限。

仓库内运行测试（不会联网）：

```bash
python -m unittest discover -s docs/api/examples/tests -p "test_*.py"
node --test docs/api/examples/tests/automation.test.mjs
```
