# Temporary TK payment verification

Approved 2026-11-14: only 秀美二楼 (tenant 166, tenant administrator user 226), for 24 hours from activation. Reuse Jiying's `VERIFY_1` package: CNY 1 and 1 point per unit, quantities 1/5/10. User manually confirms the actual quoted amount and performs the payment.

## Scope

- Fix blank recharge/invite navigation: fixed direct routes must have names distinct from the backend-generated `TkRecharge` / `TkInvite` menu routes. Existing same-name registration removed their default child pages.
- Add temporary test card and quantity/total selection on the existing recharge page. Retain quantity and idempotency key during uncertain quote recovery; quantity-less historical drafts still mean one unit.
- TK and SaaS both require explicit enablement, a future UTC cutoff and matching tenant/user allowlists. Four production packages, existing interfaces, signed payment notifications and ledger architecture remain in place.
- Use existing order and credit tables. New test points expire six calendar months after successful payment; duplicate notifications cannot grant twice.
- Keep channel minimums and exact amounts. Recognized minimum rejections must display the reason and safely permit another quantity; network/unknown results preserve the original request.
- Closing the test gate blocks new quotes/payments while existing order lookup, bound payment reconciliation and notifications continue.

## Runtime settings

TK `tk.payment` properties: `verify-enabled`, `verify-expires-at`, `verify-tenant-ids`, `verify-user-ids`. Defaults are closed. Whitelists are numeric CSV strings.

SaaS web and worker use identical `CLIPFORGE_VERIFY_ENABLED`, `CLIPFORGE_VERIFY_EXPIRES_AT`, `CLIPFORGE_VERIFY_TENANT_IDS`, `CLIPFORGE_VERIFY_USER_IDS`. Public SaaS catalog still shows only four regular packages. TK appends the locally fixed test package for the permitted identity.

## Verification and rollout

1. Sync latest branches/main, preserve unrelated source and production changes.
2. Regression-test actual Vue Router menu registration, quantity recovery, permission/cutoff failures and the real SQL idempotent ledger.
3. Independently review the combined payment/recovery changes; run types, scoped lint/format, frontend production build and Java packaging.
4. Commit and push each changed repository, deploy new releases with rollback configuration. Preserve old hashed TK assets, runtime files, all SaaS Compose settings and the callback DNS override. Apply no schema migrations.
5. Verify authenticated tenant/platform menu navigation, authorized-vs-other-tenant test visibility, exact quantity totals, minimum rejection recovery and live provider quote creation. Do not pay on the user's behalf.

The exact UTC cutoff is recorded by the activation tooling on deployment, shared by both services. Existing quotes and payment history must remain auditable after the cutoff.
