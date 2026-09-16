# Meta Social P0 implementation contract
User approved implementation and clarified video publishing is the PRIMARY outcome: Instagram Reels/video and Facebook Page video are required now, alongside two OAuth flows, account management, async task/detail with retry and existing publish center integration. JPEG/text remain secondary. Scheduling UI remains deferred. No deployment, real external posting or Git commits. Preserve existing dashboard/index.vue and tests/minimax-voice-replay.test.cjs user edits.

Product root: C:/Users/lhd/Documents/TK自动混剪SaaS产品/ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master
Java root: yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk.

## Ownership
Auth agent: service/social/auth/, service/social/platform/, controller/admin/social/TkSocialAuthController.java and TkSocialAccountController.java; dal/dataobject/social/TkSocialAccountDO.java and TkSocialAuthSessionDO.java, matching Mappers, auth/platform tests.
Main: all remaining Social backend, SQL, media, queue.
Frontend agent: src/api/tk/socialPublish/index.ts, src/views/tk/video-publish-center/components/MetaPublishPanel.vue and related new components; minimal host index.vue edits; new tests.

## HTTP contract
Existing request client adds /admin-api; use CommonResult and PageResult(list,total). CamelCase JSON; platforms INSTAGRAM/FACEBOOK_PAGE.
Permissions: tk:social-account:authorize/query/update, tk:social-publish:create/query/retry.
POST /tk/social-auth/instagram/redirect-url or /facebook/redirect-url, {} -> {authorizeUrl,sessionId}.
GET respective /callback public, one-time hashed state determines tenant. Popup can close; frontend polls session, need not trust postMessage.
GET /tk/social-auth/session?sessionId -> {status,message}, PENDING/PROCESSING/PAGES_READY/SUCCESS/FAILED/EXPIRED.
GET /tk/social-account/facebook-pages?sessionId -> [{id,name,tasks}] no tokens.
POST /tk/social-account/facebook-pages/bind {sessionId,pageIds:[string]} -> boolean.
GET /tk/social-account/page?pageNo=1&pageSize=100&platform=optional -> PageResult.
POST /tk/social-account/validate?id; DELETE /tk/social-account/unbind?id; DELETE /tk/social-account/delete?id.
Safe account response fields: id,platform,accountType,externalAccountId,accountName,username,status,tokenExpiresAt,lastValidatedAt,failReason. No token/cipher in DTO.
POST /tk/social-publish/media/upload multipart file, JPEG <=8MB or MP4 <=100MB -> {id,fileName,publicUrl,mediaType,fileSize,status}. Media copied to independent social-media storage. GET /tk/social-publish/capabilities returns enabled,mediaTypes,platforms,maxVideoBytes.
POST /tk/social-publish/create {accountIds:[number],mediaId?:number,generationTaskId?:number,title:string,instagramCaption?:string,facebookMessage?:string,idempotencyKey:string} -> number taskId. mediaId and generationTaskId mutually exclusive. Generated video is snapshotted to dedicated storage before task creation, retaining stable bytes through queue processing. No arbitrary media URLs. No media means Facebook text only. Empty Facebook text rejected when no media.
GET /tk/social-publish/task-page pageNo,pageSize,status optional -> PageResult.
GET /tk/social-publish/detail-page pageNo,pageSize,taskId required -> PageResult.
POST /tk/social-publish/retry?detailId -> boolean; FAILED/REAUTH_REQUIRED only, not UNKNOWN or SUCCESS.
POST /tk/social-publish/status/sync?taskId -> boolean, schedules async work/reconciliation, never blindly resubmit.
Task fields: id,title,status,targetCount,successCount,failedCount,pendingCount,createTime,instagramCaption,facebookMessage,mediaId.
Detail fields: id,publishTaskId,platform,socialAccountId,accountName,status,platformStatus,externalContainerId,externalMediaId,externalPostId,publishUrl,retryCount,errorCode,errorMessage,publishedTime.
States PENDING/PROCESSING/SUCCESS/PARTIAL_SUCCESS/FAILED/REAUTH_REQUIRED/UNKNOWN. UNKNOWN means remote mutation outcome unknown, no automatic resubmit. Aggregate PARTIAL_SUCCESS only after all details terminal.

