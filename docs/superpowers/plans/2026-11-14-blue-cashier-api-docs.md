# White-blue cashier and API documentation implementation plan

> Use subagent-driven-development task by task; the user has approved development, commits, pushes and deployment.

**Goal:** Restore recoverable payment entry points and deliver a consistent white/blue recharge page, cashier and public API documentation.

**Architecture:** Retain Vue/Element Plus TK frontend, TK order store and signed SaaS Stripe bridge. Reuse existing APIs, order identities and catalog prices. No new business tables for this feature.

## Binding constraints
- White and pale-blue surfaces. Primary action #1677FF, hover #0958D9; white/blue secondary actions; readable deep-blue-gray text and subtle borders.
- Apply the shared scoped theme only to recharge, cashier, public API docs and the docs entry action.
- Preserve four normal packages and the tenant 166/user 226 temporary VERIFY_1 gate with its existing cutoff. Never change amounts, add credits manually, reset the gate, or charge the user.
- Preserve current interfaces, tenant isolation, signature verification, idempotency and fulfillment.
- Responsive styling may remain; user explicitly excluded mobile acceptance. Verify desktop only (1440x1000 and a narrower desktop).
- Every project must sync latest origin main/master before changes; preserve incoming API/session/docs work. Commit/push TK main and its feature branch. Push SaaS feature branch after integrating master. Recheck remote heads before artifacts.
- Complete tests, independent review, builds, commit, push, deployment and live desktop acceptance without another approval prompt.
- Final user-facing execution result is a concise diff.

## Task 1: SaaS cashier recovery
Files: packages/billing/stripe/src/clipforge.ts; packages/billing/stripe/test/clipforge-payments.test.mjs; relevant existing contract tests if needed.
- Review refinement: independently reproduced cancellation/pending race requires a narrow additive migration and synchronized declarative SQL to preserve canceled state against older pending observations. Preserve the existing service-only invoker permissions and all legitimate paid/refund transitions; do not edit or replay the original deployed migration. Add real-SQL regression and independent pgTAP isolation review.
- [ ] Write failing real-SQL fixture regressions: checkout followed by repeated GET/reconcile and reopening must return safe WeChat QR or Alipay redirect for a requires_action pending intent; terminal and unsupported states must not expose stale actions.
- [ ] Confirm RED, implement one shared safe action extraction path for checkout and reconcile; reuse the already retrieved/validated intent, avoiding an additional provider GET on normal reconcile.
- [ ] Unknown/malformed/unsafe actions must never become arbitrary links or falsely report successful payment. Preserve gate semantics and late payment fulfillment.
- [ ] Run all ClipForge tests and scoped type/lint/format checks. Self-review. Report exact commands/results and any boundaries to cashier-task1-report.md in the task scratch folder.
- [ ] Independent task review of source diff and evidence.

## Task 2: TK visual redesign and payment UI recovery
Files: recharge/index.vue, recharge/PaymentDialog.vue, utils/tenantOnboarding.ts; Public/ApiDocs.vue and apiDocs.css; optional ApiDocMarkdown.vue; tk/open-api/index.vue; one shared scoped theme stylesheet in src/styles/; focused tests.
- [ ] Add behavior regressions for allowed Alipay redirect domains and payment entry recovery/fallback/terminal UI; observe meaningful RED before behavior code.
- [ ] Create scoped shared theme tokens. Reorganize recharge into account header, package selection, payment method and purchase summary, then history. Keep test package distinct and keep error recovery/idempotency logic.
- [ ] Build an intentional wide cashier with an order summary column and a centered white QR/payment action column; primary amount, channel brand cue, clear state steps, loading/entry-missing/retry/image-failure/paid-credit-pending/complete/terminal states. Reopening queries original order. Do not create another order to recover an uncertain result. Do not use quote expiry to falsely expire an already bound valid payment action.
- [ ] Align frontend safe URL validation with the server's supported exact Stripe hosts. Do not preserve stale payment URLs into terminal states.
- [ ] Redesign API docs white/blue, clear sidebar/search/active state, compact useful intro, readable parameter tables and pale code surfaces; wide desktop detail layout includes a right-side example area; retain language switch, code/address/response copying, search, guides, downloads and anchors. Keep all document content, auth warnings and paid-task warnings. No automatic live API execution.
- [ ] Run relevant frontend regression suites, vue-tsc, scoped lint/style/format, generated docs checks, production build and branding check. Desktop browser visual/interaction verification only; no mobile acceptance.
- [ ] Self-review, report files/commands/evidence in cashier-task2-report.md, independent review.

## Task 3: Integration, release and desktop acceptance
- [ ] Review combined branch changes and resolve blocking findings. Check schema changes inherited from upstream independently; no ad hoc schema policy alterations.
- [ ] Inspect current production compose, image revisions, database migrations and TK release; preserve every current config/override/callback DNS entry and unrelated latest features.
- [ ] Build immutable releases from clean pushed revisions. Retain previous releases/config for rollback, keep old hashed TK assets, record source and artifact hashes, wait for origin/public propagation during checks.
- [ ] Deploy SaaS web and worker and TK; apply only required independently reviewed migrations (upstream and cancellation fence) if not already present, with backup, source-body drift checks and privilege checks.
- [ ] Desktop live acceptance: permitted tenant sees test, other tenant does not; existing WeChat pending order displays a real QR after repeated query/reopen; safe Alipay path is covered by regression/provider response as available; normal ordering remains intact. Do not complete a real payment on the user's behalf.
- [ ] Verify public API docs search/navigation, code language/copy/downloads, request/response layout and desktop overflow; verify authenticated recharge/cashier at desktop sizes.
- [ ] Log out/delete only created QA sessions, remove temporary private token files, close our preview/browser sessions, confirm clean trees and pushed revisions.

