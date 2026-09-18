<template>
  <div class="minimax-voice-selector">
    <div class="minimax-voice-filters">
      <el-input v-model="filter.name" clearable :placeholder="tt('voice.searchPlaceholder')" />
      <el-select v-model="filter.language" clearable :placeholder="tt('voice.language')">
        <el-option v-for="item in languageOptions" :key="item" :label="item" :value="item" />
      </el-select>
      <el-select v-model="filter.country" clearable :placeholder="tt('voice.country')">
        <el-option v-for="item in countryOptions" :key="item" :label="item" :value="item" />
      </el-select>
      <el-select v-model="filter.gender" clearable :placeholder="tt('voice.gender')">
        <el-option v-for="item in genderOptions" :key="item" :label="item" :value="item" />
      </el-select>
    </div>

    <div v-loading="loading" class="minimax-voice-list">
      <div
        v-for="option in filteredOptions"
        :key="option.voiceId"
        class="minimax-voice-option"
        :class="{ selected: modelValue === option.voiceId, disabled }"
        role="radio"
        :aria-checked="modelValue === option.voiceId"
        :tabindex="disabled ? -1 : 0"
        @click="selectVoice(option.voiceId)"
        @keydown.enter.prevent="selectVoice(option.voiceId)"
        @keydown.space.prevent="selectVoice(option.voiceId)"
      >
        <span class="minimax-voice-main">
          <strong>{{ option.label }}</strong>
          <small>{{
            [option.language, option.country, option.gender].filter(Boolean).join(' · ')
          }}</small>
          <small v-if="option.description">{{ option.description }}</small>
        </span>
        <span class="minimax-voice-actions" @click.stop>
          <el-tooltip
            :content="playingVoiceId === option.voiceId ? tt('voice.stop') : tt('voice.preview')"
          >
            <el-button
              circle
              text
              :aria-label="
                playingVoiceId === option.voiceId
                  ? tt('voice.stopPreview')
                  : tt('voice.previewVoice')
              "
              :disabled="disabled || !option.previewUrl"
              @click="togglePreview(option)"
            >
              <Icon
                :icon="playingVoiceId === option.voiceId ? 'ep:video-pause' : 'ep:video-play'"
              />
            </el-button>
          </el-tooltip>
          <el-tooltip
            :content="option.favorite ? tt('voice.removeFavorite') : tt('voice.favorite')"
          >
            <el-button
              circle
              text
              :aria-label="option.favorite ? tt('voice.removeFavorite') : tt('voice.favoriteVoice')"
              :loading="favoriteVoiceId === option.voiceId"
              :disabled="disabled || Boolean(favoriteVoiceId)"
              @click="toggleFavorite(option)"
            >
              <Icon :icon="option.favorite ? 'ep:star-filled' : 'ep:star'" />
            </el-button>
          </el-tooltip>
        </span>
      </div>
      <el-empty
        v-if="!loading && !filteredOptions.length"
        :description="tt('voice.noMatches')"
        :image-size="56"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { TkMiniMaxVoiceApi, type TkMiniMaxVoiceOptionVO } from '@/api/tk/voice'
import { useTkI18n } from '@/hooks/web/useTkI18n'

const props = defineProps<{
  modelValue: string
  options: TkMiniMaxVoiceOptionVO[]
  loading?: boolean
  disabled?: boolean
  isEn?: boolean
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: string): void
  (event: 'refresh'): void
}>()

const filter = reactive({ name: '', language: '', country: '', gender: '' })
const { tt } = useTkI18n()
const currentAudio = ref<HTMLAudioElement>()
const playingVoiceId = ref('')
const favoriteVoiceId = ref('')

const uniqueValues = (field: 'language' | 'country' | 'gender') =>
  computed(() => [...new Set(props.options.map((option) => option[field]).filter(Boolean))])

const languageOptions = uniqueValues('language')
const countryOptions = uniqueValues('country')
const genderOptions = uniqueValues('gender')
const filteredOptions = computed(() => {
  const keyword = filter.name.trim().toLowerCase()
  return props.options.filter(
    (option) =>
      (!keyword || option.label.toLowerCase().includes(keyword)) &&
      (!filter.language || option.language === filter.language) &&
      (!filter.country || option.country === filter.country) &&
      (!filter.gender || option.gender === filter.gender)
  )
})

const selectVoice = (voiceId: string) => {
  if (!props.disabled) {
    emit('update:modelValue', voiceId)
  }
}

const stopPreview = () => {
  const audio = currentAudio.value
  if (audio) {
    audio.onended = null
    audio.onerror = null
    audio.pause()
  }
  currentAudio.value = undefined
  playingVoiceId.value = ''
}

const togglePreview = async (option: TkMiniMaxVoiceOptionVO) => {
  if (playingVoiceId.value === option.voiceId) {
    stopPreview()
    return
  }
  stopPreview()
  const audio = new Audio(option.previewUrl)
  currentAudio.value = audio
  playingVoiceId.value = option.voiceId
  audio.onended = () => {
    if (currentAudio.value !== audio) {
      return
    }
    stopPreview()
  }
  audio.onerror = () => {
    if (currentAudio.value !== audio) {
      return
    }
    stopPreview()
    ElMessage.error(tt('voice.previewFailed'))
  }
  try {
    await audio.play()
  } catch {
    if (currentAudio.value !== audio) {
      return
    }
    stopPreview()
    ElMessage.error(tt('voice.previewFailed'))
  }
}

const toggleFavorite = async (option: TkMiniMaxVoiceOptionVO) => {
  if (favoriteVoiceId.value) {
    return
  }
  favoriteVoiceId.value = option.voiceId
  try {
    if (option.favorite) {
      await TkMiniMaxVoiceApi.unfavorite(option.voiceId)
    } else {
      await TkMiniMaxVoiceApi.favorite(option.voiceId)
    }
    emit('refresh')
  } catch {
    ElMessage.error(tt('voice.favoriteFailed'))
  } finally {
    favoriteVoiceId.value = ''
  }
}

watch(
  () => props.disabled,
  (disabled) => disabled && stopPreview()
)
onBeforeUnmount(stopPreview)
</script>

<style scoped>
.minimax-voice-selector {
  display: grid;
  min-width: 0;
  gap: 10px;
}

.minimax-voice-filters {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.minimax-voice-list {
  display: grid;
  height: clamp(180px, 30vh, 280px);
  min-width: 0;
  overflow: hidden auto;
  border: 1px solid var(--el-border-color-light);
}

.minimax-voice-option {
  display: flex;
  min-height: 54px;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 7px 10px;
  color: var(--el-text-color-primary);
  text-align: left;
  cursor: pointer;
  background: var(--el-bg-color);
  border: 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.minimax-voice-option:last-child {
  border-bottom: 0;
}

.minimax-voice-option.selected {
  background: var(--el-color-primary-light-9);
  box-shadow: inset 3px 0 var(--el-color-primary);
}

.minimax-voice-option.disabled {
  cursor: not-allowed;
  opacity: 0.65;
}

.minimax-voice-main {
  display: grid;
  flex: 1 1 auto;
  min-width: 0;
  gap: 3px;
}

.minimax-voice-main small {
  overflow: hidden;
  color: var(--el-text-color-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.minimax-voice-actions {
  display: flex;
  flex: 0 0 56px;
  justify-content: flex-end;
}

.minimax-voice-actions :deep(.el-button) {
  width: 28px;
  height: 28px;
  margin-left: 4px;
}

@media (width <= 720px) {
  .minimax-voice-filters {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
