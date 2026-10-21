# TikTok upload progress reconciliation

The user approved repairing HTTP 416 handling and synchronizing the server after the production diagnosis.

**Goal:** Continue the original upload session after uncertain transport outcomes without replaying accepted chunks or initializing another post.

**Architecture:** Keep the current executors, leases, retry generations and status callbacks. Pass an optional deadline-aware progress callback from the Open API publishing service through its platform adapter into the TikTok upload client. Use the existing official status API only after an uncertain transfer, HTTP 416 or retryable HTTP 5xx. Validate monotonic progress against the original chunk plan and file size before changing the next byte offset. Each write retains the ownership check; retries and elapsed time remain bounded. Missing, invalid or unavailable progress returns work to existing durable recovery.

**Scope:** Backend only; no schema or public HTTP contract changes. Existing success protection, file identity validation and expired-session handling remain in use. No manual historical reset or batch repost is part of this release.

## Tasks

- [x] Repair the pre-existing test compilation blocker in the dispatch-policy test helper.
- [x] Add a failing regression proving that the current upload client replays bytes after an acknowledgement is lost.
- [x] Extend the upload client and adapter with optional official-progress reconciliation; skip accepted ranges, retry unchanged progress within limits, reject backward/partial/out-of-file progress, and stop uncertain transfers without a resolver.
- [x] Wire both initial upload and same-session recovery to status checks using the matching connection and publish ID. Preserve lease fencing and existing terminal behavior.
- [x] Add stateful HTTP tests for lost acknowledgement, HTTP 416, unchanged/invalid/missing progress, status-query failure, completed upload, and owner loss before the next write.
- [x] Run focused upload, recovery, expiry, transaction and dispatch tests, then build a complete Spring Boot executable JAR with Maven.
- [x] Commit and push the repair branch. Back up the live JAR, stage and hash-check the release, atomically replace the artifact and restart only tk-yudao-prod. Verify PID, restart count, health, logs and observed upload progress; restore the backup if health fails.

## Risks and controls

An API query can lag behind the upload receiver, so retries are bounded and progress is never guessed. Another worker may advance the session; only validated forward chunk boundaries or the complete file may be skipped, with ownership rechecked before each PUT. Logs contain offsets, byte counts and fixed error summaries, never tokens or signed upload URLs. Existing recovery work is resumed through the established scheduler rather than bulk status edits. Production currently matches the local ec93a71 baseline and the fetched origin/main is an ancestor of that baseline; rebuilding from this branch preserves the deployed changes.

## Verified before release

- 160 focused tests passed with JDK 17; complete Maven Spring Boot repackage passed.
- Stateful receiver regressions cover accepted bytes followed by a lost response or HTTP 500/502/504.
- Progress status queries use the remaining upload deadline, reuse the current upload token and make one bounded request; normal background status reconciliation keeps its existing retry policy.
- Independent review found no remaining production-code blocker after both follow-up fixes.

## Production release evidence

- Code commit: `ab7d9f5001a11a7b9e8d463843a76eefae10c26e`; pushed to `codex/tiktok-upload-progress-reconcile`.
- Full executable JAR SHA-256: `bde99681d6bce6695361d30c85b836afb594e7e60e6a34b4f07d1e8e5e23389b`; the staged and live artifacts match.
- Backup and release journal: `/data/Tk/backups/upload-progress-ab7d9f50-20261021-175703/`.
- `tk-yudao-prod`: active/running, new PID `3092047`, `NRestarts=0`; backend tenant lookup returned code 0 over HTTP 200 and Nginx returned HTTP 200. Three deployed core class hashes match the local build.
- Live evidence on 2026-10-21 Beijing time: at 17:59:08 an HTTP 503 was followed by authoritative progress 0 -> 32,000,000 bytes; at 17:59:29 the same upload advanced 32,000,000 -> 64,000,000; at 17:59:40 a recovery upload advanced 64,000,000 -> 96,000,000. Accepted ranges were skipped within the existing session.
- These observations confirm the new reconciliation path is running. They do not establish that all historical recovery tasks are complete or that upstream HTTP errors have stopped.
