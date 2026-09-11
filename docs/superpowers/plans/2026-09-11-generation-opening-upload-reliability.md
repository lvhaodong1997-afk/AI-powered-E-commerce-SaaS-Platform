# 黄金三秒独立上传与连续生成可靠性改造实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** 将黄金三秒视频从同步生成请求中拆出，解决重新上传后 multipart 请求中断导致的 400/页面 524，并允许已有任务生成期间继续创建新任务。

**Architecture:** 黄金视频先通过独立分片上传会话上传并获得受权限保护的 `openingUploadId`/文件 URL。生成按钮只提交 JSON，并在点击时冻结当前标题、文案和黄金视频引用；后台已有任务继续使用自己的快照，新任务不会覆盖旧任务。

**Tech Stack:** Spring Boot、Java、Vue 3、TypeScript、Axios、Element Plus、MySQL、现有本地/OSS 文件存储、JUnit 5。

## Global Constraints

- 生成中的旧任务不阻塞新任务创建。
- 每个任务使用提交瞬间的标题、文案和黄金视频，不读取后续修改后的表单状态。
- 黄金开头继续使用原生视频内容；后续 AI 配音、BGM 和系统字幕从实测黄金开头时长之后开始。
- 不过滤 `S1_HOOK`。
- 保留现有 `/tk/generation/create-with-opening` 接口兼容性，前端停止调用该接口。
- 不新增生成任务表；优先复用 `tk_upload_session` 和现有生成字段。
- 不把黄金视频上传写入 `tk_material_video`。
- 保留当前用户、公司、租户和素材库的数据权限校验。

---

### Task 1: 抽取可复用的分片上传能力

**Files:**
- Create: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/upload/TkChunkUploadStorageService.java`
- Create: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/upload/TkChunkUploadStorageServiceImpl.java`
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/upload/TkMaterialChunkUploadServiceImpl.java`
- Create: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/service/upload/TkChunkUploadStorageServiceImplTest.java`

**Interfaces:**
- `createSession(uploadId, fileName, fileSize, contentType, metadata)` creates the manifest and temporary directory.
- `getStatus(uploadId)` returns chunk size, total chunks, uploaded chunks, uploaded bytes and status.
- `saveChunk(uploadId, chunkIndex, MultipartFile chunk)` replaces only the specified chunk.
- `mergeAndValidate(uploadId, relativePath)` merges all chunks, checks exact file size and validates MP4/MOV/WEBM container markers.
- `deleteSessionFiles(uploadId)` removes temporary chunks and manifest.

- [ ] **Step 1: Write failing tests** for complete chunks, missing chunks, size mismatch, invalid container and cleanup.
- [ ] **Step 2: Run the focused test** from the product source root:

```powershell
..\..\..\.runtime\apache-maven-3.9.10\bin\mvn.cmd -pl yudao-module-tk -Dtest=TkChunkUploadStorageServiceImplTest test
```

- [ ] **Step 3: Implement the storage service** by moving the low-level manifest, chunk path, merge and container-validation behavior out of the material-specific service.
- [ ] **Step 4: Keep material upload behavior unchanged**: material completion still creates `tk_material_video`, updates library counters and submits parsing; only the shared file operations are delegated.
- [ ] **Step 5: Re-run the focused test** and confirm all cases pass.

### Task 2: Add independent golden-video upload APIs

**Files:**
- Create: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/controller/admin/generation/vo/TkGenerationOpeningUploadSessionCreateReqVO.java`
- Create: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/controller/admin/generation/vo/TkGenerationOpeningUploadCompleteRespVO.java`
- Create: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/upload/TkGenerationOpeningUploadService.java`
- Create: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/upload/TkGenerationOpeningUploadServiceImpl.java`
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/controller/admin/generation/TkGenerationTaskController.java`
- Create: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/service/upload/TkGenerationOpeningUploadServiceImplTest.java`

**Interfaces:**
- `POST /tk/generation/opening/session/create` receives `libraryId`, `fileName`, `fileSize`, `contentType` and returns `uploadId`, `chunkSize`, `totalChunks`.
- `GET /tk/generation/opening/session/{uploadId}` returns upload status.
- `POST /tk/generation/opening/chunk` receives `uploadId`, `chunkIndex`, `chunk`.
- `POST /tk/generation/opening/session/complete` returns `uploadId`, `fileName`, `fileUrl`.
- `DELETE /tk/generation/opening/session/{uploadId}` cancels and cleans up the session.

- [ ] **Step 1: Write failing service tests** proving that a completed opening upload returns a generation-opening URL and does not insert a material-video row.
- [ ] **Step 2: Run the focused test** and confirm it fails before the service exists.
- [ ] **Step 3: Implement the service** using `tk_upload_session`, shared chunk storage, `tk/{tenant}/{company}/generation-openings/` and existing local/OSS configuration.
- [ ] **Step 4: Validate** file extension, file size, video container, library readability, tenant, company and current creator on every operation.
- [ ] **Step 5: Re-run the focused service and controller tests**.

### Task 3: Bind uploaded opening sessions to JSON generation

