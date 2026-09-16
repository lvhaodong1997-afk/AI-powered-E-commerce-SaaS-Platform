# Meta Social frontend delivery

Delivered the authorized frontend scope, including the subsequent Instagram/Facebook VIDEO requirements. No commits, deployment, Maven runs, or real platform posts were performed.

## Files

Frontend base: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-ui/yudao-ui-admin-vue3/`.

- Added `src/api/tk/socialPublish/index.ts`: typed exact API routes; existing CommonResult unwrapping; multipart upload; 180-second upload/create timeouts; VIDEO/IMAGE and optional width, height, durationSeconds, frameRate; maxVideoBytes with legacy maxVideoSizeMb fallback.
- Added `src/views/tk/video-publish-center/components/MetaPublishPanel.vue`: independent Meta account management, media/target/copy composer, task/detail pages, permissions and status messages.
- Added `src/views/tk/video-publish-center/components/MetaGeneratedPicker.vue`: existing successful-generation API, title search and pagination, generation query permission, server suitability notice.
- Added `src/views/tk/video-publish-center/components/metaPublishController.ts`: capabilities gate, authorization/session/Page selection, upload/create/retry, serialized polling and lifecycle handling.
- Modified `src/views/tk/video-publish-center/index.vue`: seven added lines/two removed lines for a permission-gated Meta tab/mount and hiding TikTok toolbar actions only on the Meta tab. Existing TikTok handlers remain unchanged.
- Added `tests/meta-social-publish.test.cjs`: 18 behavioral tests.

## Behavior and integration

- First request is capabilities under `tk:social-publish:query`. Disabled or failed capabilities do not trigger Social account/task table queries. Configuration can be refreshed.
- MP4 upload defaults to 100 MiB, overridden by maxVideoBytes; JPEG limit is 8 MiB. Generated selection submits generationTaskId exclusively; uploaded selection submits mediaId exclusively. The panel also accepts an optional generationTaskId prop; current host uses its internal generated picker without changing TikTok route handling.
- Independent Instagram/Facebook copy, up to 20 account targets, title/caption limits aligned with current create request DTO. Supports AUTHORIZED and the current auth service's ACTIVE account state; unavailable accounts cannot publish.
- Facebook video is labeled Reels, with this workflow's supported 9:16, >=540x960, 23–60 fps, 4–60 seconds constraints. Instagram video fps is shown as 23–60. These are workflow constraints, not universal platform maxima. Uploaded metadata is displayed when supplied. Generated-list suitability is explicitly pending server preflight because that existing list does not supply complete media metadata.
- Authorization opens the popup synchronously before requesting the redirect URL, removes opener, polls the owner-bound session without trusting postMessage, and binds only current-session Page candidates. Closing the popup does not discard a potentially successful authorization. Terminal states, cancellation, component deactivation and unmount clean up timers; late responses cannot revive polling. Authorization wait is bounded to ten minutes.
- Task creation returns/shows taskId and queues asynchronous status tracking. A lost create response preserves the same idempotency key for an unchanged retry. A status-read failure after accepted creation does not report creation as failed.
- FAILED / REAUTH_REQUIRED details can be retried with permission; reauthorization has its own action. UNKNOWN provides manual platform-verification guidance and no retry control. Status synchronization only calls the server reconciliation endpoint.
- Capabilities, query, authorize, update, create and retry permissions gate outgoing controller requests as well as the related UI controls. Generated listing additionally requires tk:generation:query.

## Validation

- Test-first red runs observed for the missing implementation, request limits, AUTHORIZED support, serialized polling after partial request failure, maxVideoBytes and create timeout; corrected implementations then passed.
- `node --test tests/meta-social-publish.test.cjs tests/tiktok-upload-request.test.cjs`: **19 passed, 0 failed** (18 Meta behavior cases plus existing TikTok upload regression).
- Bundled `pnpm.cmd ts:check`: **exit 0**, including final VIDEO/metadata/capabilities changes.
- Focused `git diff --check`: **exit 0**; Git emitted only the existing Windows line-ending conversion notice.
- Node used: `C:/Users/lhd/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node.exe`. pnpm used: workspace `.runtime/npm-global/node_modules/.bin/pnpm.cmd`, with bundled Node prepended to PATH. System Node 18 was not used for checks.

No browser-based end-to-end or configured-app OAuth/video-posting verification was performed. Real Instagram/Facebook publishing, signed media access and final server preflight require the integrated backend plus configured apps/accounts. Existing dashboard/index.vue and minimax test edits were not changed by this work.

## Review follow-up: initialization reconnect

- Reproduced initial account/task read failures leaving the enabled panel without a polling timer (three failing regression cases).
- Initialization now waits for both reads to settle and schedules one reconnect loop even if either read fails. Failed account reads are retried until successful; normal status polling then continues. Data-read errors use recoverable polling feedback rather than a configuration error.
- Manual refresh does not create duplicate timers. Disabled/unauthorized/failed capabilities and stopped components do not initiate table-query reconnects.
- Final focused frontend run: **24 passed, 0 failed** (23 Meta cases plus TikTok upload regression); bundled `pnpm ts:check`: **exit 0**.
- `UNKNOWN` remains non-retryable in the frontend. Existing status/sync action supports the backend's read-only reconciliation without adding a public status or a new publish mutation.
