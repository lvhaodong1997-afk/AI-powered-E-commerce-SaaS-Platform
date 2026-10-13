# 电商素材 BGM 实施计划

**Goal:** dashboard 电商素材复用引流素材的 BGM 上传、选择、试听、音量设置，并让背景音乐覆盖应配乐的成片区间。

**Architecture:** 共用现有 BGM 接口、租户音乐库、生成任务字段与 FFmpeg 渲染服务。普通模式按成片时长混音；原生黄金开头沿用正文偏移。

**Tech Stack:** Vue 3、Element Plus、Spring Boot、MySQL、FFmpeg、JUnit 5。

## 约束

- 支持 MP3/WAV/M4A，最大 20MB，音量 1%～30%，默认 10%。
- 电商初始关闭 BGM，引流保持原有默认开启；上传成功自动开启并选中。
- AI 文案和手动文案均支持；重做恢复原任务设置。
- 无新增数据库字段、接口、第三方服务或权限。
- 不改变口播速度；原生开头保留原声，BGM 从正文开始。

## Task 1：后端接受电商 BGM

文件：产品源码根目录下 `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/generation/TkGenerationTaskServiceImpl.java` 及对应 `src/test/java/.../TkGenerationTaskServiceImplTest.java`。

- [x] 将原有电商忽略 BGM 测试改为验证 BGM 编号、来源、地址、默认音量和权限校验。
- [x] 使用 bundled Maven 运行该用例，确认因忽略电商 BGM 而失败。
- [x] 去掉 `resolveBgmSelection` 中仅引流限制，保留开关、素材编号和可读性校验。
- [x] 运行完整任务服务测试和 BGM 服务测试。

## Task 2：共享 dashboard BGM

文件：`yudao-ui/yudao-ui-admin-vue3/src/views/tk/dashboard/index.vue`。

- [x] 共享配置区域；提交快照直接保存 BGM 开关，生成前校验覆盖两类素材。
- [x] 两类素材加载同一音乐库；电商初始关闭，引流保持开启；切换用途重置到对应默认状态，重做继续回填任务值。
- [x] 保留现有上传/试听/滑块，试听在用途切换及离开页面时停止。
- [x] 更新中英文说明；重做音量使用空值判断，避免覆盖有效值。
- [x] 执行 `pnpm ts:check`、dashboard 现有测试、`pnpm tk:i18n:check`、`pnpm build:prod`。

## Task 3：BGM 延续到成片结束

文件：`yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/generation/pipeline/DefaultTkVideoRenderService.java` 及对应测试。

- [x] 更新普通口播混音用例：循环 BGM 按成片时长裁剪，以音乐时长作为混音结束条件。
- [x] 运行用例确认旧 `duration=first` 失败。
- [x] 为口播补静音并裁至成片时长，为 BGM 增加 `atrim=duration=<videoDuration>`，使用 `amix=duration=longest`；保留片尾淡出和原生开头偏移。
- [x] 使用真实 FFmpeg 验证短口播、短音乐场景：输出音轨覆盖视频，口播结束后的中段仍有音乐，片尾淡出。
- [x] 回归无 BGM、仅 BGM、原生开头等现有渲染用例。

## Task 4：审查、发布

- [x] 检查 focused diff，进行独立代码审查并处理问题。
- [x] 通过 frontend build 与 backend package 后提交、推送。
- [x] 生产发布按备份、SHA-256 核对、原子替换、重启执行。
- [x] 分别验证前端资源、后端 PID/端口/HTTP/日志；不执行数据库迁移。
- [x] 发布后确认新代码和构建产物一致，清理本次临时文件。

## 已完成验证

- 后端定向 48 个测试通过，前端手动翻译及 BGM 流程 12 个测试通过；dashboard concurrency、opening upload、BGM display name 测试通过。
- 前端类型检查、多语言检查、生产构建及后端生产打包通过。
- 真实 FFmpeg：20 秒画面、14 秒口播、4 秒循环音乐，输出音轨 20.000 秒。
- 审查发现口播结束时 BGM 增益变化，补静音后前后 BGM 分量比由 2.000 降至 1.000。
- 最终独立审查无阻塞问题；未创建生产业务任务，生产用户上传到生成的完整流程尚未验证。

## 发布记录

- 功能提交 `2f42d35` 已快进合入并推送 `origin/main`。
- 2026-10-13 发布前后端，沿用 `/data/Tk/current` 和已有配置；无数据库迁移。
- 备份：`/data/Tk/backups/ecommerce-bgm-2f42d35-20261013`，保留旧 JAR、完整旧前端、SHA-256 清单和发布收据。
- JAR SHA-256：`0db0b0b9c38dbc533a5d5b2ba294666f8f3a7ff0b49030abd6cdafc3a2ba9e05`；前端全部文件与本地清单一致。
- 公网入口脚本 `index-BseXdBLJ.js` 和页面脚本 `dashboard-DAz84CCP.js` 返回 HTTP 200，字节内容与发布包一致；浏览器登录页正常加载。
- `tk-yudao-prod` PID 从 `1915203` 变为 `919004`，active/running、NRestarts=0，48080/18080 监听正常，后端 HTTP 200。
- 实际启动日志在 `app/systemd.out.log`：17:42:02 启动成功，检查期间无 ERROR。
- 已清理生产 staging 和交换后残留目录；回滚备份保留。
