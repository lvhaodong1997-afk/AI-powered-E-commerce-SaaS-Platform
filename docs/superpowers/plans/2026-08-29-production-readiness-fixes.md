# TK Production Readiness Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the confirmed production release blockers without changing existing public API paths or the product architecture.

**Architecture:** Keep the existing Spring Boot, MyBatis-Plus tenant interceptor, Vue 3, Element Plus, and TK locale catalog. Add ownership to transcript tasks, make the risky migration independently idempotent, sanitize frontend presentation, and align stale tests with the already-approved material fallback behavior.

**Tech Stack:** Java 8 target bytecode, Spring Boot 2.7, MyBatis-Plus, JUnit 5, Mockito 4.11, MySQL 8, Vue 3, TypeScript, Vite, Element Plus, Node scripts.

## Global Constraints

- Make the smallest changes that preserve existing architecture and public HTTP paths.
- Do not deploy production, restart services, execute SQL, commit, or discard unrelated working-tree changes.
- Do not expose signed OSS query parameters in rendered page text or tooltips.
- Keep the material fallback rule: a duration or segment shortage is a warning when at least one usable source exists.
- Run Maven tests with JDK 17 because Mockito 4.11 and its current Byte Buddy dependency fail under the configured JDK 21 runtime.
- Production frontend builds require Node `>=20.19.0`; report a blocker if that runtime is unavailable.

---

### Task 1: Protect transcript extraction tasks by permission and ownership

**Files:**
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/controller/admin/reference/TkReferenceAnalysisControllerTest.java`
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/service/reference/TkOpenVideoTranscriptExtractServiceImplTest.java`
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/controller/admin/reference/TkOpenVideoTranscriptExtractController.java`
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/dal/dataobject/TkOpenVideoTranscriptTaskDO.java`
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/reference/TkOpenVideoTranscriptExtractServiceImpl.java`
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/main/resources/sql/tk_open_video_transcript_task_mysql.sql`
- Create: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/main/resources/sql/tk_open_video_transcript_task_tenant_upgrade_mysql.sql`

**Interfaces:**
- Consumes: `TkDataScopeService.getCurrentScope()` and `validateReadable(tenantId, companyId, creator)`.
- Produces: the same three HTTP paths under `/tk/open/video/transcript`, now authenticated and tenant/creator scoped.

- [ ] Replace the controller reflection test so create and sync require `tk:reference:analyze`, query requires `tk:reference:query`, and none of the three methods carries `@PermitAll` or `@TenantIgnore`.
- [ ] Add service tests proving create stores the current tenant/company, query calls `validateReadable`, and the async runnable restores the captured tenant with `TenantUtils.execute`.
- [ ] Run the two focused tests and confirm they fail for the expected missing permission/ownership behavior.
- [ ] Change `TkOpenVideoTranscriptTaskDO` to extend `TenantBaseDO` and add nullable `companyId`.
- [ ] Resolve `TkUserScope` during create, set tenant/company explicitly, capture the tenant before scheduling, and wrap `runExtractTask` in `TenantUtils.execute`.
- [ ] Validate readable ownership before mapping a task response.
- [ ] Add `tenant_id` and `company_id` to the create-table SQL and an idempotent upgrade that adds each missing column and tenant-oriented indexes independently.
- [ ] Run the focused tests with JDK 17 and require zero failures.

### Task 2: Make TikTok publish-center migration independently idempotent

**Files:**
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/main/resources/sql/tk_tiktok_publish_center_upgrade_mysql.sql`
- Create: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/sql/TkSqlMigrationIdempotencyTest.java`

**Interfaces:**
- Consumes: MySQL 8 `information_schema.columns`.
- Produces: repeatable migration for five task columns and five detail columns.

- [ ] Add a resource-level JUnit test requiring one independent existence check and prepared statement for every added column; reject a combined multi-column `ADD COLUMN` statement.
- [ ] Run the test and confirm it fails against the current combined `ALTER TABLE` statements.
- [ ] Split nullable `generation_task_id` modification and each added column into independent guarded statements for both tables.
- [ ] Run the focused SQL test and require zero failures.

### Task 3: Correct production frontend presentation and sensitive URL rendering

**Files:**
- Create: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-ui/yudao-ui-admin-vue3/tests/production-presentation.test.cjs`
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-ui/yudao-ui-admin-vue3/index.html`
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-ui/yudao-ui-admin-vue3/package.json`
- Create: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-ui/yudao-ui-admin-vue3/scripts/check-prod-branding.mjs`
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-ui/yudao-ui-admin-vue3/src/hooks/web/useTitle.ts`
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-ui/yudao-ui-admin-vue3/src/views/Login/Login.vue`
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-ui/yudao-ui-admin-vue3/src/views/tk/material-library/index.vue`
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-ui/yudao-ui-admin-vue3/src/views/tk/video-publish-center/index.vue`
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-ui/yudao-ui-admin-vue3/src/views/tk/creative-workshop/index.vue`
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-ui/yudao-ui-admin-vue3/src/locales/tk/zh-CN.ts`
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-ui/yudao-ui-admin-vue3/src/locales/tk/en.ts`
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-ui/yudao-ui-admin-vue3/scripts/check-tk-hardcoded-i18n.mjs`
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/enums/ErrorCodeConstants.java`

