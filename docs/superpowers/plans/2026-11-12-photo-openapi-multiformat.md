# Photo OpenAPI and Multi-format Upload Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Deliver the confirmed photo OpenAPI and shared asynchronous JPEG/WebP/PNG/GIF/HEIC/HEIF normalization through push and verified production deployment.

**Architecture:** Extend existing media and upload sessions with durable conversion state and leases. Preserve the video path and publishing journal; persist ordered PHOTO associations and use the existing TikTok photo initializer. Web and OpenAPI share conversion while keeping their ownership systems separate.

**Tech Stack:** Java 17, Spring, MyBatis, MySQL 8, Vue 3, ImageMagick with libheif, OSS.

## Global Constraints

- Approved requirements: `docs/superpowers/specs/2026-11-12-photo-openapi-multiformat-design.md`; use its exact limits, routes, ownership, status, retry and storage requirements.
- Product root P = `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/`; Java base J = P + `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/`; frontend F = P + `yudao-ui/yudao-ui-admin-vue3/`.
- Preserve all existing video API defaults and contracts; originals private, final JPEGs in tk-video-create. Do not migrate the material library.
- No real TikTok post during verification. Do not reinitialize existing publish IDs or unknown init outcomes.
- TDD: record a meaningful failing test before implementation, then green commands and output. Use narrow tests per cycle and the complete related suite before commit.
- Production has extra patches. Copy the live release and patch only a reviewed entry allowlist; assert every other JAR entry hash is identical. Retain identity/legal/verification assets.
- Local Maven at `C:/Users/lhd/Documents/TK自动混剪SaaS产品/.runtime/apache-maven-3.9.10/bin/mvn.cmd`, Java at `C:/Program Files/Eclipse Adoptium/jdk-17.0.19.10-hotspot`.

### Task 1: Shared normalization and durable upload lifecycle

**Files:** Create J `service/media/TkPhotoNormalizationService.java` and focused conversion/recovery components. Modify J `service/open/tiktok/TkOpenTiktokMediaService.java`, media controller/VO/DO/mapper; J `service/upload/TkUploadSessionService.java`, upload session DO/mapper; J `service/tiktok/TkTiktokPublishMediaServiceImpl.java` and photo controller. Extend OSS exact policy/private original helpers and config only as needed. Add P `yudao-module-tk/src/main/resources/sql/tk_open_tiktok_photo_multiformat_upgrade_mysql.sql`, tests and actual image fixtures.

**Interfaces:** Produce shared `normalize(Path source, Path target)` result carrying width, height, bytes and final SHA256; use a dedicated result type if needed. OpenAPI `create(..., mediaType)` keeps original overload; PHOTO complete returns PROCESSING quickly, polling returns READY only after CAS conversion succeeds. Admin POST `/tk/tiktok-publish/media/photo/uploads` returns uploadId/status; GET `/{uploadId}` returns status, failReason and successful numeric mediaId/fileUrl; DELETE cancels. Keep existing synchronous JPEG upload endpoint unchanged. Persist each owner separately; no shared OpenAPI/admin IDs.

- [ ] Write failed tests for valid PNG/GIF, unsupported magic, oversize/pixel bombs, conversion timeout, cancellation/lease CAS, retry/recovery, client and tenant ownership, and unchanged VIDEO behavior. Run focused Maven tests and record RED.
- [ ] Implement whitelist ImageMagick CLI arguments (no shell), local-only input/output, restrictive coder policy, bounded resources/time/concurrency, first frame, orientation, white background, sRGB, long edge 1920/short edge 1080 without upscaling, final true JPEG <=20MiB. Reject disguised unsupported files.
- [ ] Implement durable persisted conversion using claim tokens/lease and expired-lease recovery, bounded polling batches, source ETag/SHA binding and independent final SHA. Cancelled/expired workers cannot publish READY; cleanup only task-owned scratch/output keys.
- [ ] Add PHOTO OpenAPI upload including exact private ACL/key/content type/length policy, verify source object privacy and identity; keep existing LOCAL chunk path and signed body limits. Store source fields separately from final READY fields. Originals may remain until session retry window ends, then cleanup safely.
- [ ] Add admin async upload/session worker sharing the normalizer; preserve synchronous JPEG endpoint response. Add idempotent SQL for both owner tables and conversion recovery indexes, defaults preserving old rows.
- [ ] Run narrow tests and related media/upload regression suite; commit scoped implementation and write report with RED/GREEN evidence and integration signatures.

### Task 2: Ordered photo OpenAPI publishing and lifecycle protections

