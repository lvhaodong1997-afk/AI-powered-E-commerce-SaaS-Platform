export const audienceCategories = [
  ['SILVER', '银发经济', 'Silver economy'],
  ['GEN_Z', 'Z世代经济', 'Gen Z'],
  ['WOMEN', '她经济（女性消费）', 'Women'],
  ['SINGLE', '单身经济', 'Singles'],
  ['PET', '宠物经济', 'Pets'],
  ['MATERNAL', '母婴经济', 'Maternal and baby'],
  ['EMOTIONAL', '情绪消费', 'Emotional consumption'],
  ['LOWER_TIER', '下沉市场消费', 'Lower-tier markets'],
  ['MIDDLE_CLASS', '中产品质消费', 'Middle-class quality'],
  ['HIGH_NET_WORTH', '高净值消费', 'High-net-worth']
].map(([code, zh, en]) => ({ code, zh, en }))
const formKeys = [
  'ttsProvider',
  'voiceCode',
  'mimoVoiceProfileId',
  'mimoVoiceMode',
  'mimoVoiceCode',
  'mimoVoicePrompt',
  'mimoVoiceSampleUrl',
  'bgmEnabled',
  'bgmAssetId',
  'bgmVolume'
]
const copyAudio = (form) => Object.fromEntries(formKeys.map((key) => [key, form[key]]))
export const createAudioSelection = () => ({ selected: undefined, manualDraft: undefined })
export function presetToForm(preset) {
  return {
    ttsProvider: preset.ttsProvider || 'MINIMAX',
    voiceCode:
      preset.ttsProvider === 'DASHSCOPE' && preset.voiceProfileId
        ? `custom:${preset.voiceProfileId}`
        : preset.voiceCode || '',
    mimoVoiceProfileId: preset.ttsProvider === 'MIMO' ? preset.voiceProfileId : undefined,
    mimoVoiceMode: preset.mimoVoiceMode || 'PRESET',
    mimoVoiceCode: preset.mimoVoiceCode || 'Mia',
    mimoVoicePrompt: preset.mimoVoicePrompt || '',
    mimoVoiceSampleUrl: preset.mimoVoiceSampleUrl || '',
    bgmEnabled: Boolean(preset.bgmEnabled),
    bgmAssetId: preset.bgmAssetId,
    bgmVolume: preset.bgmVolume ?? 0.1
  }
}
export function formVoicePayload(form) {
  const payload = {
    ttsProvider: form.ttsProvider,
    voiceId: undefined,
    voiceCode: undefined,
    voiceProfileId: undefined,
    mimoVoiceMode: undefined,
    mimoVoiceCode: undefined,
    mimoVoicePrompt: undefined,
    mimoVoiceSampleUrl: undefined
  }
  if (form.ttsProvider === 'MIMO') {
    if (form.mimoVoiceProfileId) payload.voiceProfileId = form.mimoVoiceProfileId
    else {
      payload.mimoVoiceMode = form.mimoVoiceMode
      if (form.mimoVoiceMode === 'VOICE_DESIGN')
        payload.mimoVoicePrompt = form.mimoVoicePrompt?.trim()
      else if (form.mimoVoiceMode === 'VOICE_CLONE')
        payload.mimoVoiceSampleUrl = form.mimoVoiceSampleUrl?.trim()
      else payload.mimoVoiceCode = form.mimoVoiceCode?.trim()
    }
  } else {
    const profile = /^custom:(\d+)$/.exec(form.voiceCode || '')
    if (profile && form.ttsProvider === 'DASHSCOPE') payload.voiceProfileId = Number(profile[1])
    else payload.voiceCode = form.voiceCode
  }
  return payload
}
export function selectAudioPreset(state, form, preset) {
  if (preset?.configStatus !== 'READY') return false
  if (!state.selected) state.manualDraft = copyAudio(form)
  state.selected = { ...preset }
  Object.assign(form, presetToForm(preset))
  return true
}
export function clearAudioPreset(state, form) {
  if (state.manualDraft) Object.assign(form, state.manualDraft)
  state.selected = undefined
  state.manualDraft = undefined
}
export function captureAudioSubmission(state, form) {
  return Object.freeze({
    voicePayload: Object.freeze(formVoicePayload(form)),
    bgmEnabled: Boolean(form.bgmEnabled),
    bgmAssetId: form.bgmAssetId,
    bgmVolume: form.bgmVolume,
    audioPresetId: state.selected?.id,
    audioPresetVersion: state.selected?.version,
    audioMixVersion: 2
  })
}
export function presetSavePayload(slot, form) {
  return {
    id: slot?.id,
    version: slot?.version,
    audienceCode: slot.audienceCode,
    ...formVoicePayload(form),
    bgmEnabled: Boolean(form.bgmEnabled),
    bgmAssetId: form.bgmAssetId,
    bgmVolume: form.bgmVolume
  }
}
