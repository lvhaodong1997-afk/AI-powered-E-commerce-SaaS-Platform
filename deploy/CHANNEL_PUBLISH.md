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
