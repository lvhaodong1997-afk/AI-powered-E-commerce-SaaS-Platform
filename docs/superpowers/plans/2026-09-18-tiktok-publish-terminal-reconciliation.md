# TikTok Publish Terminal Reconciliation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make A-side TikTok publishing resolve interrupted attempts before sending the existing `publish.success` or `publish.failed` callback, without changing C-side APIs or callback handling.

**Architecture:** Reuse the existing TikTok `video.list` client capability through the open publishing adapter. A detail without `publishId` remains internal `RECOVERY_REQUIRED` while A searches the account's recent videos using the task title and publish time. Only a confirmed match emits the existing success event; only a confirmed absence after the reconciliation window emits the existing failed event. Unknown attempts are never automatically republished.

**Tech Stack:** Spring Boot, MyBatis-Plus, Java 8 source compatibility, JUnit 5, Mockito, MySQL 8.

## Global Constraints

- C-side API paths, request signatures, callback URLs, and event names remain unchanged.
- C-side receives only the existing `publish.success` and `publish.failed` terminal events.
- Do not automatically republish an attempt whose TikTok request may already have been accepted.
- Preserve unrelated working-tree changes.
- Do not log access tokens, client secrets, or signed media URLs.

---

### Task 1: Add failing reconciliation tests

**Files:**
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/service/open/tiktok/TkOpenTiktokPublishServiceTest.java`
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/service/open/platform/TkOpenTiktokPlatformAdapterTest.java` if the existing test file is present

**Interfaces:**
- Consumes: `TkOpenPublishPlatformAdapter.listRecentVideos(String, Long, Integer)` and a reconciliation method on `TkOpenTiktokPublishService`.
- Produces: tests proving a confirmed recent video produces the existing success callback, while an unresolved attempt never produces a success or failed callback and is not resubmitted.

- [ ] Write tests for a recovered video match and for an unresolved attempt.
- [ ] Run the focused tests and confirm they fail because the adapter/service methods do not yet exist.

### Task 2: Expose existing TikTok video.list through the adapter

**Files:**
- Modify: `.../yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/open/platform/TkOpenPublishPlatformAdapter.java`
- Modify: `.../yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/open/platform/TkOpenTiktokPlatformAdapter.java`

**Interfaces:**
- Consumes: existing `TkTiktokApiClient.listVideos` and `TkTiktokApiClient.VideoInfo`.
- Produces: a platform-neutral recent-video result containing video ID, create time, title/description, duration, share URL, and failure metadata.

- [ ] Add the smallest adapter result type and method needed to list one recent page.
- [ ] Delegate to the existing TikTok client and map access-token failures consistently.
- [ ] Run the adapter tests.

### Task 3: Reconcile unknown publish attempts before terminal callback

**Files:**
- Modify: `.../yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/open/tiktok/TkOpenTiktokPublishService.java`
- Modify: `.../yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/open/tiktok/TkOpenTiktokPublishStatusJob.java`
- Modify: `.../yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/dal/mysql/openapi/TkOpenTiktokPublishDetailMapper.java` only if a focused query is required

**Interfaces:**
- Consumes: details with `publishId` through the existing status endpoint and details without `publishId` through recent-video reconciliation.
- Produces: existing `publish.success` or `publish.failed` only after a confirmed platform result.

- [ ] Add a bounded reconciliation pass for `RECOVERY_REQUIRED` details.
- [ ] Match only within a narrow publish-time window and with the requested title/description; leave ambiguous matches unresolved.
- [ ] On a confirmed match, persist the public post ID when available and emit the existing success event once.
- [ ] On confirmed absence, keep the attempt blocked until the configured reconciliation deadline; then emit the existing failed event with the existing payload shape.
- [ ] Ensure the recovery pass cannot call `initVideoPost` again.
- [ ] Run the service tests and verify no new callback event name is used.

### Task 4: Add persistence and callback regression coverage

**Files:**
- Modify: `.../yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/service/open/tiktok/TkOpenTiktokPublishServiceTest.java`
- Modify: `.../yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/service/open/api/TkOpenApiCallbackServiceTest.java`
- Modify: `.../yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/dal/mysql/openapi/TkOpenPublishRecoveryMapperTest.java` if query coverage is needed

**Interfaces:**
- Consumes: the reconciliation state transitions and existing callback dedupe key.
- Produces: regression evidence for restart recovery, success callback, failed callback, ambiguous match, and repeated scans.

- [ ] Verify repeated scans emit one terminal callback.
- [ ] Verify ambiguous and missing matches do not emit an incorrect terminal callback before the deadline.
- [ ] Verify a later retry attempt has an independent callback dedupe key.
- [ ] Run focused tests, then the TK module tests.

### Task 5: Build, commit, push, and deploy

**Files:**
- Modify only the implementation, tests, migration, and plan files selected above.

- [ ] Build `yudao-server.jar` and calculate SHA-256.
- [ ] Review `git diff` and exclude unrelated UI changes.
- [ ] Commit the selected changes and push `main`.
- [ ] Back up the production JAR, config, and publishing tables.
- [ ] Upload and atomically replace the JAR, restart `tk-yudao-prod`, and verify PID, ports, local HTTP, and public HTTP.
- [ ] Verify callback reconciliation counts and absence of new duplicate publish attempts.
