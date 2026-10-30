<template>
  <el-dialog
    v-model="visible"
    :title="text('人群音频预设', 'Audience audio presets')"
    width="960px"
    destroy-on-close
  >
    <p>{{
      text(
        '为每个人群保存自己的音色与背景音乐',
        'Save your own voice and background music for each audience'
      )
    }}</p>
    <div class="preset-layout">
      <nav :aria-label="text('人群分类', 'Audience categories')">
        <el-button
          v-for="item in audienceCategories"
          :key="item.code"
          :type="activeCode === item.code ? 'primary' : 'default'"
          :disabled="saving"
          @click="openSlot(item.code)"
        >
          {{ isEn ? item.en : item.zh }} · {{ statusLabel(item.code) }}
        </el-button>
      </nav>
      <el-form label-position="top" class="preset-editor" @submit.prevent>
        <h3>{{ activeLabel }}</h3>
        <el-form-item :label="text('音色提供方', 'Voice provider')">
          <el-select v-model="draft.ttsProvider" @change="changeProvider">
            <el-option
              v-for="item in providers"
              :key="item.value"
              :value="item.value"
              :label="item.label"
            />
          </el-select>
        </el-form-item>
        <MiniMaxVoiceSelector
          v-if="draft.ttsProvider !== 'MIMO'"
          v-model="draft.voiceCode"
          :provider="draft.ttsProvider"
          :options="draft.ttsProvider === 'MINIMAX' ? miniMaxOptions : dashScopeOptions"
          :is-en="isEn"
          :favorites-disabled="draft.ttsProvider === 'DASHSCOPE' && !dashScopeFavoritesReady"
          @favorite-change="favoriteChanged"
        />
        <template v-else>
          <el-form-item :label="text('已保存的 MiMo 音色', 'Saved MiMo voice')">
            <el-select
              v-model="draft.mimoVoiceProfileId"
              clearable
              :placeholder="
                text('选择已保存音色或配置新音色', 'Select a saved voice or configure one')
              "
            >
              <el-option
                v-for="item in mimoSavedOptions"
                :key="item.value"
                :value="item.value"
                :label="item.label"
              />
            </el-select>
          </el-form-item>
          <el-form-item :label="text('音色模式', 'Voice mode')">
            <el-select v-model="draft.mimoVoiceMode" :disabled="Boolean(draft.mimoVoiceProfileId)">
              <el-option value="PRESET" :label="text('预置音色', 'Preset')" /><el-option
                value="VOICE_DESIGN"
                :label="text('音色设计', 'Design')"
              /><el-option value="VOICE_CLONE" :label="text('音色克隆', 'Clone')" />
            </el-select>
          </el-form-item>
          <el-form-item
            v-if="draft.mimoVoiceMode === 'PRESET'"
            :label="text('预置音色', 'Preset voice')"
          >
            <el-select v-model="draft.mimoVoiceCode" :disabled="Boolean(draft.mimoVoiceProfileId)"
              ><el-option
                v-for="item in mimoPresetOptions"
                :key="item.value"
                :value="item.value"
                :label="item.label"
            /></el-select>
          </el-form-item>
          <el-form-item
            v-else-if="draft.mimoVoiceMode === 'VOICE_DESIGN'"
            :label="text('音色描述', 'Voice description')"
            ><el-input
              v-model="draft.mimoVoicePrompt"
              type="textarea"
              :disabled="Boolean(draft.mimoVoiceProfileId)"
          /></el-form-item>
          <el-form-item v-else :label="text('参考音频 URL', 'Reference audio URL')"
            ><el-input
              v-model="draft.mimoVoiceSampleUrl"
              :disabled="Boolean(draft.mimoVoiceProfileId)"
          /></el-form-item>
        </template>
        <el-button
          v-if="draft.ttsProvider !== 'MINIMAX'"
          plain
          :loading="voicePreviewing"
          @click="previewVoice"
          >{{ text('试听音色', 'Preview voice') }}</el-button
        >
        <el-button text @click="$emit('manage-voices')">{{
          text('管理自定义音色', 'Manage custom voices')
        }}</el-button>
        <el-divider />
        <el-form-item :label="text('背景音乐', 'Background music')"
          ><el-switch v-model="draft.bgmEnabled"
        /></el-form-item>
        <el-form-item v-if="draft.bgmEnabled" :label="text('默认 BGM', 'Default BGM')">
          <el-select
            v-model="draft.bgmAssetId"
            clearable
            filterable
            :placeholder="text('选择音乐曲目', 'Select a music track')"
            ><el-option
              v-for="item in bgmAssets"
              :key="item.id"
              :value="item.id"
              :label="item.name"
          /></el-select>
          <el-button :disabled="!previewUrl" @click="preview">{{
            text('试听', 'Preview')
          }}</el-button>
        </el-form-item>
        <el-form-item
          v-if="draft.bgmEnabled"
          :label="`${text('音乐音量', 'Music volume')} · ${Math.round(draft.bgmVolume * 100)}%`"
          ><el-slider
            v-model="draft.bgmVolume"
            :min="0"
            :max="1"
            :step="0.01"
            :format-tooltip="(n: number) => `${Math.round(n * 100)}%`"
        /></el-form-item>
        <el-button text @click="$emit('manage-bgm')">{{
          text('管理 BGM', 'Manage BGM')
        }}</el-button>
        <el-alert
          v-if="slot?.configStatus === 'INCOMPLETE'"
          :closable="false"
          type="warning"
          :title="
            text(
              '配置已失效，请重新选择音色或曲目后保存',
              'Configuration is incomplete. Choose valid voice and music, then save.'
            )
          "
        />
        <small>{{
          text(
            '音色与音乐由用户自行配置；已创建任务保留音频快照。',
            'Choose your own voice and music. Existing tasks retain their audio snapshots.'
          )
        }}</small>
      </el-form>
    </div>
    <template #footer>
      <el-button v-if="slot?.id" :disabled="saving" type="danger" plain @click="reset">{{
        text('重置此预设', 'Reset preset')
      }}</el-button>
      <el-button :disabled="saving" @click="visible = false">{{
        text('取消', 'Cancel')
      }}</el-button>
      <el-button type="primary" :loading="saving" @click="save">{{
        text('保存预设', 'Save preset')
      }}</el-button>
    </template>
  </el-dialog>
