# 180-Second Target Duration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Raise ClipForge Studio's target video duration ceiling from 60 seconds to 180 seconds without changing the existing 8-second minimum or 15-second default.

**Architecture:** Keep `TkVideoDurationSupport` as the backend hard-limit source and retain `tk.generation.ffmpeg.max-target-duration` as an environment-specific lower ceiling. Align the dashboard constants and bilingual copy, then extend prompt contracts and focused boundary tests so 61-180 second jobs use the existing generation pipeline.

**Tech Stack:** Java 8, Spring Boot 2.7, JUnit 5, Vue 3, TypeScript, Node test runner.

## Global Constraints

- Minimum duration remains 8 seconds.
- Default duration remains 15 seconds.
- System hard maximum becomes 180 seconds.
- Existing API fields and database schema remain unchanged.
- Existing 8-60 second task behavior remains compatible.

---

### Task 1: Backend Duration Boundary

**Files:**
- Create: `yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/service/generation/pipeline/TkVideoDurationSupportTest.java`
- Modify: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/generation/pipeline/TkVideoDurationSupport.java`

- [ ] Add tests proving 180 is retained, 181 is clamped to 180, and a lower configured maximum still applies.
- [ ] Run the focused test and confirm it fails because 180 currently normalizes to 60.
- [ ] Change the backend hard maximum to 180.
- [ ] Re-run the focused test and confirm it passes.

### Task 2: Frontend Duration Boundary and Copy

**Files:**
- Create: `yudao-ui/yudao-ui-admin-vue3/tests/target-duration-limit.test.cjs`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/views/tk/dashboard/index.vue`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/locales/tk/zh-CN.ts`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/locales/tk/en.ts`

- [ ] Add a source contract test for the 180-second constant and bilingual 8-180 copy.
- [ ] Run the test and confirm it fails against the current 60-second copy.
- [ ] Update the dashboard and dedicated TK locale dictionaries.
- [ ] Re-run the contract test.

### Task 3: Long-Form Prompt Contract

**Files:**
- Modify: `yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/service/generation/pipeline/TkGeminiPromptConfigTest.java`
- Modify: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/generation/pipeline/TkGeminiPromptConfig.java`

- [ ] Add a test requiring explicit 90/120/180-second prompt guidance.
- [ ] Run the focused test and confirm it fails.
- [ ] Add proportional long-duration guidance while retaining existing 15-60 second budgets.
- [ ] Re-run prompt and duration tests.

### Task 4: Verification and Deployment

**Files:**
- Verify only the files above plus effective production configuration.

- [ ] Run focused backend tests and frontend contract tests.
- [ ] Run frontend type-check and production build.
- [ ] Build the backend JAR with the bundled Maven runtime.
- [ ] Read `/data/Tk/current/app/application-prod.yaml` and confirm the effective maximum.
- [ ] Upload artifacts, back up the current JAR/frontend, restart the backend, and verify PID, ports, internal HTTP, and public HTTP.
