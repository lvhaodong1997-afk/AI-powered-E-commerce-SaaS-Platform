const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const ts = require('typescript')
const { test } = require('node:test')

const source = fs.readFileSync(path.join(__dirname, '../src/views/tk/dashboard/index.vue'), 'utf8')
const section = (start, end) => source.slice(source.indexOf(start), source.indexOf(end, source.indexOf(start)))
const ref = (value) => ({ value })
const computed = (getter) => ({ get value() { return getter() } })
const noop = () => {}

async function harness(flow = 'ECOMMERCE') {
  const translation = await import('../src/views/tk/dashboard/manualTranslation.mjs')
  const state = translation.createManualTranslationState()
  translation.updateManualTranslationContext(state, { flow, sourceText: '原始文案', targetLanguage: 'en' })
  const audioRequests = []
  const precheckRequests = []
  const referenceRequests = []
  const warnings = []
  const createForm = {
    libraryId: 1, sourceUrl: '', title: 'Example', targetLanguage: 'en', materialPurpose: flow,
    clipPlanMode: 'FULL_POOL_RANDOM', openingVideoUrl: '', openingProcessMode: 'NATIVE',
    bgmEnabled: false, bgmVolume: 0.1
  }
  const ctx = vm.createContext({
    ...translation, ref, computed, console,
    manualTranslation: state, createForm,
    manualTranslationFlow: ref(flow),
    isLeadGenerationManualMode: ref(flow === 'LEAD_GENERATION'),
    isManualEcommerceMode: ref(flow === 'ECOMMERCE'),
    isLeadGenerationFlow: ref(flow === 'LEAD_GENERATION'),
    manualEcommerceScriptText: ref('原始文案'),
    manualLeadScriptText: ref(''), ecommerceScriptMode: ref('AI'),
    hydratingReplay: ref(false), activeStep: ref(0), openingUploadRef: ref(undefined),
    MATERIAL_PURPOSE_LEAD_GENERATION: 'LEAD_GENERATION', MATERIAL_PURPOSE_ECOMMERCE: 'ECOMMERCE',
    ANALYSIS_PROVIDER_GEMINI: 'GEMINI', TTS_PROVIDER_MINIMAX: 'MINIMAX', defaultVoiceCode: 'voice', defaultTargetLanguage: 'zh-cn',
    getDefaultMiniMaxVoiceCode: () => 'voice', normalizeAnalysisProvider: () => 'GEMINI', analysisResultExpanded: ref(false),
    DEFAULT_TARGET_DURATION: 30, DEFAULT_OPENING_CLIP_START: 0, DEFAULT_OPENING_CLIP_DURATION: 3,
    normalizeMaterialPurpose: (value) => value,
    normalizeProductCategoryCode: (value) => value || 'DEFAULT',
    resolveClipPlanModeFromRouteConfig: (value) => JSON.parse(value || '{}').clipPlanMode || 'SEGMENTED',
    isManualScriptFromRouteConfig: (value) => JSON.parse(value || '{}').scriptMode === 'MANUAL',
    isManualLeadGenerationSource: (value) => Boolean(value?.startsWith('manual-lead-generation://')),
    isManualEcommerceSource: (value) => Boolean(value?.startsWith('manual-ecommerce://')),
    restoreVoiceSelection: async () => {}, loadBgmAssets: async () => {}, nextTick: async () => {},
    resetScriptDisplay: noop, findScriptIndexById: () => 0,
    TkReferenceApi: { getLatest: async (request) => { referenceRequests.push(request); return { id: 99 } } },
    selectedLibrary: ref({ companyId: 2, name: 'Library' }), referenceAnalysis: ref(undefined),
    selectedVoicePayload: () => ({ ttsProvider: 'MINIMAX', voiceCode: 'voice' }),
    isVoiceoverEnabled: ref(true), voiceConfigReady: ref(true),
    openingUpload: { status: 'idle' }, openingVideoFile: ref(undefined),
    supportsClipPlanMode: ref(true), getTargetDuration: () => 30, getSubtitlePayload: () => ({ subtitleEnabled: true }),
    batchGenerationEnabled: ref(false), videosPerScript: ref(1), DEFAULT_PRODUCT_CATEGORY_CODE: 'DEFAULT',
    audioExporting: ref(false), audioExportResult: ref(undefined),
    generationSubmittingCount: ref(0), precheckingGenerationCount: ref(0),
    precheckFailure: ref(undefined), hasActiveGenerationTasks: ref(false), currentGenerationTask: ref(undefined),
    generationProgress: { percent: 0 }, generationPhases: ref([{ percent: 5 }]),
    message: { warning: (value) => warnings.push(value), success: noop, error: noop },
    copy: ref({ manualTranslationRequired: 'Wait for translation', manualScriptTooLong: 'Too long' }),
    refreshCreditBalance: async () => {},
    TkGenerationApi: {
      createAudioExport: async (request) => { audioRequests.push(request); return { status: 'SUCCESS', audioUrl: '/audio' } },
      precheckGeneration: async (request) => { precheckRequests.push(request); return { passed: true } }
    }
  })
  const code = [
    section('const manualCopy = computed', 'const handleTranslateManualScript'),
    section('const resolvePromptTextForGeneration', 'const buildPrecheckFailureState'),
    section('const precheckGeneration = async', 'const goFixPrecheckMaterials'),
    section('const handleCreateGeneration = async', 'const handleRetryGeneration = async'),
    section('async function hydrateReplayFromAnalysis', 'async function hydrateReplayPayload'),
    section('function isAnalysisMatchingCurrentForm', 'const analyzeButtonText')
  ].join('\n')
  const js = ts.transpileModule(code, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.None } }).outputText
  vm.runInContext(js + '\nObject.assign(globalThis, { createGenerationSubmissionSnapshot, createGenerationPayload, handleCreateAudioExport, handleCreateGeneration, precheckGeneration, hydrateReplayFromGeneration, hydrateReplayFromAnalysis, isAnalysisMatchingCurrentForm, hasCurrentSuccessfulAnalysis })', ctx)
  const script = { scriptText: '原始文案', title: 'Manual' }
  ctx.selectedScriptsForGeneration = ref([script])
  const translate = (text = 'Translated copy') => {
    translation.setManualTranslationEnabled(state, true)
    const request = translation.beginManualTranslation(state)
    translation.completeManualTranslation(state, request, { sourceText: '原始文案', targetLanguage: 'en', translatedText: text })
  }
  return { ctx, state, translation, translate, script, audioRequests, precheckRequests, referenceRequests, warnings }
}