</template>
<script setup lang="ts">
import { AudioAudiencePresetApi, type AudioAudiencePresetVO } from '@/api/tk/audio-audience-preset'
import type { TkBgmAssetVO } from '@/api/tk/bgm'
import type { TkMiniMaxVoiceOptionVO } from '@/api/tk/voice'
import MiniMaxVoiceSelector from './MiniMaxVoiceSelector.vue'
import {
  audienceCategories,
  presetToForm,
  presetSavePayload,
  formVoicePayload
} from '../audioSettings.mjs'
import { TkGenerationApi } from '@/api/tk/generation'
const props = defineProps<{
  targetLanguage: string
  modelValue: boolean
  isEn: boolean
  presets: AudioAudiencePresetVO[]
  initialCode?: string
  bgmAssets: TkBgmAssetVO[]
  miniMaxOptions: TkMiniMaxVoiceOptionVO[]
  dashScopeOptions: TkMiniMaxVoiceOptionVO[]
  dashScopeFavoritesReady: boolean
  providers: Array<{ label: string; value: string }>
  mimoSavedOptions: Array<{ label: string; value: number }>
  mimoPresetOptions: Array<{ label: string; value: string }>
}>()
const emit = defineEmits([
  'update:modelValue',
  'changed',
  'manage-bgm',
  'manage-voices',
  'favorite-change'
])
const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})
const text = (zh: string, en: string) => (props.isEn ? en : zh)
const message = useMessage()
const activeCode = ref('SILVER')
const saving = ref(false)
const slot = ref<AudioAudiencePresetVO>()
const activeLabel = computed(() => {
  const item = audienceCategories.find((item) => item.code === activeCode.value)
  return props.isEn ? item?.en : item?.zh
})
const draft = reactive(presetToForm({}))
let audio: HTMLAudioElement | undefined
const stop = () => {
  audio?.pause()
  audio = undefined
}
const openSlot = (code: string) => {
  stop()
  activeCode.value = code
  const saved = props.presets.find((item) => item.audienceCode === code)
  slot.value = saved ? { ...saved } : undefined
  Object.assign(draft, presetToForm(props.presets.find((item) => item.audienceCode === code) || {}))
}
watch(
  () => props.modelValue,
  (value) => {
    if (value) openSlot(props.initialCode || activeCode.value)
    else stop()
  }
)
const statusLabel = (code: string) => {
  const status = props.presets.find((item) => item.audienceCode === code)?.configStatus
  return status === 'READY'
    ? text('已配置', 'Ready')
    : status === 'INCOMPLETE'
      ? text('需配置', 'Incomplete')
      : text('未配置', 'Not configured')
}
const voicePreviewing = ref(false)
let voiceAudio: HTMLAudioElement | undefined
let voiceUrl: string | undefined
let voicePreviewRevision = 0
const stopVoice = () => {
  voicePreviewRevision++
  voicePreviewing.value = false
  voiceAudio?.pause()
  voiceAudio = undefined
  if (voiceUrl) URL.revokeObjectURL(voiceUrl)
  voiceUrl = undefined
}
const previewVoice = async () => {
  if (voicePreviewing.value) return
  stopVoice()
  voicePreviewing.value = true
  const revision = voicePreviewRevision
  try {
    const blob = await TkGenerationApi.previewVoice({
      ...formVoicePayload(draft),
      targetLanguage: props.targetLanguage
    })
    if (revision !== voicePreviewRevision || !props.modelValue) return
    voiceUrl = URL.createObjectURL(blob)
    voiceAudio = new Audio(voiceUrl)
    voiceAudio.onended = stopVoice
    await voiceAudio.play()
  } finally {
    if (revision === voicePreviewRevision) voicePreviewing.value = false
  }
}
watch(
  () => [
    props.modelValue,
    activeCode.value,
    props.targetLanguage,
    draft.ttsProvider,
    draft.voiceCode,
    draft.mimoVoiceProfileId,
    draft.mimoVoiceMode,
    draft.mimoVoiceCode,
    draft.mimoVoicePrompt,
    draft.mimoVoiceSampleUrl
  ],
  stopVoice,
  { flush: 'sync' }
)
onBeforeUnmount(stopVoice)
const changeProvider = () => {
  draft.voiceCode = ''
  draft.mimoVoiceProfileId = undefined
}
const favoriteChanged = (voiceId: string, favorite: boolean) =>
  emit('favorite-change', draft.ttsProvider, voiceId, favorite)