**Interfaces:**
- Consumes: existing `useTkI18n`, `formatDate`, and Vite `%VITE_APP_TITLE%` substitution.
- Produces: safe display labels while preserving actual URLs for API requests and link navigation.

- [ ] Add a static Node test requiring a title fallback, 1 GB material limit, localized overview labels, formatted timestamps, safe video labels, no raw `outputUrl`/`videoUrl` interpolation, and no developer placeholder messages.
- [ ] Run the test and confirm it fails for the current presentation defects.
- [ ] Make `useTitle` fall back to `ClipForge Studio` and avoid leading separators when either title is empty.
- [ ] Keep Vite title substitution but add a post-build guard that fails when `%VITE_APP_TITLE%` remains or `ClipForge Studio` is absent from `dist-prod/index.html`.
- [ ] Use `ClipForge Studio` as the login hero title and replace unverified numerical metrics with qualitative workflow, access, and confirmation facts.
- [ ] Change material upload validation and copy to `1_000_000_000` bytes / `1GB`.
- [ ] Replace the backend's hardcoded `100MB` upload error text with limit-neutral copy so configuration remains the source of truth.
- [ ] Render only query-free filenames for generated and task video URLs; format numeric or string timestamps through a local formatter backed by `formatDate`.
- [ ] Move the four overview card labels into the TK locale catalog.
- [ ] Remove developer-facing placeholder messages; disable controls whose only behavior is a placeholder request while retaining the local prompt-template action.
- [ ] Exempt `data-dashboard` and `generation-batch` from the hardcoded scanner because each already owns a complete zh/en copy table covered by the catalog coverage check.
- [ ] Run the presentation test, `tk:i18n:check`, and `ts:check`.

### Task 4: Align stale backend tests with approved behavior

**Files:**
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/service/generation/TkGenerationPrecheckServiceImplTest.java`
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/service/material/TkMaterialVideoServiceImplTest.java`

**Interfaces:**
- Consumes: existing warning-based material fallback and batch-delete implementation.
- Produces: tests that reflect current approved behavior without changing production logic.

- [ ] Change shortage tests to assert `passed=true`, warning codes and actionable metadata, while retaining hard failures for an empty library.
- [ ] Change deletion tests to verify `deleteBatchIds(Collections.singletonList(id))`; keep the no-delete-on-file-cleanup-failure assertion.
- [ ] Run all six originally failing test classes under JDK 17 and require 58 tests with zero failures/errors.

### Task 5: Integrated verification and release inventory

**Files:**
- No production file changes unless a previous task's verification reveals a direct regression.

**Interfaces:**
- Consumes: Tasks 1-4.
- Produces: verified local JAR/frontend artifacts and a precise list of remaining deployment prerequisites.

- [ ] Run focused transcript, SQL, precheck, material, TikTok, token, and voice tests under JDK 17.
- [ ] Run the TK module test suite under JDK 17.
- [ ] Run frontend presentation tests, i18n check, type check, and production build under Node `>=20.19.0`.
- [ ] Build `yudao-server` with dependencies and confirm the TK module and migration resources are embedded.
- [ ] Review only the files changed for this plan and report any pre-existing failures separately.
- [ ] Do not deploy; provide exact validated artifacts and the remaining production DB/JAR/frontend synchronization steps.
