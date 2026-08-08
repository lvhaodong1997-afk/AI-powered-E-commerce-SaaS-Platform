# DashScope Video Analysis Routing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an explicitly selected `DASHSCOPE_VIDEO` route for reference-video analysis while preserving Gemini as the unchanged default.

**Architecture:** Introduce a reference-analysis client boundary and provider router inside the TK reference module. Persist provider/model on analysis records and include provider in cache lookups. Reuse the existing queue, credit, prompt, JSON parsing, persistence, and generation flows.

**Tech Stack:** Java 8, Spring Boot 2.7, Hutool HTTP, MyBatis Plus, Vue 3, TypeScript, Element Plus, MySQL 8

## Global Constraints

- `analysisProvider` defaults to `GEMINI` when omitted.
- `DASHSCOPE_VIDEO` is used only after explicit frontend selection.
- Never fall back automatically between providers.
- Do not change existing Gemini request behavior outside reference analysis.
- Run only TK reference-analysis tests and frontend checks related to changed files.

---

### Task 1: Persist and Validate the Analysis Provider

**Files:**
- Create: `yudao-module-tk/src/main/resources/sql/tk_reference_analysis_provider_upgrade_mysql.sql`
- Modify: `yudao-module-tk/src/main/resources/sql/tk_mysql.sql`
- Create: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/reference/TkReferenceAnalysisProvider.java`
- Modify: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/controller/admin/reference/vo/TkReferenceAnalyzeReqVO.java`
- Modify: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/controller/admin/reference/vo/TkReferenceAnalysisRespVO.java`
- Modify: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/dal/dataobject/TkReferenceAnalysisDO.java`
- Test: `yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/service/reference/TkReferenceAnalysisProviderTest.java`

**Interfaces:**
- Produces: `TkReferenceAnalysisProvider.normalize(String): String`
- Produces: request/response/DO fields `analysisProvider` and response/DO field `analysisModel`

- [ ] **Step 1: Write the failing provider normalization test**

```java
assertEquals("GEMINI", TkReferenceAnalysisProvider.normalize(null));
assertEquals("GEMINI", TkReferenceAnalysisProvider.normalize("gemini"));
assertEquals("DASHSCOPE_VIDEO", TkReferenceAnalysisProvider.normalize("dashscope_video"));
assertThrows(ServiceException.class, () -> TkReferenceAnalysisProvider.normalize("unknown"));
```

- [ ] **Step 2: Run the focused test and confirm it fails because the provider class is absent**

```powershell
..\..\..\.runtime\apache-maven-3.9.10\bin\mvn.cmd -pl yudao-module-tk -am -Dtest=TkReferenceAnalysisProviderTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] **Step 3: Implement the provider constants and normalization**

```java
public final class TkReferenceAnalysisProvider {
    public static final String GEMINI = "GEMINI";
    public static final String DASHSCOPE_VIDEO = "DASHSCOPE_VIDEO";

    public static String normalize(String provider) {
        String value = StrUtil.blankToDefault(provider, GEMINI).trim().toUpperCase();
        if (!GEMINI.equals(value) && !DASHSCOPE_VIDEO.equals(value)) {
            throw exception(TK_REFERENCE_ANALYSIS_PROVIDER_INVALID);
        }
        return value;
    }
}
```

- [ ] **Step 4: Add fields and an idempotent schema migration**

Add `analysis_provider varchar(32) NOT NULL DEFAULT 'GEMINI'` and `analysis_model varchar(64) NULL` to the analysis table. Use `information_schema.columns`, prepared statements, and `UPDATE ... SET analysis_provider='GEMINI'` for legacy rows, matching existing upgrade scripts.

- [ ] **Step 5: Re-run the focused provider test**

Expected: `TkReferenceAnalysisProviderTest` passes.

---

### Task 2: Add the Reference Analysis Client Router

**Files:**
- Create: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/reference/ai/TkReferenceAiAnalysisContext.java`
- Create: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/reference/ai/TkReferenceAiAnalysisResult.java`
- Create: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/reference/ai/TkReferenceAiAnalysisClient.java`
- Create: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/reference/ai/TkReferenceAiAnalysisRouter.java`
- Create: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/reference/ai/TkGeminiReferenceAnalysisClient.java`
- Test: `yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/service/reference/ai/TkReferenceAiAnalysisRouterTest.java`

