# MiMo TTS Provider Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add MiMo as an optional TTS provider alongside the existing DashScope voice flow without changing the default generation path.

**Architecture:** Introduce a small provider router in the TK generation audio layer. Keep the current DashScope service intact, add a MiMo adapter beside it, and route per task based on a new persisted provider field. The frontend defaults to the existing provider and only reveals MiMo-specific settings when the user explicitly chooses MiMo.

**Tech Stack:** Spring Boot, MyBatis, Vue 3, TypeScript, Element Plus, existing TK generation pipeline, existing file/Oss services.

## Global Constraints

- Preserve the existing DashScope generation path as the default.
- Do not break historical tasks, voice preview, or current task replay behavior.
- Add MiMo as a separate opt-in provider; do not overwrite existing provider semantics.
- Keep changes minimal and local to the TK module and TK frontend.
- Use focused tests before implementation and verify both backend and frontend behavior.

---

### Task 1: Add provider routing for TTS synthesis

**Files:**
- Modify: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/generation/pipeline/TkVoiceSynthesisService.java`
- Modify: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/generation/pipeline/DefaultTkVoiceSynthesisService.java`
- Create: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/generation/pipeline/TkMimoTtsClient.java`
- Create: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/generation/pipeline/TkVoiceProviderRouter.java`
- Test: `yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/service/generation/pipeline/TkMimoTtsClientTest.java`

**Interfaces:**
- Consumes: `TkGenerationTaskDO`, `TkGenerationProperties`, `FileApi`, existing `TkDashScopeTtsClient`
- Produces: `TkAudioAsset` for either `DASHSCOPE` or `MIMO`

- [ ] **Step 1: Write the failing test**

Create a test that asks the router to synthesize with `ttsProvider = MIMO` and asserts the MiMo client is used, while `ttsProvider = null` still routes to DashScope.

- [ ] **Step 2: Run test to verify it fails**

Run: `..\..\..\.runtime\apache-maven-3.9.10\bin\mvn.cmd -pl yudao-module-tk -Dtest=TkMimoTtsClientTest test`
Expected: FAIL because the MiMo client/router does not exist yet.

- [ ] **Step 3: Write minimal implementation**

Add a new provider router that chooses between DashScope and MiMo by a normalized provider string. Keep the DashScope branch unchanged.

- [ ] **Step 4: Run test to verify it passes**

Run: `..\..\..\.runtime\apache-maven-3.9.10\bin\mvn.cmd -pl yudao-module-tk -Dtest=TkMimoTtsClientTest test`
Expected: PASS.

---

### Task 2: Persist provider and MiMo parameters on generation tasks

**Files:**
- Modify: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/dal/dataobject/TkGenerationTaskDO.java`
- Modify: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/controller/admin/generation/vo/TkGenerationTaskCreateReqVO.java`
- Modify: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/controller/admin/generation/vo/TkGenerationTaskRespVO.java`
- Modify: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/controller/admin/generation/vo/TkGenerationTaskSummaryRespVO.java`
- Modify: `yudao-module-tk/src/main/resources/sql/tk_mysql.sql`
- Modify: `yudao-module-tk/src/main/resources/sql/tk_reference_analysis_upgrade_mysql.sql`
- Test: `yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/service/generation/TkGenerationTaskServiceImplTest.java`

**Interfaces:**
- Consumes: `ttsProvider`, `mimoVoiceMode`, `mimoVoiceId`, `mimoVoicePrompt`, `mimoVoiceSampleUrl`
- Produces: persisted task rows and API payloads that carry provider metadata

- [ ] **Step 1: Write the failing test**

Add a creation test that submits a MiMo request and asserts the task persists the provider field while the default request still persists DashScope.

- [ ] **Step 2: Run test to verify it fails**

Run: `..\..\..\.runtime\apache-maven-3.9.10\bin\mvn.cmd -pl yudao-module-tk -Dtest=TkGenerationTaskServiceImplTest test`
Expected: FAIL because the task model and mapping do not yet store provider metadata.

- [ ] **Step 3: Write minimal implementation**

Add nullable provider columns and map them through create/get/page DTOs. Keep existing fields unchanged for current clients.

- [ ] **Step 4: Run test to verify it passes**

Run: `..\..\..\.runtime\apache-maven-3.9.10\bin\mvn.cmd -pl yudao-module-tk -Dtest=TkGenerationTaskServiceImplTest test`
Expected: PASS.

---

### Task 3: Add MiMo settings to the frontend generation form

**Files:**
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/views/tk/dashboard/index.vue`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/api/tk/generation/index.ts`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/locales/tk/zh-CN.ts`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/locales/tk/en.ts`
- Test: `yudao-ui/yudao-ui-admin-vue3/scripts/check-dashboard-config-drawers.cjs`

**Interfaces:**
- Consumes: `ttsProvider`, `voiceCode`, `voiceProfileId`, `targetLanguage`
- Produces: form payloads that include MiMo-only options only when selected

- [ ] **Step 1: Write the failing test**

Add a UI check or script assertion that the MiMo settings block exists but stays hidden until the provider is switched to MiMo.

- [ ] **Step 2: Run test to verify it fails**

Run: `..\..\..\..\..\.runtime\npm-global\node_modules\.bin\pnpm.cmd ts:check`
Expected: FAIL or missing references until the form and locale keys are added.

- [ ] **Step 3: Write minimal implementation**

Render a provider selector with default DashScope, then conditionally show MiMo fields.

- [ ] **Step 4: Run test to verify it passes**

Run: `..\..\..\..\..\.runtime\npm-global\node_modules\.bin\pnpm.cmd ts:check`
Expected: PASS.

---

### Task 4: Document and verify the new provider path

**Files:**
- Modify: `docs/superpowers/specs/2026-08-07-mimo-tts-provider-integration-design.md` if needed
- Modify: `docs/superpowers/plans/2026-08-07-mimo-tts-provider-integration.md`
- Test: backend focused tests plus frontend type check

**Interfaces:**
- Consumes: completed backend and frontend changes
- Produces: a verified, deployable optional MiMo provider path

- [ ] **Step 1: Re-run backend and frontend verification**

Run:
`..\..\..\.runtime\apache-maven-3.9.10\bin\mvn.cmd -pl yudao-module-tk -am test`
`..\..\..\..\..\.runtime\npm-global\node_modules\.bin\pnpm.cmd ts:check`

- [ ] **Step 2: Review for compatibility**

Confirm existing DashScope creation, preview, and replay still work.

- [ ] **Step 3: Commit**

Commit the plan and implementation together after verification.

