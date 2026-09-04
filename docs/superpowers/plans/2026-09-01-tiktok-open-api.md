# TikTok Open API Implementation Plan

**Goal:** 在不改变现有管理后台发布中心行为的前提下，为多个外部调用方提供以 `clientId` 隔离的 TikTok 授权、上传、发布、查询和回调 API。

**Base URL:** `https://tkassetplant.fnn.net.cn/admin-api/tk/open/v1/tiktok`

**Architecture:** 在 `yudao-module-tk` 内新增独立开放 API 适配层。开放层完成 HMAC 认证、调用方上下文、公开 ID 映射、幂等、限流和回调；TikTok OAuth、Token、上传和发布执行继续复用现有服务。开放 API 不接收租户字段，也不使用后台登录态作为权限边界。

## P0: Core API

### Task 1: Authentication and response contract

- Add tests for canonical request signing, timestamp drift, nonce replay, client status, IP/rate checks and request context cleanup.
- Add open API response/error types, client context, HMAC verifier, request filter and exception handler.
- Exclude only `/tk/open/v1/tiktok/**` from the existing login requirement; TikTok browser callback is verified by OAuth state.

### Task 2: Persistence and public IDs

- Add SQL for clients, resource bindings, idempotency records, callback events, request logs/usage and upload metadata.
- Add DO/Mapper classes and a binding service enforcing `client_id + resource_type + public_id` uniqueness.
- Store client secrets encrypted or hashed where verification permits; never return a stored secret from query APIs.

### Task 3: OAuth and connections

- Add session creation/query, OAuth callback, connection list and disconnect endpoints.
- Bind auth session and account IDs to opaque IDs owned by the current client.
- Reuse existing TikTok authorization/token services without exposing tenant or internal IDs.

### Task 4: Media upload

- Add OSS upload session create/query/complete/cancel endpoints.
- Validate extension, content type, configured size limit and optional SHA-256.
- Bind upload/media IDs to the current client and reject cross-client access as not found.

### Task 5: Publish workflow

- Add publish create/query/detail/retry endpoints and request validation.
- Implement `clientId + Idempotency-Key` behavior using a canonical request hash.
- Persist the task first, return immediately, and dispatch details asynchronously through the existing publish service.

### Task 6: Callback delivery

- Persist authorization and publish events before delivery.
- Sign callbacks with the client's callback secret and deliver with bounded retry/backoff.
- Add a scheduled retry job and status updates without blocking the publish worker.

## P1: Integration Operations

### Task 7: QR, local chunks and client management

- Expose QR authorization through the same auth-session contract.
- Add signed local chunk upload endpoints as an alternative when OSS direct upload is unavailable.
- Add authenticated admin endpoints/page for client creation, enable/disable, secret rotation, callbacks, IP allowlist and limits.
- Add SDK examples and a checked-in OpenAPI JSON document matching the production-domain contract.

## P2: Extensibility and Governance

### Task 8: Platform adapter, quota and callback operations

- Introduce a publishing-platform adapter contract with TikTok as the first implementation while preserving existing TikTok services.
- Record per-client request/operation usage and enforce configured daily quotas.
- Add callback event list/detail/manual replay admin operations.
- Define a TK-local gateway policy interface so authentication, quota and audit can later move to an independent gateway without changing controllers.

## Verification

1. Run each new unit test class red before implementation and green after implementation.
2. Run focused existing TikTok authorization/upload/publish tests after each integration step.
3. Run `mvn.cmd -pl yudao-module-tk -am test` from the product source root.
4. Run the narrow frontend type/check script when the P1 admin page is added.
5. Verify no open endpoint accepts `tenantId`, no response exposes internal IDs or secrets, and cross-client resource tests return not found.

## Change Safety

- Preserve all existing management controllers and service contracts.
- Add new classes and additive SQL; do not rewrite existing publish tables unless a direct compatibility field is required.
- Do not deploy, edit production configuration, or create a release archive in this task.
- Do not commit user changes unless explicitly requested.
