# TikTok P1/P2 Production Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent TikTok content synchronization and automatic public-link capture from failing on long external titles, allow account deletion when historical soft-deleted rows share the same `open_id`, and make generic TK failures actionable in the UI.

**Architecture:** Keep the existing TikTok services and MyBatis-Plus logical deletion flow. Store external titles as `TEXT`, and replace the account uniqueness rule with a generated active-only key so deleted history retains its original `open_id` while active accounts remain unique per tenant. No public API contract changes.

**Tech Stack:** Java, Spring Boot, MyBatis-Plus, MySQL, JUnit 5, Mockito, Maven.

## Global Constraints

- Preserve existing TikTok authorization, content sync, publish, and manual publish-URL behavior.
- Do not physically delete production business records.
- Keep tenant and company data-scope validation unchanged.
- Do not expose credentials in source, logs, commits, or final reporting.
- Verify the feature branch before pushing, then merge and verify `main` before packaging.

---

### Task 1: Add regression tests for long titles and account uniqueness migration

**Files:**
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/service/tiktok/TkTiktokContentDisplayServiceImplTest.java`
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/service/tiktok/TkTiktokPublishServiceImplTest.java`
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/service/tiktok/TkTiktokAccountServiceImplTest.java`
- Test contract: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/main/resources/sql/tk_tiktok_publish_p1p2_upgrade_mysql.sql`

**Interfaces:**
- Tests must exercise the existing private persistence paths through `ReflectionTestUtils` and Mockito captures.
- The migration test is a text-level contract test for `TEXT` titles and the active-only generated uniqueness key.

- [ ] **Step 1: Write the failing tests**

Add assertions that a 300-character `VideoInfo.title` is passed unchanged to `TkTiktokContentVideoMapper.insert`, a 300-character public post title is passed unchanged to `TkTiktokPublishPostMapper.insert`, and the account migration contains `active_open_id` plus the replacement unique key while dropping the old key.

- [ ] **Step 2: Run the focused tests to verify RED**

Run from the nested source root:

```powershell
..\..\..\.runtime\apache-maven-3.9.10\bin\mvn.cmd -pl yudao-module-tk '-Dtest=TkTiktokContentDisplayServiceImplTest,TkTiktokPublishServiceImplTest,TkTiktokAccountServiceImplTest' '-DargLine=-Dnet.bytebuddy.experimental=true' test
```

Expected: the new title and migration assertions fail against the current `varchar(255)` schema contract; existing tests remain otherwise executable.

- [ ] **Step 3: Commit test-only changes**

```powershell
git add yudao-module-tk/src/test/java
git commit -m "test(tk): cover long TikTok titles and account deletion history"
```

### Task 2: Fix TikTok title persistence and account uniqueness

**Files:**
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/main/resources/sql/tk_tiktok_publish_p1p2_upgrade_mysql.sql`
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/main/resources/sql/tk_tiktok_publish_center_mysql.sql`
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-ui/yudao-ui-admin-vue3/src/config/axios/service.ts`

**Interfaces:**
- Existing service method signatures remain unchanged.
- The migration remains idempotent and supports existing installations.

- [ ] **Step 1: Change external title columns to `TEXT`**

Add guarded `ALTER TABLE ... MODIFY COLUMN title TEXT` statements for `tk_tiktok_content_video` and `tk_tiktok_publish_post`, and use `TEXT` in their `CREATE TABLE` definitions.

- [ ] **Step 2: Replace the account uniqueness rule**

Add a nullable stored generated column:

```sql
`active_open_id` varchar(128) GENERATED ALWAYS AS (IF(`deleted` = b'0', `open_id`, NULL)) STORED
```

Drop `uk_tk_tiktok_account_open_id` when present and create `uk_tk_tiktok_account_active_open_id` on `(tenant_id, active_open_id)`. Multiple deleted rows can then retain their original identity while active rows remain unique.

- [ ] **Step 3: Keep service behavior unchanged except for explicit deletion intent**

Retain `deleteAccount` logical deletion and data-scope checks. Add a mapper-level helper only if the generated-column migration requires a MyBatis mapping refresh; do not catch and suppress duplicate-key exceptions or physically delete rows.

- [ ] **Step 4: Run the focused tests to verify GREEN**

```powershell
..\..\..\.runtime\apache-maven-3.9.10\bin\mvn.cmd -pl yudao-module-tk '-Dtest=TkTiktokContentDisplayServiceImplTest,TkTiktokPublishServiceImplTest,TkTiktokAccountServiceImplTest' '-DargLine=-Dnet.bytebuddy.experimental=true' test
```

Expected: all focused tests pass with zero failures.

### Task 3: Verify and deliver through production

**Files:**
- Modify only files proven necessary by Tasks 1-2, including the scoped Axios error-message mapping.
- Build artifact: `yudao-server/target/yudao-server.jar` or the repository's configured server artifact.

**Interfaces:**
- Deployment target: `tk-yudao-prod` on `175.155.64.171:22100`.
- Production app directory: `/data/Tk/current/app`.
- Production backend: `127.0.0.1:48080`; Nginx: `18080`.

- [ ] **Step 1: Run the TK module test suite and frontend check**
- [ ] **Step 2: Review the diff and commit the feature branch**
- [ ] **Step 3: Push the feature branch and merge it into `main`**
- [ ] **Step 4: Build from the merged `main` commit**
- [ ] **Step 5: Back up the production JAR and database metadata, apply the idempotent migration, and atomically replace the JAR**
- [ ] **Step 6: Restart only `tk-yudao-prod` and verify systemd, PID, ports, HTTP, account/content endpoints, and timestamp-filtered logs**

## Self-Review

- The title fix covers both content listing and post-publication persistence paths.
- The account migration preserves deleted row data and changes only the uniqueness projection.
- No existing controller or API request/response contract is changed.
- Deployment verification must distinguish local build evidence from live server evidence.