test('dashboard audio precheck and generation payload select the same translated copy for both manual flows', async () => {
  for (const flow of ['ECOMMERCE', 'LEAD_GENERATION']) {
    const h = await harness(flow)
    h.translate()
    const snapshot = h.ctx.createGenerationSubmissionSnapshot()
    await h.ctx.handleCreateAudioExport()
    await h.ctx.precheckGeneration(h.script, snapshot)
    const payload = h.ctx.createGenerationPayload(h.script, snapshot)
    assert.equal(h.audioRequests[0].scriptText, 'Translated copy')
    assert.equal(h.precheckRequests[0].promptText, 'Translated copy')
    assert.equal(payload.promptText, 'Translated copy')
    assert.equal(payload.manualOriginalText, '原始文案')
    assert.equal(payload.manualTranslationEnabled, true)
    assert.equal(payload.targetLanguage, h.audioRequests[0].targetLanguage)
    assert.equal(payload.clipPlanMode, 'FULL_POOL_RANDOM')
  }
})

test('pending translation blocks audio and video handlers before any submission', async () => {
  const h = await harness()
  h.translation.setManualTranslationEnabled(h.state, true)
  h.translation.beginManualTranslation(h.state)
  await h.ctx.handleCreateAudioExport()
  await h.ctx.handleCreateGeneration()
  assert.equal(h.audioRequests.length, 0)
  assert.equal(h.ctx.generationSubmittingCount.value, 0)
  assert.deepEqual(h.warnings, ['Wait for translation', 'Wait for translation'])
})

test('precheck and subsequent batch payloads retain translated snapshot after user edits or switches off', async () => {
  const h = await harness()
  h.translate()
  const snapshot = h.ctx.createGenerationSubmissionSnapshot()
  let finishPrecheck
  h.ctx.TkGenerationApi.precheckGeneration = (request) => {
    h.precheckRequests.push(request)
    return new Promise((resolve) => { finishPrecheck = resolve })
  }
  const checking = h.ctx.precheckGeneration(h.script, snapshot)
  h.translation.updateManualTranslationContext(h.state, { flow: 'ECOMMERCE', sourceText: '用户后来修改', targetLanguage: 'de' })
  h.translation.setManualTranslationEnabled(h.state, false)
  h.ctx.createForm.targetLanguage = 'de'
  finishPrecheck({ passed: true })
  await checking
  for (let video = 0; video < 3; video++) {
    const payload = h.ctx.createGenerationPayload(h.script, snapshot)
    assert.equal(payload.promptText, h.precheckRequests[0].promptText)
    assert.equal(payload.promptText, 'Translated copy')
    assert.equal(payload.manualOriginalText, '原始文案')
    assert.equal(payload.targetLanguage, 'en')
  }
})

