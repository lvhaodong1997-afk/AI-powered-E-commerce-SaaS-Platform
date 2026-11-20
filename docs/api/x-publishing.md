# X video publishing OpenAPI

Base path: `/admin-api/tk/open/v1/x`. Use the HTTPS host supplied by the operator. The service is off until enabled and a client is provisioned. See [OpenAPI](tk-platform-openapi.json) and [HMAC signing](tk-openapi-signing-selfcheck.md).

HMAC still authenticates only the partner server, not an end user. Keep `clientSecret` on the partner backend. Sign the full `/admin-api/...` path and exact body or query bytes with the existing `X-TK-*` headers. Because query strings are part of the signature, `endUserId` on GET or DELETE must be included when the request is signed.

There are two provisioning modes. A client is in **pilot mode** only when its grant sets both `owner-user-id` and `x-user-id`. Otherwise it is in **multi-user mode**, and YAML may only set the tenant/company scope.

| Method and relative path | Permission | Result |
| --- | --- | --- |
| `POST /auth/sessions` | `x.auth` | `sessionId`, `status`, `authorizeUrl` |
| `GET /auth/sessions/{sessionId}` | `x.auth` | `connectionId` on `SUCCESS` |
| `GET /connections` | `x.auth` | Connections for the authenticated end user |
| `DELETE /connections/{connectionId}` | `x.auth` | Revokes this client's connection for that end user |
| `POST /publish/tasks` | `x.publish` | `taskId` and current status |
| `GET /publish/tasks/{taskId}` | `x.publish` | Status, `postId` and `postUrl` on success |

## Multi-user partner access

Use one HMAC client for the partner, not one client per end user. On every signed request except the consent pages, send the partner's stable `endUserId`. Put it in the JSON body for POST, and in the `endUserId` query parameter for GET and DELETE, so it is covered by the HMAC canonical request.

1. Create a session with `{ "endUserId": "partner-user-1", "ownerUserId": 123 }`. `ownerUserId` must be an enabled member of the provisioned tenant and company. The first successful call binds that end user to that owner in `tk_open_x_end_user` and cannot be moved to another owner later. Later session requests may omit `ownerUserId` once the binding exists.
2. Open `authorizeUrl` in that end user's browser. The owner of the designated X account must agree on the consent page and finish X OAuth in the same browser. The link expires after 10 minutes. Do not log or forward the link beyond the account owner.
3. Poll the authorization session about every five seconds, always with the same `endUserId`. Persist `connectionId` when the status is `SUCCESS`. A leaked `connectionId` is not sufficient to publish, list, or revoke; the request must also present the bound `endUserId` under the same HMAC client.
4. Submit a completed generation task ID that belongs to the same tenant, company, and bound owner as the connection. This endpoint accepts no media URLs or direct uploads. `text` can be empty and may not exceed 280 UTF-16 code units.

```http
POST /admin-api/tk/open/v1/x/publish/tasks
Content-Type: application/json
Idempotency-Key: order-0001-x
```

```json
{"connectionId":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA","generationTaskId":"123456","text":"Product demo","endUserId":"partner-user-1"}
```

`code: 0` means the operation succeeded, not that a post exists. Poll the `taskId` with the same `endUserId` until `SUCCEEDED`, `FAILED`, `REAUTH_REQUIRED` or `UNKNOWN`. On `UNKNOWN`, check the X timeline and do not submit a replacement automatically. On a lost response, reuse the original key, input and `endUserId`, with a new nonce and signature. A changed payload with the same key returns `409 IDEMPOTENCY_KEY_CONFLICT`. Idempotency is scoped to the client, end user, and provisioned owner.

`GET /connections?endUserId=partner-user-1` returns only that end user's connections. Revocation prevents subsequent work on the connection; requests already in flight to X cannot be recalled. The account owner can also disconnect the account in the X publishing workspace, which revokes associated OpenAPI connections. The same X account cannot be reused by a different end user or HMAC client.

## Single-account pilot

Existing grants that set both `owner-user-id` and `x-user-id` keep the previous behaviour: create a session with an empty body, and do not send `endUserId`. Authorization must complete with that numeric X user, and generation tasks must belong to that owner. This mode is for a single designated account only.

## Deployment and provisioning

Apply `tk_open_x_upgrade_mysql.sql` and the additive `tk_open_x_multiuser_upgrade_mysql.sql` after the channel publishing migration and before booting the new backend, even when X OpenAPI is off. No frontend rebuild is required for API behaviour, but the Open API administration page should be updated so operators can grant `x.auth` and `x.publish` without dropping those permissions on save. A rollback to the previous JAR can leave the additive columns and table in place. Keep existing X OAuth settings and the social token encryption key unchanged. Do not enable every client, and do not invent user bindings in configuration.

Set the following in protected server configuration. In the existing Open API administration, grant only `x.auth,x.publish` for a dedicated client. Do not copy credentials into YAML in source control.

Pilot client:

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

Multi-user partner client (scope only; user bindings are created through the signed API):

```yaml
tk:
  x-open-api:
    enabled: true
    grants:
      - client-id: client_partner
        tenant-id: 7
        company-id: 8
```

The service stays off until `enabled` is true and a grant exists for that client. Without a grant, the signed route denies access even if the client has a wildcard permission. Secret rotation, IP rules, rate limits and request IDs stay with the existing HMAC gateway. Publish status is retrieved by polling; this interface does not add a new webhook pipeline.
