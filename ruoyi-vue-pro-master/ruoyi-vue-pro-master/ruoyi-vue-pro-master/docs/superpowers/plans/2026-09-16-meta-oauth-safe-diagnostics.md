# Meta OAuth Safe Diagnostics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add secret-safe stage diagnostics to the existing Instagram OAuth callback flow.

**Architecture:** Existing platform exceptions retain their safe error code and gain an optional fixed stage. The authorization service records a mapped user-facing reason and emits a structured warning with allowlisted metadata only.

**Tech Stack:** Java 8, Spring Boot, SLF4J/Logback, JUnit 5, Mockito

## Global Constraints

- Do not log OAuth codes, state values, tokens, app secrets, response bodies, request parameters, or exception messages.
- Do not change database schema or public API response shape.
- Use focused tests in `yudao-module-tk` before packaging.

---

### Task 1: Add OAuth stage diagnostics

**Files:**
- Modify: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/social/platform/TkSocialPlatformException.java`
- Modify: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/social/platform/TkSocialPlatformClient.java`
- Modify: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/social/platform/TkSocialHttpTransport.java`
- Modify: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/social/auth/TkSocialAuthService.java`
- Test: `yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/service/social/platform/TkSocialPlatformClientTest.java`
- Test: `yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/service/social/platform/TkSocialHttpTransportTest.java`
- Test: `yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/service/social/auth/TkSocialAuthSecurityTest.java`

**Interfaces:**
- Consumes: existing `TkSocialPlatformException` and Instagram authorization flow.
- Produces: `TkSocialPlatformException.withStage(String)`, structured safe warning fields, and stage-specific safe session failure reasons.

- [x] **Step 1: Write failing tests**

Add tests that require a long-token failure stage, top-level Meta error-code parsing, and secret-free callback logging.

- [x] **Step 2: Verify the tests fail**

Run the focused Maven tests and confirm failures are caused by the missing stage API and missing diagnostics.

- [x] **Step 3: Implement the minimal production changes**

Add immutable staged exception copies, wrap the four Instagram authorization boundaries, parse both supported Meta error shapes, and log only allowlisted fields.

- [x] **Step 4: Verify focused and module tests**

Run the three focused test classes, then package `yudao-module-tk` and dependencies with tests enabled.

Verification result: the three focused classes passed 36/36. The complete TK module run passed 658/659; the sole failure is the pre-existing `TkGenerationSchemaContractTest.referenceAnalysisSchemaContainsTaskTitleColumn` assertion in unchanged schema-test/SQL files. A clean `yudao-server` reactor package completed successfully with tests skipped.

- [x] **Step 5: Review and deploy**

Inspect the focused diff for secrets, commit and push `main`, build a production JAR, perform backup and hash checks, switch the release atomically, restart `tk-yudao-prod`, and verify PID, ports, HTTP, and recent logs.

Deployment result: commit `e6e63ff` was pushed to `main`; release `release-20260916-meta-oauth-e6e63ff` was validated and switched atomically with rollback link `meta-oauth-e6e63ff-20260916-110919`. The deployed JAR SHA-256 matched the local artifact, `tk-yudao-prod` remained active with `NRestarts=0`, ports 48080/18080 were listening, and backend, Nginx, and both public entry points returned HTTP 200.