**Files:**
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/controller/admin/generation/vo/TkGenerationTaskCreateReqVO.java`
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/generation/TkGenerationTaskServiceImpl.java`
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/generation/TkGenerationTaskService.java`
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/service/generation/TkGenerationTaskServiceImplTest.java`
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/controller/admin/generation/TkGenerationTaskControllerTest.java`

**Interfaces:**
- Add optional request field `openingUploadId`.
- When `openingUploadId` is present, the service requires the session to be `COMPLETED`, validates current-user access, and resolves the trusted file URL/name.
- When only `openingVideoUrl` is present, preserve the existing remote-link behavior.
- Keep the legacy `MultipartFile openingVideoFile` path in `/create-with-opening` for compatibility.

- [ ] **Step 1: Add a failing test** where a completed opening upload is accepted and an expired, cancelled or other-user upload is rejected.
- [ ] **Step 2: Add a failing test** proving generation task creation stores the resolved `opening_video_url` and does not depend on a later form mutation.
- [ ] **Step 3: Implement optional `openingUploadId` resolution** before task construction; do not add a database column because the task already stores the resolved URL and file name.
- [ ] **Step 4: Preserve native-opening duration handling** in the existing pipeline and keep `S1_HOOK` unchanged.
- [ ] **Step 5: Run the focused generation tests**.

### Task 4: Replace frontend multipart generation with upload-then-create

**Files:**
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-ui/yudao-ui-admin-vue3/src/api/tk/generation/index.ts`
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-ui/yudao-ui-admin-vue3/src/views/tk/dashboard/index.vue`
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-ui/yudao-ui-admin-vue3/src/utils/tkChunkUpload.ts`
- Create: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-ui/yudao-ui-admin-vue3/tests/tk/dashboard-opening-upload.spec.ts`

**Interfaces:**
- Add frontend API methods matching Task 2.
- Add `openingUploadId` to the generation payload type.
- Use `uploadFileInChunks` for the opening file and keep retry count, progress and cancellation behavior consistent with material upload.

- [ ] **Step 1: Write failing frontend contract tests** for upload completion, replacement and generation payloads.
- [ ] **Step 2: Make the tests fail** because the dashboard still appends `openingVideoFile` to `FormData`.
- [ ] **Step 3: Add an opening-upload state** containing `status`, `uploadId`, `fileName`, `fileUrl`, `error` and a replacement token.
- [ ] **Step 4: Change file selection** so a new file cancels the old session, clears the old URL, starts a new upload and ignores late callbacks from the replaced file.
- [ ] **Step 5: Change generation payload creation** so it sends `openingUploadId`/`openingVideoUrl` and never appends the local video file.
- [ ] **Step 6: Remove the frontend call path to `createGenerationWithOpening`** while leaving its API method and backend endpoint available for compatibility.
- [ ] **Step 7: Run the frontend contract test and type check**:

```powershell
..\..\..\..\..\.runtime\npm-global\node_modules\.bin\pnpm.cmd test:unit -- tests/tk/dashboard-opening-upload.spec.ts
..\..\..\..\..\.runtime\npm-global\node_modules\.bin\pnpm.cmd ts:check
```

### Task 5: Support new submissions while old tasks are processing

**Files:**
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-ui/yudao-ui-admin-vue3/src/views/tk/dashboard/index.vue`
- Create: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-ui/yudao-ui-admin-vue3/tests/tk/dashboard-generation-concurrency.spec.ts`

**Interfaces:**
- `generationSubmittingCount` only locks the current submission request.
- Existing `hasActiveGenerationTasks` must not disable a new generation submission.
- Each submission snapshots title, script text, opening upload ID and generation settings before asynchronous calls begin.

- [ ] **Step 1: Write a failing test** for: task A is active, title/script/opening are changed, task B is submitted, and task A's snapshot remains unchanged.
- [ ] **Step 2: Write a failing test** for repeated clicks during the same submission; exactly one request should be sent.
- [ ] **Step 3: Add an entry guard** for only the current in-flight submission, without checking `hasActiveGenerationTasks` as a global lock.
- [ ] **Step 4: Snapshot the selected script and form values** before precheck and task creation; use that snapshot for all requests belonging to the submission.
- [ ] **Step 5: Keep `batchGenerationTasks` and polling records** for existing tasks when a new submission starts; never clear task A because task B was created.
- [ ] **Step 6: Run the concurrency tests and frontend type check**.

### Task 6: Production verification and delivery

**Files:**
- Modify only deployment artifacts if a production configuration change is proven necessary: `deploy/nginx/tk-auto-mix.conf` and relevant deployment scripts.
- Do not change production files until local tests pass and deployment is explicitly requested.

- [ ] **Step 1: Run backend focused tests, then the TK module test suite.**
- [ ] **Step 2: Run frontend contract tests and `pnpm ts:check`.**
- [ ] **Step 3: Verify the local flow:** upload A, generate A, while A is processing replace with B, change title/script, generate B, and confirm both task records keep their own values.
- [ ] **Step 4: Verify no `create-with-opening` request is sent by the frontend.**
- [ ] **Step 5: Before deployment, check production root and `/data` disk usage and clean only approved temporary upload files; the observed 99% root usage and exhausted swap are a separate P0 stability risk.**
- [ ] **Step 6: Commit only the requested implementation files, push the branch, synchronize the latest `main` as requested, deploy and verify logs.**
- [ ] **Step 7: Production success criteria:** chunk requests complete, generation is JSON with `openingUploadId`, no multipart EOF/ClientAbortException appears, no true 524/400 is recorded for this flow, and both old/new tasks complete with the correct native opening.

## Expected Behavior

```text
任务 A：黄金视频 A + 文案 A -> 提交 -> 后台生成

用户继续操作：更换黄金视频 B + 修改文案 B
             -> B 独立上传完成
             -> 提交任务 B

结果：任务 A 继续使用 A；任务 B 使用 B；两者互不覆盖。
```

## Out of Scope

- 不修改 AI 文案生成规则。
- 不过滤 `S1_HOOK`。
- 不重做黄金开头时间轴算法。
- 不把黄金视频转入素材库。
- 不通过单纯提高 Axios、Nginx 或 Cloudflare 超时时间作为唯一修复。