**Interfaces:**
- `TkReferenceAiAnalysisClient.provider(): String`
- `TkReferenceAiAnalysisClient.analyze(TkReferenceAiAnalysisContext): TkReferenceAiAnalysisResult`
- Result contains `provider`, `model`, and raw `content`

- [ ] **Step 1: Write failing router tests**

Verify `GEMINI` selects only the Gemini client, `DASHSCOPE_VIDEO` selects only the DashScope client, and an unsupported provider is rejected.

- [ ] **Step 2: Run `TkReferenceAiAnalysisRouterTest` and confirm RED**

Use the same focused Maven command with `-Dtest=TkReferenceAiAnalysisRouterTest`.

- [ ] **Step 3: Implement immutable context/result types and the router**

The context carries the built prompt, resolved video URL, and existing `List<TkAiImageInput>`. The router builds a provider-to-client map during construction and rejects duplicate or missing providers.

- [ ] **Step 4: Wrap existing Gemini behavior without altering it**

`TkGeminiReferenceAnalysisClient` must call the existing:

```java
geminiClient.generateText(context.getPrompt(), context.getImages())
```

and return provider `GEMINI`; do not modify `TkGeminiClient`.

- [ ] **Step 5: Re-run router tests**

Expected: all router tests pass.

---

### Task 3: Implement the DashScope Video Client

**Files:**
- Create: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/reference/ai/TkDashScopeVideoAnalysisClient.java`
- Modify: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/enums/ErrorCodeConstants.java`
- Modify: `yudao-module-tk/src/main/resources/sql/tk_api_key_config_upgrade_mysql.sql`
- Modify: `yudao-module-tk/src/main/resources/sql/tk_mysql.sql`
- Test: `yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/service/reference/ai/TkDashScopeVideoAnalysisClientTest.java`

**Interfaces:**
- Consumes: `TkApiKeyConfigService` keys under provider `DASHSCOPE`
- Calls: `POST https://{workspace-id}.cn-beijing.maas.aliyuncs.com/compatible-mode/v1/chat/completions`
- Produces: provider `DASHSCOPE_VIDEO`, configured model, raw assistant content

- [ ] **Step 1: Write failing request-construction tests**

Assert the serialized body contains:

```json
{
  "model": "qwen3.7-plus",
  "messages": [{
    "role": "user",
    "content": [
      {"type":"video_url","video_url":{"url":"https://cdn.example/video.mp4"},"fps":2.0},
      {"type":"text","text":"analysis prompt"}
    ]
  }],
  "enable_thinking": false,
  "temperature": 0.2
}
```

Also assert blank API key, blank WorkspaceId, blank video URL, non-2xx response, empty assistant content, and invalid `fps` fail with TK-local errors.

- [ ] **Step 2: Run the client test and confirm RED**

Run only `TkDashScopeVideoAnalysisClientTest`.

- [ ] **Step 3: Implement configuration resolution**

Read `api-key`, `workspace-id`, `video-model`, `video-fps`, `video-timeout-seconds`, `video-enable-thinking`, and `video-temperature` via `TkApiKeyConfigService`. Use `DASHSCOPE_API_KEY` only as the existing configuration fallback. Validate `fps` in `[0.1, 10]`.

- [ ] **Step 4: Implement the HTTP request and response parsing**

Use Hutool HTTP as existing DashScope clients do. Set Bearer authorization, JSON content type, configured timeout, and extract `choices[0].message.content`. Include HTTP status, DashScope request ID, and bounded response detail in errors; never log the API key.

- [ ] **Step 5: Add configuration seed rows**

Add only the new DashScope video keys to both SQL seed/upgrade files, preserving existing TTS and voice-clone keys.

- [ ] **Step 6: Re-run the client test**

Expected: request, configuration, response, and failure tests pass.

---

### Task 4: Route Existing Reference Analysis and Isolate Cache Entries

