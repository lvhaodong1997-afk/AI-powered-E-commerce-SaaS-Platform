# TikTok Upload Terminal And App Scheduling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将分片上传地址过期且未传完的 TikTok 发布收敛为可靠失败终态，并为本应用发布中心增加可改期、可取消、可重启恢复的定时发布。

**Architecture:** 外部开放发布继续以发布尝试表作为执行证据，只在“上传 HTTP 403 + 官方仍为 PROCESSING_UPLOAD + uploaded_bytes 小于 file_size”同时成立时生成 `UPLOAD_EXPIRED_CONFIRMED` 失败终态和幂等回调。本应用定时发布复用现有发布执行器，任务与素材路径持久化到数据库和配置目录，数据库调度器原子认领到期任务，不依赖 JVM 内存定时器。

**Tech Stack:** Java 8、Spring Boot、MyBatis-Plus、JUnit 5、Mockito、MySQL 8、Vue 3、TypeScript、Element Plus。

## Global Constraints

- 现有立即发布请求不传 `scheduledAt` 时行为保持不变。
- 外部 C 方接口不新增必填参数，失败终态继续通过现有 `publish.failed` 回调发送。
- 定时任务只允许在执行前改期或取消；执行器认领后拒绝修改。
- 定时素材根目录使用 `tk.generation.upload.scheduled-publish-root-dir`，不得硬编码生产路径或 `/tmp`。
- 所有生产代码必须先有失败测试；本轮只本地开发、提交和推送，不部署生产服务器。

---

### Task 1: 收敛上传地址过期终态

**Files:**
- Modify: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/open/tiktok/TkOpenTiktokPublishService.java`
- Modify: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/open/tiktok/TkOpenTiktokPublishTerminalService.java`
- Modify: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/tiktok/TkTiktokPublishServiceImpl.java`
- Test: `yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/service/open/tiktok/TkOpenTiktokPublishTerminalServiceTest.java`
- Test: `yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/service/open/tiktok/TkOpenTiktokPublishServiceTest.java`
- Test: `yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/service/tiktok/TkTiktokPublishServiceImplTest.java`

**Interfaces:**
- Consumes: `TkTiktokApiClient.UploadException.isUploadUrlExpired()`、官方 `PublishStatusResult.uploadedBytes`、尝试记录 `fileSize`。
- Produces: `TkOpenTiktokPublishTerminalService.confirmUploadExpired(...)` 和应用内 `UPLOAD_URL_EXPIRED` 失败原因。

- [ ] **Step 1: Write failing terminal-evidence tests**

增加测试，要求只有拥有当前上传 lease、publish_id 匹配、错误记录为 HTTP 403、官方状态为 `PROCESSING_UPLOAD` 且 `uploadedBytes < fileSize` 时才允许失败，并断言回调事件为 `publish.failed`、尝试来源为 `UPLOAD_EXPIRED_CONFIRMED`。

- [ ] **Step 2: Run focused tests and verify RED**

Run: `..\..\..\.runtime\apache-maven-3.9.10\bin\mvn.cmd -pl yudao-module-tk -am -Dtest=TkOpenTiktokPublishTerminalServiceTest,TkOpenTiktokPublishServiceTest,TkTiktokPublishServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because `confirmUploadExpired` and strict 403 reconciliation do not exist.

- [ ] **Step 3: Implement minimal strict evidence path**

上传恢复捕获 403 后重新查询官方状态；成功或官方失败沿用现有权威终态；只有上传未完成证据完整时调用 `confirmUploadExpired`。本应用发布在同样条件下写入失败终态，否则继续 `UPLOAD_PENDING`。

- [ ] **Step 4: Run focused tests and verify GREEN**

Run the command from Step 2.

Expected: PASS with zero failures.

- [ ] **Step 5: Commit terminal-state change**

```bash
git add yudao-module-tk/src/main/java yudao-module-tk/src/test/java
git commit -m "fix(tk): terminalize expired TikTok uploads"
```

### Task 2: 增加本应用定时发布持久化模型

**Files:**
- Create: `yudao-module-tk/src/main/resources/sql/tk_tiktok_app_scheduled_publish_upgrade_mysql.sql`
- Modify: `yudao-module-tk/src/main/resources/sql/tk_tiktok_publish_center_mysql.sql`
- Modify: `yudao-module-tk/src/main/resources/sql/tk_schema_comment_zh_upgrade_mysql.sql`
- Modify: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/dal/dataobject/TkTiktokPublishTaskDO.java`
- Modify: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/dal/mysql/TkTiktokPublishTaskMapper.java`
- Test: `yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/service/tiktok/TkTiktokPublishP1P2MigrationTest.java`

**Interfaces:**
- Produces: `scheduledAt`、`scheduleStatus`、`scheduleVersion`、`startedAt`、`finishedAt`、`scheduledLocalPath`、`scheduledMediaStatus`、`scheduledMediaFailReason` 和到期扫描索引。

- [ ] **Step 1: Write failing schema/model tests**

