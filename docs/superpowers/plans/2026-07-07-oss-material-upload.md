# OSS Material Upload Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move TK material video uploads to Aliyun OSS direct upload while keeping existing material playback and generation flows usable.

**Architecture:** The backend signs short-lived browser POST policies and verifies completed OSS objects before inserting `tk_material_video`. The frontend uploads files directly to OSS with signed FormData and then calls backend complete. Existing local upload endpoints remain present as rollback paths, but production config switches material upload to OSS.

**Tech Stack:** Spring Boot, Hutool HTTP, Vue 3, Element Plus, Axios, Aliyun OSS browser POST policy.

## Global Constraints

- Do not commit OSS secrets into repository files.
- Store production OSS credentials only in external server config or environment variables.
- Do not break existing local `/uploads/**` material URLs.
- Use public-read OSS URLs for material playback.

---

### Task 1: Backend OSS Upload Session

**Files:**
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/framework/config/TkGenerationProperties.java`
- Create: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/controller/admin/upload/vo/TkOssUploadSessionRespVO.java`
- Create: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/upload/TkMaterialOssUploadService.java`
- Create: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/upload/TkMaterialOssUploadServiceImpl.java`
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/controller/admin/upload/TkUploadController.java`

**Interfaces:**
- Produces: `createMaterialVideoOssSession(libraryId, fileName, fileSize, contentType)` returning signed POST fields.
- Produces: `completeMaterialVideoOssUpload(uploadId, tags, usagePhase, segmentType)` returning material video ID.

- [ ] Add OSS upload config fields under `tk.generation.upload.oss`.
- [ ] Generate policy with `starts-with` key prefix and content-length-range.
- [ ] Validate library permissions and file basics before signing.
- [ ] On complete, HEAD public OSS URL and verify file size before DB insert.

### Task 2: Frontend Direct Upload

**Files:**
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-ui/yudao-ui-admin-vue3/src/api/tk/material/index.ts`
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-ui/yudao-ui-admin-vue3/src/views/tk/material-library/index.vue`

**Interfaces:**
- Consumes: OSS session response from Task 1.
- Produces: direct browser upload to `uploadUrl`, then backend complete.

- [ ] Add OSS session and complete API methods.
- [ ] Replace material upload queue implementation with OSS POST upload when session returns `uploadMode = oss`.
- [ ] Keep local chunk methods untouched for rollback and older deployments.
- [ ] Preserve user-facing progress, retry, success, failure states.

### Task 3: Production Config And Verification

**Files:**
- Modify server-only: `/data/Tk/current/app/application-prod.yaml`

**Interfaces:**
- Consumes: OSS credentials from external config.
- Produces: production upload storage type set to `oss`.

- [ ] Add OSS production config without writing secrets to repo.
- [ ] Build backend with bundled Maven.
- [ ] Build frontend with bundled pnpm.
- [ ] Deploy jar and web dist with backups.
- [ ] Verify health endpoint, upload API presence, and frontend asset deployment.
