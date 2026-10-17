# TikTok Publish Fair Scheduling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make TikTok publish dispatch deterministic and fair by prioritizing aged pending work while reserving capacity for recent work, with recovery work isolated from new initialization.

**Architecture:** Keep the existing prepare, upload, and recovery executors. Replace competing immediate and periodic submissions with one database-backed selector that uses a 3:1 aged-to-recent admission policy and a 15-minute starvation guard. Existing lease fencing and `RECOVERY_REQUIRED` reconciliation remain authoritative.

**Tech Stack:** Spring Boot, MyBatis-Plus, MySQL, JUnit 5, Maven.

## Global Constraints

- Only modify this repository's TK module and its migration/tests.
- Preserve existing publish APIs, state values, TikTok calls, owner fencing, and recovery semantics.
- Never initialize a detail that already has `publish_id`.
- Preserve unrelated untracked `tmp/` files.

---

### Task 1: Add deterministic dispatch policy tests

**Files:**
- Create: `yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/service/open/tiktok/TkOpenTiktokPublishDispatchPolicyTest.java`

- [ ] **Step 1: Write tests** for aged-first selection, 3:1 aged/recent fairness, starvation promotion, and exclusion of recovery rows.
- [ ] **Step 2: Run the focused test** and confirm it fails because the policy class does not exist.

### Task 2: Implement selector and unify dispatch entry points

**Files:**
- Create: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/open/tiktok/TkOpenTiktokPublishDispatchPolicy.java`
- Modify: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/open/tiktok/TkOpenTiktokPublishService.java`
- Modify: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/open/tiktok/TkOpenTiktokPublishStatusJob.java`
- Modify: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/dal/mysql/openapi/TkOpenTiktokPublishDetailMapper.java`

- [ ] **Step 1:** Add pure policy selection with `AGE_THRESHOLD=10m`, `STARVATION_THRESHOLD=15m`, and a 3:1 aged/recent quota.
- [ ] **Step 2:** Add one scheduler wake-up path; after-commit submission only wakes it, while the periodic scan performs the bounded selection.
- [ ] **Step 3:** Select only `PENDING` rows without `publish_id`, ordered by `create_time,id`; leave rejected rows durable and pending.
- [ ] **Step 4:** Keep recovery/status/callback lanes independent and preserve existing fencing.
- [ ] **Step 5:** Run the focused test and backend module tests.

### Task 3: Add the queue index migration

**Files:**
- Create: `yudao-module-tk/src/main/resources/sql/tk_tiktok_publish_dispatch_upgrade_mysql.sql`
- Modify: `yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/sql/TkSqlMigrationIdempotencyTest.java`

- [ ] **Step 1:** Add an idempotent `(status, create_time, id)` index for pending selection.
- [ ] **Step 2:** Add migration idempotency coverage and run the focused SQL test.

### Task 4: Verify and release

- [ ] **Step 1:** Run focused dispatch tests and the TK Maven module tests.
- [ ] **Step 2:** Review `git diff --check` and ensure only intended files are staged.
- [ ] **Step 3:** Commit the implementation, push the branch, package the backend, back up the live JAR, atomically replace it, restart only `tk-yudao-prod`, and verify PID, ports, HTTP, service state, and release hash.
