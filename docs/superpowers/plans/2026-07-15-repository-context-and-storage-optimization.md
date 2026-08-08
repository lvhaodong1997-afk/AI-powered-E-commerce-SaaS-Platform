# Repository Context and Storage Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce repeated Codex scanning and reclaim reproducible or superseded project storage while preserving source code, the bundled runtime, deployment configuration, and the latest release.

**Architecture:** Add layered repository guidance at the root and each active subproject, plus one concise project map. Remove only explicitly approved cache and artifact paths, then use Git-native diagnostics and maintenance for `.git` rather than deleting or rebuilding it.

**Tech Stack:** Markdown, Git ignore rules, PowerShell, Git, Maven, Node.js

## Global Constraints

- Keep `.runtime/` and all source code.
- Keep `deploy/voice-video-upload-20260715/`, deployment configuration, scripts, and documentation.
- Do not flatten the nested backend directory.
- Do not change application behavior, database content, production configuration, or server state.
- Do not delete or rebuild `.git/`.

---

### Task 1: Repository Navigation and Ignore Boundaries

**Files:**
- Create: `.gitignore`
- Create: `AGENTS.md`
- Create: `docs/project-map.md`
- Create: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/AGENTS.md`
- Create: `tk-ops-console/AGENTS.md`

**Interfaces:**
- Consumes: Existing Maven modules, Node scripts, deployment layout, and bundled runtime paths.
- Produces: Durable instructions used by Codex before repository searches.

- [ ] **Step 1: Inventory exact source and command paths**

Run focused `rg --files` and manifest reads while excluding caches, binaries, dependencies, logs, and output directories.

- [ ] **Step 2: Create root ignore rules**

Ignore `.runtime/`, `.codex-build/`, `.codex-ytdlp-wheel/`, `.playwright-cli/`, dependency directories, build output, logs, temporary output, PID files, deployment binaries, IDE metadata, and OS metadata. Keep deployment scripts, service definitions, Nginx configuration, Markdown, source, and lockfiles visible.

- [ ] **Step 3: Create layered Codex guidance**

Document repository boundaries, protected paths, low-token search rules, focused validation, backend commands, and operations-console commands.

- [ ] **Step 4: Create the project map**

Map product source, Maven modules, frontend source, operations console, deployment configuration, runtime commands, and common task-to-path routes.

- [ ] **Step 5: Validate every documented path**

Run PowerShell `Test-Path` checks for each root, manifest, runtime executable, configuration path, and validation target referenced by the guides.

### Task 2: Reproducible Cache and Historical Artifact Cleanup

**Files:**
- Delete: `.codex-build/`
- Delete contents: `logs/`
- Delete contents: `output/`
- Delete: approved historical entries under `deploy/`

**Interfaces:**
- Consumes: The retention list in the approved design.
- Produces: A smaller working tree with the latest release and operational deployment files intact.

- [ ] **Step 1: Resolve and verify deletion targets**

Resolve every target to an absolute path and confirm it is inside the repository root. Record the pre-delete total size.

- [ ] **Step 2: Delete reproducible caches and temporary output**

Delete `.codex-build/` and the contents of `logs/` and `output/` using PowerShell `Remove-Item` with literal, verified paths.

- [ ] **Step 3: Delete superseded releases**

Delete deployment artifacts dated 2026-07-06 through 2026-07-10 and `deploy/tk-web-tenant-fix.tar.gz`. Preserve the 2026-07-15 release and operational deployment files.

- [ ] **Step 4: Verify retention and deletion**

Confirm every retained path exists and every approved historical target no longer exists.

### Task 3: Git Object Diagnosis and Safe Maintenance

**Files:**
- Modify internally through Git: `.git/`

**Interfaces:**
- Consumes: Git object database and reflogs.
- Produces: Object statistics and safe storage reduction without repository replacement.

- [ ] **Step 1: Inspect object composition**

Run `git count-objects -vH`, inspect pack files, refs, reflogs, and the largest reachable or dangling objects.

- [ ] **Step 2: Select safe maintenance**

Run only Git-native maintenance supported by the diagnosis. Do not delete `.git` files manually. Preserve reachable objects and current index state.

- [ ] **Step 3: Verify Git integrity**

Run `git fsck --full`, `git status --short`, and `git count-objects -vH`. Report any pre-existing corruption or unreachable objects before claiming success.

### Task 4: Final Verification

**Files:**
- Verify: all files created or retained above

**Interfaces:**
- Consumes: Outputs of Tasks 1 through 3.
- Produces: Evidence of space reclaimed and reduced search noise.

- [ ] **Step 1: Re-measure top-level storage**

Use the same PowerShell size calculation used before cleanup and compare totals.

- [ ] **Step 2: Verify ignore behavior**

Use `git check-ignore -v` on representative runtime, cache, dependency, log, output, and deployment binary paths.

- [ ] **Step 3: Review guidance for placeholders and stale paths**

Search the new documents for placeholders and verify the listed commands and paths against the working tree.

- [ ] **Step 4: Report outcome**

Summarize deleted categories, retained assets, disk space reclaimed, Git object findings, verification results, and the unresolved Git identity configuration that prevents commits.
