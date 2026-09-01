# TikTok Token Auto Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refresh expiring TikTok access tokens without user interaction, retry exact token failures once, expose accurate account health, and pre-refresh recently active accounts without making real TikTok calls during verification.

**Architecture:** Add a focused token lifecycle service between publishing workflows and `TkTiktokApiClient`. It uses the existing encrypted token columns, a per-account Redisson lock, proactive expiry checks, rotated refresh-token persistence, and one reactive retry on exact TikTok token errors. A scheduled job pre-refreshes recently active accounts, while the account API derives a user-facing health status from stored expiries.

**Tech Stack:** Java 17, Spring Boot, MyBatis-Plus, Redisson, JUnit 5, Mockito, Vue 3, TypeScript, Element Plus.

## Global Constraints

- Do not call real TikTok refresh, publish, upload, creator-info, or status APIs in tests or deployment verification.
- Keep existing public endpoints and request payloads compatible.
- Persist every refresh-token rotation returned by TikTok.
- Retry only exact token-invalid failures and at most once.
- Preserve all unrelated worktree changes.
- Deploy with build, backup, upload, restart, and PID/port/HTTP verification; leave real publishing to users.

---

### Task 1: Typed TikTok token refresh client

**Files:**
- Modify: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/tiktok/TkTiktokApiClient.java`
- Modify: `yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/service/tiktok/TkTiktokApiClientTest.java`

**Interfaces:**
- Produces: `TokenRefreshResult refreshAccessToken(String refreshToken)`.
- Produces: structured `errorCode` fields and `isAccessTokenInvalid()` on creator-info, publish, and status results.

- [ ] Write parsing tests for a complete successful refresh response, rotated refresh token, and TikTok error response.
- [ ] Run `TkTiktokApiClientTest` and verify compilation fails because the refresh API/result does not exist.
- [ ] Add form request construction, response parsing, typed error metadata, and exact token-invalid helpers.
- [ ] Run `TkTiktokApiClientTest` and verify all tests pass without network access.

### Task 2: Central token lifecycle service

**Files:**
- Create: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/tiktok/TkTiktokTokenService.java`
- Create: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/tiktok/TkTiktokTokenServiceImpl.java`
- Create: `yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/service/tiktok/TkTiktokTokenServiceImplTest.java`
- Modify: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/dal/mysql/TkTiktokAccountMapper.java`

**Interfaces:**
- Produces: `String getValidAccessToken(Long accountId)`.
- Produces: `String forceRefreshAccessToken(Long accountId)`.
- Produces: `int refreshExpiringActiveAccounts(int limit)`.

- [ ] Write tests for valid-token reuse, five-minute proactive refresh, rotated-token persistence, missing/expired refresh token, refresh rejection, and lock-time recheck.
- [ ] Run the new test and verify it fails because the service does not exist.
- [ ] Implement encrypted token access, refresh expiry validation, Redisson account lock, database re-read, refresh call, and atomic persistence.
- [ ] Mark only unrecoverable refresh failures as `INVALID/UNAUTHORIZED`; keep transient failures retryable and save a sanitized reason.
- [ ] Add the active-account candidate query using a 30-day activity window and 30-minute access-token threshold.
- [ ] Run the new test and existing TikTok account/auth tests.

### Task 3: Publish and status retry integration

**Files:**
- Modify: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/tiktok/TkTiktokPublishServiceImpl.java`
- Modify: `yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/service/tiktok/TkTiktokPublishServiceImplTest.java`

**Interfaces:**
- Consumes: token lifecycle service methods from Task 2.
- Consumes: exact error classification from Task 1.

- [ ] Write tests proving creator-info and status calls retry once after forced refresh, do not retry non-token errors, and fail when refresh is unrecoverable.
- [ ] Run focused publish tests and verify the new assertions fail against direct token decryption.
- [ ] Replace direct token decryption in initial publish and status synchronization with the lifecycle service.
- [ ] Implement one exact-token-error forced refresh and retry while preserving per-account partial success.
- [ ] Run focused publish tests.

### Task 4: Active-account pre-refresh job

**Files:**
- Create: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/tiktok/TkTiktokTokenRefreshJob.java`
- Create: `yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/service/tiktok/TkTiktokTokenRefreshJobTest.java`

**Interfaces:**
- Consumes: `refreshExpiringActiveAccounts(50)`.

- [ ] Write a test proving the scheduled job delegates to the token service and contains failures without exposing tokens.
- [ ] Run it and verify failure because the job does not exist.
- [ ] Add a configurable 30-minute schedule and bounded batch size of 50.
- [ ] Run the job test.

### Task 5: Accurate account health UI

**Files:**
- Modify: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/controller/admin/tiktok/TkTiktokAccountController.java`
- Modify: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/dal/mysql/TkTiktokAccountMapper.java`
- Modify: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/tiktok/TkTiktokPublishServiceImpl.java`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/views/tk/video-publish-center/index.vue`
- Test: `yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/controller/admin/tiktok/TkTiktokAccountControllerTest.java`

**Interfaces:**
- Produces display status `AUTO_REFRESH` when access is near expiry but refresh authorization remains valid.
- Produces abnormal overview count based on refresh authorization, not the 24-hour access token alone.

- [ ] Write controller/mapper behavior tests for `VALID`, `AUTO_REFRESH`, and `EXPIRED` display states.
- [ ] Run focused tests and verify they fail against stored-only status behavior.
- [ ] Derive account health without exposing token values; show a warning tag and expiry time in the existing table.
- [ ] Make unrecoverable refresh errors explain that reauthorization is required; keep raw TikTok log IDs in publish details.
- [ ] Run backend focused tests and frontend type checking.

### Task 6: Mock-only verification and production deployment

**Files:**
- No source files beyond Tasks 1-5.

- [ ] Run all focused TikTok unit tests and confirm no test creates a real HTTP client call.
- [ ] Run the TK Maven package with tests skipped only after the focused tests pass.
- [ ] Run frontend type checking and production build with the bundled Node runtime.
- [ ] Inspect the focused diff for secrets, accidental token logging, unrelated changes, and retry loops.
- [ ] Check production disk, process, config path, and database columns without reading token values.
- [ ] Back up the current JAR and web directory, upload verified artifacts, restart the backend, and reload Nginx.
- [ ] Verify JAR hash, PID, ports `48080` and `18080`, public page/API HTTP responses, and absence of new startup errors.
- [ ] Do not invoke TikTok refresh or publish endpoints; record real user publishing as the remaining acceptance test.