**Files:** Modify J `controller/open/tiktok/vo/TkOpenTiktokPublishVO.java`, `service/open/tiktok/TkOpenTiktokPublishService.java`, platform adapters, task DO/mapper, terminal handling/callback, file reference and cleanup mappers. Create ordered task-media DO/mapper. Extend the same SQL. Add publish/recovery/mapper tests.

**Interfaces:** Consume Task 1 READY media with mediaType PHOTO and immutable final JPEG fields. VIDEO mediaId remains required by service; PHOTO uses ordered photoMediaIds. `TkOpenPublishPlatformAdapter` gains backward-compatible photo initialization delegation to `TkTiktokApiClient.initPhotoPost`; all prechecks complete before journal-fenced one-time init.

- [ ] Write and run RED tests: PHOTO order/cover persisted; foreign/not-READY/nonphoto rejected; video regression; title/caption conditional limits; duplicate idempotency digest includes order/music; inbox scope; unknown init fence; photo terminal success independent of video display metrics; cleanup sees active associations.
- [ ] Add default VIDEO mediaType and PHOTO request/response fields, conditional validation (1-35, title90/caption4000 UTF16), reject simultaneous video/photo fields and inapplicable duet/stitch/music. Inbox maps MEDIA_UPLOAD with video.upload; direct uses creator privacy constraints.
- [ ] Persist unique ordered associations transactionally and make task mediaId nullable for PHOTO. Load ordered photos, verify owner/type/READY, prepare all immutable direct-pull URLs then invoke existing photo API with cover/music/title/caption. Preserve attempts, result journal, init ambiguity, scheduling, retry/reschedule/cancel, callback and query behavior.
- [ ] Add association references to candidate, claim and physical delete checks; protect pending/scheduled/processing/download/recovery. Apply photo-specific terminal status so absent video share URL/metrics do not block success.
- [ ] Run focused and complete related publish tests, MySQL SQL validation where available; commit and write report.

### Task 3: Web asynchronous photo flow and public integration documentation

**Files:** Modify F `src/utils/tiktokPhotoPublish.ts`, `src/api/tk/videoPublishCenter/index.ts`, `src/views/tk/video-publish-center/index.vue`, `tests/tiktok-photo-publish.test.cjs`; docs `tiktok-open-api.md`, `openapi/tiktok-open-api.json`, `openapi/tiktok-open-api-c-party-integration.md`, `sdk/tiktok-open-api-{python,node,java}.md`, `api/tiktok-photo-publishing.md`.

**Interfaces:** Consume Task 1 admin async endpoints, submit only resulting numeric admin mediaIds; OpenAPI documentation consumes Task 1/2 wire fields. Browser does not decode HEIC.

- [ ] Add and run failing frontend behavioral tests for selecting new formats, server upload/polling, cancellation/stale completion, retries, final preview, sorting/cover and submit disabled while conversion pending.
- [ ] Implement server conversion flow with upload/processing/ready/failed UI, bounded polling/backoff, abort/cancel on removal, isolated retries, final JPEG preview. Show static-frame/white-background conversion note. Preserve video UI.
- [ ] Update OpenAPI JSON and SDK examples for PHOTO upload/complete/poll/create/query; accurately document private ACL form field, signing/local chunk size, UTF16 limits, PHOTO-only options, idempotency and callbacks. Keep video examples compatible.
- [ ] Run frontend tests, typecheck, production build and JSON parsing; commit and write report.

### Task 4: Review, push and production deployment

**Files:** Reviewed deploy configuration/scripts only; untracked stage/manifest/receipts under tmp. No secret in Git.

**Interfaces:** Consume reviewed compiled classes, frontend output and migration. Use existing SSH host verification and service tk-yudao-prod; production current is discovered afresh.

- [ ] Read-only inventory active release, SHA, job drain, schema, decoder dependencies and disk. Validate real HEIC/HEIF and other format fixtures through deployed decoder before advertising support.
- [ ] Independent whole-change review and resolve important issues; run all affected backend tests and web verification; fetch and push authorized branch with proxy.
- [ ] Back up live release/config/schema; prepare new release from live baseline, allowlist patched classes/resources, overlay frontend retaining existing identity and legal assets. Assert untouched entries hashes. Apply idempotent SQL, install pinned supported decoder/policy and execute real conversion/privacy/OSS/MySQL smoke.
- [ ] Recheck no conflicting active jobs, stop safely, activate atomically, start and check health/API routes/assets/logs and anonymous final GET/Range versus private original403. On activation failure restore old release and verify health. Never reset business tasks.
- [ ] Remove only enumerated test objects, retain rollback release and receipts; report commit and deployed version with actual validation scope.
