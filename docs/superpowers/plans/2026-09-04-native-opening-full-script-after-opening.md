# Native Opening Full Script After Opening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a native uploaded opening play first, then start the complete original script's AI voice and system subtitles without requiring or filtering `segmentTimeline`/`S1_HOOK`, while preserving complete audio playback.

**Architecture:** Keep the uploaded opening as the first native clip and use the server-probed `openingDurationMs` as the only media boundary. The existing task script remains the full script; TTS and subtitle generation receive it unchanged, and their timelines are shifted by the native opening duration. The render target is expanded when needed so the complete delayed audio is not truncated.

**Tech Stack:** Spring Boot/Java, JUnit 5, Mockito, Vue 3/TypeScript, MySQL migration SQL, FFprobe, FFmpeg.

## Global Constraints

- Do not make `segmentTimeline` or `S1_HOOK` a native-opening precheck requirement.
- Do not remove `S1_HOOK` from TTS or system subtitle input.
- Preserve the native opening's original video/audio and start generated media after its server-probed duration.
- Treat the requested duration as a minimum when complete delayed audio is longer.
- Preserve the five existing user-modified files and unrelated worktree changes.
- Keep standard opening mode and existing API fields backward compatible.

---

### Task 1: Define the full-script native-opening contract with failing tests

**Files:**
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/service/generation/pipeline/TkNativeOpeningSupportTest.java`
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/service/generation/TkGenerationPrecheckServiceImplTest.java`

**Interfaces:**
- Consumes: `TkNativeOpeningSupport.resolveNarrationScript`, `TkGenerationPrecheckServiceImpl.precheck`.
- Produces: Regression coverage proving native mode keeps the complete script and passes without a timeline.

- [ ] **Step 1: Replace the filtering test with the desired full-script test**

Use this behavior assertion:

```java
@Test
void nativeModeKeepsCompleteScriptWithoutTimeline() {
    assertEquals("Hook line Body line", TkNativeOpeningSupport.resolveNarrationScript(
            "Hook line Body line", null, TkNativeOpeningSupport.MODE_NATIVE));
}
```

Remove assertions that native mode throws for a missing/malformed timeline or removes `S1_HOOK`. Keep standard-mode and duration coverage.

- [ ] **Step 2: Replace the precheck rejection test with a pass test**

Keep the existing request fixture, add a valid opening URL, and assert that a null `segmentTimeline` does not create the timeline error:

```java
@Test
void precheckAllowsNativeOpeningWithoutScriptTimelineSource() {
    TkGenerationPrecheckServiceImpl service = createService(Arrays.asList(
            material(1L, 4L, "PRODUCT_SHOW", "S3_REVEAL"),
            material(2L, 8L, "PRODUCT_SHOW", "S4_DEMO"),
            material(3L, 5L, "RESULT_EFFECT", "S5_PROOF")
    ));
    TkGenerationTaskCreateReqVO reqVO = createRequest(15);
    reqVO.setOpeningProcessMode("NATIVE");
    reqVO.setOpeningVideoUrl("https://example.com/opening.mp4");
    reqVO.setScriptOptionId(1579L);

    TkGenerationPrecheckRespVO result = service.precheck(reqVO);

    assertTrue(result.getPassed());
    assertTrue(result.getErrors().stream()
            .noneMatch(issue -> "NATIVE_OPENING_TIMELINE_MISSING".equals(issue.getCode())));
}
```

- [ ] **Step 3: Run the focused tests and verify the expected red failure**

Run from the product source root:

```powershell
..\..\..\.runtime\apache-maven-3.9.10\bin\mvn.cmd -pl yudao-module-tk -am -Dtest=TkNativeOpeningSupportTest,TkGenerationPrecheckServiceImplTest test
```

Expected: failure because the current native narration rejects a missing timeline and precheck adds `NATIVE_OPENING_TIMELINE_MISSING`.

### Task 2: Remove timeline filtering and native precheck blocking

**Files:**
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/generation/pipeline/TkNativeOpeningSupport.java`
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/generation/TkGenerationPrecheckServiceImpl.java`

**Interfaces:**
- Consumes: Full `scriptText`, native/standard mode, existing material precheck rules.
- Produces: Native mode returns the full script unchanged and no longer requires a script-option timeline.

- [ ] **Step 1: Implement the minimal full-script narration behavior**

Make `resolveNarrationScript` return the trimmed full script for both modes while keeping its existing signature for compatibility:

```java
public static String resolveNarrationScript(String fullScript, String segmentTimeline, String mode) {
    return StrUtil.trimToEmpty(fullScript);
}
```

Remove timeline parsing/filtering helpers and imports only when no remaining caller needs them.

- [ ] **Step 2: Remove the timeline-specific precheck dependency**

Delete the script-option mapper/DO dependencies, the `validateNativeOpeningTimeline` call, and that method. Leave material, route, and segment checks unchanged.

- [ ] **Step 3: Run the focused tests and verify green**

```powershell
..\..\..\.runtime\apache-maven-3.9.10\bin\mvn.cmd -pl yudao-module-tk -am -Dtest=TkNativeOpeningSupportTest,TkGenerationPrecheckServiceImplTest test
```

