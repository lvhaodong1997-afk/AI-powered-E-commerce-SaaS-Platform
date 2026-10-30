# Task 4 report — Shop frontend and review materials

Status: DONE_WITH_CONCERNS (local frontend implementation and focused checks complete; live gates remain).

## Implemented

- Added frozen Shop API types and wrappers for capabilities, OAuth sessions/accounts, product pages, immutable task create/get/confirm, and Shop media sessions. Multipart chunks use the Shop `file` field; ordinary TikTok upload wrappers remain untouched.
- Added cancellable Shop upload wrapper with AbortController, per-operation revision checks, bounded retry inherited from the generic chunk helper, and late completion rejection. Publish POST is never retried; ambiguous confirm reconciles with GET only.
- Added pure behavior controller helpers for strict OAuth origin/source/session filtering, cryptographic idempotency keys, immutable-form invalidation, precheck advisory/blocking normalization, stale product protection, bounded non-overlapping polling, and Shop-only startup permissions. Added 12 node:test behavioral checks with RED before helper implementation and GREEN after implementation.
- Added `TiktokShopPublishPanel.vue`, `TiktokShopProductPicker.vue`, and `TiktokShopPublishConfirm.vue`, integrated the Shop tab with the existing publish center, and added a Shop-only startup branch that does not invoke ordinary TikTok lists when only parent + Shop query permissions are present.
- Added Chinese/English Shop message catalog and loader wiring. Confirmation displays server task snapshot identifiers and exact requestHash/version metadata; no mutable source URL is presented as the locked snapshot.
- Added `docs/tiktok-shop/P0-联调与审核.md` with callback, permissions, environment, shared media root, recording flow and external gate checklist.

## TDD evidence

- RED: `.superpowers/sdd/task-4-red.log` — helper missing; tests failed for the expected missing implementation.
- GREEN: `.superpowers/sdd/task-4-helper-green.log` — initial 10 behavior tests passed.
- Upload RED: `.superpowers/sdd/task-4-upload-red.log` — Shop upload wrapper missing.
- Upload GREEN: `.superpowers/sdd/task-4-upload-green.log` — 12/12 behavior tests passed.
- Vue type check initially found two event/nullability errors; both were corrected. Final type check exited 0 with bundled Node 24 and `vue-tsc --noEmit --incremental false`.

## Validation

```text
node --test tests/tiktok-shop-publishing.test.cjs
12 tests, 0 failures, 0 errors, 0 skipped
node ./node_modules/vue-tsc/bin/vue-tsc.js --noEmit --incremental false
exit 0
```

No backend files were changed. No live TikTok authorization, product catalog, precheck, publish, migration, deployment or real secret was used. Existing ordinary TikTok and Meta services were preserved. Existing compiler/toolchain warnings are outside this focused check; a full frontend lint/build remains a follow-up if needed by the integration owner.

## Files

`src/api/tk/tiktokShop/index.ts`; `src/utils/tiktokShopUpload.ts`; `src/views/tk/video-publish-center/components/tiktokShopBehavior.ts`; `TiktokShopPublishPanel.vue`; `TiktokShopProductPicker.vue`; `TiktokShopPublishConfirm.vue`; `src/locales/tk/tiktokShopMessages.ts`; `src/locales/tk/en.ts`; `src/locales/tk/zh-CN.ts`; `src/views/tk/video-publish-center/index.vue`; `tests/tiktok-shop-publishing.test.cjs`; `docs/tiktok-shop/P0-联调与审核.md`.

## Concerns

Live OAuth popup behavior, provider product availability, real media upload, precheck and publication remain unverified. The UI intentionally shows immutable source IDs and sizes because the frozen backend DTO exposes no safe snapshot preview URL. Deployment must apply the SQL migration, configure keys, grant the parent menu plus narrow Shop children, and mount the same trusted media root on every worker.
