# X and YouTube publishing

Apply `tk_channel_publish_upgrade_mysql.sql` after the existing social schema and before deploying the server. The migration adds two separate menu entries and leaves role grants unchanged. Grant each channel's query, authorize and create permissions to the appropriate pilot roles, along with `tk:generation:query` for video selection.

Both channels are disabled by default. Use the protected server environment to configure:

| Setting | X | YouTube |
| --- | --- | --- |
| Enable | TK_X_PUBLISH_ENABLED | TK_YOUTUBE_PUBLISH_ENABLED |
| Client identifier | TK_X_CLIENT_ID | TK_YOUTUBE_CLIENT_ID |
| Client secret | TK_X_CLIENT_SECRET | TK_YOUTUBE_CLIENT_SECRET |
| Redirect URI | TK_X_REDIRECT_URI | TK_YOUTUBE_REDIRECT_URI |

Register the exact public HTTPS callback on each provider. The standard paths are `/admin-api/tk/channel-publish/x/callback` and `/admin-api/tk/channel-publish/youtube/callback`. Do not log callback query strings at the reverse proxy. Keep the existing `tk.social.encryption-key` stable; the new channels reuse this 32-byte Base64 key for the refresh credentials and upload session URLs. Meta does not have to be enabled.

Before enabling customer roles, bind a test account for each provider. Verify an X video post, a private YouTube upload and processing completion, and a fresh-token publication after expiry. Check browser and provider channel identity before confirmation. Do not automatically retry a record marked UNKNOWN. See the provider channel first to confirm what happened.

For X OpenAPI, apply the additive `tk_open_x_upgrade_mysql.sql`, `tk_open_x_multiuser_upgrade_mysql.sql`, `tk_open_x_client_isolation_upgrade_mysql.sql`, and `tk_open_x_workspace_isolation_mysql.sql` after the channel migration before restarting the backend. The isolation script does not copy workbench token ciphertext; migrated connections must be re-authorized before they can publish, and leftover social-table OpenAPI tasks are refused without being auto-cancelled. Rollback keeps the new tables and unique key and reverts the JAR; do not share a workbench refresh token with OpenAPI. See the [client provisioning and verification instructions](../docs/api/x-publishing.md). The OpenAPI switch is separate and defaults to off.

For X photo publishing, apply `tk_open_x_photo_upgrade_mysql.sql` before starting the backend. Set `TK_X_PHOTO_PRIVATE_ROOT` to a persistent, service-owned directory outside Nginx roots and public upload paths. A multi-instance deployment needs the same private filesystem on every node. Deploy the SaaS web build with the backend; the X guide is included in the API reference. Allow photos to finish and stop accepting new photo jobs before rolling back; keep the nullable columns in place. A real-post smoke needs an explicitly authorized X test account.

## X OpenAPI release health and rollback

Use `deploy/tk-x-openapi/health_and_rollback.sh` from the locked release script. Do not treat `systemctl is-active` alone as success, and do not declare success before 127.0.0.1:48080 is listening. A release is healthy only after a stability window in which all of the following remain true: the `current` symlink still points at this release, the JAR sha256 matches, port 48080 is listening, the unit stays `active` with `NRestarts` unchanged, unauthenticated local and public `GET /admin-api/tk/open/v1/x/connections` return `OPEN_API_AUTH_HEADER_MISSING` (typically HTTP 401), and the local and public homepages return HTTP 200. Truncate curl body files before each request so a leftover body cannot pass the check. On failure, roll back immediately to the previous release while the same deploy lock is still held, and only if `current` is still this release. Never reactivate `release-x-openapi-isolation-20261120-98787ddc-ff004a2`. Additive MySQL objects stay in place on rollback; do not drop them. The OpenAPI master switch is not changed by a release.
Before rolling back to a JAR without workspace isolation, disable the X OpenAPI switch and leave it disabled until an isolation-aware JAR is active. Keep the additive columns and index.

## YouTube OpenAPI

Apply `tk_open_youtube_openapi_mysql.sql` before enabling `TK_YOUTUBE_OPEN_API_ENABLED`. The separate OpenAPI switch is off by default. Reuse the configured Google OAuth client and the existing callback, or register and set `TK_YOUTUBE_OPEN_API_REDIRECT_URI` to the dedicated `/admin-api/tk/open/v1/youtube/auth/callback` URL. Never copy workbench tokens. Do not log token-bearing launch or callback query strings at any proxy. Grant `youtube.auth` and `youtube.publish` only to approved HMAC clients. See the [integration guide](../docs/api/youtube-publishing.md). On rollback leave the additive tables in place and disable the OpenAPI switch.
