const assert = require('node:assert/strict')
const { test } = require('node:test')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const ts = require('typescript')
const { ref } = require('vue')
const dashboard = fs
  .readFileSync(path.join(__dirname, '../src/views/tk/dashboard/index.vue'), 'utf8')
  .replace(/\r\n/g, '\n')
const editor = fs
  .readFileSync(
    path.join(__dirname, '../src/views/tk/dashboard/components/AudioAudiencePresetDialog.vue'),
    'utf8'
  )
  .replace(/\r\n/g, '\n')
const compile = (code) =>
  ts.transpileModule(code, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText
async function selection() {
  const helpers = await import('../src/views/tk/dashboard/audioSettings.mjs')
  const createForm = {
    ttsProvider: 'MINIMAX',
    voiceCode: 'manual-B',
    bgmEnabled: true,
    bgmAssetId: 8,
    bgmVolume: 0.2
  }
  const audioSelection = helpers.createAudioSelection()
  helpers.selectAudioPreset(audioSelection, createForm, {
    id: 1,
    version: 1,
    audienceCode: 'SILVER',
    configStatus: 'READY',
    ttsProvider: 'DASHSCOPE',
    voiceCode: 'preset-A',
    bgmEnabled: true,
    bgmAssetId: 5,
    bgmVolume: 0.4
  })
  return { ...helpers, createForm, audioSelection, applyAudioChange: async (action) => action() }
}
test('replay replaces active preset audio with the task audio, never the old manual draft', async () => {
  const h = await selection(),
    task = {
      ttsProvider: 'DASHSCOPE',
      voiceCode: 'replayed-C',
      bgmEnabled: true,
      bgmAssetId: 12,
      bgmVolume: 0.6
    }
  const restoreAt = dashboard.indexOf('    await restoreVoiceSelection(task)')
  const code = dashboard.slice(
    dashboard.lastIndexOf('    createForm.analysisProvider =', restoreAt),
    dashboard.indexOf('    openingVideoFile.value =', restoreAt)
  )
  const context = vm.createContext({
    ...h,
    task,
    ANALYSIS_PROVIDER_GEMINI: 'GEMINI',
    DEFAULT_TARGET_DURATION: 30,
    DEFAULT_OPENING_CLIP_START: 0,
    DEFAULT_OPENING_CLIP_DURATION: 3,
    restoreVoiceSelection: async (task) =>
      Object.assign(h.createForm, { ttsProvider: task.ttsProvider, voiceCode: task.voiceCode }),
    restoreSubtitleReplay: () => {}
  })
  await vm.runInContext(`(async()=>{${compile(code)}})()`, context)
  assert.equal(h.createForm.voiceCode, 'replayed-C')
  assert.equal(h.createForm.bgmAssetId, 12)
  assert.equal(h.audioSelection.selected, undefined)
})
test('deleting selected preset music A never silently selects saved manual music B', async () => {
  const h = await selection(),
    snapshot = h.captureAudioSubmission(h.audioSelection, h.createForm)
  const code = dashboard.slice(
    dashboard.indexOf('const handleBgmDeleted ='),
    dashboard.indexOf('const handlePresetFavoriteChange =')
  )
  const context = vm.createContext(h)
  vm.runInContext(compile(code) + '\nglobalThis.remove=handleBgmDeleted', context)
  await context.remove(5)
  assert.equal(h.createForm.bgmAssetId, undefined)
  assert.equal(h.audioSelection.selected, undefined)
  assert.equal(snapshot.bgmAssetId, 5)
  assert.equal(snapshot.voicePayload.voiceCode, 'preset-A')
})
test('preset becoming incomplete never falls back to another manual music track', async () => {
  const h = await selection()
  const code = dashboard.slice(
    dashboard.indexOf('const refreshAudioPresets ='),
    dashboard.indexOf('const refreshAudioManagers =')
  )
  const context = vm.createContext({
    ...h,
    audioPresets: ref([]),
    presetsLoading: ref(false),
    AudioAudiencePresetApi: { getList: async () => [{ id: 1, configStatus: 'INCOMPLETE' }] }
  })
  vm.runInContext(compile(code) + '\nglobalThis.refresh=refreshAudioPresets', context)
  await context.refresh()
  assert.equal(h.createForm.bgmAssetId, undefined)
  assert.equal(h.audioSelection.selected, undefined)
})
for (const reason of ['close', 'voice change'])
  test(`pending voice preview cannot play after ${reason}`, async () => {
    let finish
    const response = new Promise((resolve) => (finish = resolve))
    let plays = 0,
      urls = 0
    const props = { modelValue: true, targetLanguage: 'en' },
      draft = { ttsProvider: 'DASHSCOPE', voiceCode: 'A' }
    const context = vm.createContext({
      ref,
      props,
      draft,
      formVoicePayload: (form) => ({ ...form }),
      TkGenerationApi: { previewVoice: () => response },
      URL: {
        createObjectURL: () => {
          urls++
          return 'blob:sample'
        },
        revokeObjectURL: () => {}
      },
      Audio: class {
        play() {
          plays++
          return Promise.resolve()
        }
        pause() {}
      }
    })
    const code = editor.slice(
      editor.indexOf('const voicePreviewing ='),
      editor.indexOf('watch(\n', editor.indexOf('const voicePreviewing ='))
    )
    vm.runInContext(
      compile(code) + '\nglobalThis.preview=previewVoice;globalThis.cancel=stopVoice',
      context
    )
    const pending = context.preview()
    if (reason === 'close') props.modelValue = false
    else draft.voiceCode = 'B'
    context.cancel()
    finish({})
    await pending
    assert.equal(plays, 0)
    assert.equal(urls, 0)
  })
