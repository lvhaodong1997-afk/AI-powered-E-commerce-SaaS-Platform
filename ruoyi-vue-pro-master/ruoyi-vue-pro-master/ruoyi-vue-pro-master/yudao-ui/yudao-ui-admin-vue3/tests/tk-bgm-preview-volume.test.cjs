const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const { test } = require('node:test')
const ts = require('typescript')
const { computed, effectScope, markRaw, nextTick, reactive, ref, watch } = require('vue')

const source = fs.readFileSync(path.join(__dirname, '../src/views/tk/dashboard/index.vue'), 'utf8')
  .replace(/\r\n/g, '\n')

function section(start, end) {
  const from = source.indexOf(start)
  const to = source.indexOf(end, from)
  assert.ok(from >= 0 && to > from, `Missing dashboard section: ${start}`)
  return source.slice(from, to)
}

function harness(t) {
  const scope = effectScope()
  const createdAudio = []
  const createForm = reactive({ bgmAssetId: 1, bgmEnabled: true, bgmVolume: 0.1 })
  const bgmPreviewAudio = ref()
  const assets = [{ id: 1, fileUrl: '/first.mp3' }, { id: 2, fileUrl: '/second.mp3' }]
  // Node has no HTMLAudioElement; keep Vue reactivity and the dashboard handlers real.
  class AudioDouble {
    constructor(url) {
      this.src = url
      this.volume = 1
      this.currentTime = 0
      this.paused = true
      this.playCalls = 0
      this.pauseCalls = 0
      createdAudio.push(markRaw(this))
    }
    async play() {
      this.paused = false
      this.playCalls++
    }
    pause() {
      this.paused = true
      this.pauseCalls++
    }
  }
  const ctx = vm.createContext({
    createForm, bgmPreviewAudio, watch, Audio: AudioDouble,
    selectedBgmAsset: computed(() => assets.find((asset) => asset.id === createForm.bgmAssetId)),
    copy: ref({ bgmPlayError: 'BGM playback failed' }),
    message: { error: (message) => assert.fail(message) }
  })
  const code = [
    section('const stopBgmPreview =', 'const handleSaveMimoVoice ='),
    section('const handlePreviewBgm =', 'const handleBgmUploadChange ='),
    section('watch(\n  () => [createForm.bgmAssetId', 'watch(\n  () => createForm.openingVideoUrl')
  ].join('\n')
  const js = ts.transpileModule(code, {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.None }
  }).outputText
  scope.run(() => vm.runInContext(js + '\nglobalThis.preview = handlePreviewBgm', ctx))
  t.after(() => {
    scope.stop()
    bgmPreviewAudio.value?.pause()
  })
  return { createForm, bgmPreviewAudio, createdAudio, preview: ctx.preview }
}

test('changing BGM volume updates the playing audio without pausing or restarting', async (t) => {
  const h = harness(t)
  await h.preview()
  const audio = h.bgmPreviewAudio.value
  audio.currentTime = 7.25

  for (const volume of [0.24, 0.01, 0.3, 0.1]) {
    h.createForm.bgmVolume = volume
    await nextTick()
    assert.equal(audio.volume, volume)
    assert.equal(h.bgmPreviewAudio.value, audio)
    assert.equal(audio.paused, false)
    assert.equal(audio.currentTime, 7.25)
    assert.equal(audio.playCalls, 1)
    assert.equal(audio.pauseCalls, 0)
  }
})

test('starting a BGM preview uses the currently selected volume', async (t) => {
  const h = harness(t)
  h.createForm.bgmVolume = 0.16
  await nextTick()
  await h.preview()
  assert.equal(h.bgmPreviewAudio.value.volume, 0.16)
  assert.equal(h.bgmPreviewAudio.value.src, '/first.mp3')
})

test('changing volume without an active preview does not start playback', async (t) => {
  const h = harness(t)
  h.createForm.bgmVolume = 0.24
  await nextTick()
  assert.equal(h.bgmPreviewAudio.value, undefined)
  assert.equal(h.createdAudio.length, 0)
})

test('selecting another BGM stops and clears the previous preview', async (t) => {
  const h = harness(t)
  await h.preview()
  const audio = h.bgmPreviewAudio.value
  h.createForm.bgmAssetId = 2
  await nextTick()
  assert.equal(audio.paused, true)
  assert.equal(h.bgmPreviewAudio.value, undefined)
})

test('disabling BGM stops and clears the current preview', async (t) => {
  const h = harness(t)
  await h.preview()
  const audio = h.bgmPreviewAudio.value
  h.createForm.bgmEnabled = false
  await nextTick()
  assert.equal(audio.paused, true)
  assert.equal(h.bgmPreviewAudio.value, undefined)
})
