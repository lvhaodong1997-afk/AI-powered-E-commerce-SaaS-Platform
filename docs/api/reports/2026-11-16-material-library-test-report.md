# 素材库 API 与自动出片测试报告

- 日期：2026-11-16（Asia/Shanghai）。
- 环境：`https://clipforgestudio.fnn.net.cn`；租户「秀美二楼」（166），账号 `xiumei02`。
- 范围：完善已有素材接口文档、OpenAPI、Postman 和可下载示例；真实验证创建素材库、两种上传入口、解析、维护、删除、自动出片。此次不新增后端端点或数据库结构。
- 证据：[脱敏 JSON](2026-11-16-material-library-evidence.json)。不包含密码、Token、OSS 签名或成片临时下载地址。

## 验收结论

素材库创建和素材上传功能已经存在。本次补全 18 个路径、19 个操作和 17 个 Schema；完整 OpenAPI 共 55 个路径、57 个操作，原有接口保持兼容。新增指南、19 条 Postman 请求和默认只读的 Node.js smoke 工具，并加入在线文档分组及下载包。

线上素材主流程 **27/27 通过**，独立删除及非空库保护 **13/13 通过**。普通 multipart 与实际 OSS 会话直传均解析为 `AVAILABLE`。新建库 155 的素材用于任务 **2071**，最终 `SUCCESS`、进度 100，下载后完整解码和浏览器播放通过。

**限制：逐字字幕未完全通过。** 服务返回 `subtitleKaraokeStatus=partial`，4 段中 3 段使用普通字幕，原因 `WORD_TIMING_UNRELIABLE`。这是现有生成能力的降级表现，不能将任务成功等同于逐字字幕完全成功。本次没有修改字幕后端，也未进行完整口播内容、音色和混音的主观验收。

## 交付范围

| 模块 | 本次补充 |
| --- | --- |
| 素材库 | create、update、get、page、delete |
| 视频 | upload、get、page、usage-phase、segment-type 及兼容别名、segment-summary、delete、delete-batch |
| 上传会话 | create、get、chunk、complete、cancel |
| 接入说明 | Token + tenant-id、菜单权限、异步解析、错误码、非幂等及重试边界 |
| 工具 | OpenAPI、Postman 一次性写入开关、OSS 无平台凭证直传、Node.js smoke |

线上当前选中 `uploadMode=oss`。本地 `/chunk` 分支由模拟 HTTP 服务测试覆盖，**没有在生产环境切换存储或执行真实分片续传**。分段兼容别名的映射和权限通过源码契约测试覆盖，不将其计入线上独立调用次数。

## 测试准备与资源

测试视频来自该账号已有素材 6071，下载为 1,227,278 字节 MP4，用于本次专用库上传。只对本次创建的删除测试资源执行删除，未清理原有业务素材。

| 资源 | ID | 结果 |
| --- | --- | --- |
| 主测试素材库 | 155 | 保留，ECOMMERCE |
| 普通上传视频 | 6372 | AVAILABLE，保留 |
| OSS 会话上传视频 | 6373 | AVAILABLE，保留 |
| 空库删除测试 | 156 | 已删除 |
| 非空库与视频删除测试 | 157；6374、6375、6376 | 按单条、批量、清空后删库顺序完成 |
| 出片任务 | 2071 | SUCCESS，成片保留 |
| 配音与 BGM | MiniMax `Chinese (Mandarin)_Gentle_Senior`；148 | 使用现有配置及该账号 BGM |

## 线上用例明细

下表通过表示该用例的响应和断言满足预期；错误请求按预期被拒绝也记为通过。HTTP 200 不等于业务成功。

### 素材主流程：27/27

| 编号 | 用例 | 结果 |
| --- | --- | --- |
| M01 | library page query | 通过 |
| M02 | video page query | 通过 |
| M03 | anonymous query rejected with 401 | 通过 |
| M04 | create dedicated ECOMMERCE library | 通过 |
| M05 | get own library | 通过 |
| M06 | update own library | 通过 |
| M07 | search dedicated library | 通过 |
| M08 | ordinary multipart upload | 通过 |
| M09 | ordinary upload: parse AVAILABLE | 通过 |
| M10 | update own video usage | 通过 |
| M11 | update own video segment | 通过 |
| M12 | verify own video metadata update | 通过 |
| M13 | empty library name rejected | 通过 |
| M14 | empty session filename rejected | 通过 |
| M15 | invalid extension rejected | 通过 |
| M16 | empty file rejected | 通过 |
| M17 | create disposable session for cancellation | 通过 |
| M18 | get own disposable session | 通过 |
| M19 | cancel own disposable session | 通过 |
| M20 | create session for upload completion | 通过 |
| M21 | get own upload session | 通过 |
| M22 | upload bytes via selected session mode | 通过 |
| M23 | complete own upload session | 通过 |
| M24 | session upload: parse AVAILABLE | 通过 |
| M25 | query own video page | 通过 |
| M26 | query own segment summary | 通过 |
| M27 | get retained library statistics | 通过 |

### 独立删除与保护：13/13

