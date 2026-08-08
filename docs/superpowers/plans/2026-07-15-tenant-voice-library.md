# Tenant Voice Library Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow each tenant to upload an authorized reference recording, create and manage a DashScope cloned voice, and safely use it for preview and video generation.

**Architecture:** Store cloned voice metadata in a tenant-aware `tk_voice_profile` table. A provider client submits public sample URLs to DashScope voice enrollment, while the service owns validation, status transitions, tenant authorization, and cleanup. Existing system voices remain compatible, while custom voices are referenced by profile ID so clients never gain cross-tenant access through raw provider voice IDs.

**Tech Stack:** Spring Boot, MyBatis-Plus, Hutool HTTP, MySQL, Vue 3, Element Plus, TypeScript, Vitest/JUnit 5/Mockito.

## Global Constraints

- Every custom voice belongs to exactly one positive tenant ID.
- Only READY and enabled custom voices can be previewed or used in a generation task.
- Reference audio requires explicit authorization confirmation and must be MP3, WAV, or M4A, at most 20 MB.
- DashScope endpoint and target model must be configurable through `tk_api_key_config`.
- Existing hard-coded system voice codes and historical tasks remain usable.
- Deleting a custom voice removes the provider voice where supported and deletes stored sample/preview files.

---

### Task 1: Tenant voice domain and persistence

**Files:**
- Create: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/dal/dataobject/TkVoiceProfileDO.java`
- Create: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/dal/mysql/TkVoiceProfileMapper.java`
- Create: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/enums/TkVoiceProfileStatusEnum.java`
- Create: `yudao-module-tk/src/main/resources/sql/tk_voice_profile_upgrade_mysql.sql`
- Modify: `yudao-module-tk/src/main/resources/sql/tk_mysql.sql`
- Test: `yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/service/voice/TkVoiceProfileServiceImplTest.java`

- [ ] Write tests that reject tenant zero, missing consent, invalid files, cross-tenant access, and non-READY selection.
- [ ] Run focused tests and confirm they fail because the domain service is absent.
- [ ] Add the table, mapper, status enum, error codes, and service contracts.
- [ ] Run focused tests and confirm persistence/domain tests pass.

### Task 2: DashScope enrollment and lifecycle

**Files:**
- Create: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/voice/TkDashScopeVoiceEnrollmentClient.java`
- Create: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/voice/TkVoiceProfileService.java`
- Create: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/voice/TkVoiceProfileServiceImpl.java`
- Test: `yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/service/voice/TkDashScopeVoiceEnrollmentClientTest.java`

- [ ] Write HTTP contract tests for create/delete response parsing and provider errors.
- [ ] Run tests and confirm expected failures.
- [ ] Implement configurable enrollment calls and service status transitions `CLONING -> READY|FAILED`.
- [ ] Store samples through `FileApi`, generate a preview after enrollment, and clean up provider/storage resources on deletion.
- [ ] Run focused tests and confirm all voice service tests pass.

### Task 3: Secure generation and preview integration

**Files:**
- Modify: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/controller/admin/generation/vo/TkGenerationTaskCreateReqVO.java`
- Modify: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/controller/admin/generation/vo/TkVoicePreviewReqVO.java`
- Modify: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/generation/TkGenerationTaskServiceImpl.java`
- Modify: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/controller/admin/generation/TkGenerationTaskController.java`
- Test: `yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/service/generation/TkGenerationVoiceSelectionTest.java`

- [ ] Write tests proving custom profile IDs resolve only inside the current tenant and raw system codes remain compatible.
- [ ] Run tests and confirm failures before implementation.
- [ ] Resolve profile ID to provider voice code on the backend for create/precheck/preview.
- [ ] Run focused and module tests.

### Task 4: Tenant voice management UI

**Files:**
- Create: `yudao-ui/yudao-ui-admin-vue3/src/api/tk/voice/index.ts`
- Create: `yudao-ui/yudao-ui-admin-vue3/src/views/tk/voice/components/VoiceProfileDialog.vue`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/views/tk/dashboard/index.vue`

- [ ] Add typed API calls for list/upload/retry/enable/delete/preview.
- [ ] Add a management dialog with upload, consent, status, preview, retry, enable, and delete controls.
- [ ] Replace fixed dropdown data with grouped system and current-tenant custom voices.
- [ ] Submit `voiceProfileId` for custom voices and retain `voiceCode` for system voices.
- [ ] Run frontend typecheck and build.

### Task 5: Deployment verification

**Files:**
- Modify: deployment artifacts only under `/data/Tk` on `175.155.64.171:22100`.

- [ ] Run backend tests and package with the repository-bundled Maven runtime.
- [ ] Run frontend typecheck/build.
- [ ] Back up the current `/data/Tk/current` database/application artifacts.
- [ ] Apply the idempotent SQL migration, deploy frontend and backend, and restart only TK services.
- [ ] Verify health, tenant-scoped list behavior, upload failure messaging, and existing system voice generation.
