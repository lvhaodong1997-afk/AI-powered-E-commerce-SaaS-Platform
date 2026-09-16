# TikTok Open Account Profile And Batch Metrics Implementation Plan

**Goal:** Extend the TikTok Open API with account profile statistics and multi-task video metrics while preserving existing routes.

**Architecture:** Reuse OAuth connections, token refresh, TikTok user info, and video query layers. Persist account profile fields on `tk_open_tiktok_connection`; resolve task IDs to public post IDs, group by connection, query TikTok in batches of 20, and reuse existing metric persistence.

**Tech Stack:** Spring Boot, MyBatis Plus, Java, JUnit 5, MySQL, TikTok Open API v2, python-docx, LibreOffice.

## Global Constraints

- Use `connectionId` or `externalAccountId` as account keys; account names are display fields.
- Never return tokens or secrets.
- Keep existing OAuth, connection, and single-task metrics routes compatible.
- Use `user.info.basic`, `user.info.profile`, `user.info.stats`, and `video.list`.
- Keep each TikTok video query request at 20 IDs or fewer.

## Tasks

### 1. Contract tests

Modify `TkOpenTiktokControllerMappingTest.java`, `TkOpenTiktokPublishServiceTest.java`, and add `TkOpenTiktokAccountProfileTest.java`. Add failing tests for the profile refresh route, batch metrics route, profile field mapping, same-connection batching, and per-item metric results. Run the focused Maven tests and confirm they fail because the new contract is absent.

### 2. User info and connection persistence

Modify `TkTiktokApiClient.java`, `TkOpenPublishPlatformAdapter.java`, `TkOpenTiktokPlatformAdapter.java`, and `TkOpenTiktokConnectionDO.java`. Parse and pass profile fields plus follower, following, likes, and video counts. Add an idempotent SQL migration for `bio_description`, `profile_deep_link`, `is_verified`, `follower_count`, `following_count`, `likes_count`, `video_count`, and `stats_updated_at`.

### 3. Authorization profile response and refresh

Modify `TkOpenTiktokAuthVO.java`, `TkOpenTiktokAuthService.java`, and `TkOpenTiktokAuthController.java`. Save user info during authorization, expose the fields in session and connection responses, and add `POST /admin-api/tk/open/v1/tiktok/connections/{connectionId}/profile/refresh` using existing token refresh and client scoping.

### 4. Batch task metrics

Modify `TkOpenTiktokPublishVO.java`, `TkOpenTiktokPublishController.java`, `TkOpenTiktokPublishService.java`, `TkOpenPublishPlatformAdapter.java`, `TkOpenTiktokPlatformAdapter.java`, and the task mapper if a bulk task lookup is needed. Add `POST /admin-api/tk/open/v1/tiktok/publish/tasks/metrics/batch` with 1-50 task IDs. Resolve public post IDs, group by connection, chunk into 20, refresh and retry invalid tokens once, persist existing metric columns, and return one result per requested task.

### 5. Documentation

Update `C:\Users\lhd\Desktop\TK开放API第三方接口接入文档.docx` with the authorization profile fields, profile refresh API, batch metrics API, status values, limits, and the complete integration flow. Render all pages with the bundled DOCX renderer and run the accessibility audit.

### 6. Regression verification

Run the focused TK tests and then `mvn -pl yudao-module-tk -am test`. Run `git diff --check`. Inspect the DOCX render and verify response DTOs and examples contain no tokens or secrets.
