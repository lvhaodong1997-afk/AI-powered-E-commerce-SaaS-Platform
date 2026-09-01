# Precise Material Duration And Fallback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Keep generated videos continuous and duration-accurate when material metadata contains fractional seconds or the library has insufficient unique material.

**Architecture:** Keep the existing generation pipeline and public API. Add millisecond metadata while preserving integer-second compatibility, generate exact clip segments, and use non-adjacent dynamic reuse as the final fallback. Frontend code remains unchanged.

**Tech Stack:** Spring Boot, MyBatis-Plus, FFmpeg/FFprobe, JUnit 5, Mockito, MySQL.

## Global Constraints

- Do not change frontend files or public API contracts.
- Preserve the existing `duration` field and existing clip-plan JSON fields.
- Never use `tpad=clone` to satisfy a duration gap.
- A material-duration shortage must not fail a generation task when at least one usable video source exists.

### Task 1: Regression tests

**Files:**
- Modify: `yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/service/generation/pipeline/TkRenderMediaSupportTest.java`
- Modify: `yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/service/generation/pipeline/DefaultTkVideoRenderServiceTest.java`

- [ ] Add tests that require millisecond clip duration conversion, precise trim arguments, and no `tpad` fallback.
- [ ] Run the focused tests and confirm the new assertions fail against the current implementation.

### Task 2: Precise material metadata

**Files:**
- Modify: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/dal/dataobject/TkMaterialVideoDO.java`
- Modify: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/material/TkMaterialVideoParseServiceImpl.java`
- Create: `yudao-module-tk/src/main/resources/sql/tk_material_duration_precision_upgrade_mysql.sql`

- [ ] Add nullable `durationMs` while keeping `duration`.
- [ ] Store FFprobe duration in milliseconds without ceiling to whole seconds.
- [ ] Provide an idempotent SQL upgrade for existing deployments.

### Task 3: Exact clip planning and fallback

**Files:**
- Modify: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/generation/pipeline/TkClipPlanItem.java`
- Modify: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/generation/pipeline/DefaultTkClipPlannerService.java`

- [ ] Preserve existing second fields and add optional millisecond plan fields.
- [ ] Prefer precise metadata when available.
- [ ] Fill deficits with unused same-library material, then non-adjacent reuse, and mark reuse mode.
- [ ] Trim the final selected item to the remaining target duration.

### Task 4: Continuous rendering

**Files:**
- Modify: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/generation/pipeline/DefaultTkVideoRenderService.java`
- Modify: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/generation/pipeline/TkRenderMediaSupport.java`

- [ ] Render each clip with explicit start and duration arguments.
- [ ] Remove short-section frame cloning.
- [ ] Reuse a real dynamic segment when the available source duration is short.
- [ ] Preserve subtitle, audio, BGM, upload, and existing step-log behavior.

### Task 5: Verification

- [ ] Run the focused planner and render tests.
- [ ] Run the TK module test suite.
- [ ] Build `yudao-server` with dependencies.
- [ ] Review the diff and confirm no frontend files changed.
- [ ] Apply the SQL upgrade, deploy the jar, restart the backend, and perform an HTTP health check.
