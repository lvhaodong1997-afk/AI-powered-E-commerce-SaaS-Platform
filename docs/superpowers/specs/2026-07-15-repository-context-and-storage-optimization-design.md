# Repository Context and Storage Optimization Design

Date: 2026-07-15

## Goal

Reduce repeated Codex project scanning, improve incremental task navigation, and reclaim disk space without removing source code or the repository-bundled runtime toolchain.

## Current State

- The repository root combines backend source, an operations console, deployment artifacts, build caches, logs, output files, and bundled runtimes.
- The root Git repository has no commits yet and almost every project directory is untracked.
- There is no root `AGENTS.md`, root `.gitignore`, or concise project map.
- Approximate directory sizes measured before cleanup:
  - `.git/`: 6.6 GB
  - `.codex-build/`: 4.5 GB
  - `ruoyi-vue-pro-master/`: 1.45 GB
  - `.runtime/`: 1.07 GB
  - `deploy/`: 624 MB
  - `logs/`: 96 MB
  - `output/`: 36 MB

## Cleanup Scope

### Delete

- `.codex-build/` because it contains reproducible build cache data.
- Temporary contents under `logs/` and `output/`.
- Historical deployment artifact directories and archives dated from 2026-07-06 through 2026-07-10.
- The standalone `deploy/tk-web-tenant-fix.tar.gz` artifact dated 2026-07-14 because it is superseded by the 2026-07-15 release.

### Retain

- `.runtime/` because it contains the known local Maven and Node toolchain used by this project.
- The latest `deploy/voice-video-upload-20260715/` release.
- `deploy/nginx/`, `deploy/systemd/`, `deploy/scripts/`, and `deploy/README_DEPLOY.md`.
- All source code, plans, mockups, configuration, and database files.
- `.git/` until its large-object and unreachable-object composition is diagnosed.

## Git Cleanup Safety

Inspect `.git/` with Git-native object statistics and large-object analysis. Only prune unreachable objects after confirming they are not the sole copy of required work. Do not rebuild or delete the Git repository as part of this optimization.

## Context Structure

Create these durable navigation files:

- Root `AGENTS.md`: repository boundaries, low-token exploration rules, common commands, verification rules, and protected paths.
- `docs/project-map.md`: concise mapping of product areas to backend, frontend, operations console, deployment, and configuration paths.
- Backend `AGENTS.md`: Maven module boundaries, TK module focus, server entry points, tests, and bundled Maven commands.
- Operations-console `AGENTS.md`: Node entry point, public UI, checks, and dependency exclusions.

Codex should start from the root guide and project map, search only the requested feature's definitions, references, direct dependencies, and focused tests, and expand scope only when a concrete dependency is unresolved.

## Ignore Rules

Add a root `.gitignore` covering build caches, bundled runtimes, dependency directories, logs, outputs, generated binaries, deployment archives, PID files, IDE files, and OS metadata. Keep source, deployment configuration, scripts, documentation, and lockfiles visible to Git.

## Verification

- Re-measure top-level directory sizes after cleanup.
- Confirm retained deployment configuration and the latest release still exist.
- Confirm deleted historical artifact paths no longer exist.
- Run `git status --short` and verify generated or binary directories are ignored.
- Validate that every path and command documented in the new navigation files exists.
- Run syntax or build checks only for files changed beyond documentation and ignore rules.

## Non-Goals

- Do not flatten the triple-nested backend source directory.
- Do not change application behavior, deployment configuration, database content, or server state.
- Do not delete `.runtime/`, source dependencies required for offline work, or the latest release.
- Do not sync cleanup changes to the production server.
