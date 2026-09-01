# Video Duration Correction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ensure a generated video reaches the effective target duration when concatenated material is shorter, such as 114 seconds of material for a 116-second task.

**Architecture:** Keep the existing TK rendering pipeline. After segment concat, probe the merged video and, only when it is shorter than the task target, re-encode it with the existing `setpts` speed filter to stretch it to the target. Use the effective target as the final render duration instead of the merged file's shorter probe value.

**Tech Stack:** Java, Spring service, FFmpeg/FFprobe, existing TK render helpers.

## Global Constraints

- Preserve unrelated user changes in the dirty worktree.
- Do not change frontend, database schema, or third-party integrations.
- Keep the existing audio timing source; do not stretch TTS audio.
- Per user instruction, do not run tests in this execution.

---

### Task 1: Correct short merged-video duration

**Files:**
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/generation/pipeline/DefaultTkVideoRenderService.java`

**Interfaces:**
- Consumes: `TkGenerationTaskDO.targetDuration`, the concatenated `merged-video.mp4`, and existing `TkRenderMediaSupport.buildVideoSpeedFilter`.
- Produces: a merged video at least as long as the effective target when the original merged video is short, and final rendering that uses the effective target duration.

- [ ] **Step 1: Add a duration-correction helper command**

Add a package-private command builder that re-encodes a short merged video with the existing normalization filter and a `setpts` factor derived from source and target duration. Keep the command free of audio streams.

- [ ] **Step 2: Apply correction immediately after concat**

Probe the concatenated file, compare it with `TkVideoDurationSupport.normalize(task.getTargetDuration())`, and return the corrected file only when the shortfall exceeds the existing 0.05-second epsilon. Leave longer files unchanged so final rendering can trim them.

- [ ] **Step 3: Use the target duration for final merge**

Pass the normalized task target to `buildFinalRenderCommand` instead of `probeDuration(mergedVideo)`, while retaining the corrected merged file as the video input for subtitle analysis and final rendering.

- [ ] **Step 4: Review the focused diff**

Inspect only the modified render service diff and confirm no unrelated files or behavior changed. Do not run tests per the global constraint.

