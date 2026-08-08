# Material Recent Use Soft Exclusion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prefer unused material videos by excluding material IDs used in the latest 10 successful tasks for the same library, with fallback reuse when fresh material cannot satisfy a section duration.

**Architecture:** Reuse `tk_generation_task.clip_plan` as the source of historical material usage. `DefaultTkClipPlannerService` queries recent successful tasks through `TkGenerationTaskMapper`, parses historical `materialVideoId` values, then orders each segment's shuffled candidates as fresh first and recently used fallback second.

**Tech Stack:** Java, Spring Boot, MyBatis Plus, JUnit 5, Mockito, Maven.

## Global Constraints

- Do not create a new material usage table for this change.
- Only successful generation tasks count as recent usage.
- Exclusion scope is same `libraryId`.
- The exclusion is soft: recently used material can still be reused when fresh material is not enough.
- Preserve current behavior of using whole source videos without trimming.

---

### Task 1: Add Tests For Soft Exclusion

**Files:**
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/service/generation/pipeline/DefaultTkClipPlannerServiceTest.java`

**Interfaces:**
- Consumes: `DefaultTkClipPlannerService.plan(TkGenerationTaskDO, String)`
- Produces: regression coverage for fresh-first selection and fallback reuse.

- [ ] Add a test where recent successful task clip plans include one S4 material, and fresh S4 material is enough; assert the recent material is not selected.
- [ ] Add a test where recent successful task clip plans include one S4 material, and fresh S4 material is not enough; assert the planner falls back to the recent material.
- [ ] Run the focused test class and confirm the new tests fail before implementation.

### Task 2: Implement Recent Usage Query And Candidate Ordering

**Files:**
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/dal/mysql/TkGenerationTaskMapper.java`
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/generation/pipeline/DefaultTkClipPlannerService.java`

**Interfaces:**
- Produces: `TkGenerationTaskMapper.selectRecentSuccessfulClipPlansByLibraryId(Long libraryId, Long excludeTaskId, int limit)`
- Produces: fresh-first and fallback candidate ordering inside `DefaultTkClipPlannerService`.

- [ ] Add mapper method selecting latest successful tasks for the same library, excluding the current task ID, with only `id` and `clipPlan`.
- [ ] Inject `TkGenerationTaskMapper` into `DefaultTkClipPlannerService`.
- [ ] Parse recent clip plans into a `Set<Long>` of recently used material IDs.
- [ ] For each segment, shuffle fresh candidates first, then recently used candidates.
- [ ] Keep current in-task duplicate prevention and insufficient-material error behavior.

### Task 3: Verify, Build, And Deploy

**Files:**
- Runtime artifact: `yudao-server/target/yudao-server.jar`

**Interfaces:**
- Consumes: local Maven wrapper/runtime Maven.
- Produces: deployed backend jar on `/data/Tk/current/app/yudao-server.jar`.

- [ ] Run focused module test for `DefaultTkClipPlannerServiceTest`.
- [ ] Build backend jar with tests skipped only after focused tests pass.
- [ ] Upload jar to server with timestamped backup.
- [ ] Restart backend service.
- [ ] Verify public health/API reachability after restart.