Expected: both classes pass, including complete `S1_HOOK` retention and native precheck without a timeline.

### Task 3: Ensure complete delayed audio controls render duration

**Files:**
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/generation/pipeline/DefaultTkGenerationPipelineService.java`
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/generation/pipeline/DefaultTkVideoRenderService.java`
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/service/generation/pipeline/DefaultTkGenerationPipelineServiceTest.java`
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/service/generation/pipeline/TkNativeOpeningSupportTest.java`

**Interfaces:**
- Consumes: `openingDurationMs`, complete TTS duration, existing render command builders.
- Produces: Final duration equal to or greater than both the target and `openingDuration + ttsDuration`; delayed voice and subtitles retain the complete script.

- [ ] **Step 1: Add the duration regression**

Add:

```java
@Test
void nativeOpeningExtendsTargetForCompleteDelayedNarration() {
    assertEquals(33D, TkNativeOpeningSupport.resolveEffectiveDuration(30D, 3D, 30D), 0.001D);
}
```

Retain or add render-command assertions proving native voice delay uses the opening duration and the native mix uses `duration=longest`.

- [ ] **Step 2: Run the focused render tests and verify red if behavior is missing**

```powershell
..\..\..\.runtime\apache-maven-3.9.10\bin\mvn.cmd -pl yudao-module-tk -am -Dtest=TkNativeOpeningSupportTest,DefaultTkGenerationPipelineServiceTest,DefaultTkVideoRenderServiceTest test
```

Expected: a failing assertion only where the current behavior does not yet preserve full delayed narration or its required duration.

- [ ] **Step 3: Pass effective duration to rendering**

Keep the calculation `max(targetDuration, openingDuration + audioDuration)` and ensure the effective value, not the original requested target, is passed to rendering. Keep the configured maximum as an explicit guard; fail clearly when the maximum would be exceeded instead of truncating audio.

- [ ] **Step 4: Preserve full-script voice and subtitle timing**

Keep `DefaultTkVideoRenderService.buildSubtitleTimeline` using the complete narration script and shift the resulting timeline by `nativeOpeningDurationSeconds(task)`. Keep native source audio as the first audio input and generated voice/BGM as delayed inputs.

- [ ] **Step 5: Run the focused render tests and verify green**

Run the command from Step 2 and confirm all focused duration/render tests pass.

### Task 4: Update UI copy to match the new contract

**Files:**
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-ui/yudao-ui-admin-vue3/src/views/tk/dashboard/index.vue`

**Interfaces:**
- Consumes: Existing `openingProcessMode` selector and precheck response.
- Produces: UI language that matches full-script-after-opening behavior.

- [ ] **Step 1: Update the native-opening hint**

State that the uploaded opening keeps its original content and the complete selected script's AI voice and system subtitles start after the opening ends. Do not add a copy-mode control.

- [ ] **Step 2: Remove stale timeline-blocking copy**

Search the page for `分段时间`, `S1_HOOK`, and `segmentTimeline`; revise only messages that claim native generation is blocked by a missing timeline or that the hook is excluded from generated narration.

- [ ] **Step 3: Run the frontend type check**

Run from `yudao-ui/yudao-ui-admin-vue3`:

```powershell
..\..\..\..\..\.runtime\npm-global\node_modules\.bin\pnpm.cmd ts:check
```

Expected: exit code 0 with no new type errors.

### Task 5: Verify, review, commit, push, and synchronize the server

**Files:**
- Verify all changed files with `git diff` and `git status --short`.
- Preserve unrelated user changes and do not include credentials.

- [ ] **Step 1: Run the focused and full backend tests**

```powershell
..\..\..\.runtime\apache-maven-3.9.10\bin\mvn.cmd -pl yudao-module-tk -am test
```

Expected: exit code 0.

- [ ] **Step 2: Run the frontend type check**

```powershell
Set-Location ..\yudao-ui\yudao-ui-admin-vue3
..\..\..\..\..\.runtime\npm-global\node_modules\.bin\pnpm.cmd ts:check
```

Expected: exit code 0.

- [ ] **Step 3: Review the final diff before integration**

Confirm there is no accidental broad rewrite, no remaining native timeline blocker/filter, and no unrelated file change. Request review before commit/integration.

- [ ] **Step 4: Commit the implementation**

```powershell
git add docs/superpowers/plans/2026-09-04-native-opening-full-script-after-opening.md ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-ui/yudao-ui-admin-vue3/src/views/tk/dashboard/index.vue
git commit -m "fix(tk): start full script after native opening"
```

- [ ] **Step 5: Push main**

```powershell
git push origin main
```

Expected: remote `main` advances without force push.

- [ ] **Step 6: Build from main and synchronize the authorized server**

Build the backend/frontend release from the updated `main`, record artifact hashes, then use the supplied SSH endpoint to back up the current release, stage the verified artifact, atomically switch, restart or reload the service as required, and verify process, port, HTTP response, page title, and recent logs. Redact the supplied password in all output and never store it in repository files.
