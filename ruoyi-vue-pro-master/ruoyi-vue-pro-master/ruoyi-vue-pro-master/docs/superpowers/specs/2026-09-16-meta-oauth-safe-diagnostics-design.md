# Meta OAuth Safe Diagnostics Design

## Goal

Identify the exact Instagram OAuth failure stage in production without recording authorization codes, access tokens, app secrets, provider response bodies, or exception messages.

## Design

- Preserve `TkSocialPlatformException.code` as the sanitized provider/HTTP diagnostic code.
- Add an optional authorization `stage` to `TkSocialPlatformException` and wrap each Instagram authorization boundary with one of four constants: short-token exchange, long-token exchange, profile validation, or permission validation.
- In `TkSocialAuthService`, emit one structured warning containing only platform, session id, stage, diagnostic code, and exception class.
- Store a stage-specific, user-actionable failure reason in the existing authorization session row. No schema or API shape changes are required.
- Parse Meta's top-level Instagram OAuth error code as well as the Graph API nested error shape so diagnostics retain safe Meta codes.

## Security Constraints

- Never log callback `code`, `state`, access tokens, app secrets, exception messages, request parameters, or response bodies.
- Allow only fixed stage constants and alphanumeric diagnostic codes in logs.
- Preserve the existing generic failure reason for unexpected local exceptions.

## Verification

- Regression tests prove long-token failures carry a stage without changing their provider code.
- Security tests capture the authorization-service log and prove callback codes, tokens, secrets, and provider messages are absent.
- Transport tests prove top-level Instagram OAuth error codes are classified safely.

