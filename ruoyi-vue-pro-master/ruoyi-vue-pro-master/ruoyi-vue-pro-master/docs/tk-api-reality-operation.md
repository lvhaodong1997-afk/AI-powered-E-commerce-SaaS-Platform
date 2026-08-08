# TK 自动混剪接口真实性核查与操作文档

核查时间：2026-06-29

核查范围：

- 前端 TK 页面：`yudao-ui/yudao-ui-admin-vue3/src/views/tk`
- 前端 TK API 封装：`yudao-ui/yudao-ui-admin-vue3/src/api/tk`
- 后端 TK 模块：`yudao-module-tk`
- 后端运行配置：`yudao-server/src/main/resources/application-local.yaml`
- Python worker：`worker/app/main.py`

结论先看：

- 前端 TK API 封装没有 mock 层，都会请求后端。
- 素材库、素材视频上传、对标分析、生成任务、生成记录查询都有后端 Controller、Service、Mapper 和数据库表支撑，属于真实接口。
- 对标分析已接入真实视频内容分析：直链视频直接下载，抖音/TikTok/B 站等作品页优先复用本项目内置的 `tools/reference-video-download/douyin_tiktok_bilibili_tool.py` 下载脚本和 `Douyin_TikTok_Download_API` 解析下载真实视频文件，再用 FFprobe 读取元数据、FFmpeg 抽关键帧，并把关键帧图片和元数据送给 Gemini/OpenAI 兼容多模态接口生成卖点和文案。
- 视频生成流水线也是真实代码，会走 Gemini/OpenAI 兼容文本/多模态接口、DashScope TTS、FFmpeg、文件服务和数据库状态回写。
- 但 Python worker 目前是摆设，只返回 accepted，没有接入队列、FFmpeg、TTS、大模型或状态回写。
- 首页汇总里的 `estimatedOrders` 是写死数字，不是真实订单统计。
- 素材视频解析已接入真实实现：上传后进入 `PARSING`，后台用 FFprobe 识别时长/分辨率，用 FFmpeg 抽封面，成功后更新为 `AVAILABLE`，失败后更新为 `FAILED` 并写入原因。
- 种子 SQL 里有 demo 生成任务和 demo TikTok 链接，这些是演示数据，不代表真实生成过视频。

## 1. 本地请求链路

本地前端配置：

- 前端端口：`.env` 中 `VITE_PORT=80`
- 后端地址：`.env.local` 中 `VITE_BASE_URL='http://localhost:48080'`
- API 前缀：`.env.local` 中 `VITE_API_URL=/admin-api`

所以前端 API 封装里的 `/tk/dashboard/summary`，真实请求地址是：

```text
http://localhost:48080/admin-api/tk/dashboard/summary
```

本地后端配置：

- 后端端口：`48080`
- MySQL：`127.0.0.1:3307/tk_auto_mix`
- Redis：`127.0.0.1:6380`
- Gemini Key：环境变量 `GEMINI_API_KEY` 或表 `tk_api_key_config`
- DashScope Key：环境变量 `DASHSCOPE_API_KEY` 或表 `tk_api_key_config`
- FFmpeg：环境变量 `FFMPEG_PATH`，未配置时使用命令 `ffmpeg`
- 参考视频下载脚本：默认使用本项目 `tools/reference-video-download` 内置脚本，可通过 `TK_REFERENCE_DOWNLOAD_SCRIPT_*` 环境变量覆盖

## 2. 真实接口清单

### 2.1 首页汇总

| 项目 | 内容 |
| --- | --- |
| 接口 | `GET /admin-api/tk/dashboard/summary` |
| 前端封装 | `src/api/tk/dashboard/index.ts` |
| 后端入口 | `TkDashboardController#getSummary` |
| 权限 | `tk:dashboard:query` |
| 真实性 | 部分真实 |

真实部分：

