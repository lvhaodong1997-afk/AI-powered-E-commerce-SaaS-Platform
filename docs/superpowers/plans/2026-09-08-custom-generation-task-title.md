# Custom Generation Task Title Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow users to name a single generated video task while preserving the existing default title and all batch-generation behavior.

**Architecture:** Reuse the existing `tk_generation_task.title` column. Add an optional `title` field to the single-task creation request, normalize and validate it in `TkGenerationTaskServiceImpl`, and submit it from the dashboard form. The batch request, batch loop, and batch UI behavior are out of scope.

**Tech Stack:** Spring Boot, Java, MyBatis-Plus, JUnit 5, Mockito, Vue 3, TypeScript, Element Plus.

## Global Constraints

- Only single-video generation is in scope.
- Do not modify `/tk/generation/create-batch` or batch naming behavior.
- Preserve the fallback title `{libraryName} · 智能混剪任务` when no custom title is supplied.
- Do not change output filenames, TikTok publish titles, rendering, or database schema.
- Preserve unrelated uncommitted TikTok publishing changes in the workspace.

---

### Task 1: Add failing backend coverage

**Files:**
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/test/java/cn/iocoder/yudao/module/tk/service/generation/TkGenerationTaskServiceImplTest.java`

**Interfaces:**
- Consumes: existing single-task test setup and `TkGenerationTaskCreateReqVO`.
- Produces: regression coverage proving a custom title is persisted and a blank title keeps the existing fallback.

- [ ] **Step 1: Write the failing tests**

Add tests to the existing service test class using the same mocked dependencies and setup already used by `createGenerationTaskInheritsBusinessTraceIdFromReferenceAnalysis`:

```java
@Test
void createGenerationTaskStoresTrimmedCustomTitle() {
    // Arrange the existing single-task fixture with library name "Demo".
    TkGenerationTaskCreateReqVO reqVO = buildSingleTaskRequest();
    reqVO.setTitle("  夏季防晒视频  ");

    // Act through service.createGenerationTask(reqVO).

    // Assert the captured inserted task title is "夏季防晒视频".
}

@Test
void createGenerationTaskUsesDefaultTitleWhenCustomTitleIsBlank() {
    // Arrange the existing single-task fixture with library name "Demo".
    TkGenerationTaskCreateReqVO reqVO = buildSingleTaskRequest();
    reqVO.setTitle("   ");

    // Act through service.createGenerationTask(reqVO).

    // Assert the captured inserted task title is "Demo · 智能混剪任务".
}
```

Use the existing fixture pattern in this test class; do not alter batch tests.

- [ ] **Step 2: Run the focused test and verify RED**

Run from `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master`:

```powershell
..\\..\\..\\.runtime\\apache-maven-3.9.10\\bin\\mvn.cmd -pl yudao-module-tk -Dtest=TkGenerationTaskServiceImplTest test
```

Expected result: compilation or assertion failure because `TkGenerationTaskCreateReqVO` does not yet expose `title` and the service does not persist it.

### Task 2: Implement single-task title handling

**Files:**
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/controller/admin/generation/vo/TkGenerationTaskCreateReqVO.java`
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-ui/yudao-ui-admin-vue3/src/api/tk/generation/index.ts`
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/service/generation/TkGenerationTaskServiceImpl.java`

**Interfaces:**
- Consumes: optional request property `title`.
- Produces: trimmed, validated `TkGenerationTaskDO.title` for single-task creation; unchanged batch behavior because batch code is not edited.

- [ ] **Step 1: Add the request field**

Add the optional field to `TkGenerationTaskCreateReqVO`:

```java
@Schema(description = "用户自定义生成任务名称", example = "夏季防晒视频")
@Size(max = 128, message = "任务名称不能超过128个字符")
private String title;
```

Add the matching optional property to `TkGenerationTaskVO`:

```ts
title?: string
```

- [ ] **Step 2: Add service normalization**

Add a private resolver in `TkGenerationTaskServiceImpl`:

```java
private String resolveTaskTitle(String requestedTitle, String libraryName) {
    String title = StrUtil.trimToEmpty(requestedTitle);
    if (title.length() > 128) {
        throw new IllegalArgumentException("任务名称不能超过128个字符");
    }
    return StrUtil.isBlank(title) ? StrUtil.format("{} · 智能混剪任务", libraryName) : title;
}
```

Use the resolver in the existing `TkGenerationTaskDO.builder()` call, replacing only the current fixed `.title(...)` expression. Do not change `createGenerationTasks`, `copyCreateReqVO`, or any batch-specific code.

- [ ] **Step 3: Run the focused test and verify GREEN**

Run the same Maven command from Task 1. Expected result: `TkGenerationTaskServiceImplTest` passes.

### Task 3: Add the single-video dashboard input

**Files:**
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-ui/yudao-ui-admin-vue3/src/views/tk/dashboard/index.vue`

**Interfaces:**
- Consumes: `createForm.title`.
- Produces: `title` in the existing payload used by `/tk/generation/create` and `/tk/generation/create-with-opening`.

- [ ] **Step 1: Add the form state and input**

Add `title: string` to the existing `createForm` type and initialize it to an empty string. Add one `el-form-item` labeled `任务名称` beside the existing single-video generation settings:

```vue
<el-form-item label="任务名称">
  <el-input
    v-model="createForm.title"
    maxlength="128"
    show-word-limit
    clearable
    placeholder="不填写则使用系统默认名称"
  />
</el-form-item>
```

Do not add batch naming controls or template preview logic.

- [ ] **Step 2: Submit the value in the existing single-task payload**

Add the trimmed value to `createGenerationPayload`:

```ts
title: createForm.title.trim() || undefined,
```

The existing single-generation and opening-video generation paths will reuse this payload. Leave the batch branch untouched.

- [ ] **Step 3: Run focused frontend verification**

Run from `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-ui/yudao-ui-admin-vue3`:

```powershell
..\\..\\..\\..\\..\\.runtime\\npm-global\\node_modules\\.bin\\pnpm.cmd ts:check
```

Expected result: TypeScript check passes.

### Task 4: Final regression verification

**Files:**
- No additional production files.

- [ ] **Step 1: Run backend focused tests**

```powershell
..\\..\\..\\.runtime\\apache-maven-3.9.10\\bin\\mvn.cmd -pl yudao-module-tk -Dtest=TkGenerationTaskServiceImplTest,TkGenerationTaskControllerTest test
```

- [ ] **Step 2: Inspect the focused diff**

```powershell
git diff -- ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-module-tk ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-ui/yudao-ui-admin-vue3/src/api/tk/generation/index.ts ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-ui/yudao-ui-admin-vue3/src/views/tk/dashboard/index.vue
```

Confirm the diff contains only single-task title support and no batch-generation changes.

- [ ] **Step 3: Check workspace status**

```powershell
git status --short
```

Confirm existing TikTok publishing changes remain untouched and report the new branch name and focused test results.
```
