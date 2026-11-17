# TikTok Shop creator and seller authorization

Apply the original `tk_tiktok_shop_publish_mysql.sql` first, then the additive
`yudao-module-tk/src/main/resources/sql/tk_tiktok_shop_seller_upgrade_mysql.sql`.
The seller migration uses information_schema guarded ALTER statements and CREATE
TABLE IF NOT EXISTS, compatible with MySQL 5.7 and 8. Reapplying it preserves
accounts, shop IDs, authorization sessions and existing permission seeds.
Back up these tables before release. Apply the migration before starting the new
server; it is additive, so rollback can restore the previous application binary
without dropping tables or columns.

Existing creator variables remain unchanged. `TIKTOK_SHOP_ENVIRONMENT` defaults
to PRODUCTION. Seller configuration is independent of `TIKTOK_SHOP_ENABLED`:

| Environment variable | Default / requirement |
| --- | --- |
| TIKTOK_SHOP_SELLER_ENABLED | false; keep disabled until seller setup is valid |
| TIKTOK_SHOP_SELLER_APP_KEY | Dedicated verified seller-capable application key |
| TIKTOK_SHOP_SELLER_APP_SECRET | Seller application secret; use protected server environment |
| TIKTOK_SHOP_SELLER_AUTHORIZATION_URL | Exact official Partner Center seller authorization link |
| TIKTOK_SHOP_SELLER_ENVIRONMENT | PRODUCTION or TEST; explicitly match the intended environment |

Both authorization types reuse `TIKTOK_SHOP_REDIRECT_URI` (HTTPS callback),
`TIKTOK_SHOP_FRONTEND_ORIGIN` (exact browser origin), and the existing dedicated
AES-256 `TIKTOK_SHOP_TOKEN_ENCRYPTION_KEY`. Do not rotate that key without a token
migration or reauthorization procedure. A changed application, secret, environment,
authorization URL, callback, frontend origin, or encryption key invalidates new
pending sessions. Legacy creator sessions remain compatible during their normal
10-minute lifetime.

Copy the seller link from the correct app's Partner Center. Allowed forms are:
`https://services.us.tiktokshop.com/open/authorize?service_id=...` (US) and
`https://services.tiktokshop.com/open/authorize?service_id=...` (ROW). Do not add
state, redirect, host overrides or creator credentials. The backend adds its own
random single-use state. TEST is an explicit identity label, not an invented
sandbox hostname; use credentials and link supplied by TikTok for that environment.

The app must have approved seller scopes, and the seller must grant
`seller.authorization.info` to enumerate authorized shops. Read-only products and
orders additionally require their official seller scopes (respectively
`seller.product.basic` and `seller.order.info`). A creator.video.write approval
does not enable seller APIs. The observed current app has only effective creator
video scope; this release leaves seller disabled until the operator supplies valid
seller setup. This release does not request platform scopes or auto-grant roles.

Read and authorization endpoints reuse existing explicit permissions:
`tk:tiktok-shop-account:query` and `tk:tiktok-shop-account:authorize`. Operators
must grant these intentionally. Seller tokens are never supplied to creator
publishing; seller account/shop IDs belong to independent tables.

After deployment, validate capabilities, creator account reads and publishing
configuration without publishing content. With an approved seller setup, validate
authorization and read-only shop/product/order access using its assigned tenant,
company and user. Internal callers must run `requireReadableShop(localShopId)`
before obtaining a token, then pass that returned tenant/company/account identity
to the seller token service. Do not return raw entities, shop_cipher, credentials,
authorization codes or tokens in API responses or logs.

Primary authorization reference:
https://partner.tiktokshop.com/docv2/page/authorization-overview-202407