**Files:**
- Modify: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/reference/TkReferenceAnalysisService.java`
- Modify: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/reference/TkReferenceAnalysisServiceImpl.java`
- Modify: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/controller/admin/reference/TkReferenceAnalysisController.java`
- Modify: `yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/dal/mysql/TkReferenceAnalysisMapper.java`
- Test: `yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/service/reference/TkReferenceAnalysisServiceImplTest.java`

**Interfaces:**
- `getLatest(..., materialPurpose, analysisProvider)` includes provider
- Cache selectors include normalized provider

- [ ] **Step 1: Add failing service tests**

Cover default Gemini routing, explicit DashScope routing, provider/model persistence, and same-video/different-provider cache isolation. Verify a DashScope failure follows the existing task-failure and credit-refund path without invoking Gemini.

- [ ] **Step 2: Run only `TkReferenceAnalysisServiceImplTest` and confirm RED**

- [ ] **Step 3: Normalize and persist provider when queuing analysis**

Normalize `reqVO.analysisProvider` before cache lookup. Store it on the new analysis row and pass it through queued execution.

- [ ] **Step 4: Route the actual analysis call**

Keep `TkReferenceVideoContentService.analyze(...)` and prompt construction unchanged. Replace the direct `geminiClient.generateText(prompt, images)` call with router invocation. Supply both resolved video URL and existing images; each provider uses only its required media input.

- [ ] **Step 5: Add provider to mapper cache predicates and latest-result endpoint**

Update `selectLatestReusable` and `selectLatestSuccess` to compare normalized provider. Update the controller/service `getLatest` contract with an optional `analysisProvider` query parameter defaulting to Gemini.

- [ ] **Step 6: Re-run the focused service test**

Expected: existing Gemini tests plus new routing/cache tests pass.

---

### Task 5: Add the Explicit Frontend Selector

**Files:**
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/api/tk/reference/index.ts`
- Modify: `yudao-ui/yudao-ui-admin-vue3/src/views/tk/dashboard/index.vue`

**Interfaces:**
- Type: `AnalysisProvider = 'GEMINI' | 'DASHSCOPE_VIDEO'`
- Analyze and latest requests carry `analysisProvider`

- [ ] **Step 1: Add the provider type and request fields**

Add `analysisProvider?: AnalysisProvider` to analyze input, response, and latest query parameter types.

- [ ] **Step 2: Add a compact analysis-engine selector**

Use the existing form layout and an Element Plus segmented/radio control with two options: “现有分析” (`GEMINI`) and “百炼视频理解” (`DASHSCOPE_VIDEO`). Default `createForm.analysisProvider` to `GEMINI`.

- [ ] **Step 3: Include provider in analyze, latest, and result-match logic**

Send the selected provider on analysis creation and latest-result queries. Require provider equality when deciding whether an existing analysis matches the current form, so switching engines does not display stale results.

- [ ] **Step 4: Run focused frontend verification**

```powershell
rg -n "analysisProvider|DASHSCOPE_VIDEO" src/api/tk/reference/index.ts src/views/tk/dashboard/index.vue
..\..\..\..\..\.runtime\npm-global\node_modules\.bin\pnpm.cmd ts:check
```

Expected: request paths include the provider and TypeScript exits `0`.

---

### Task 6: Run Focused Integration Verification

**Files:**
- Verify only files changed by Tasks 1-5

- [ ] **Step 1: Run the focused backend tests together**

```powershell
..\..\..\.runtime\apache-maven-3.9.10\bin\mvn.cmd -pl yudao-module-tk -am -Dtest=TkReferenceAnalysisProviderTest,TkReferenceAiAnalysisRouterTest,TkDashScopeVideoAnalysisClientTest,TkReferenceAnalysisServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: all specified tests pass.

- [ ] **Step 2: Re-run frontend TypeScript verification**

Run `pnpm.cmd ts:check` from the frontend root. Expected: exit code `0`.

- [ ] **Step 3: Verify scope boundaries**

Confirm focused diffs do not modify `TkGeminiClient`, generation pipeline behavior, credits, Nginx, or unrelated frontend pages. Confirm no automatic provider fallback exists.
