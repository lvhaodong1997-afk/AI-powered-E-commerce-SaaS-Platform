# Readable Time Display Implementation Plan

> **For agentic workers:** Execute this plan inline in the current session with test-first checkpoints.

**Goal:** Format user-visible TK date/time values instead of rendering raw Unix timestamps.

**Architecture:** Keep the backend's existing millisecond timestamp contract. Add one frontend formatter that normalizes seconds, milliseconds, numeric strings, and date strings, then use it at user-visible TK time fields.

**Tech Stack:** Vue 3, TypeScript, dayjs, Node.js static UI contract tests, pnpm/vue-tsc.

## Global Constraints

- Do not change database schemas or backend API response formats.
- Use `YYYY-MM-DD HH:mm:ss` for full date/time display and `-` for empty values.
- Do not treat video durations or cover offsets as calendar timestamps.
- Preserve unrelated working-tree changes and deploy only the built frontend artifact after verification.

### Task 1: Add the timestamp formatter contract

**Files:**
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-ui/yudao-ui-admin-vue3/src/utils/formatTime.ts`
- Create: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-ui/yudao-ui-admin-vue3/tests/time-format-contract.test.cjs`

- [ ] Write a static contract test requiring `formatTimestamp` to normalize a numeric timestamp and requiring the UI pages to call it for date fields.
- [ ] Run `node tests/time-format-contract.test.cjs`; it must fail because the helper and replacements do not exist.
- [ ] Add `formatTimestamp(value, format, emptyText)` to `formatTime.ts`, treating values below `1_000_000_000_000` as seconds and preserving date strings.
- [ ] Run the contract test again and confirm it passes.

### Task 2: Replace raw TK time rendering

**Files:**
- Modify: TK generation, batch, dashboard, open-api, publish-center, route, business-log, company, account-stats, and Meta publish pages under `yudao-ui/yudao-ui-admin-vue3/src/views/tk/`.

- [ ] Replace direct `createTime`, `updateTime`, `lastSyncTime`, token expiry, retry, delivery, and stats time output with `formatTimestamp`.
- [ ] Leave `videoDuration`, `coverTimestampMs`, and other duration/offset fields unchanged.
- [ ] Run the static contract test and inspect the focused diff for missed raw date fields.

### Task 3: Verify, commit, push, and deploy

**Files:**
- No additional source files.

- [ ] Run the frontend contract test, `pnpm ts:check`, and the relevant formatting/lint check.
- [ ] Build the production frontend and record its SHA-256.
- [ ] Commit only the plan, formatter, tests, and focused TK page changes; push `main`.
- [ ] Back up the current frontend release, upload the verified artifact, compare remote SHA-256, atomically switch, and verify service, HTTP, public page, and asset loading.
