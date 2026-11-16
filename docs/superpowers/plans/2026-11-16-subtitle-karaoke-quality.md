# Subtitle Karaoke Quality Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Execute with failing regression tests and independent review.

**Goal:** Reduce unnecessary partial karaoke while preserving truthful timing and existing APIs.

**Architecture:** Keep the existing Java timeline, Python faster-whisper and ASS renderer. Diagnose every fallback; preserve complete ASR word groups at caption boundaries; perform at most one quality-triggered backup recognition and choose by validated spoken-text timing coverage.

**Tech Stack:** Java/Spring, Python/faster-whisper, FFmpeg/ASS.

## Global Constraints

- User approved P0 implementation, latest-main synchronization, commit/push main and server deployment.
- Minimal changes; no database migration, new paid API, forced-align dependency, UI redesign or public status change.
- Estimated/interpolated timings must never be promoted to real word timings. A multi-character ASR word may remain a group highlight.
- Preserve original text, existing line/width limits and valid accurate karaoke; keep off/enabled/partial/disabled semantics.
- Retry at most once per task with a bounded total recognition timeout; avoid retry for missing configuration/model or disabled retry.
- Measure trustworthy spoken-text coverage, not just caption count; report diagnostic reasons matching actual renderer behavior.
- Preserve unrelated workspace edits and server configuration. Never force-push. Build release from clean latest remote committed revision and record checksums.

## Task 1: Baseline and diagnostics

- [ ] Read task2071 original narration, ASR JSON, timeline, quality report and ASS from authorized server; collect sanitized evidence and classify failure causes.
- [ ] Add failing tests for missing timing, mismatched units, split groups and invalid timing reasons.
- [ ] Add backward-compatible quality JSON fields for each segment's actual reason and trustworthy spoken-text coverage; reports must agree with ASS validation.
- [ ] Verify focused tests and record evidence.

Files: product `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/generation/pipeline/{DefaultTkSubtitleTimelineService,TkSubtitleKaraokeQuality,TkSubtitleSegment,DefaultTkAssSubtitleRenderService}.java`; matching focused tests.

## Task 2: Alignment boundaries and bounded quality retry

- [ ] Add failing regression tests where globally acceptable ASR has partial reliable word timing, where a Chinese word spans a caption boundary, and where a backup is worse/disabled/times out.
- [ ] Keep speech matching separate from visible original text; preserve complete source word boundaries where captions can fit. Never fabricate reliable character timestamps.
- [ ] Reevaluate validated timing coverage after initial ASR; if insufficient and backup available, run once, respect shared recognition budget, and select a valid candidate by reliable text coverage and content quality. Retain safe fallback if retry fails.
- [ ] Validate repeat phrases, numbers, punctuation, Chinese/English/mixed text and estimated timings with at least 20 independently meaningful cases; test no regressions in line breaks, keyword highlighting and karaoke rendering.

Files: `DefaultTkSubtitleTimelineService.java`, `TkGenerationProperties.java` only when a bounded configuration is necessary; relevant tests; Python script only if baseline evidence demonstrates an input/output problem.

## Task 3: Verification and release

- [ ] Run focused backend tests using bundled Maven `-pl yudao-module-tk -am`, Python subtitle tests and existing render/delivery tests. Review diff independently; fix important findings.
- [ ] Reprocess task2071 cached narration without invoking TTS or charging a new task. Compare raw/timeline/ASS quality against original; if audio is incomplete report it honestly.
- [ ] Commit implementation and evidence report, push non-forced to main; refetch and build a clean remote revision.
- [ ] Prepare backend release by copying existing server release, replacing built jar and any changed subtitle scripts only. Preserve frontend assets, configuration, runtimes and media; validate release, stage checksums and rollback path, ensure no active tasks at restart.
- [ ] Deploy, health-check authenticated API, confirm existing task data and validate generated subtitles with controlled real generation if needed. Record deployed commit, artifact hashes, measurements and any limits.

## Acceptance

- Original task2071: pursue 4/4 reliable captions if original narration is complete and timing recoverable; otherwise report exact remaining causes.
- At least 20 meaningful regression cases; no negative/zero/backward/out-of-range trusted word timings; no estimated timing falsely classified as actual.
- Benchmark target >=95% reliable spoken-text coverage and manually annotated P95 boundary error <=200ms; do not claim measured targets without actual labeled evidence.
- Full-text voice coverage is independent of subtitle text coverage; do not mask unspoken text through alignment.
