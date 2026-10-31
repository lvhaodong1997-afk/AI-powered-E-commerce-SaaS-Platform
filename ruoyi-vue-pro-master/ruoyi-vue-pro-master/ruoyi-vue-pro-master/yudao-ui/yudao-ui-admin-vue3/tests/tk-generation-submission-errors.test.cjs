const assert = require('node:assert/strict')
const { test } = require('node:test')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const ts = require('typescript')
const read = (name) => fs.readFileSync(path.join(__dirname, '../src', name), 'utf8').replace(/\r\n/g, '\n')
const compile = (source) => ts.transpileModule(source, {
  compilerOptions: { target: ts.ScriptTarget.ES2022 }
}).outputText

test('the response interceptor preserves an explicit business rejection and its displayed state', async () => {
  const source = read('config/axios/service.ts')
  const start = source.indexOf('  async (response: AxiosResponse<any>) => {')
  const end = source.indexOf('  (error: AxiosError) => {', start)
  const handler = source.slice(start, end).trim().replace(/,$/, '')
  const notices = []
  const context = vm.createContext({
    ApiEncrypt: { getEncryptHeader: () => 'x-encrypt' },
    useI18n: () => ({ t: (key) => key }),
    result_code: 0,
    errorCode: {},
    ignoreMsgs: [],
    ElNotification: { error: (notice) => notices.push(notice) }
  })
  vm.runInContext(compile(`globalThis.respond = ${handler}`), context)
  await assert.rejects(context.respond({
    data: { code: 400, msg: '预设公司范围不匹配' },
    config: { url: '/tk/generation/create' }, headers: {}
  }), (error) => {
    assert.equal(error.businessCode, 400)
    assert.equal(error.displayedByAxios, true)
    return true
  })
  assert.equal(notices.length, 1)
})

async function submit(error, recovered = []) {
  const source = read('views/tk/dashboard/index.vue')
  const code = source.slice(source.indexOf('const handleCreateGeneration = async () =>'),
    source.indexOf('const handleRetryGeneration = async () =>'))
  const errors = [], successes = [], recoveryCalls = []
  const box = (value) => ({ value })
  const context = vm.createContext({
    generationSubmittingCount: box(0), ensureManualCopyReady: () => true,
    isManualEcommerceMode: box(true), manualEcommerceScriptText: box('copy'),
    isLeadGenerationManualMode: box(false), isVoiceoverEnabled: box(false),
    voiceConfigReady: box(true), openingVideoFile: box(undefined), openingUpload: {},
    createForm: { libraryId: 10 }, copy: box({ generationSubmissionFailed: 'unknown', generationRecovered: 'recovered' }),
    createGenerationSubmissionSnapshot: () => ({ isManualEcommerceMode: true, videosPerScript: 1 }),
    selectedScriptsForGeneration: box([{ scriptText: 'copy' }]), hasActiveGenerationTasks: box(false),
    generationProgress: {}, generationPhases: box([]), precheckFailure: box(undefined),
    currentGenerationTask: box(undefined), batchGenerationTasks: box([]), analysisProgress: {},
    generationSubmissionError: box(''), activeStep: box(0),
    finishTaskProgress: (progress) => { progress.failed = true },
    resetTaskProgress: () => {}, startGenerationSubmissionProgress: () => {},
    ensureBgmReadyForGeneration: async () => true, precheckGenerationScripts: async () => true,
    createBatchGenerationTaskIds: async () => { throw error },
    getTrackedGenerationTaskIds: () => [],
    recoverSubmittedGenerationTaskIds: async (...args) => { recoveryCalls.push(args); return recovered },
    registerGenerationTasks: () => {}, clearGenerationPolling: () => {},
    message: { error: (msg) => errors.push(msg), success: (msg) => successes.push(msg) }
  })
  vm.runInContext(compile(code) + '\nglobalThis.submit = handleCreateGeneration', context)
  await context.submit()
  return { context, errors, successes, recoveryCalls }
}

test('explicit validation failure keeps the reason without adding an unknown-result warning', async () => {
  const error = Object.assign(new Error('预设公司范围不匹配'), { businessCode: 400, displayedByAxios: true })
  const result = await submit(error)
  assert.deepEqual(result.errors, [])
  assert.equal(result.context.generationSubmissionError.value, error.message)
  assert.equal(result.context.generationProgress.failed, true)
  assert.equal(result.context.generationSubmittingCount.value, 0)
})

test('a timeout retains task recovery and the unknown-result warning', async () => {
  const result = await submit(new Error('timeout'))
  assert.equal(result.recoveryCalls.length, 1)
  assert.deepEqual(result.errors, ['unknown'])
})

test('a partially accepted submission still recovers created tasks after a business error', async () => {
  const result = await submit(Object.assign(new Error('validation'), { businessCode: 400 }), [42])
  assert.equal(result.recoveryCalls.length, 1)
  assert.deepEqual(result.successes, ['recovered'])
  assert.deepEqual(result.errors, [])
  assert.equal(result.context.activeStep.value, 5)
})

test('a previous submission error never replaces an existing task status', () => {
  const source = read('views/tk/dashboard/index.vue')
  const expression = source.match(/<p>{{\s*(precheckFailure\?\.message[\s\S]*?)}}<\/p>/)[1]
  for (const failed of [false, true]) {
    const context = vm.createContext({
      precheckFailure: undefined, generationSubmissionError: 'old validation error',
      generationProgress: { failed }, currentGenerationTask: { id: 42 },
      friendlyGenerationFailureReason: () => 'task failure', currentGenerationStepDescription: 'running'
    })
    assert.equal(vm.runInContext(expression, context), failed ? 'task failure' : 'running')
  }
})
