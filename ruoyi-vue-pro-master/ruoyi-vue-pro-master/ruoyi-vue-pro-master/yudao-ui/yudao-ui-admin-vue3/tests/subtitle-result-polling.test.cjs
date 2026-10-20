const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const ts = require('typescript')
const { test } = require('node:test')

const source = fs.readFileSync(path.join(__dirname, '../src/views/tk/dashboard/index.vue'), 'utf8')
const section = (start, end) => source.slice(source.indexOf(start), source.indexOf(end, source.indexOf(start)))
const deferred = () => {
  let resolve
  const promise = new Promise((done) => { resolve = done })
  return { promise, resolve }
}
const resultCopy = { partial: 'partly applied', disabled: 'disabled', unknown: 'older task' }
const statusTask = (id = 7) => ({ id, status: 'SUCCESS', progress: 100, outputUrl: `/result-${id}.mp4` })
const detailTask = (status, id = 7) => ({ ...statusTask(id), libraryId: 1,
  subtitleEnabled: true, subtitleKaraokeEnabled: true,
  ...(status ? { subtitleKaraokeStatus: status, subtitleKaraokeReason: 'timing unavailable' } : {}) })

async function harness(getDetail) {
  const queue = await import('../src/views/tk/dashboard/generationTaskQueue.mjs')
  const { karaokeResultMessage } = await import('../src/views/tk/dashboard/subtitlePreview.mjs')
  const requests = []
  const tracked = []
  const ctx = vm.createContext({
    ...queue, currentGenerationTask: { value: undefined }, batchGenerationTasks: { value: [] },
    generationProgress: { startedAt: 1, running: true }, generationPhases: { value: [] },
    generationPhaseIndexByTask: () => 9, formatElapsed: () => '1s',
    clearGenerationPolling: () => {}, refreshCreditBalance: async () => {}, getData: async () => {},
    finishTaskProgress: () => {}, message: { success: () => {} },
    copy: { value: {} },
    TkGenerationApi: {
      getGenerationStatusBatch: async () => [statusTask()],
      getGeneration: async (id) => { requests.push(id); return getDetail(id) }
    },
    startGenerationBatchPolling: (ids) => tracked.push(Array.from(ids))
  })
  const code = section('const syncGenerationBatchProgress =', 'const startGenerationPolling =') + '\n' +
    section('const getTrackedGenerationTaskIds =', 'const parseGenerationCreateTime =')
  vm.runInContext(ts.transpileModule(code, { compilerOptions: {
    target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.None
  } }).outputText + '\nObject.assign(globalThis, { syncGenerationBatchProgress, pollGenerationTaskBatch, registerGenerationTasks })', ctx)
  return { ctx, requests, tracked, notice: () => karaokeResultMessage(ctx.currentGenerationTask.value, resultCopy) }
}

for (const status of ['partial', 'disabled']) {
  test(`normal single-task batch polling loads ${status} subtitle result`, async () => {
    const h = await harness(async () => detailTask(status))
    h.ctx.registerGenerationTasks([7])
    assert.deepEqual(h.tracked, [[7]], 'ordinary single creation uses batch polling')
    await h.ctx.pollGenerationTaskBatch([7])
    assert.equal(h.notice(), resultCopy[status])
    assert.deepEqual(h.requests, [7], 'result details come from existing authorized detail endpoint')
    assert.equal(h.ctx.currentGenerationTask.value.subtitleKaraokeReason, 'timing unavailable')
    h.ctx.syncGenerationBatchProgress([statusTask()])
    assert.equal(h.notice(), resultCopy[status], 'late status-only poll retains loaded result details')
  })
}

test('overlapping completion status cannot erase diagnostics loaded by the first poll', async () => {
  const detail = deferred()
  let calls = 0
  const h = await harness(async () => ++calls === 1 ? detailTask('partial') : detail.promise)
  await h.ctx.pollGenerationTaskBatch([7])
  const later = h.ctx.pollGenerationTaskBatch([7])
  await new Promise((resolve) => setImmediate(resolve))
  assert.equal(h.notice(), resultCopy.partial)
  detail.resolve(detailTask('partial'))
  await later
  assert.equal(h.notice(), resultCopy.partial)
})

test('late result detail cannot overwrite a different focused task or cleared result', async () => {
  for (const next of [[{ id: 8, status: 'RENDERING', progress: 30 }], []]) {
    const detail = deferred()
    const h = await harness(() => detail.promise)
    const polling = h.ctx.pollGenerationTaskBatch([7])
    await new Promise((resolve) => setImmediate(resolve))
    h.ctx.syncGenerationBatchProgress(next)
    detail.resolve(detailTask('disabled'))
    await polling
    assert.equal(h.ctx.currentGenerationTask.value?.id, next[0]?.id)
    assert.equal(h.notice(), '')
  }
})

test('different task status cannot inherit previous task diagnostics', async () => {
  const h = await harness(async () => detailTask('partial'))
  await h.ctx.pollGenerationTaskBatch([7])
  h.ctx.syncGenerationBatchProgress([statusTask(8)])
  assert.equal(h.ctx.currentGenerationTask.value.id, 8)
  assert.equal(h.ctx.currentGenerationTask.value.subtitleKaraokeStatus, undefined)
})

test('older successful detail retains unknown status and failed detail fetch keeps completion usable', async () => {
  const h = await harness(async () => detailTask(undefined))
  await h.ctx.pollGenerationTaskBatch([7])
  assert.equal(h.notice(), resultCopy.unknown)
  assert.equal(h.ctx.currentGenerationTask.value.subtitleKaraokeStatus, undefined)
  const unavailable = await harness(async () => { throw new Error('unavailable') })
  await unavailable.ctx.pollGenerationTaskBatch([7])
  assert.equal(unavailable.ctx.currentGenerationTask.value.outputUrl, '/result-7.mp4')
  assert.equal(unavailable.ctx.generationProgress.running, false)
})

test('running focus never loads result details', async () => {
  const h = await harness(async () => detailTask('partial'))
  h.ctx.TkGenerationApi.getGenerationStatusBatch = async () => [{ id: 7, status: 'RENDERING', progress: 50 }]
  await h.ctx.pollGenerationTaskBatch([7])
  assert.deepEqual(h.requests, [])
})


test('late detail cannot overwrite a newer result for the same task', async () => {
  const detail = deferred()
  const h = await harness(() => detail.promise)
  const polling = h.ctx.pollGenerationTaskBatch([7])
  await new Promise((resolve) => setImmediate(resolve))
  h.ctx.syncGenerationBatchProgress([{ ...statusTask(), outputUrl: '/retry-result.mp4' }])
  detail.resolve(detailTask('disabled'))
  await polling
  assert.equal(h.ctx.currentGenerationTask.value.outputUrl, '/retry-result.mp4')
  assert.equal(h.ctx.currentGenerationTask.value.subtitleKaraokeStatus, undefined)
})

test('mismatched detail response cannot supply diagnostics for the displayed result', async () => {
  for (const wrong of [{ id: 8 }, { status: 'RENDERING' }, { outputUrl: '/different.mp4' }]) {
    const h = await harness(async () => ({ ...detailTask('disabled'), ...wrong }))
    await h.ctx.pollGenerationTaskBatch([7])
    assert.equal(h.ctx.currentGenerationTask.value.id, 7)
    assert.equal(h.ctx.currentGenerationTask.value.outputUrl, '/result-7.mp4')
    assert.equal(h.ctx.currentGenerationTask.value.subtitleKaraokeStatus, undefined)
  }
})
