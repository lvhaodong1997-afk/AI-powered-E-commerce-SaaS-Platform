# 快速开始：通过 API 完成一次出片

适用对象：使用已开通账号，通过自己的服务端或自动化工具提交完整口播、选择素材/音色/BGM，并下载成片的用户。自动出片使用 **账号 Token + tenant-id**，与 TikTok 发布接口的 HMAC 凭证不同。

## 1. 准备接入信息

向管理员取得产品地址、租户名称、实际登录用户名和密码，并开通 `tk:generation:query`、`tk:generation:create`、`tk:material-library:query`。用户名是登录账号，不是显示昵称。先确认租户内已有可用素材库、音色和当前账号自己上传的 BGM。

以下为 Bash/cURL 示例，Windows 可使用 Git Bash；PowerShell 请使用 `curl.exe` 并调整换行/环境变量语法。示例值均为占位值，响应中的 ID 才可用于后续请求。

```bash
export TK_BASE_URL='https://clipforgestudio.fnn.net.cn'
export TK_TENANT_NAME='<已开通租户名称>'
```

不要把真实密码、Token 或带签名的下载链接粘贴到共享文档、前端代码、Git 仓库中。

## 2. 查询租户并登录

```bash
curl -sS --get "$TK_BASE_URL/admin-api/system/tenant/get-id-by-name" \
  --data-urlencode "name=$TK_TENANT_NAME"
```

成功示例：`{"code":0,"data":1,"msg":""}`。将实际 `data` 设置为 `TK_TENANT_ID`；`data=null` 表示未找到，联系管理员确认名称。

```bash
export TK_TENANT_ID='<上一步返回的租户ID>'
```

在本机创建 `login.json`，填入自己的凭证：

```json
{"username":"YOUR_LOGIN_NAME","password":"YOUR_PASSWORD"}
```

```bash
curl -sS "$TK_BASE_URL/admin-api/system/auth/login" \
  -H "tenant-id: $TK_TENANT_ID" -H 'Content-Type: application/json' \
  --data-binary @login.json
```

若环境开启验证码，必须按原登录流程先取得有效 `captchaVerification`，将其加入登录 JSON。API 不绕过验证码，也不保证首次登录完全无交互。登录响应 `data` 含 `accessToken`、`refreshToken`、`expiresTime`（默认 Unix 毫秒时间戳）。保存令牌后清理本机明文凭证文件。

```bash
export TK_ACCESS_TOKEN='<登录返回的accessToken>'
export TK_REFRESH_TOKEN='<登录返回的refreshToken>'
```

后续每个出片请求都同时带 `Authorization: Bearer ...` 和 `tenant-id`。

## 3. 查询积分、素材库、音色和 BGM

```bash
curl -sS "$TK_BASE_URL/admin-api/tk/automation/v1/credits" \
  -H "Authorization: Bearer $TK_ACCESS_TOKEN" -H "tenant-id: $TK_TENANT_ID"

curl -sS --get "$TK_BASE_URL/admin-api/tk/automation/v1/material-libraries" \
  -H "Authorization: Bearer $TK_ACCESS_TOKEN" -H "tenant-id: $TK_TENANT_ID" \
  --data-urlencode 'name=芒果干' --data-urlencode 'pageNo=1' --data-urlencode 'pageSize=20'

curl -sS "$TK_BASE_URL/admin-api/tk/automation/v1/voices" \
  -H "Authorization: Bearer $TK_ACCESS_TOKEN" -H "tenant-id: $TK_TENANT_ID"

curl -sS "$TK_BASE_URL/admin-api/tk/automation/v1/bgms" \
  -H "Authorization: Bearer $TK_ACCESS_TOKEN" -H "tenant-id: $TK_TENANT_ID"
```

每次先判断 JSON `code=0`，再读取 `data`。素材库返回 `data.list` 和 `data.total`；分页默认 10 条，`pageSize` 最大 200，应遍历分页。选定真实 `libraryId`、`bgmAssetId` 和一个音色标识；同名库有多个时需要明确选择。