- `generatedVideoCount` 来自 `tk_generation_task` 统计。
- `materialVideoCount` 来自 `tk_material_video` 统计。
- `parsingVideoCount` 来自 `tk_material_video.status = PARSING` 统计。
- `libraries` 来自最近 5 个素材库。
- `recentTasks` 来自最近 5 个生成任务。

摆设部分：

- `estimatedOrders` 是写死的：平台管理员返回 `342`，普通公司范围返回 `128`。
- 没有订单表、成交表、TikTok 店铺订单接口接入。

### 2.2 素材库接口

| 接口 | 方法 | 真实性 | 说明 |
| --- | --- | --- | --- |
| `/admin-api/tk/material-library/page` | GET | 真实 | 分页查询 `tk_material_library` |
| `/admin-api/tk/material-library/get` | GET | 真实 | 查询单个素材库 |
| `/admin-api/tk/material-library/create` | POST | 真实 | 创建素材库并写库 |
| `/admin-api/tk/material-library/update` | PUT | 真实 | 更新素材库 |
| `/admin-api/tk/material-library/delete` | DELETE | 真实 | 删除素材库；如果库下有视频会拒绝删除 |

后端会校验当前用户的数据范围：

- 平台管理员可读全部公司，写入时必须传 `companyId`。
- 公司管理员/公司用户只能操作自己 `tk_company_id` 下的数据。

### 2.3 素材视频接口

| 接口 | 方法 | 真实性 | 说明 |
| --- | --- | --- | --- |
| `/admin-api/tk/material-video/page` | GET | 真实 | 分页查询素材视频 |
| `/admin-api/tk/material-video/get` | GET | 真实 | 后端已实现，但前端 API 层当前未封装 |
| `/admin-api/tk/material-video/upload` | POST | 真实 | 上传文件到系统文件服务，写入 `tk_material_video`，并提交后台解析 |
| `/admin-api/tk/material-video/delete` | DELETE | 真实 | 删除视频记录，并回减素材库视频数和总大小 |

上传限制：

- 文件不能为空。
- 单文件最大 `100MB`。
- 只允许 `mp4`、`mov`、`webm`。
- 上传目录：`tk/{tenantId}/{companyId}/material-videos`

解析链路：

1. 上传接口先保存原视频到文件服务。
2. 数据库写入 `tk_material_video`，状态为 `PARSING`。
3. 后台解析任务下载视频文件。
4. 调用 FFprobe 读取视频时长和分辨率。
5. 调用 FFmpeg 抽取首帧封面。
6. 封面上传到文件服务目录：`tk/{tenantId}/{companyId}/material-covers`。
7. 成功后更新 `duration`、`resolution`、`coverUrl`、`status=AVAILABLE`。
8. 失败后更新 `status=FAILED` 和 `failReason`。

限制：

- 视频文件 URL 必须是后端服务器可下载的 `http://` 或 `https://` 地址。
- 服务器必须安装 FFmpeg/FFprobe，或通过 `FFMPEG_PATH`、`FFPROBE_PATH` 指向可执行文件。
- 解析任务运行在 Java 进程内线程池，服务重启会中断正在解析的视频。
- 生成混剪任务只会使用 `AVAILABLE` 状态的视频，解析中或失败的视频不会进入素材匹配。

### 2.4 对标分析接口

| 接口 | 方法 | 真实性 | 说明 |
| --- | --- | --- | --- |
| `/admin-api/tk/reference/analyze` | POST | 真实 | 解析真实对标视频内容，抽关键帧，调用 Gemini/OpenAI 兼容多模态接口生成结构化分析和 12 条文案方案 |
| `/admin-api/tk/reference/latest` | GET | 真实 | 查询同素材库、同链接最近成功分析 |
| `/admin-api/tk/reference/page` | GET | 真实 | 分页查询对标分析记录 |

`analyze` 的真实链路：

