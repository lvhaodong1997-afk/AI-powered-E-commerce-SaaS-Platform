# Full Pool Random Fixed Opening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep an uploaded opening video as the first three seconds in full-pool random mode, then fill the remaining effective duration with the existing random material selection algorithm.

**Architecture:** Reuse the existing `openingVideoUrl` task fields and `FULL_POOL_RANDOM` route mode. The clip planner prepends one `OPENING` item and passes only the remaining duration to random selection; precheck applies the same remaining-duration calculation, while the frontend marks a local opening file in its JSON precheck payload.

**Tech Stack:** Java 8, Spring Boot, JUnit 5, Vue 3, TypeScript.

## Global Constraints

- Do not add database columns or endpoints.
- Do not change segmented mode.
- Full-pool random behavior without an opening remains unchanged.
- The fixed opening duration is three seconds under the existing opening rule.

---

### Task 1: Clip Planner Fixed Opening

**Files:**
- Modify: `yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/service/generation/pipeline/DefaultTkClipPlannerServiceTest.java`
- Modify: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/generation/pipeline/DefaultTkClipPlannerService.java`

**Interfaces:**
- Consumes: `TkGenerationTaskDO.openingVideoUrl`, `openingVideoName`, and effective target duration.
- Produces: a clip plan whose first item is `OPENING` and whose remaining items use `selectBestFitRandomClips`.

- [x] Add a test with a 10-second target, an opening URL, and material durations 3/4/7; assert the first item is a 3-second `OPENING`, its order is 1, and random materials begin at order 2 using the remaining 7 seconds.
- [x] Run `DefaultTkClipPlannerServiceTest` and confirm the new test fails because the current random branch returns only `MATERIAL` items.
- [x] Update `planFullPoolRandom` to prepend the opening and calculate `randomTargetDuration = targetDuration - openingDuration`.
- [x] Keep the existing no-opening path byte-for-byte equivalent in behavior.
- [x] Re-run `DefaultTkClipPlannerServiceTest` and confirm it passes.

### Task 2: Remaining-Duration Precheck

**Files:**
- Modify: `yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/service/generation/TkGenerationPrecheckServiceImplTest.java`
- Modify: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/generation/TkGenerationPrecheckServiceImpl.java`

**Interfaces:**
- Consumes: `TkGenerationTaskCreateReqVO.openingVideoUrl` or `openingVideoName`.
- Produces: full-pool random warnings and errors based on `targetDuration - 3` when an opening exists.

- [x] Add a test where a 10-second target, opening name, and a 7-second material pass precheck.
- [x] Add a test where the same request with only an 8-second material fails with `MATERIAL_TOO_LONG_FOR_TARGET`, proving the remaining target is seven seconds.
- [x] Run `TkGenerationPrecheckServiceImplTest` and confirm the second test fails before implementation.
- [x] Add one helper that returns the random target duration and use it for full-pool warnings and errors.
- [x] Re-run `TkGenerationPrecheckServiceImplTest` and confirm it passes.

### Task 3: Frontend Precheck Marker and Copy

**Files:**
- Create: `yudao-ui/yudao-ui-admin-vue3/tests/full-pool-random-opening.test.cjs`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/views/tk/dashboard/index.vue`

**Interfaces:**
- Consumes: `openingVideoFile.value` and `createForm.clipPlanMode`.
- Produces: `openingVideoName` in the JSON precheck payload and explicit fixed-opening helper text.

- [x] Add a source contract test asserting `createGenerationPayload` adds the local file name when `openingVideoFile.value` exists and includes the fixed-opening Chinese and English copy.
- [x] Run the contract test and confirm it fails.
- [x] Add the local opening file marker to `createGenerationPayload` and update the opening helper copy.
- [x] Run the contract test and `pnpm ts:check`.

### Task 4: Focused Regression Verification

**Files:**
- Verify only the files listed above.

**Interfaces:**
- Consumes: completed backend and frontend changes.
- Produces: focused evidence that both modes remain compatible.

- [x] Run both focused backend test classes with Maven.
- [x] Run the frontend source contract test and TypeScript check.
- [x] Review `git diff --check` and focused diffs for accidental unrelated changes.