test('manual lead replay with a normal optional URL restores original and translation without fetching AI analysis', async () => {
  const h = await harness('LEAD_GENERATION')
  await h.ctx.hydrateReplayFromGeneration({
    libraryId: 1, sourceUrl: 'https://www.tiktok.com/@reference/video/123',
    materialPurpose: 'LEAD_GENERATION', targetLanguage: 'en', promptText: 'Saved translation',
    generationRouteConfig: JSON.stringify({
      manualTranslationEnabled: true, manualOriginalText: 'Saved original', manualTranslationTargetLanguage: 'de'
    })
  })
  assert.equal(h.referenceRequests.length, 0)
  assert.equal(h.ctx.manualLeadScriptText.value, 'Saved original')
  assert.equal(h.state.translatedText, 'Saved translation')
  assert.equal(h.state.enabled, true)
  assert.equal(h.ctx.createForm.targetLanguage, 'de-de')
  assert.equal(h.ctx.referenceAnalysis.value, undefined)
  assert.equal(h.ctx.createForm.sourceUrl, 'https://www.tiktok.com/@reference/video/123')
})

test('manual lead replay recognizes disabled translation metadata and keeps original selection', async () => {
  const h = await harness('LEAD_GENERATION')
  await h.ctx.hydrateReplayFromGeneration({
    libraryId: 1, sourceUrl: 'https://example/reference', materialPurpose: 'LEAD_GENERATION',
    targetLanguage: 'en', promptText: 'Saved original',
    generationRouteConfig: JSON.stringify({ manualTranslationEnabled: false, manualOriginalText: 'Saved original' })
  })
  assert.equal(h.referenceRequests.length, 0)
  assert.equal(h.ctx.manualLeadScriptText.value, 'Saved original')
  assert.equal(h.state.enabled, false)
  assert.equal(h.translation.selectManualCopy(h.state).promptText, 'Saved original')
})

test('lead replay preserves old manual URL detection and AI selection IDs', async () => {
  const old = await harness('LEAD_GENERATION')
  await old.ctx.hydrateReplayFromGeneration({
    libraryId: 1, sourceUrl: 'manual-lead-generation://1', materialPurpose: 'LEAD_GENERATION', promptText: 'Old manual'
  })
  assert.equal(old.referenceRequests.length, 0)
  assert.equal(old.ctx.manualLeadScriptText.value, 'Old manual')
  for (const selectedIds of [{ referenceAnalysisId: 99 }, { scriptOptionId: 42 }]) {
    const ai = await harness('LEAD_GENERATION')
    await ai.ctx.hydrateReplayFromGeneration({
      libraryId: 1, sourceUrl: 'https://example/reference', materialPurpose: 'LEAD_GENERATION',
      promptText: 'AI script', ...selectedIds,
      generationRouteConfig: JSON.stringify({ manualTranslationEnabled: false, manualOriginalText: 'Original' })
    })
    assert.equal(ai.referenceRequests.length, 1)
    assert.equal(ai.ctx.manualLeadScriptText.value, '')
    assert.equal(ai.ctx.referenceAnalysis.value.id, 99)
  }
})

test('old ecommerce manual replay restores saved copy and clears only internal source URL', async () => {
  const h = await harness('ECOMMERCE')
  await h.ctx.hydrateReplayFromGeneration({ libraryId: 1, materialPurpose: 'ECOMMERCE', targetLanguage: 'es',
    sourceUrl: 'manual-ecommerce://1', promptText: 'Old ecommerce copy', generationRouteConfig: '{"scriptMode":"MANUAL"}' })
  assert.equal(h.referenceRequests.length, 0)
  assert.equal(h.ctx.createForm.sourceUrl, '')
  assert.equal(h.ctx.createForm.targetLanguage, 'es-es')
  assert.equal(h.ctx.manualEcommerceScriptText.value, 'Old ecommerce copy')
  assert.equal(h.translation.selectManualCopy(h.state).promptText, 'Old ecommerce copy')
  assert.equal(h.state.enabled, false)
})

