import assert from 'node:assert/strict'
import test from 'node:test'
import {
  createAudioSelection,
  selectAudioPreset,
  clearAudioPreset,
  captureAudioSubmission,
  presetSavePayload,
  presetToForm,
  formVoicePayload
} from '../src/views/tk/dashboard/audioSettings.mjs'
const manual = () => ({
  ttsProvider: 'MINIMAX',
  voiceCode: 'manual',
  voiceEnabled: false,
  bgmEnabled: true,
  bgmAssetId: 8,
  bgmVolume: 0
})
const preset = () => ({
  id: 2,
  version: 3,
  audienceCode: 'SILVER',
  configStatus: 'READY',
  ttsProvider: 'MIMO',
  voiceProfileId: 9,
  bgmEnabled: true,
  bgmAssetId: 5,
  bgmVolume: 1
})
test('optional selection applies only READY audio and preserves explicit voice disabled', () => {
  const form = manual(),
    state = createAudioSelection()
  assert.equal(state.selected, undefined)
  assert.equal(selectAudioPreset(state, form, { ...preset(), configStatus: 'RESET' }), false)
  assert.deepEqual(form, manual())
  assert.equal(selectAudioPreset(state, form, { ...preset(), configStatus: 'INCOMPLETE' }), false)
  assert.equal(selectAudioPreset(state, form, preset()), true)
  assert.equal(form.bgmVolume, 1)
  assert.equal(form.mimoVoiceProfileId, 9)
  assert.equal(form.voiceEnabled, false)
})
test('switching presets and clearing restores original manual draft without nonaudio mutation', () => {
  const form = { ...manual(), title: 'keep' },
    state = createAudioSelection()
  selectAudioPreset(state, form, preset())
  selectAudioPreset(state, form, { ...preset(), id: 4, bgmVolume: 0.6 })
  clearAudioPreset(state, form)
  assert.equal(form.voiceCode, 'manual')
  assert.equal(form.bgmAssetId, 8)
  assert.equal(form.bgmVolume, 0)
  assert.equal(form.title, 'keep')
  assert.equal(state.selected, undefined)
})
test('submission captures independent preset metadata and audio before reset/delete', () => {
  const form = manual(),
    state = createAudioSelection()
  selectAudioPreset(state, form, preset())
  const snapshot = captureAudioSubmission(state, form)
  state.selected.version = 20
  clearAudioPreset(state, form)
  form.bgmAssetId = 99
  assert.equal(snapshot.audioPresetVersion, 3)
  assert.equal(snapshot.audioPresetId, 2)
  assert.equal(snapshot.audioMixVersion, 2)
  assert.equal(snapshot.bgmAssetId, 5)
  assert.equal(snapshot.voicePayload.voiceProfileId, 9)
  assert.ok(Object.isFrozen(snapshot.voicePayload))
  assert.equal(captureAudioSubmission(state, form).audioPresetId, undefined)
})
test('saving RESET slot preserves optimistic version and detects separate concurrent requests', () => {
  const slot = { ...preset(), configStatus: 'RESET' },
    draft = manual()
  const first = presetSavePayload(slot, draft),
    second = presetSavePayload({ ...slot, version: 4 }, draft)
  assert.equal(first.id, 2)
  assert.equal(first.version, 3)
  assert.equal(second.version, 4)
  draft.bgmVolume = 0.8
  assert.equal(first.bgmVolume, 0)
})
test('profile and MiMo modes roundtrip through editor without stale fields', () => {
  const form = presetToForm(preset())
  assert.equal(formVoicePayload(form).voiceProfileId, 9)
  form.mimoVoiceProfileId = undefined
  form.mimoVoiceMode = 'VOICE_DESIGN'
  form.mimoVoicePrompt = 'warm'
  const payload = formVoicePayload(form)
  assert.equal(payload.mimoVoicePrompt, 'warm')
  assert.equal(payload.voiceProfileId, undefined)
  const dash = presetToForm({ ...preset(), ttsProvider: 'DASHSCOPE', voiceProfileId: 44 })
  assert.equal(dash.voiceCode, 'custom:44')
  assert.equal(formVoicePayload(dash).voiceProfileId, 44)
})

test('MiniMax retains dictionary voiceCode rather than unsupported custom profile identifiers', () => {
  const form = presetToForm({
    ttsProvider: 'MINIMAX',
    voiceCode: 'English_Graceful_Lady',
    voiceProfileId: 88
  })
  assert.equal(form.voiceCode, 'English_Graceful_Lady')
  assert.equal(formVoicePayload(form).voiceCode, 'English_Graceful_Lady')
  assert.equal(formVoicePayload(form).voiceProfileId, undefined)
})
