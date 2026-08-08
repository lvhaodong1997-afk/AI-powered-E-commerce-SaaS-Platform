# 连点生成视频 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow every click on “生成视频” to create an independent generation task without changing backend interfaces or the existing generation flow.

**Architecture:** Keep the change frontend-only. Extract the active-task merge and task selection rules into a tiny helper module, then let the dashboard page reuse the existing batch progress UI to track multiple concurrently created tasks. Do not add new backend endpoints or database fields.

**Tech Stack:** Vue 3 SFC, TypeScript, Element Plus, existing TK generation API client, Node.js for a focused helper check.

## Global Constraints

- Frontend only.
- Preserve existing batch generation mode and current API contracts.
- Minimal change set; do not change unrelated generation features.
- Only verify the touched behavior.

---

### Task 1: Add a focused queue helper test

**Files:**
- Create: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-ui/yudao-ui-admin-vue3/scripts/test-generation-task-queue.mjs`

**Interfaces:**
- Consumes: `src/views/tk/dashboard/generationTaskQueue.mjs`
- Produces: a direct Node check that validates task merging and latest-active-task selection.

- [ ] **Step 1: Write the failing test**

```js
import assert from 'node:assert/strict'
import {
  getGenerationFocusTask,
  mergeGenerationTasks
} from '../src/views/tk/dashboard/generationTaskQueue.mjs'

const baseTasks = [{ id: 101, status: 'SUCCESS', progress: 100 }]
const merged = mergeGenerationTasks(baseTasks, [102, 103, 102])
assert.equal(merged.length, 3)
assert.deepEqual(merged.map((item) => item.id), [101, 102, 103])
assert.equal(merged[1].status, 'PENDING')
assert.equal(getGenerationFocusTask(merged)?.id, 102)
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node scripts/test-generation-task-queue.mjs`
Expected: module import failure because the helper does not exist yet.

### Task 2: Implement queue bookkeeping helper

**Files:**
- Create: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-ui/yudao-ui-admin-vue3/src/views/tk/dashboard/generationTaskQueue.mjs`

**Interfaces:**
- Produces:
  - `mergeGenerationTasks(existingTasks, newTaskIds)`
  - `getGenerationFocusTask(tasks)`
  - `isTerminalGenerationStatus(status)`

- [ ] **Step 1: Write minimal implementation**

```js
const TERMINAL_STATUSES = new Set(['SUCCESS', 'FAILED'])

export const isTerminalGenerationStatus = (status) => TERMINAL_STATUSES.has(status || '')

export const mergeGenerationTasks = (existingTasks, newTaskIds) => {
  const taskMap = new Map()
  existingTasks.forEach((task) => {
    taskMap.set(task.id, task)
  })
  newTaskIds.forEach((id) => {
    if (!taskMap.has(id)) {
      taskMap.set(id, { id, status: 'PENDING', progress: 0 })
    }
  })
  return [...taskMap.values()]
}

export const getGenerationFocusTask = (tasks) =>
  tasks.find((task) => !isTerminalGenerationStatus(task.status)) ||
  tasks.find((task) => task.status === 'FAILED') ||
  tasks[tasks.length - 1]
```

- [ ] **Step 2: Run test to verify it passes**

Run: `node scripts/test-generation-task-queue.mjs`
Expected: PASS.

### Task 3: Wire the dashboard page to the helper and remove the click lock

**Files:**
- Modify: `ruoyi-vue-pro-master/ruoyi-vue-pro-master/ruoyi-vue-pro-master/yudao-ui/yudao-ui-admin-vue3/src/views/tk/dashboard/index.vue`

**Interfaces:**
- Consumes: helper functions from `generationTaskQueue.mjs`
- Produces: repeated clicks submit independent tasks, while the page keeps a merged active-task list and batch polling state.

- [ ] **Step 1: Update the submit path**

```ts
import {
  getGenerationFocusTask,
  isTerminalGenerationStatus,
  mergeGenerationTasks
} from './generationTaskQueue.mjs'
```

```ts
const hasActiveGenerationTasks = computed(() =>
  batchGenerationTasks.value.some((task) => !isTerminalGenerationStatus(task.status))
)
```

```ts
const analyzeButtonDisabled = computed(
  () => analyzing.value || precheckingGeneration.value || hasActiveGenerationTasks.value
)
```

```ts
const registerGenerationTasks = (taskIds: number[]) => {
  batchGenerationTasks.value = mergeGenerationTasks(batchGenerationTasks.value, taskIds)
  const activeTaskIds = batchGenerationTasks.value.map((task) => task.id).filter(Boolean) as number[]
  if (activeTaskIds.length > 1) {
    startGenerationBatchPolling(activeTaskIds)
    return
  }
  if (activeTaskIds.length === 1) {
    startGenerationPolling(activeTaskIds[0])
  }
}
```

```ts
const handleCreateGeneration = async () => {
  ...
  generating.value += 1
  precheckFailure.value = undefined
  try {
    if (!hasActiveGenerationTasks.value) {
      resetTaskProgress(generationProgress, 'generation')
      startTaskProgress(generationProgress, generationPhases.value, 'generation', 92)
      currentGenerationTask.value = undefined
      batchGenerationTasks.value = []
    }
    ...
    const taskIds = (await createBatchGenerationTaskIds(scripts)).filter((id) => id && !Number.isNaN(id))
    if (!taskIds.length) {
      ...
      return
    }
    registerGenerationTasks(taskIds)
    ...
  } finally {
    generating.value = Math.max(0, generating.value - 1)
  }
}
```

- [ ] **Step 2: Remove button-level loading lock**

```vue
<el-button
  type="primary"
  class="generate-button"
  @click="handleCreateGeneration"
  v-hasPermi="['tk:generation:create']"
>
```

- [ ] **Step 3: Keep the existing queue/status card intact**

```vue
<div
  v-if="generating || generationProgress.running || generationProgress.failed || currentGenerationTask"
  class="generate-submit progressing"
>
```

```ts
const clearCurrentGenerationTask = () => {
  currentGenerationTask.value = undefined
  batchGenerationTasks.value = []
  precheckFailure.value = undefined
  resetTaskProgress(generationProgress, 'generation')
}
```

### Task 4: Focused verification

**Files:**
- No new files

**Interfaces:**
- Uses the helper test and the frontend type-checker.

- [ ] **Step 1: Run the helper test**

Run: `node scripts/test-generation-task-queue.mjs`

- [ ] **Step 2: Run the narrow frontend check**

Run: `..\..\..\..\..\.runtime\npm-global\node_modules\.bin\pnpm.cmd ts:check`

Expected: pass with no new type errors in the dashboard page.
