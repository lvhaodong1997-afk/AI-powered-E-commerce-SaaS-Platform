# TikTok User Video Publish Implementation Plan

> **For agentic workers:** This plan is executed inline in the current session. Existing unrelated worktree changes must be preserved.

**Goal:** Allow TikTok publish tasks to use either a generated video or a user-uploaded video while preserving existing generated-video requests and automating QR authorization polling.

**Architecture:** Add a tenant-scoped publish-media record and upload endpoints, then make the existing publish task resolve one of two video sources. Keep the current TikTok publishing adapter and account fan-out unchanged except for source and cover metadata. Fix QR authorization at the request/parse boundary and let the frontend poll until a terminal state.

**Tech Stack:** Spring Boot, MyBatis-Plus, existing TK upload/file APIs, Vue 3, TypeScript, TikTok Content Posting API.

## Global Constraints

- Preserve all existing user changes and generated files.
- Keep `generationTaskId` requests backward compatible.
- Do not change production systems or deployment files in this task.
- Validate uploaded media by tenant, owner, MIME type, size, duration, and dimensions.
- Do not claim independent image-cover support until TikTok API behavior is verified; use timestamp cover as the compatible fallback.

### Task 1: Publish media model and upload API

**Files:**
- Create: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/dal/dataobject/TkTiktokPublishMediaDO.java`
- Create: matching Mapper, Service, Controller, and request/response VOs under the existing TK package layout
- Modify: existing TK upload/storage helpers only where required for reuse
- Create: `yudao-module-tk/src/main/resources/sql/tk_tiktok_publish_media_upgrade_mysql.sql`
- Test: focused service/controller tests for ownership and completion validation

Implement a tenant-scoped media record, chunk upload session lifecycle, media metadata validation, cover URL/timestamp fields, and cleanup of expired incomplete media. Reuse existing file storage and chunk upload behavior.

### Task 2: Extend publish source resolution

**Files:**
- Modify: `TkTiktokPublishCreateReqVO.java`
- Modify: `TkTiktokPublishServiceImpl.java`
- Modify: `TkTiktokPublishTaskDO.java`, `TkTiktokPublishDetailDO.java`, their response VOs and mappers
- Modify: `tk_tiktok_publish_center_mysql.sql` or add an idempotent upgrade script
- Test: publish service tests for generated and uploaded source paths and the XOR validation

Make `generationTaskId` and `uploadedVideoId` mutually exclusive and required as a pair. Persist `sourceType`, uploaded media ID, video URL, cover URL, and timestamp. Continue using the existing account fan-out, retry, upload-source selection, and status sync.

### Task 3: Publish-center upload and metadata UI

**Files:**
- Modify: `src/api/tk/videoPublishCenter/index.ts`
- Modify: `src/views/tk/video-publish-center/index.vue`
- Create components only if the current drawer cannot stay readable without them
- Test: focused frontend type check and existing publish UI tests

Add source switching, upload progress, preview, title/caption editing, cover timestamp selection, and submit payload wiring. Surface an explicit limitation if the TikTok account/API cannot accept an independent image cover.

### Task 4: QR authorization repair and automatic polling

**Files:**
- Modify: QR authorization backend service/controller and the account authorization frontend page/API
- Test: regression test for decoded callback values and terminal-state polling

Use the QR-specific authorization parameters, fully decode callback content, poll at a bounded interval, stop on success/failure/expiry, and clear timers on unmount or dialog close.

### Task 5: Verification

Run the narrowest backend tests first, then the TK module test command and frontend `ts:check`. Review the focused diff and confirm no production/deployment files changed.
