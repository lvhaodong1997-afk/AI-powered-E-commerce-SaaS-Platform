# DeepSeek Manual Translation Implementation Plan

**Goal:** Dashboard manual e-commerce and lead-generation copy uses the original by default, or a valid DeepSeek translation when enabled; audio/video and replay use the same immutable script snapshot.

**Architecture:** Add a protected synchronous translation endpoint and a dedicated method in the existing DeepSeek client. Keep the existing generation pipeline: promptText contains the selected final copy, while generation_route_config stores original copy and translation metadata.

**Tech stack:** Vue 3, TypeScript, Spring Boot, existing DeepSeek HTTP client, JUnit 5, Node tests.

## Constraints

- Preserve existing AI, manual and pure-material generation behavior, existing APIs, route and clip settings.
- Translation is disabled by default. No automatic original-text fallback on translation failure.
- Original and final copy each have a 3000-character limit; reject empty, truncated and oversized translations.
- Target language follows the existing selector. Changing original text, language or manual flow invalidates pending/results.
- Use only DeepSeek, dedicated translation instructions, disabled thinking. Keep existing rewrite and verification methods unchanged.
- New create parameters: manualTranslationEnabled?: boolean, manualOriginalText?: string.
- New endpoint POST /tk/generation/translate-script: {sourceText,targetLanguage} -> {sourceText,targetLanguage,translatedText}; permission tk:generation:create.
- Route metadata keys: manualTranslationEnabled, manualOriginalText, manualTranslationTargetLanguage. Merge without dropping existing config keys.
- Production changes authorized; stage, compare SHA-256, back up, atomic replace, restart, verify PID/ports/HTTP/logs and authenticated translation.

## Task 1: DeepSeek translation endpoint

- [ ] Add failing HTTP-client tests for dedicated translation messages, disabled thinking, stop completion and rejected length completion. Run focused Maven tests.
- [ ] Add TkDeepSeekClient.translateText(String sourceText, String targetLanguage), strict language validation in a new translation service, create/request response VOs and protected endpoint.
- [ ] Translation-only optional keys translation-model, translation-timeout-seconds, translation-max-output-tokens, translation-retry-count; defaults use configured model, 60 seconds, 8192 output tokens, zero retries. Do not change rewrite/verification budgets.
- [ ] Reject unsupported language rather than falling back to Chinese. Never log raw copy or credentials. Test validation and endpoint permission.

## Task 2: Dashboard translation UI and state

- [ ] Add failing behavior tests for original/translated selection, stale requests, changed language/text, disabled state and replay.
- [ ] Add shared translation controls for both manual inputs: switch, target label, readonly translated text, retranslate, status/errors and effective script label; bilingual copy.
- [ ] Update resolvePromptTextForGeneration, submission snapshot, manual script options, audio export and duration estimates to use final copy. Invalid enabled translation blocks audio/video submission.
- [ ] Add API method and optional create parameters above. Restore metadata and final promptText in replay, disabling default for old tasks.
- [ ] Run focused Node tests, pnpm ts:check and tk:i18n:check.

## Task 3: Task metadata persistence

- [ ] Add failing route-config and task-service tests for translated manual tasks, mismatched blank original, unchanged old requests, rejected AI-mode translation and preserved clip config.
- [ ] Validate metadata only for manual flows, store original + enabled + normalized language by merging JSON. Disabled translation uses original text; enabled translation uses submitted final promptText.
- [ ] Keep pipeline direct script use and assert it never calls rewrite/translation again. Run route, task, script and controller tests.

## Task 4: Review, build, integrate and deploy

- [ ] Review diffs for snapshot consistency, races, permissions, limits and existing API regressions; fix findings and rerun affected checks.
- [ ] Build backend using bundled Maven -pl yudao-server -am -DskipTests package; build frontend with bundled pnpm build:prod.
- [ ] Commit only this change, integrate into original main by fast-forward if clean, push origin/main and verify remote SHA.
- [ ] Verify current production paths/config and active workloads. Stage artifacts, verify hashes, back up JAR/frontend, atomic replace and restart tk-yudao-prod.
- [ ] Confirm service, PID, restarts, ports, HTTP and startup logs; exercise authenticated DeepSeek translation and static frontend assets; clean staging.