1. 校验素材库可读。
2. 校验公司和素材库归属一致。
3. 如果 `forceRefresh != true`，先查同素材库、同链接最近一次成功分析。
4. 没有可复用结果时，先解析真实对标视频内容。
5. 如果请求本身是 `mp4/mov/webm/m4v` 直链，直接下载该视频。
6. 如果不是直链，优先调用本项目内置下载脚本：
   `py douyin_tiktok_bilibili_tool.py --repo Douyin_TikTok_Download_API download <sourceUrl> --out-dir <taskDir> --prefix reference`。
7. 下载脚本复用 `Douyin_TikTok_Download_API`，支持抖音、TikTok、B 站作品页解析，返回本地视频文件路径和结构化平台数据。
8. 如果脚本禁用或失败，后端才尝试 HTML meta/script 兜底解析视频地址。
9. 调用 FFprobe 读取真实视频时长和分辨率。
10. 调用 FFmpeg 抽取最多 5 张关键帧。
11. 将关键帧以 base64 图片形式连同真实视频元数据、素材库信息一起发送给 `TkGeminiClient.generateText(prompt, images)`。
12. 要求 AI 严格基于关键帧画面和真实元数据返回 JSON。
13. 解析产品名、视频时长、发布时间、卖点、人群、场景、视频结构、卖点卡片、12 条文案方案。
14. 写入 `tk_reference_analysis`。
15. 写入 `tk_reference_script_option`。
16. 返回分析结果和文案方案。

重要纠偏：

- 当前代码没有“AI 失败后规则兜底成功返回”的逻辑。
- 当前代码是：AI 失败时写一条 `FAILED` 分析记录，然后抛错给前端。
- 旧文档里如果写了“AI 失败会兜底生成结果”，以当前源码为准，那是不准确的。

外部依赖：

- `tk_api_key_config` 表或环境变量必须配置 `GEMINI` 的 `api-key`。
- `GEMINI.api-format=openai` 时会请求 `{base-url}/chat/completions`，请求体使用 OpenAI 兼容的 `image_url` 多模态格式。
- `GEMINI.api-format=gemini` 时会请求 Gemini 原生 `generateContent`，请求体使用 `inline_data` 图片输入。
- 模型必须支持图片输入；只支持文本的模型会导致分析失败。
- 非直链对标视频依赖本项目内置下载脚本、Python 运行环境和 `Douyin_TikTok_Download_API` 依赖；当前本机默认 Python 命令为 `py`。
- 如果部署机器没有 `py`，需要配置 `TK_REFERENCE_DOWNLOAD_SCRIPT_PYTHON` 为可执行的 Python 路径。
- 如果平台返回内容为空、作品受限、链接不是作品页、脚本依赖缺失或脚本路径错误，接口会失败并写入失败记录。
- FFmpeg/FFprobe 必须可用，否则无法读取视频元数据和抽关键帧。

### 2.5 生成任务接口

| 接口 | 方法 | 真实性 | 说明 |
| --- | --- | --- | --- |
| `/admin-api/tk/generation/create` | POST | 真实 | 创建生成任务，可传远程黄金三秒视频 URL |
| `/admin-api/tk/generation/create-with-opening` | POST | 真实 | 上传本地黄金三秒视频并创建生成任务 |
| `/admin-api/tk/generation/page` | GET | 真实 | 分页查询生成任务 |
| `/admin-api/tk/generation/get` | GET | 真实 | 查询单个生成任务 |

创建任务会真实写入 `tk_generation_task`，并立即提交 Java 进程内异步线程池：

```text
PENDING -> ANALYZING -> SCRIPT_READY -> VOICE_READY -> MATERIAL_MATCHED -> RENDERING -> EXPORTING -> SUCCESS
```

失败时：

```text
FAILED
```

失败原因会写入 `tk_generation_task.fail_reason`。

### 2.6 视频生成流水线