音色必须同时提交列表中的 `ttsProvider`。系统音色用 `voiceCode`，租户音色用 `voiceProfileId`，二选一；MiMo 必须用 `voiceProfileId`。BGM 仅从当前账号自己上传的曲目中选择。人群池及缺失时的处理见 [自动出片接口](tk-automation-v1.md)。

## 4. 提交生成任务（会使用积分）

创建本机 `create-task.json`，将下方示例 ID、音色编码替换成查询结果：

```json
{
  "libraryId": 123,
  "title": "芒果干_精致妈妈_01",
  "scriptText": "这里填写完整、可直接朗读的口播正文。",
  "ttsProvider": "MINIMAX",
  "voiceCode": "VOICE_CODE_FROM_LIST",
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

```bash
curl -sS "$TK_BASE_URL/admin-api/tk/automation/v1/tasks" \
  -H "Authorization: Bearer $TK_ACCESS_TOKEN" -H "tenant-id: $TK_TENANT_ID" \
  -H 'Content-Type: application/json' --data-binary @create-task.json
```

`code=0` 表示任务已提交，不代表成片已完成。立即保存 `data.taskId`。**创建没有幂等去重；网络超时/响应丢失后不可盲目重复 POST。** 先在网页生成记录中按标题、时间核对。查询接口可在退避后重试。

## 5. 查询到完成或失败

```bash
export TK_TASK_ID='<创建返回的taskId>'
curl -sS "$TK_BASE_URL/admin-api/tk/automation/v1/tasks/$TK_TASK_ID" \
  -H "Authorization: Bearer $TK_ACCESS_TOKEN" -H "tenant-id: $TK_TENANT_ID"
```

建议每 3–5 秒查询一次，并设置整体等待超时。`completed=true` / `status=SUCCESS` 才算成功；`failed=true` / `status=FAILED` 时读取 `failCode`、`failReason` 并停止轮询。不要仅凭 `progress=100` 判断成功。检查 `subtitleKaraokeStatus`、`subtitleKaraokeReason`，逐字字幕可能降级。

## 6. 获取下载地址并保存文件

```bash
curl -sS --get "$TK_BASE_URL/admin-api/tk/automation/v1/tasks/$TK_TASK_ID/download" \
  -H "Authorization: Bearer $TK_ACCESS_TOKEN" -H "tenant-id: $TK_TENANT_ID" \
  --data-urlencode 'fileName=芒果干_精致妈妈_01.mp4'
```

此接口返回 JSON，`data` 含 `taskId`、`fileName`、`downloadUrl`。它不直接返回 MP4 字节。将成功响应中的完整 URL 原样保存为变量，再下载：

```bash
export TK_DOWNLOAD_URL='<data.downloadUrl原样值>'
curl --fail --location "$TK_DOWNLOAD_URL" --output '芒果干_精致妈妈_01.mp4'
```

对存储下载地址**不要附加平台 Token**。临时链接过期时重新获取；不要自行改写其中签名和查询参数。文件名最长 128 字符，必须以 `.mp4` 结尾，不允许路径或控制字符。

## 令牌续期及下载资源

在到期前刷新：

```bash
curl -sS --request POST --get "$TK_BASE_URL/admin-api/system/auth/refresh-token" \
  -H "tenant-id: $TK_TENANT_ID" --data-urlencode "refreshToken=$TK_REFRESH_TOKEN"
```

保存返回的两种新令牌和 `expiresTime`；刷新失败则重新登录。刷新 URL 含敏感参数，避免写入共享访问日志。

可导入 [Postman 集合](tk-automation.postman_collection.json) 与 [环境模板](tk-automation.postman_environment.json)。默认禁止收费创建；核对参数后仅将本机环境 `allowPaidCreate` 改为 `true` 才会发送一次，发送前自动恢复为 `false`。

需要脚本自动执行时，使用 [Python/Node.js 示例及运行说明](examples/README.md)，先运行只读查询，再显式创建；已有任务可按任务号继续查询下载。完整字段见 [自动出片接口](tk-automation-v1.md)，异常与重试见 [调用规则](calling-rules.md)，机器契约见 [OpenAPI](tk-platform-openapi.json)。
