# Social Video OSS Upload 1GB Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move uploaded social videos to browser-to-OSS upload and raise the accepted video size to 1 GiB without routing the video bytes through the application server.

**Architecture:** Keep the existing multipart endpoint for JPEG and backward compatibility. Add social-video upload-session and upload-complete endpoints that reuse the existing OSS POST policy signer, upload session ownership checks, and OSS HEAD verification. The browser posts the video directly to OSS, then the backend creates the READY social-media row from the verified object and browser media metadata.

**Tech Stack:** Spring Boot, MyBatis-Plus, Aliyun OSS POST policy, Vue 3, TypeScript, Axios/XMLHttpRequest, JUnit 5, Mockito.

## Global Constraints

- Maximum social video size is `1L * 1024 * 1024 * 1024` bytes.
- Only MP4 videos use the new direct OSS flow; JPEG upload behavior remains unchanged.
- OSS credentials remain external configuration and are never written to source or logs.
- The backend validates tenant/company ownership, exact object-key prefix, object size, and upload-session state before creating media.
- Existing `/tk/social-publish/media/upload` remains available for compatibility.
- Do not deploy until focused backend tests, frontend type check, production builds, and live health checks pass.

---

### Task 1: Define the 1GB limit and direct-upload contracts

**Files:**
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/social/TkSocialMediaService.java`
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/upload/TkUploadSessionService.java`
- Create: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/controller/admin/social/vo/TkSocialVideoUploadSessionReqVO.java`
- Create: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/controller/admin/social/vo/TkSocialVideoUploadCompleteReqVO.java`
- Create: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/controller/admin/social/vo/TkSocialVideoUploadSessionRespVO.java`
- Test: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/service/social/TkSocialMediaServiceTest.java`

**Interfaces:**
- `MAX_VIDEO_BYTES = 1L * 1024 * 1024 * 1024`.
- Upload-session response contains `uploadId`, `uploadUrl`, `objectKey`, `publicUrl`, `accessKeyId`, `policy`, `signature`, `successActionStatus`, and `expiration`.
- Completion request contains upload/session identity, file metadata, and browser video metadata (`width`, `height`, `durationSeconds`, `frameRate`).

- [ ] **Step 1: Add a failing test for the 1GB limit constant and rejected oversize file.**
- [ ] **Step 2: Run the focused test and verify it fails because the current limit is 100MB.**
- [ ] **Step 3: Add the 1GB constant and request VO validation.**
- [ ] **Step 4: Run the focused test and verify it passes.**

### Task 2: Implement social OSS session and completion endpoints

**Files:**
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/controller/admin/social/TkSocialPublishController.java`
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/social/TkSocialMediaService.java`
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/upload/TkUploadSessionService.java`
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/upload/TkOssObjectStorageService.java`
- Test: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/service/social/TkSocialMediaServiceTest.java`

**Interfaces:**
- `POST /tk/social-publish/media/video-upload-session` creates a tenant-scoped exact-key OSS POST policy.
- `POST /tk/social-publish/media/video-upload-complete` HEAD-verifies the exact OSS object and creates a `READY` `tk_social_media` row.

- [ ] **Step 1: Add failing tests for exact object-key validation, OSS size verification, and media-row creation.**
- [ ] **Step 2: Run the focused tests and verify the new completion behavior fails.**
- [ ] **Step 3: Implement session creation, social upload-session persistence, policy signing, and completion verification.**
- [ ] **Step 4: Add the capabilities response value `1024` MB and keep the legacy multipart endpoint.**
- [ ] **Step 5: Run the focused tests and verify they pass.**

### Task 3: Switch frontend video uploads to direct OSS

**Files:**
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-ui/yudao-ui-admin-vue3/src/api/tk/socialPublish/index.ts`
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-ui/yudao-ui-admin-vue3/src/views/tk/video-publish-center/components/metaPublishController.ts`
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-ui/yudao-ui-admin-vue3/src/views/tk/video-publish-center/components/MetaPublishPanel.vue`

**Interfaces:**
- JPEG uses the existing Axios multipart request.
- MP4 requests a session, uploads FormData to OSS with `XMLHttpRequest` progress, then calls completion with browser metadata.

- [ ] **Step 1: Add a focused frontend test or type-level fixture for the direct-upload API shape.**
- [ ] **Step 2: Verify the new test/fixture fails before the API methods exist.**
- [ ] **Step 3: Implement direct video upload, progress state, 1GB client validation, and completion polling-safe UI state.**
- [ ] **Step 4: Run frontend type checking and the focused frontend tests.**

### Task 4: Build, package, deploy, and verify

**Files:**
- Modify only external production config if required: `/data/Tk/current/app/application-prod.yaml`
- Use existing deployment scripts under `deploy/` where applicable.

- [ ] **Step 1: Run focused backend tests and frontend type check/build.**
- [ ] **Step 2: Build the backend JAR and production frontend bundle.**
- [ ] **Step 3: Back up the current server artifacts, upload the new artifacts, atomically switch, and restart the service.**
- [ ] **Step 4: Verify PID, port, HTTP health, effective OSS configuration, and logs without printing secrets.**
- [ ] **Step 5: Verify a small MP4 upload through the production UI and confirm the published media URL is OSS-backed.**