| 阶段 | 真实性 | 实际行为 |
| --- | --- | --- |
| 文案生成/确认 | 真实 | 有 `scriptOptionId` 时直接使用用户选中的文案；没有时调用 Gemini 生成 |
| TTS 配音 | 真实 | 调 DashScope TTS，下载音频，上传到文件服务 |
| 字幕生成 | 真实但简单 | 按标点切句，每句 3 秒，生成 SRT |
| 素材匹配 | 真实但简单 | 按素材标签是否出现在文案里打分排序，循环取 3 秒片段补齐时长 |
| FFmpeg 合成 | 真实 | 下载素材和音频，裁剪、拼接、烧字幕、上传最终 MP4 |
| 状态回写 | 真实 | 每阶段更新 `tk_generation_task` |

关键限制：

- 生成任务不是独立队列，也不是 Python worker，当前是 Java 进程内 `newFixedThreadPool(2)`。
- Java 服务重启会丢失正在内存中执行的任务。
- 没有任务重试、任务恢复、分布式锁或多实例调度保护。
- FFmpeg 渲染只接受 `http://` 或 `https://` 文件 URL。文件服务如果返回相对路径、本机路径或内网不可访问地址，渲染会失败。
- 远程黄金三秒视频 URL 也必须是后端服务器可下载的 HTTP 地址。
- 素材库必须至少有一个素材视频，否则任务会在素材匹配阶段失败。
- DashScope 必须有 `api-key` 和 `voice`。`voice` 可以来自任务 `voiceCode`，否则读 `tk_api_key_config` 或配置项。

## 3. 摆设与已转正能力清单

### 3.1 Python worker

| 项目 | 结论 |
| --- | --- |
| 文件 | `worker/app/main.py` |
| 接口 | `GET /health`、`POST /tasks/submit` |
| 真实性 | 摆设 |

原因：

- `/tasks/submit` 只打印日志并返回 `{"accepted": true, "status": "PENDING"}`。
- 源码注释明确写着：`TODO 接入真实队列、FFmpeg、TTS、大模型和状态回写。`
- Java 后端当前没有把生成任务提交给这个 Python worker。
- 真正的视频生成在 Java 模块 `DefaultTkGenerationPipelineService` 里执行。

### 3.2 首页预计订单数

| 字段 | 结论 |
| --- | --- |
| `estimatedOrders` | 摆设 |

原因：

- 后端直接返回固定值：平台管理员 `342`，普通公司范围 `128`。
- 没有订单表，也没有 TikTok 店铺订单接口。

### 3.3 素材视频解析能力

| 能力 | 结论 |
| --- | --- |
| 视频时长解析 | 真实 |
| 封面提取 | 真实 |
| 分辨率识别 | 真实 |
| 解析中状态流转 | 真实 |

实现方式：

- 上传后状态写入 `PARSING`。
- `TkMaterialVideoParseServiceImpl` 后台调用 FFprobe 获取 `duration` 和 `resolution`。
- 后台调用 FFmpeg 抽首帧并上传封面，回写 `coverUrl`。
- 解析成功写 `AVAILABLE`，失败写 `FAILED` 和 `failReason`。

### 3.4 Demo 数据

| 数据 | 结论 |
| --- | --- |
| SQL 里的 `/exports/demo-1.mp4`、`/exports/demo-2.mp4` | 演示数据 |
| SQL 里的 `https://www.tiktok.com/@demo/video/...` | 演示数据 |

这些记录可以让页面有数据可看，但不代表系统实际跑通过真实生成。

### 3.5 前端入口但没有独立后端动作

首页中部分文案或入口更像体验入口，不是独立真实功能：

- “查看格式”只提示真实公开视频链接格式，不再填入不可解析的演示 TikTok 链接。
- 首页的“换一批文案/重新生成”本质还是调用 `/tk/reference/analyze` 并传 `forceRefresh=true`。
- 首页没有直接查看 worker、队列、任务日志的真实接口。

## 4. 操作文档：如何本地跑通真实接口

### 4.1 准备数据库和 Redis

本地 `local` 配置要求：

```text
MySQL: 127.0.0.1:3307
Database: tk_auto_mix
Username: root
Password: 123456

Redis: 127.0.0.1:6380
Database: 0
```