测试新字段、增量迁移、新环境建表 SQL、中文注释和 `idx_tk_tiktok_publish_task_schedule` 均存在。

- [ ] **Step 2: Run migration test and verify RED**

Run: `..\..\..\.runtime\apache-maven-3.9.10\bin\mvn.cmd -pl yudao-module-tk -am -Dtest=TkTiktokPublishP1P2MigrationTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because scheduled app task columns are absent.

- [ ] **Step 3: Add additive schema and data-object fields**

迁移脚本使用 `information_schema` 幂等检查；既有记录字段为空，不改变立即发布语义。

- [ ] **Step 4: Run migration test and verify GREEN**

Run the command from Step 2.

Expected: PASS with zero failures.

### Task 3: 增加定时创建、改期、取消和恢复调度

**Files:**
- Create: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/controller/admin/tiktok/vo/TkTiktokPublishScheduleReqVO.java`
- Create: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/tiktok/TkTiktokScheduledMediaService.java`
- Modify: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/controller/admin/tiktok/vo/TkTiktokPublishCreateReqVO.java`
- Modify: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/controller/admin/tiktok/vo/TkTiktokPublishTaskRespVO.java`
- Modify: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/controller/admin/tiktok/TkTiktokPublishController.java`
- Modify: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/tiktok/TkTiktokPublishService.java`
- Modify: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/tiktok/TkTiktokPublishServiceImpl.java`
- Modify: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/tiktok/TkTiktokPublishStatusSyncJob.java`
- Modify: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/enums/ErrorCodeConstants.java`
- Test: `yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/controller/admin/tiktok/TkTiktokPublishControllerMappingTest.java`
- Test: `yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/service/tiktok/TkTiktokPublishServiceImplTest.java`

**Interfaces:**
- Consumes: optional ISO-8601 `scheduledAt` from create request.
- Produces: `reschedule(Long taskId, LocalDateTime scheduledAt)`、`cancelScheduled(Long taskId)`、`dispatchDueScheduled(int limit)`。

- [ ] **Step 1: Write failing contract and service tests**

覆盖立即发布不变、定时任务不立即提交、到期只认领一次、改期版本递增、取消后不执行、重启扫描数据库恢复和终态清理素材。

- [ ] **Step 2: Run focused tests and verify RED**

Run: `..\..\..\.runtime\apache-maven-3.9.10\bin\mvn.cmd -pl yudao-module-tk -am -Dtest=TkTiktokPublishControllerMappingTest,TkTiktokPublishServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because scheduled app contracts and dispatcher are absent.

- [ ] **Step 3: Implement scheduling and durable media**

创建定时任务前将源视频持久化到配置目录；任务写入 `SCHEDULED`。扫描器按计划时间选取并使用状态和版本条件原子更新为 `PENDING/PROCESSING` 后提交现有执行器。改期、取消均校验数据权限和可变状态，取消或终态后清理本地素材。

- [ ] **Step 4: Run focused tests and verify GREEN**

Run the command from Step 2.

Expected: PASS with zero failures.

### Task 4: 增加发布中心定时交互

**Files:**
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/api/tk/videoPublishCenter/index.ts`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/views/tk/video-publish-center/index.vue`

**Interfaces:**
- Consumes: create 的可选 `scheduledAt`，任务响应的计划字段。
- Produces: 立即/定时选择、日期时间输入、任务列表改期和取消操作。

- [ ] **Step 1: Add frontend API types and UI behavior**

默认立即发布；选择定时时校验未来时间与单账号、`DIRECT_POST` 限制。仅 `SCHEDULED` 任务显示改期和取消操作。

- [ ] **Step 2: Run frontend type verification**

Run from `yudao-ui/yudao-ui-admin-vue3`: `..\..\..\..\..\.runtime\npm-global\node_modules\.bin\pnpm.cmd ts:check`

Expected: exit code 0.

### Task 5: 完整验证、提交和推送

**Files:**
- Verify all changed files.

- [ ] **Step 1: Run backend focused suite**

Run: `..\..\..\.runtime\apache-maven-3.9.10\bin\mvn.cmd -pl yudao-module-tk -am -Dtest=TkOpenTiktokPublishTerminalServiceTest,TkOpenTiktokPublishServiceTest,TkTiktokPublishServiceImplTest,TkTiktokPublishControllerMappingTest,TkTiktokPublishP1P2MigrationTest -Dsurefire.failIfNoSpecifiedTests=false test`

- [ ] **Step 2: Run backend package without tests**

Run: `..\..\..\.runtime\apache-maven-3.9.10\bin\mvn.cmd -pl yudao-module-tk -am -DskipTests package`

- [ ] **Step 3: Inspect diff and repository status**

Run: `git diff --check` and `git status --short`.

- [ ] **Step 4: Commit remaining scheduling and UI changes**

```bash
git add docs yudao-module-tk yudao-ui
git commit -m "feat(tk): schedule app TikTok publishing"
```

- [ ] **Step 5: Push the feature branch**

Run: `git push -u origin codex/tiktok-app-scheduled-terminal-403`

Expected: remote branch updated successfully; no production deployment performed.