const previewUrl = computed(
  () => props.bgmAssets.find((item) => item.id === draft.bgmAssetId)?.fileUrl
)
const preview = async () => {
  stop()
  if (!previewUrl.value) return
  audio = new Audio(previewUrl.value)
  audio.volume = draft.bgmVolume
  try {
    await audio.play()
  } catch {
    message.error(text('试听失败', 'Preview failed'))
  }
}
watch(
  () => draft.bgmVolume,
  (value) => {
    if (audio) audio.volume = value
  }
)
watch(() => [draft.bgmAssetId, draft.bgmEnabled], stop)
const save = async () => {
  if (saving.value) return
  if (draft.bgmEnabled && !props.bgmAssets.some((item) => item.id === draft.bgmAssetId)) {
    message.warning(text('请选择可用音乐', 'Select available music'))
    return
  }
  const request = presetSavePayload(slot.value || { audienceCode: activeCode.value }, draft)
  saving.value = true
  try {
    await AudioAudiencePresetApi.save(request)
    emit('changed')
    visible.value = false
    message.success(text('预设已保存', 'Preset saved'))
  } finally {
    saving.value = false
  }
}
const reset = async () => {
  if (!slot.value?.id || slot.value.version == null || saving.value) return
  try {
    await message.confirm(
      text('重置此预设？已创建任务不受影响。', 'Reset this preset? Existing tasks are unaffected.')
    )
  } catch {
    return
  }
  saving.value = true
  try {
    await AudioAudiencePresetApi.reset(slot.value.id, slot.value.version)
    emit('changed')
    visible.value = false
  } finally {
    saving.value = false
  }
}
onBeforeUnmount(stop)
</script>
<style scoped>
.preset-layout {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 24px;
}

nav {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

nav .el-button {
  height: auto;
  min-height: 38px;
  margin: 0;
  white-space: normal;
  justify-content: flex-start;
}

.preset-editor {
  min-width: 0;
}

.preset-editor :deep(.el-select) {
  flex: 1;
}

small {
  display: block;
  margin-top: 16px;
  color: var(--el-text-color-secondary);
}

@media (width <= 700px) {
  .preset-layout {
    grid-template-columns: 1fr;
  }

  nav {
    max-height: 180px;
    overflow: auto;
  }
}
</style>