执行 TK 初始化 SQL：

```text
yudao-module-tk/src/main/resources/sql/tk_mysql.sql
yudao-module-tk/src/main/resources/sql/tk_reference_analysis_upgrade_mysql.sql
yudao-module-tk/src/main/resources/sql/tk_generation_pipeline_upgrade_mysql.sql
yudao-module-tk/src/main/resources/sql/tk_api_key_config_upgrade_mysql.sql
yudao-module-tk/src/main/resources/sql/tk_i18n_repair_mysql.sql
```

如果数据库已经初始化过，SQL 里大量语句是 `CREATE TABLE IF NOT EXISTS` 或 `ON DUPLICATE KEY UPDATE`，可重复执行；但仍建议先备份数据库。

### 4.2 配置外部服务

对标分析需要 Gemini/OpenAI 兼容多模态接口，模型必须支持图片输入：

```text
GEMINI_API_KEY=你的 key
```

完整视频生成还需要 DashScope TTS：

```text
DASHSCOPE_API_KEY=你的 key
```

同时要配置默认音色，二选一：

- 在任务里传 `voiceCode`。
- 在 `tk_api_key_config` 表写入 `provider=DASHSCOPE, config_key=voice, config_value=音色ID`。

FFmpeg 需要系统 PATH 可执行，或配置：

```text
FFMPEG_PATH=C:\path\to\ffmpeg.exe
FFPROBE_PATH=C:\path\to\ffprobe.exe
```

参考视频作品页真实下载默认复用本项目内置脚本：

```text
TK_REFERENCE_DOWNLOAD_SCRIPT_ENABLED=true
TK_REFERENCE_DOWNLOAD_SCRIPT_PYTHON=py
TK_REFERENCE_DOWNLOAD_SCRIPT_PATH=tools/reference-video-download/douyin_tiktok_bilibili_tool.py
TK_REFERENCE_DOWNLOAD_SCRIPT_REPO=tools/reference-video-download/Douyin_TikTok_Download_API
TK_REFERENCE_DOWNLOAD_SCRIPT_TIMEOUT_SECONDS=180
TK_REFERENCE_DOWNLOAD_PROXY=
TK_REFERENCE_DOWNLOAD_HTML_FALLBACK_ENABLED=true
TK_REFERENCE_DOWNLOAD_HTML_TIMEOUT_SECONDS=20
```

当前机器已验证 `py`、脚本帮助命令、`Douyin_TikTok_Download_API` 的 hybrid crawler 导入可用。换机器部署时，如果没有 Windows `py` 启动器，把 `TK_REFERENCE_DOWNLOAD_SCRIPT_PYTHON` 改成实际 Python 可执行文件路径。

如果后端服务器不能直连 TikTok/抖音，需要给下载脚本和 HTML 兜底解析配置同一个 HTTP/SOCKS 代理，例如：

```powershell
$env:TK_REFERENCE_DOWNLOAD_PROXY="http://127.0.0.1:7890"
```

Linux 示例：

```bash
export TK_REFERENCE_DOWNLOAD_PROXY=http://127.0.0.1:7890
```

代理会同时传给 Java 端 HTML/直链下载、Python 下载脚本以及脚本内的 Douyin/TikTok/Bilibili crawler。

服务器首次部署需要在项目根目录安装脚本依赖：

```powershell
cd C:\path\to\ruoyi-vue-pro-master
py -m pip install -r tools/reference-video-download/Douyin_TikTok_Download_API/requirements.txt
```

Linux 服务器通常改用：

```bash
cd /path/to/ruoyi-vue-pro-master
python3 -m pip install -r tools/reference-video-download/Douyin_TikTok_Download_API/requirements.txt
export TK_REFERENCE_DOWNLOAD_SCRIPT_PYTHON=python3
```

### 4.3 启动后端

在项目根目录：

