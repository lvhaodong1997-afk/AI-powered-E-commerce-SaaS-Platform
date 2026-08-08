# TK Auto Mix SaaS Repository Guide

## Start Here

Read `docs/project-map.md` before searching. Treat this directory as a workspace containing multiple subprojects, not as one source tree.

## Low-Token Rules

- Do not scan the entire repository by default.
- Search only the requested feature's definitions, references, direct dependencies, configuration, and focused tests.
- Expand search scope one dependency boundary at a time and only when a concrete symbol or data flow is unresolved.
- Prefer `rg` with explicit source paths and exclusions over root-wide recursive listing.
- Do not read `.runtime/`, `.codex-build/`, `.codex-ytdlp-wheel/`, `.playwright-cli/`, `.git/`, `node_modules/`, `target/`, `dist*/`, `build/`, `logs/`, `output/`, deployment archives, JAR files, or generated media.
- Read existing plans only when the current task explicitly continues that plan.
- Use `git status --short` and focused diffs for follow-up work. This repository may contain unrelated user changes; preserve them.

## Active Subprojects

- Product backend and web frontend: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/`
- Operations console: `tk-ops-console/`
- Deployment configuration: `deploy/nginx/`, `deploy/systemd/`, `deploy/scripts/`, and `deploy/README_DEPLOY.md`
- Product plans and mockups: `docs/`

Read the nearest child `AGENTS.md` after entering an active subproject.

## Protected Paths

- Keep `.runtime/`; it contains the repository-bundled toolchain.
- Do not change production systems or server files unless the user explicitly requests deployment or synchronization.
- Do not delete the latest release under `deploy/voice-video-upload-20260715/` without explicit approval.
- Do not move or flatten the nested backend root without a dedicated migration task.

## Focused Verification

- Operations console: run `npm run check` in `tk-ops-console/`.
- Backend TK module: use `.runtime/apache-maven-3.9.10/bin/mvn.cmd` with `-pl yudao-module-tk -am` from the product source root.
- Web frontend: use `.runtime/npm-global/node_modules/.bin/pnpm.cmd` from `yudao-ui/yudao-ui-admin-vue3/` and select the narrowest relevant script.
- Documentation or ignore-only changes: validate paths, search boundaries, and `git check-ignore`; do not run unrelated full builds.

