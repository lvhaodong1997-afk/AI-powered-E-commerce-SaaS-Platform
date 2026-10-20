<template>
  <div class="minimax-voice-selector">
    <el-radio-group
      v-model="filter.favoritesOnly"
      :disabled="disabled"
      :aria-label="tt('voice.favoriteFilter')"
    >
      <el-radio-button :value="false">{{ tt('voice.all') }}</el-radio-button>
      <el-radio-button :value="true"
        >{{ tt('voice.myFavorites') }} ({{ favoriteCount }})</el-radio-button
      >
    </el-radio-group>
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
        :class="{
          selected: modelValue === option.voiceId,
          disabled: disabled || option.unavailable
        }"
        role="radio"
        :aria-checked="modelValue === option.voiceId"
        :aria-disabled="disabled || option.unavailable"
        :tabindex="disabled || option.unavailable ? -1 : 0"
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
          <small v-if="option.unavailable">{{ tt('voice.unavailableFavorite') }}</small>
        </span>
        <span class="minimax-voice-actions" @click.stop @keydown.stop>
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
              :disabled="disabled || option.unavailable || !option.previewUrl"
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
              :loading="favoriteState.pending === option.voiceId"
              :aria-pressed="option.favorite"
              :disabled="
                disabled ||
                favoritesDisabled ||
                loading ||
                Boolean(favoriteState.pending) ||
                (option.favoriteDisabled && !option.favorite)
              "
              @click="toggleFavorite(option)"
            >
              <Icon :icon="option.favorite ? 'ep:star-filled' : 'ep:star'" />
            </el-button>
          </el-tooltip>
        </span>
      </div>
      <el-empty
        v-if="!loading && !filteredOptions.length"
        :description="tt(getVoiceEmptyState(options, filter))"
        :image-size="56"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  TkMiniMaxVoiceApi,
  TkVoiceFavoriteApi,
  type TkMiniMaxVoiceOptionVO,
  type TkFavoriteVoiceProvider
} from '@/api/tk/voice'
import { filterVoiceOptions, getVoiceEmptyState, persistVoiceFavorite } from '../voiceFavorites.mjs'
import { useTkI18n } from '@/hooks/web/useTkI18n'

const props = defineProps<{
  modelValue: string
  provider?: TkFavoriteVoiceProvider
  options: TkMiniMaxVoiceOptionVO[]
  loading?: boolean
  disabled?: boolean
  favoritesDisabled?: boolean
  isEn?: boolean
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: string): void
  (event: 'favorite-change', voiceId: string, favorite: boolean): void
}>()

const filter = reactive({ name: '', language: '', country: '', gender: '', favoritesOnly: false })
const { tt } = useTkI18n()
const currentAudio = ref<HTMLAudioElement>()
const playingVoiceId = ref('')
const favoriteState = reactive({ pending: '' })
const favoriteCount = computed(() => props.options.filter((option) => option.favorite).length)

const uniqueValues = (field: 'language' | 'country' | 'gender') =>
  computed(() => [...new Set(props.options.map((option) => option[field]).filter(Boolean))])

const languageOptions = uniqueValues('language')
const countryOptions = uniqueValues('country')
const genderOptions = uniqueValues('gender')
const filteredOptions = computed(() => filterVoiceOptions(props.options, filter))

const selectVoice = (voiceId: string) => {
  if (!props.disabled && !props.options.find((option) => option.voiceId === voiceId)?.unavailable) {
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
  if (
    props.disabled ||
    props.favoritesDisabled ||
    props.loading ||
    (option.favoriteDisabled && !option.favorite)
  )
    return
  try {
    await persistVoiceFavorite(
      favoriteState,
      option,
      async (voiceId: string, favorite: boolean) => {
        if (!props.provider || props.provider === 'MINIMAX') {
          await (favorite
            ? TkMiniMaxVoiceApi.favorite(voiceId)
            : TkMiniMaxVoiceApi.unfavorite(voiceId))
        } else {
          await (favorite
            ? TkVoiceFavoriteApi.favorite(props.provider, voiceId)
            : TkVoiceFavoriteApi.unfavorite(props.provider, voiceId))
        }
      },
      (voiceId: string, favorite: boolean) => emit('favorite-change', voiceId, favorite)
    )
  } catch {
    ElMessage.error(tt('voice.favoriteFailed'))
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
