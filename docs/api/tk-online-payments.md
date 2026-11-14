# TK online tenant payments

The SaaS ClipForge bridge owns the authoritative catalog, quote and channel payment. TK owns tenant authorization, local order identity and credit accounting. Browser returns never credit an account. All user-facing endpoints require the current tenant administrator; platform refund requires the platform administrator. Request tenant/user/credits are never accepted from the browser.

## Configuration and deployment

Apply `yudao-module-tk/src/main/resources/sql/tk_online_payment_upgrade_mysql.sql` before starting this version. Its preflight query must return no frozen-account/log mismatches. Existing credits are adopted into a non-expiring legacy batch under the account lock; active work allocations are preserved. Do not force a migration over unbalanced legacy data.

Server-only Spring properties (standard environment binding supported):

```
tk.payment.enabled=false
tk.payment.worker-enabled=true
tk.payment.live-mode=true
tk.payment.gateway-url=https://tokdeploy.com
tk.payment.gateway-secret=<independent server-only >=32 characters>
tk.payment.notify-secret=<different independent >=32 characters>
tk.payment.notify-path=/admin-api/tk/payments/notify
```

Bridge secrets match SaaS CLIPFORGE_GATEWAY_SECRET / CLIPFORGE_NOTIFY_SECRET. Gateway origin must be HTTPS without credentials/query/hash, port443. New purchases default off. Worker recovery is independent of that switch. Configure webhook, actual merchant mode and supported channels before enabling purchases. Never show a test merchant as real payment availability.

## API

All routes use the existing `/admin-api` prefix. User and platform administration APIs use the existing CommonResult wrapper; the server-to-server notification acknowledgement below has its dedicated success code.

- GET `/tk/recharge/packages`: authoritative catalog and enabled methods.
- POST `/tk/payments/quote`: `{packageCode,quantity:1,paymentMethod,idempotencyKey}`. The key is 16–64 ASCII letters/digits/underscore/hyphen. It is unique within the tenant and freezes the original user/package/channel. Local order is durable before any gateway call. On uncertainty query the original order; never create another key to repeat payment.
- POST `/tk/payments/{orderNo}/pay`: `{amountCents,currency}` explicitly confirms the frozen actual payment amount. Currency is uppercase CNY/HKD/USD.
- GET `/tk/payments/{orderNo}`: current local snapshot with bounded reconciliation when pending. `created` means quote creation is still being recovered. Additional `creditStatus` distinguishes PENDING, CREDITED, REFUND_PENDING, REFUNDED and REVIEW. Only CREDITED indicates usable credit issuance.
- GET `/tk/payments?pageNo=1&pageSize=10`: tenant-scoped PageResult.
- POST `/tk/payments/{orderNo}/cancel`: cancels the original channel order.
- POST `/tk/payments/{orderNo}/refund`: platform-only full refund; reserves unused batch credits before contacting the channel. No partial refunds or self-service refund interface.
- POST `/tk/payments/{orderNo}/refund/release-failed`: platform-only recovery. No request proof/body is accepted: the server fetches the canonical original payment/refund snapshot outside its DB transaction. Only a verified bound full refund with `refundStatus=failed` or `canceled` may release the reservation. A previously unknown local refund ID is adopted only from that canonical binding; an established refund/payment ID cannot change. Unknown, pending, generic review and timed-out results retain reserved credits. Replays are idempotent, expired reservations become expired credits, and the order stays in REVIEW so it cannot charge or refund again.
- POST `/tk/payments/notify`: exact anonymous-login/tenant-filter exemption, mandatory raw-body HMAC, timestamp and nonce. Signed content only selects a known local order; TK retrieves the authoritative bridge state before making any credit change. Only successful processing returns HTTP 200 with `{"code":200,"data":true}`; this is intentionally distinct from browser API CommonResult success code 0. Failed processing must not acknowledge the delivery.

Snapshots preserve order/tenant/user/package/quantity/catalogVersion/base amount/points/actual amount/currency/mode/channel/FX. Version and payment ID cannot move backward or rebind. SaaS versions every visible quote-to-pending transition: quoted 0, payment_pending 1, then paid 2 (or a later version after intervening transitions). The additive `refundStatus` is canonical provider proof, never a client flag. Payment date persists at millisecond precision; six calendar months are measured from this verified UTC payment instant, never callback arrival. Provider redirect/QR fields are display-only.

## Ledger and recovery

Payment order row locks plus unique source keys provide one credit grant per payment. The aggregate account row serializes all batch mutations, normal task freeze/settle/failure release, manual adjustments, expiry and refund. Credits allocate by earliest expiry with legacy non-expiring credits last. Expired frozen credits remain settleable; failed work returns them to expired balance rather than available balance. Grants, expiry, completed refunds and definite-failure reservation releases write audit logs in the same transaction. Current locking reads bypass preliminary MyBatis SESSION snapshots and MySQL repeatable-read snapshots. A conditional log transition plus checked allocation transitions selects a single completion winner, including zero-cost tasks.

The existing system tenant `accountCount` update joins a Spring transaction through the system-defined optional `TenantCreditQuotaUpdater`; its TK implementation requires that transaction. It acquires the account lock, validates the absolute quota and updates the ledger before the outer tenant update commits. Impossible reductions roll the entire edit back. The lock remains held across the tenant write and serializes paid grants. No system-to-TK Maven dependency is introduced; tenant creation retains its existing dynamic transaction boundary.

Confirmed external refunds of used/frozen credits enter REVIEW and preserve balances for manual reconciliation. Unknown refund requests retain reserved credits and recover the original refund. Do not clear order/batch/provider bindings to retry. Stop new purchases through the switch, retaining compatible workers/webhooks/tables for existing orders. Completed paid orders stop routine polling; failed notification reconciliation explicitly schedules recovery, including later refunds.

Deployment must preserve the actual production baseline and existing source overlays. Back up schema/data and release/configuration before migration. Roll back application pointers and purchase switches as needed, retaining all additive payment tables and pending history. Never drop payment data as a rollback.