```powershell
cd C:\Users\lhd\Documents\TK自动混剪SaaS产品\ruoyi-vue-pro-master\ruoyi-vue-pro-master\ruoyi-vue-pro-master
$env:GEMINI_API_KEY="你的 key"
$env:DASHSCOPE_API_KEY="你的 key"
$env:TK_REFERENCE_DOWNLOAD_SCRIPT_PYTHON="py"
$env:TK_REFERENCE_DOWNLOAD_PROXY="http://127.0.0.1:7890" # 仅后端不能直连 TikTok/抖音时需要
C:\Users\lhd\Documents\TK自动混剪SaaS产品\.runtime\apache-maven-3.9.10\bin\mvn.cmd -pl yudao-server -am spring-boot:run -Dspring-boot.run.profiles=local
```

后端启动后访问：

```text
http://localhost:48080/admin-api
```

### 4.4 启动前端

```powershell
cd C:\Users\lhd\Documents\TK自动混剪SaaS产品\ruoyi-vue-pro-master\ruoyi-vue-pro-master\ruoyi-vue-pro-master\yudao-ui\yudao-ui-admin-vue3
pnpm install
pnpm dev --mode local
```

本项目 `.env` 当前配置 `VITE_PORT=80`，所以本地默认前端地址是：

```text
http://localhost/
```

默认登录信息来自 `.env`：

```text
租户：TK自动混剪
账号：admin
密码：admin123
```

### 4.5 页面验证路径

1. 登录后台。
2. 进入 `TK素材工厂 -> 素材库`。
3. 创建一个素材库，或使用 SQL 种子里的素材库。
4. 上传至少 1 个 `mp4/mov/webm` 素材视频。
5. 进入 `TK素材工厂 -> 首页`。
6. 输入 TikTok 对标链接。
7. 选择素材库。
8. 点击“开始分析”。
9. 如果 Gemini 配置正确，应生成分析结果和 12 条文案方案。
10. 上传本地黄金三秒视频，或填写后端可下载的远程视频 URL。
11. 点击“生成混剪视频”。
12. 进入 `TK素材工厂 -> 生成记录` 查看任务状态。

### 4.6 数据库验证 SQL

看素材库：

```sql
SELECT id, tenant_id, company_id, name, video_count, total_size, status
FROM tk_material_library
ORDER BY id DESC;
```

看素材视频：

```sql
SELECT id, library_id, file_name, file_url, duration, resolution, status, fail_reason
FROM tk_material_video
ORDER BY id DESC;
```

看对标分析：

```sql
SELECT id, library_id, source_url, product_name, status, fail_reason, create_time
FROM tk_reference_analysis
ORDER BY id DESC;
```

看文案方案：

```sql
SELECT id, analysis_id, option_no, title, estimated_conversion_rate, conversion_level
FROM tk_reference_script_option
ORDER BY id DESC;
```

看生成任务：

```sql
SELECT id, library_id, status, progress, title, audio_url, subtitle_url, output_url, fail_reason, update_time
FROM tk_generation_task
ORDER BY id DESC;
```

### 4.7 常见失败和判断方式