test('changing country blocks audio video and precheck until new translation succeeds', async () => {
  const h = await harness()
  h.translate()
  const stale = h.translation.beginManualTranslation(h.state)
  h.ctx.createForm.targetLanguage = 'en-gb'
  h.translation.updateManualTranslationContext(h.state, { flow: 'ECOMMERCE', sourceText: '原始文案', targetLanguage: 'en-gb' })
  assert.equal(h.translation.completeManualTranslation(h.state, stale, { sourceText: '原始文案', targetLanguage: 'en', translatedText: 'Stale' }), false)
  await h.ctx.handleCreateAudioExport()
  await h.ctx.handleCreateGeneration()
  await assert.rejects(h.ctx.precheckGeneration(h.script), /MANUAL_COPY_NOT_READY/)
  assert.equal(h.audioRequests.length, 0)
  assert.equal(h.precheckRequests.length, 0)
  const request = h.translation.beginManualTranslation(h.state)
  h.translation.completeManualTranslation(h.state, request, { sourceText: '原始文案', targetLanguage: 'en-gb', translatedText: 'UK copy' })
  await h.ctx.handleCreateAudioExport()
  await h.ctx.precheckGeneration(h.script)
  assert.equal(h.audioRequests[0].scriptText, 'UK copy')
  assert.equal(h.precheckRequests[0].promptText, 'UK copy')
})

test('historical AI replay keeps exact analysis lookup locale while country selection is canonical', async () => {
  for (const [legacy, canonical] of [['en', 'en-us'], ['de', 'de-de']]) {
    for (const selection of [{ referenceAnalysisId: 99, scriptOptionId: 42 }, { scriptOptionId: 42 }]) {
      const h = await harness('LEAD_GENERATION')
      const savedAnalysis = { id: 99, targetLanguage: legacy, scriptOptions: [{ id: 42, scriptText: 'Historical AI script' }] }
      h.ctx.TkReferenceApi.getLatest = async (request) => {
        h.referenceRequests.push(request)
        return request.targetLanguage === legacy ? savedAnalysis : undefined
      }
      const restoredSelections = []
      h.ctx.findScriptIndexById = (id) => {
        const index = h.ctx.referenceAnalysis.value?.scriptOptions.findIndex((script) => script.id === id) ?? -1
        restoredSelections.push({ id, index })
        return index
      }
      const displayIndexes = []
      h.ctx.resetScriptDisplay = (index) => displayIndexes.push(index)
      await h.ctx.hydrateReplayFromGeneration({ libraryId: 1, sourceUrl: 'https://example/reference',
        materialPurpose: 'LEAD_GENERATION', targetLanguage: legacy, promptText: 'Historical AI script', ...selection })
      assert.equal(h.referenceRequests[0].targetLanguage, legacy)
      assert.equal(h.ctx.createForm.targetLanguage, canonical)
      assert.equal(h.ctx.referenceAnalysis.value, savedAnalysis)
      assert.deepEqual(restoredSelections, [{ id: 42, index: 0 }])
      assert.deepEqual(displayIndexes, [0])
      assert.equal(h.ctx.activeStep.value, 4)
      assert.equal(h.ctx.manualLeadScriptText.value, '')
    }
  }
})

test('direct successful analysis replay matches canonical country while preserving regional distinctions', async () => {
  for (const [saved, selected] of [['en', 'en-us'], ['de', 'de-de'], ['fr', 'fr-fr']]) {
    const h = await harness()
    const analysis = { id: 99, status: 'SUCCESS', sourceUrl: 'https://example/reference', libraryId: 1,
      targetLanguage: saved, materialPurpose: 'ECOMMERCE', analysisProvider: 'GEMINI', referenceDuration: 30 }
    await h.ctx.hydrateReplayFromAnalysis(analysis)
    assert.equal(h.ctx.createForm.targetLanguage, selected)
    assert.equal(h.ctx.hasCurrentSuccessfulAnalysis.value, true)
  }
  const h = await harness()
  const analysis = { id: 99, status: 'SUCCESS', sourceUrl: 'https://example/reference', libraryId: 1,
    materialPurpose: 'ECOMMERCE', analysisProvider: 'GEMINI', referenceDuration: 30 }
  for (const [saved, selected] of [['en-gb', 'en-us'], ['es-mx', 'es-es'], ['pt-br', 'pt-pt'], ['unknown', 'unknown']]) {
    await h.ctx.hydrateReplayFromAnalysis({ ...analysis, targetLanguage: saved })
    h.ctx.createForm.targetLanguage = selected
    assert.equal(h.ctx.hasCurrentSuccessfulAnalysis.value, false)
  }
})
