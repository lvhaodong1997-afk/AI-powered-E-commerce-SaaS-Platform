# MiniMax BCE 配音接入

本文件是 TK 项目的配置和验收说明；本次只实现本地代码，未连接服务器、执行数据库迁移或部署。后续发布需单独授权，且只能操作确认属于 TK 的目录、数据库与服务。不得修改其它项目、共享 Nginx 配置、系统 Python、全局环境变量或未确认归属的 8090 端口进程。

## 调用链与兼容行为

浏览器选择系统音色 → TK Java 校验字典并保存 `MINIMAX + voiceCode` → TK Worker → BCE SDK 签名 POST `https://vod.bj.baidubce.com/v2/mini/t2a_v2` → HTTPS 音频 URL → Java 有界下载 MP3 → 现有 FileApi 保存正式音频。

Java 不请求 `api.minimax.cn`，不保存 BCE AK/SK。新视频任务和独立音频导出的空供应商默认 MINIMAX；显式 MIMO、DASHSCOPE 继续使用原实现。历史任务空供应商仍按 DASHSCOPE 解析，重试保留原供应商。音色设计、克隆继续走原供应商，本阶段没有 MiniMax 设计/克隆接口。

系统音色试听直接播放既有 OSS 文件，不调用生成接口；通用 `/tk/generation/voice-preview` 对 MINIMAX 明确拒绝合成：
`https://tk-video-create.oss-cn-beijing.aliyuncs.com/Cover-background/voice/{编码后的voiceId}.mp3`。
合成请求中的 `voiceId` 保留原始值，不能用 URL 编码替换。国家和语言是筛选信息，不限制目标文案语言。来源 303 条记录均未填写 gender，按原数据保留；性别筛选只有维护真实性别元数据后才有可选项。

## 进程专用配置

仅在 TK 自有虚拟环境安装 `worker/requirements.txt`。Worker 启动参数沿用现有 FastAPI 入口，绑定回环地址或 TK 私有网络；端口使用部署时确认未被其它项目占用的值。不要假定本地模板的路径或端口就是线上实际配置。

| 进程 | 环境变量 | 值或默认值 |
| --- | --- | --- |
| Worker | `MINIMAX_BCE_AK` | 百度 BCE AK，必填，仅注入 TK Worker |
| Worker | `MINIMAX_BCE_SK` | 百度 BCE SK，必填，仅注入 TK Worker |
| Worker | `MINIMAX_BCE_ENDPOINT` | `vod.bj.baidubce.com`，无协议前缀 |
| Worker | `MINIMAX_BCE_TIMEOUT_SECONDS` | `180` |
| Java + Worker | `TTS_INTERNAL_TOKEN` | 两个 TK 进程一致的随机令牌，必填 |
| Java | `MINIMAX_WORKER_URL` | 参考 Worker 根地址，默认 `http://127.0.0.1:8001` |
| Java | `MINIMAX_WORKER_TIMEOUT_SECONDS` | `200`，应大于 Worker 超时 |
| Java | `MINIMAX_AUDIO_DOWNLOAD_TIMEOUT_SECONDS` | `60` |
| Java | `MINIMAX_AUDIO_MAX_DOWNLOAD_BYTES` | `52428800`，50 MiB |

如果配置 `TTS_INTERNAL_TOKEN`，Java 通过参考 Worker 要求的 `X-Internal-Token` 请求头传递；参考项目允许令牌为空，因此当前 TK Java 也会在为空时省略该请求头。BCE 付费请求关闭自动重试。跨主机连接应使用 TK 私有网络或 HTTPS；密钥不能放在前端、字典 SQL、提交记录或全局 `/etc/environment` 中。

## 数据迁移（本次未执行）

产品源码根目录：`ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/`。

迁移文件：`yudao-module-tk/src/main/resources/sql/tk_minimax_voice_catalog_upgrade_mysql.sql`。
该文件适配 Yudao `system_dict_type/system_dict_data`，包含 303 条参考项目真实音色、默认参数及新表 `tk_voice_favorite`。仅补充缺失记录；不删除音色，不覆盖已有名称、启停状态或默认标志。默认音色使用 `remark.isDefault`。收藏唯一键为租户、用户、供应商、音色编码，严格限定登录用户范围。

迁移仅在 `remark` 为不足以容纳源元数据的短字符列时扩展为 TEXT。它是需要单独安排窗口的 DDL；在执行前确认当前数据库确属 TK，备份该库并检查该列及同名表的实际结构。SQL 不包含 `USE`、建库或跨库写入，不能在未确认的连接上执行。不要直接运行参考项目包含删除重建的原 SQL。

## API 与验收

后台统一前缀 `/admin-api`；返回沿用 CommonResult。

| 接口 | 用途 |
| --- | --- |
| `GET /tk/voice/minimax/options` | 音色目录，含当前用户收藏状态 |
| `POST /tk/voice/minimax/favorite`，JSON `{ "voiceCode": "..." }` | 收藏音色 |
| `DELETE /tk/voice/minimax/favorite?voiceCode=...` | 取消收藏 |
| 既有视频创建、音频导出接口 | `ttsProvider: "MINIMAX"`、`voiceCode: 原始voiceId`；不传 `voiceProfileId` |
| `POST /api/tts/minimax/generate` | 仅供 Java 调用的 Worker 内部接口 |

固定输出契约为 URL、MP3、单声道；默认模型 `speech-2.8-turbo`、语速 1.2、音量 1.1、音调 0、情绪 fluent、32000 Hz、128000 bps，`language_boost=auto`。字典提供可调整的模型及有界音频参数，非法音色会被拒绝。

本地验收覆盖 Worker 鉴权/BCE 请求路径/响应异常、Java 新默认与历史路由、音频下载与文件保存、成功扣费和失败退款、用户收藏隔离、303 条迁移数据、前端筛选和请求契约。真实 BCE 账号权限、余额、网关响应与 OSS 对象可访问性必须在获准的 TK 环境中另做真实联调；自动化使用模拟响应，不能替代实测。

回退时将新任务显式切回 MIMO 或 DASHSCOPE；保留已生成音频和音色/收藏记录。不要用删除字典或回滚其它项目服务的方式回退。