| 现象 | 最可能原因 | 检查方式 |
| --- | --- | --- |
| 首页接口 401 | 未登录或 token 过期 | 重新登录 |
| 首页接口无权限 | 角色缺少 TK 权限 | 检查 `system_menu.permission` 和角色菜单 |
| TK 接口提示用户范围未配置 | `system_users.tk_user_level` 或 `tk_company_id` 未设置 | 查 `system_users` |
| 对标分析失败 | Gemini key/base-url/model/api-format 配置错误，或参考视频下载脚本失败 | 查 `tk_reference_analysis.fail_reason` 和后端日志 |
| 对标分析提示 Python 依赖缺失 | `Douyin_TikTok_Download_API` requirements 没装到当前 Python | 对 `TK_REFERENCE_DOWNLOAD_SCRIPT_PYTHON` 指向的 Python 执行 `pip install -r <script-repo>\requirements.txt` |
| 对标分析提示脚本文件不存在 | `TK_REFERENCE_DOWNLOAD_SCRIPT_PATH` 或 `TK_REFERENCE_DOWNLOAD_SCRIPT_REPO` 指错，或部署包漏掉 `tools/reference-video-download` | 检查配置路径是否指向本项目内置脚本和源码目录 |
| 对标分析提示 TikTok/抖音连接超时或 `getsockopt` | 后端机器不能直连平台，或代理没有传给 Java/Python 下载链路 | 配置 `TK_REFERENCE_DOWNLOAD_PROXY=http://host:port`，确认代理进程允许后端访问 |
| 对标分析提示链接不是作品页 | 粘贴的是主页、搜索页、私密/失效作品或平台风控返回空数据 | 换公开视频作品页链接 |
| 素材上传后 FAILED | FFmpeg/FFprobe 不可用，或文件 URL 后端不可下载 | 查 `tk_material_video.fail_reason` |
| 生成任务创建成功但很快 FAILED | 素材库没有 `AVAILABLE` 视频、DashScope 未配置、voice 为空、FFmpeg 不可用、文件 URL 不可下载 | 查 `tk_generation_task.fail_reason` |
| 渲染失败提示文件 URL 不是 HTTP | 文件服务返回相对路径或本机路径 | 查 `tk_material_video.file_url`、`opening_video_url`、`audio_url` |
| 页面看到 demo 成功任务 | SQL 种子数据 | 查 `output_url` 是否 `/exports/demo-*.mp4` |

## 5. 权限清单

完整首页链路至少需要：

```text
tk:dashboard:query
tk:material-library:query
tk:material-video:query
tk:reference:analyze
tk:reference:query
tk:generation:create
tk:generation:query
```

素材库管理还需要：

```text
tk:material-library:create
tk:material-library:update
tk:material-library:delete
tk:material-video:upload
tk:material-video:delete
```

## 6. 当前接口分级总表

| 模块 | 接口/能力 | 分级 | 备注 |
| --- | --- | --- | --- |
| 首页 | `/tk/dashboard/summary` | 半真实 | 统计真实，`estimatedOrders` 写死 |
| 素材库 | 创建/更新/删除/查询 | 真实 | 完整落库 |
| 素材视频 | 上传/删除/查询/解析 | 真实 | 文件上传、时长解析、分辨率识别、封面提取、状态流转 |
| 对标分析 | analyze/latest/page | 真实 | 解析真实视频内容并依赖 Gemini/OpenAI 兼容多模态接口 |
| 生成任务 | create/create-with-opening/page/get | 真实 | 创建后提交 Java 内存线程池 |
| 文案生成 | Gemini 文案/图片分析 | 真实 | 有多模态模型配置才可用 |
| TTS | DashScope TTS | 真实 | 需要 key 和 voice |
| 素材匹配 | 标签匹配 | 半真实 | 规则简单，不是视觉理解 |
| 视频渲染 | FFmpeg 合成 | 真实 | 依赖可下载文件 URL 和 FFmpeg |
| Worker | `/tasks/submit` | 摆设 | 没接入主链路 |
| 订单预测 | `estimatedOrders` | 摆设 | 固定数字 |
| 视频解析 | 时长/封面/分辨率 | 真实 | 依赖 FFprobe/FFmpeg 和可下载文件 URL |

## 7. 建议下一步

如果要把系统从“可演示”推进到“可上线”，优先补这几块：

1. 把生成任务从 Java 内存线程池迁到可靠队列，支持重试和恢复。
2. 修正首页 `estimatedOrders`，要么接真实订单数据，要么改名为“预估曝光/演示指标”，避免误导。
3. 文件服务必须返回后端可下载的绝对 HTTP URL，否则视频解析和 FFmpeg 渲染都会失败。
4. 增加生成任务日志表或阶段明细，前端才能定位失败在 Gemini、TTS、素材下载还是 FFmpeg。
