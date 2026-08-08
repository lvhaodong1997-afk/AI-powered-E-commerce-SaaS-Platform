import assert from 'node:assert/strict'
import {
  getGenerationFocusTask,
  isTerminalGenerationStatus,
  mergeGenerationTasks
} from '../src/views/tk/dashboard/generationTaskQueue.mjs'

const baseTasks = [{ id: 101, status: 'SUCCESS', progress: 100 }]
const merged = mergeGenerationTasks(baseTasks, [102, 103, 102])

assert.equal(merged.length, 3, 'repeated clicks should append new task ids without duplicates')
assert.deepEqual(
  merged.map((task) => task.id),
  [101, 102, 103],
  'existing tasks should be preserved and new tasks appended in click order'
)
assert.equal(merged[1].status, 'PENDING', 'newly submitted tasks should get a pending placeholder')
assert.equal(merged[1].progress, 0, 'newly submitted tasks should start at zero progress')
assert.equal(getGenerationFocusTask(merged)?.id, 102, 'the UI should focus the first active task')

const failedFocus = getGenerationFocusTask([
  { id: 201, status: 'SUCCESS', progress: 100 },
  { id: 202, status: 'FAILED', progress: 80 },
  { id: 203, status: 'SUCCESS', progress: 100 }
])
assert.equal(failedFocus?.id, 202, 'failed task should stay visible when all tasks are terminal')

const completedFocus = getGenerationFocusTask([
  { id: 301, status: 'SUCCESS', progress: 100 },
  { id: 302, status: 'SUCCESS', progress: 100 }
])
assert.equal(completedFocus?.id, 302, 'latest completed task should stay visible after success')

assert.equal(isTerminalGenerationStatus('SUCCESS'), true)
assert.equal(isTerminalGenerationStatus('FAILED'), true)
assert.equal(isTerminalGenerationStatus('RENDERING'), false)

console.log('generation task queue checks passed')
