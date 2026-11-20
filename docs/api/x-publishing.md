# X video publishing OpenAPI

Base path: `/admin-api/tk/open/v1/x`. Use the HTTPS host supplied by the operator. The service is off until enabled and a client is provisioned. See [OpenAPI](tk-platform-openapi.json) and [HMAC signing](tk-openapi-signing-selfcheck.md).

| Method and relative path | Permission | Result |
| --- | --- | --- |
| `POST /auth/sessions` | `x.auth` | `sessionId`, `status`, `authorizeUrl` |
| `GET /auth/sessions/{sessionId}` | `x.auth` | `connectionId` on `SUCCESS` |
| `POST /publish/tasks` | `x.publish` | `taskId` and current status |
| `GET /publish/tasks/{taskId}` | `x.publish` | Status, `postId` and `postUrl` on success |
| `DELETE /connections/{connectionId}` | `x.auth` | Revokes this client's connection |

1. Create a session with an empty body. Open `authorizeUrl` in the designated X account owner's browser. The owner must agree on the consent page and finish X OAuth in the same browser. The link expires after 10 minutes. Do not log or forward the link beyond the account owner.
2. Poll the authorization session about every five seconds. Persist `connectionId` when the status is `SUCCESS`. For `EXPIRED` or `FAILED`, create a new session and offer the new authorization link.
3. Submit the completed generation task ID as a decimal string. The task must belong to the tenant, company and generation owner provisioned for this client. This endpoint accepts no media URLs or direct uploads. `text` can be empty and may not exceed 280 UTF-16 code units.

```http
POST /admin-api/tk/open/v1/x/publish/tasks
Content-Type: application/json
Idempotency-Key: order-0001-x
```

```json
{"connectionId":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA","generationTaskId":"123456","text":"Product demo"}
```

Sign the full `/admin-api/...` path and exact body bytes with a server-side `X-TK-*` signature. `code: 0` means the operation succeeded, not that a post exists. Poll the `taskId` until `SUCCEEDED`, `FAILED`, `REAUTH_REQUIRED` or `UNKNOWN`. On `UNKNOWN`, check the X timeline and do not submit a replacement automatically. On a lost response, reuse the original key and input, with a new nonce and signature. A changed payload with the same key returns `409 IDEMPOTENCY_KEY_CONFLICT`. Idempotency persists with the task in the provisioned tenant and owner scope.

Revocation prevents subsequent work on the connection; requests already in flight to X cannot be recalled. The account owner can also disconnect the account in the X publishing workspace, which revokes associated OpenAPI connections.

## Deployment and provisioning

Apply `tk_open_x_upgrade_mysql.sql` after the channel publishing migration and before booting the new backend, even when X OpenAPI is off. No frontend rebuild is required. A rollback to the previous JAR can leave the additive columns in place. Keep existing X OAuth settings and the social token encryption key unchanged. Enable the route only after registering a pilot client and verifying a real video with an explicitly authorized account.

Set the following in protected server configuration; grant only `x.auth,x.publish` for a dedicated client in the existing OpenAPI client administration. Do not copy credentials into YAML in source control.

```yaml
tk:
  x-open-api:
    enabled: true
    grants:
      - client-id: client_pilot
        tenant-id: 7
        company-id: 8
        owner-user-id: 9
        x-user-id: '123456789'
```

Each client has exactly one grant. The X ID is the numeric user ID, not the handle. Keep the OAuth callback and consent URLs on the same public HTTPS host. Do not log the request URI for consent routes or the query string of the OAuth callback. Secret rotation, IP rules, rate limits and request IDs are managed by the existing HMAC gateway. Without a grant, the signed route denies access even if the client has a wildcard permission.
