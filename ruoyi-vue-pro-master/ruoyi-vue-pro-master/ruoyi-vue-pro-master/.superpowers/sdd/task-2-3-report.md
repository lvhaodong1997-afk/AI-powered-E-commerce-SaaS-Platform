# Task 2-3 本应用定时发布后端实施报告

日期：2026-09-24
分支：`codex/tiktok-app-scheduled-terminal-403`
基线：`c2201c1 fix(tk): terminalize expired TikTok uploads`

## 实施结果

- 创建请求支持可选 `scheduledAt`，未传时保持原立即发布流程。
- 定时发布仅允许未来时间、单账号及 `DIRECT_POST`。
- 新增改期与取消接口：改期使用 JSON `{taskId, scheduledAt}`；取消使用 `taskId` 查询参数。
- 数据库记录计划时间、调度状态、版本、执行时间和持久化素材状态，并增加到期扫描索引。
- 到期任务通过状态及版本条件更新原子认领，认领后复用现有发布执行器。
- 定时素材复制或下载到配置的持久化根目录，执行、取消及终态清理均限制在安全根目录内。
- 调度基于数据库定期扫描，服务重启后可继续派发到期任务。
- 增量迁移、新环境建表和中文字段注释已同步更新。

## TDD 证据

RED：初始聚焦测试因缺少 `TkTiktokPublishScheduleReqVO` 无法编译；随后迁移回归测试发现新环境建表 SQL 将 8 个定时字段错误放入 `tk_tiktok_auth_session`。
GREEN：修复生产契约、SQL 表归属及 Mockito matcher 后，聚焦套件共 29 项全部通过：Controller 2、migration 2、service 24、scheduled media 1。

验证命令：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
& 'C:\Users\lhd\Documents\TK自动混剪SaaS产品\.runtime\apache-maven-3.9.10\bin\mvn.cmd' -pl yudao-module-tk -am '-Dtest=TkTiktokPublishControllerMappingTest,TkTiktokPublishServiceImplTest,TkTiktokAppScheduledPublishMigrationTest,TkTiktokScheduledMediaServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

结果：`Tests run: 29, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`。
`git diff --check`：通过。

## 自审与范围

- 已确认 Controller 契约为 JSON 改期和查询参数取消。
- 已确认定时字段只存在于 `tk_tiktok_publish_task`，不污染授权会话表。
- 已确认立即发布兼容、定时任务不进入立即队列、改期版本递增、取消阻止执行、原子认领及安全清理均有聚焦测试。
- 按要求未运行无关全量套件；未修改或提交前端文件；未部署服务器。

## 关注事项

- 上线前必须先执行增量迁移，并确认 `tk.generation.upload.scheduled-publish-root-dir` 指向持久化且容量充足的目录。
- 前端改动和前端测试仍保留在工作树中，未包含在本次后端提交。