## Shared Java contracts
All DOs extend TenantBaseDO; Lombok Data/EqualsAndHashCode(callSuper=true), ordinary setter; TableId; table tk_social_*.
TkSocialAccountDO: Long id,companyId; String platform,accountType,externalAccountId,providerUserId,accountName,username,accessTokenCiphertext,tokenType,scopes,status,failReason; LocalDateTime tokenExpiresAt,lastValidatedAt,lastAuthTime.
TkSocialAuthSessionDO: Long id,companyId; String sessionId,platform,stateHash,status,payloadCiphertext,failReason; LocalDateTime expireTime.
Temporary payload encrypted and cleared completion/expiry. sessionId always owner + tenant checked.
Auth provides service.social.auth.TkSocialAccountService:
TkSocialAccountDO requireReadable(Long id) validates current data scope.
String getValidToken(TkSocialAccountDO account) supports tenant-bound background worker, checks active/reauth, platform-correct refresh.
Auth provides service.social.platform.TkSocialPlatformClient Spring bean:
PublishResult advance(String platform,String externalAccountId,String accessToken,String mediaType,String text,String mediaUrl,String containerId,String platformStatus).
IG no container -> create only -> WAITING + containerId. Existing container -> query status; FINISHED permits media_publish; no sleeps.
FB text/photo -> SUCCESS with external IDs.
PublishResult getters String status (WAITING/SUCCESS),containerId,mediaId,postId,publishUrl,platformStatus. Use nested public static class.
TkSocialPlatformException extends RuntimeException getters String code; boolean retryable,reauthRequired,uncertain.
Mutation timeout/5xx = uncertain true, no blind retry. Explicit rate rejection may retry. Sanitize upstream errors.

## Configuration and safety
Spring tk.social.enabled defaults false so before migration no queries on missing new tables. Auth owns properties class under service/social/auth. Separate Instagram appId/secret and Facebook appId/secret + configId; env config example. Same Meta app feasibility not assumed. Configurable graph API version.
OAuth state random/hash/TTL/one-time CAS. Session ownership verified including platform. Page selection only server candidate list and valid tasks. No account ownership hijack on reauth. IG long-lived exchange/refresh, not TikTok refresh_token. Page token unknown expiry does not mean permanent.
No modifications to TikTok cipher/adapter/API/tables. Queue claim lease + fencing; network outside DB transactions; committed task before dispatch; independent details; unknown outcomes manual verification. Separate media namespace so TikTok cleanup cannot delete it. Video staging persists checkpoints, polls final published status rather than treating upload acceptance as publication.

## Verification
Meaningful TDD tests for scope, OAuth replay, Page tasks, secrets, container resume, unknown outcome, idempotency, partial success. Main runs all Maven reactor tests; agents don't run concurrent Maven; tell main when red tests ready. Frontend tests/typecheck independent. Actual OAuth/posting needs configured App/accounts; report unverified without credentials.

## Progress
- [x] Auth/platform: direct IG and FB Page OAuth, safe tokens, staged videos, GET-only reconciliation.
- [x] Schema/media/queue: additive schema, audio normalization in copy, reference-safe snapshot cleanup, durable checkpoint/lease/atomic counters.
- [x] Frontend: composer, generated picker, auth/session/Page selection, status/retry, lifecycle polling.
- [x] Tests/review: backend 120/120, frontend 24/24, typecheck passed; independent re-review closed all five findings.

Verified review fixes: preserve system-generated audio by normalizing publication copy; reconcile known-ID UNKNOWN without mutation; reload retry details under parent lock; safely collect unused snapshots; restart frontend polling after initial read failures. Tests and independent re-review completed. See meta-social-verification.md for evidence and live-environment limits. No production calls or deployment.
