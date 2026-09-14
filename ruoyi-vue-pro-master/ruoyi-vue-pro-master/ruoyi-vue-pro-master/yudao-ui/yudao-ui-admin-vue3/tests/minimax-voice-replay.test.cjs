const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')

const source = fs.readFileSync(path.resolve(__dirname, '../src/views/tk/dashboard/index.vue'), 'utf8')
const section = (start, end) => {
  const offset = source.indexOf(start)
  assert.ok(offset >= 0, `Missing function ${start}`)
  const limit = source.indexOf(end, offset)
  assert.ok(limit > offset)
  return source.slice(offset, limit)
}
const functions = [
  section('const getDefaultMiniMaxVoiceCode', 'const ensureDefaultBgmSelection'),
  section('const selectedVoicePayload', 'const findReadyCustomVoiceByCode'),
  section('async function hydrateReplayFromAnalysis', 'async function hydrateReplayFromGeneration')
    .replace('analysis: TkReferenceAnalysisVO', 'analysis'),
  section('const handlePreviewVoice =', 'const handlePreviewBgm =')
].join('\n')

const ref = (value) => ({ value })
const noop = () => {}
function harness(provider = 'MINIMAX') {
  let paidPreviews = 0
  const context = vm.createContext({
    TTS_PROVIDER_MINIMAX: 'MINIMAX', TTS_PROVIDER_DASHSCOPE: 'DASHSCOPE', TTS_PROVIDER_MIMO: 'MIMO',
    MIMO_VOICE_MODE_PRESET: 'PRESET', MIMO_VOICE_MODE_DESIGN: 'VOICE_DESIGN', MIMO_VOICE_MODE_CLONE: 'VOICE_CLONE',
    createForm: { ttsProvider: provider, voiceCode: '' },
    miniMaxVoiceOptions: ref([]), miniMaxVoiceLoading: ref(false), hydratingReplay: ref(false),
    isVoiceoverEnabled: ref(true), voiceConfigReady: ref(true), voiceConfigExpanded: ref(false),
    voicePreviewing: ref(false), voicePreviewUrl: ref(''), voicePreviewAudio: ref(undefined),
    openingVideoFile: ref(undefined), openingUploadRef: ref(undefined), referenceAnalysis: ref(undefined),
    activeStep: ref(1), analysisResultExpanded: ref(false), copy: ref({}),
    defaultVoiceCode: 'cosyvoice-old-default', defaultTargetLanguage: 'zh-cn',
    DEFAULT_TARGET_DURATION: 30, DEFAULT_PRODUCT_CATEGORY_CODE: '01',
    DEFAULT_OPENING_CLIP_START: 0, DEFAULT_OPENING_CLIP_DURATION: 3,
    normalizeMaterialPurpose: (value) => value, normalizeAnalysisProvider: (value) => value,
    nextTick: async () => {}, resetScriptDisplay: noop, stopVoicePreview: noop,
    message: { warning: noop, error: noop, success: noop, info: noop },
    TkMiniMaxVoiceApi: { getOptions: async () => [] },
    TkGenerationApi: { previewVoice: async () => { paidPreviews++; return {} } },
    URL: { createObjectURL: () => 'blob:test' },
    Audio: class { async play() {} }
  })
  vm.runInContext(functions + '\nObject.assign(globalThis, { loadMiniMaxVoices, hydrateReplayFromAnalysis, selectedVoicePayload, handlePreviewVoice })', context)
  return { context, paidPreviews: () => paidPreviews }
}

async function main() {
  const { context } = harness()
  let resolveOptions
  context.TkMiniMaxVoiceApi.getOptions = () => new Promise((resolve) => { resolveOptions = resolve })
  const loading = context.loadMiniMaxVoices()
  await context.hydrateReplayFromAnalysis({ sourceUrl: 'https://example/video', libraryId: 1 })
  assert.equal(context.createForm.voiceCode, '', 'Waiting for MiniMax catalog must not select a DashScope voice')
  const defaultId = 'Chinese (Mandarin)_Gentle_Youth'
  resolveOptions([{ voiceId: defaultId, isDefault: true }])
  await loading
  const payload = context.selectedVoicePayload()
  assert.equal(payload.ttsProvider, 'MINIMAX')
  assert.equal(payload.voiceCode, defaultId)
  assert.equal(payload.voiceProfileId, undefined)

  const selected = harness().context
  selected.createForm.voiceCode = 'German_SweetLady'
  await selected.hydrateReplayFromAnalysis({ sourceUrl: 'https://example/video', libraryId: 1 })
  assert.equal(selected.createForm.voiceCode, 'German_SweetLady', 'Replay must preserve an already selected voice')

  const legacy = harness('DASHSCOPE').context
  await legacy.hydrateReplayFromAnalysis({ sourceUrl: 'https://example/video', libraryId: 1 })
  assert.equal(legacy.createForm.voiceCode, 'cosyvoice-old-default')
  const mini = harness()
  mini.context.createForm.voiceCode = defaultId
  await mini.context.handlePreviewVoice()
  assert.equal(mini.paidPreviews(), 0, 'MiniMax previews must never reach the paid generation API')
  const old = harness('DASHSCOPE')
  old.context.createForm.voiceCode = 'cosyvoice-old-default'
  await old.context.handlePreviewVoice()
  assert.equal(old.paidPreviews(), 1, 'Existing DashScope preview remains available')
  console.log('MiniMax replay, payload and preview behavior tests passed')
}
main().catch((error) => { console.error(error); process.exitCode = 1 })