| 编号 | 用例 | 业务码 | 结果 |
| --- | --- | --- | --- |
| D01 | 创建独立删除测试库 | 0 | 通过 |
| D02 | 删除本次创建的空库 | 0 | 通过 |
| D03 | 确认删除测试库不可读 | 1060000001 | 通过 |
| D04 | 创建独立素材删除测试库 | 0 | 通过 |
| D05 | 上传独立删除测试素材 delete-fixture-1.mp4 | 0 | 通过 |
| D06 | 上传独立删除测试素材 delete-fixture-2.mp4 | 0 | 通过 |
| D07 | 上传独立删除测试素材 delete-fixture-3.mp4 | 0 | 通过 |
| D08 | 非空测试库拒绝删除 | 1060000002 | 通过 |
| D09 | 删除本次单条测试素材 | 0 | 通过 |
| D10 | 单删后素材不可读 | 1060000003 | 通过 |
| D11 | 批量删除另外两条测试素材 | 0 | 通过 |
| D12 | 批量删除后测试库素材为零 | 0 | 通过 |
| D13 | 删除已清空的本次测试库 | 0 | 通过 |

删除后查询视频返回 `1060000003`（素材视频不存在），非空库删除返回 `1060000002`，已删除库查询返回 `1060000001`。测试断言最初误用了另一个错误码，核对 `ErrorCodeConstants` 并重新 GET 6374 后纠正；产品响应符合现有契约。

### 参数与鉴权边界

已验证匿名查询返回 401、空库名与空会话文件名被拒绝、非法扩展名返回 `1060000012`、空视频返回 `1060000010`。本次没有第二个获授权租户，未验证跨租户读取或跨公司隔离；`companyId` 不能由这些测试推导出独立隔离保证。

初次 smoke 执行发现工具将站点 origin 拼成错误路径，收到 HTML 200/405，未创建资源。已修复 `/admin-api` 默认前缀，增加有无尾斜杠回归测试，然后完成本报告的成功执行。

## 自动出片结果

调用 `POST /admin-api/tk/automation/v1/tasks` 一次，使用库 155；不在超时或响应不明时自动重建。参数为 1080×1920、30 fps、`FOLLOW_NARRATION`、字幕和逐字效果开启、BGM 音量 0.4。完整请求在脱敏 JSON 中。

| 检查 | 实际结果 |
| --- | --- |
| 创建 | taskId=2071，PENDING |
| 过程 | RENDERING 88%，RENDER_SUBTITLE；随后 SUCCESS 100% |
| 最终响应 | completed=true、failed=false，存在下载地址 |
| 文件 | MP4，1,289,806 字节，7.167 秒 |
| 视频流 | H.264，1080×1920，30 fps |
| 音频流 | AAC，44.1 kHz，双声道，7.14 秒 |
| 完整解码 | FFmpeg 无错误退出 |
| 浏览器 | 播放到结束，ended=true、error=null；截图可见中文字幕 |
| 逐字字幕 | partial；3/4 段退化为普通字幕，未完全通过 |
| SHA-256 | `5b314763f714eb50af860c920ee517590bcf34695009a6c4f62a27d57543fc8c` |

观测积分：remaining 103740 → 103739，frozen 均为 10。账号可能存在其他任务，该差值只作为同期观测，不能证明本任务独占扣费或冻结结算正确。成片长度由实际文件核验；未证明完整口播逐句覆盖。

## 本地验证

从前端目录生成公开资产后运行：

```powershell
node scripts/build-api-docs.mjs
node --test tests/api-docs.test.cjs tests/api-docs-assets.test.cjs tests/material-api-contract.test.cjs
```

生成 17 个文件、55 个路径；**19/19 通过**。覆盖已有接口示例兼容、二进制字段、控制器路径/权限、Schema 引用、Unix 毫秒时间字段、异步状态、分段副作用、Postman 一次性写保护、OSS/local 分支和 int64 ID 保真。

从仓库根目录运行：

```powershell
node --test docs/api/examples/material-library-smoke.test.mjs
git diff --check
```

smoke **9/9 通过**，覆盖默认只读、origin 前缀、OSS/local、解析失败、失败 POST 不重试、禁止重定向及证据脱敏。diff 检查通过。模拟测试仅说明客户端行为符合用例，不替代真实存储验证。

## 未覆盖及后续建议

| 项目 | 当前判断 |
| --- | --- |
| 100 MiB/1 GB 临界值、大文件并发压力 | 未执行；文档分别说明固定入口限制与会话配置默认值 |
| 真实 local 分片、断网续传、会话过期 | 未执行；当前线上为 OSS，只有本地模拟覆盖分片流程 |
| 跨租户、跨公司与低权限账号 | 未执行；需要独立授权账号与权限组合 |
| OSS CORS 与浏览器直接上传 | 本次通过服务端 Node fetch 直传，未验证浏览器 CORS |
| 会话 complete 重试 | 不保证幂等；文档要求核对状态，禁止盲重试 |
| 字幕精度与全文口播 | 逐字字幕部分降级；建议独立排查 ASR 词时间戳、对齐与完整口播覆盖 |
| 发布记录 | 本报告为部署前验收；发布后另附提交号、制品哈希、线上文档和健康验证记录 |

素材 API 和文档接入链路可用；逐字字幕完整性及上述未覆盖项不在通过结论内。
