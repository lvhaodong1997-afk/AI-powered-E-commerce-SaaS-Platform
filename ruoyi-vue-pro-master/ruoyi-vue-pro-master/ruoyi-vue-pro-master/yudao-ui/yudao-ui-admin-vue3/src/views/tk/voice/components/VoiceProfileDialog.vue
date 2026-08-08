<template>
  <el-dialog v-model="visible" title="我的音色" width="980px" destroy-on-close>
    <div class="voice-create">
      <el-input v-model="name" maxlength="30" placeholder="输入音色名称" />
      <el-upload
        ref="uploadRef"
        :auto-upload="false"
        :limit="1"
        accept=".mp3,.wav,.m4a,.mp4,.mov,.webm"
        :on-change="handleFileChange"
        :on-remove="handleFileRemove"
      >
        <el-button><Icon icon="ep:upload" />选择音频/视频</el-button>
      </el-upload>
      <el-button type="primary" :loading="creating" :disabled="!canCreate" @click="handleCreate">
        创建音色
      </el-button>
    </div>
    <el-checkbox v-model="consent" class="consent">
      我已获得说话人或权利人的明确授权
    </el-checkbox>
    <div class="sample-rules">
      支持 MP3、WAV、M4A、MP4、MOV、WebM；音频最大 20MB，视频最大 100MB；视频会自动提取连续说话音频。
    </div>

    <div class="voice-toolbar">
      <span>已选择 {{ selectedIds.length }} 个音色</span>
      <div class="voice-toolbar-actions">
        <el-button :disabled="!selectedIds.length" @click="handleBatchEnabled(true)">批量启用</el-button>
        <el-button :disabled="!selectedIds.length" @click="handleBatchEnabled(false)">批量停用</el-button>
        <el-button type="danger" plain :disabled="!selectedIds.length" @click="handleBatchDelete">
          批量删除
        </el-button>
      </div>
    </div>

    <el-table
      v-loading="loading"
      :data="profiles"
      class="voice-table"
      empty-text="暂无自定义音色"
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="44" />
      <el-table-column prop="name" label="名称" min-width="150" />
      <el-table-column label="来源" width="150">
        <template #default="scope">
          <el-tag>{{ providerText(scope.row) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="标签" min-width="210">
        <template #default="scope">
          <div class="tags-cell">
            <el-input
              v-model="tagDrafts[scope.row.id]"
              size="small"
              maxlength="255"
              placeholder="例如：女声,促销,英文"
              @keyup.enter="handleSaveTags(scope.row)"
            />
            <el-button size="small" text @click="handleSaveTags(scope.row)">保存</el-button>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="scope">
          <el-tag :type="statusType(scope.row.status)">{{ statusText(scope.row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="启用" width="76">
        <template #default="scope">
          <el-switch
            :model-value="scope.row.enabled"
            :disabled="scope.row.status !== 'READY'"
            @change="(value) => handleEnabled(scope.row, Boolean(value))"
          />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="170" align="right">
        <template #default="scope">
          <el-tooltip content="试听">
            <el-button
              circle
              :disabled="scope.row.status !== 'READY' || !scope.row.previewFileUrl"
              @click="playPreview(scope.row)"
            ><Icon icon="ep:video-play" /></el-button>
          </el-tooltip>
          <el-tooltip content="重试">
            <el-button
              circle
              :disabled="scope.row.provider === 'MIMO' || scope.row.status !== 'FAILED'"
              @click="handleRetry(scope.row)"
            >
              <Icon icon="ep:refresh" />
            </el-button>
          </el-tooltip>
          <el-tooltip content="删除">
            <el-button circle type="danger" plain @click="handleDelete(scope.row)">
              <Icon icon="ep:delete" />
            </el-button>
          </el-tooltip>
        </template>
      </el-table-column>
    </el-table>
    <el-alert
      v-if="failedMessage"
      :title="failedMessage"
      type="error"
      :closable="false"
      show-icon
      class="failure"
    />
  </el-dialog>
</template>

<script setup lang="ts">
import { TkVoiceProfileApi, type TkVoiceProfileVO } from '@/api/tk/voice'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ (e: 'update:modelValue', value: boolean): void; (e: 'changed'): void }>()
const message = useMessage()
const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})
const profiles = ref<TkVoiceProfileVO[]>([])
const selectedRows = ref<TkVoiceProfileVO[]>([])
const selectedIds = computed(() => selectedRows.value.map((item) => item.id))
const tagDrafts = reactive<Record<number, string>>({})
const loading = ref(false)
const creating = ref(false)
const name = ref('')
const consent = ref(false)
const file = ref<File>()
const uploadRef = ref()
const previewAudio = ref<HTMLAudioElement>()
const canCreate = computed(() => Boolean(name.value.trim() && consent.value && file.value))
const failedMessage = computed(() => profiles.value.find((item) => item.status === 'FAILED')?.errorMessage)

const syncTagDrafts = () => {
  profiles.value.forEach((item) => {
    tagDrafts[item.id] = item.tags || ''
  })
}

const load = async () => {
  loading.value = true
  try {
    profiles.value = await TkVoiceProfileApi.getList()
    syncTagDrafts()
  } finally {
    loading.value = false
  }
}
watch(visible, (value) => value && load())

const handleSelectionChange = (rows: TkVoiceProfileVO[]) => {
  selectedRows.value = rows
}
const handleFileChange = (uploadFile: any) => { file.value = uploadFile.raw }
const handleFileRemove = () => { file.value = undefined }
const handleCreate = async () => {
  if (!canCreate.value || !file.value) return
  creating.value = true
  try {
    await TkVoiceProfileApi.create(name.value.trim(), consent.value, file.value)
    name.value = ''
    consent.value = false
    file.value = undefined
    uploadRef.value?.clearFiles?.()
    message.success('音色创建请求已完成')
    await load()
    emit('changed')
  } finally {
    creating.value = false
  }
}
const handleRetry = async (profile: TkVoiceProfileVO) => {
  await TkVoiceProfileApi.retry(profile.id)
  await load()
  emit('changed')
}
const handleEnabled = async (profile: TkVoiceProfileVO, enabled: boolean) => {
  await TkVoiceProfileApi.updateEnabled(profile.id, enabled)
  await load()
  emit('changed')
}
const handleBatchEnabled = async (enabled: boolean) => {
  if (!selectedIds.value.length) return
  await TkVoiceProfileApi.batchUpdateEnabled(selectedIds.value, enabled)
  await load()
  emit('changed')
}
const handleSaveTags = async (profile: TkVoiceProfileVO) => {
  await TkVoiceProfileApi.updateTags(profile.id, tagDrafts[profile.id])
  await load()
  emit('changed')
}
const handleDelete = async (profile: TkVoiceProfileVO) => {
  await message.delConfirm(`确认删除音色“${profile.name}”吗？`)
  await TkVoiceProfileApi.delete(profile.id)
  await load()
  emit('changed')
}
const handleBatchDelete = async () => {
  if (!selectedIds.value.length) return
  await message.delConfirm(`确认删除选中的 ${selectedIds.value.length} 个音色吗？`)
  await TkVoiceProfileApi.batchDelete(selectedIds.value)
  selectedRows.value = []
  await load()
  emit('changed')
}
const playPreview = (profile: TkVoiceProfileVO) => {
  previewAudio.value?.pause()
  previewAudio.value = new Audio(profile.previewFileUrl || '')
  previewAudio.value.play()
}
const providerText = (profile: TkVoiceProfileVO) => {
  if (profile.provider === 'MIMO') {
    return profile.mimoVoiceMode === 'VOICE_CLONE' ? 'MiMo 克隆' : 'MiMo 设计'
  }
  return 'DashScope 复刻'
}
const statusText = (status: string) => ({ CLONING: '复刻中', READY: '可使用', FAILED: '失败', DISABLED: '已停用' }[status] || status)
const statusType = (status: string) => status === 'READY' ? 'success' : status === 'FAILED' ? 'danger' : 'info'
onBeforeUnmount(() => previewAudio.value?.pause())
</script>

<style scoped>
.voice-create { display: grid; grid-template-columns: minmax(180px, 1fr) auto auto; gap: 10px; align-items: center; }
.consent { margin-top: 14px; }
.sample-rules { margin-top: 4px; color: var(--el-text-color-secondary); font-size: 12px; }
.voice-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-top: 18px; }
.voice-toolbar span { color: var(--el-text-color-secondary); font-size: 13px; }
.voice-toolbar-actions { display: flex; align-items: center; gap: 8px; }
.voice-table { margin-top: 10px; }
.tags-cell { display: grid; grid-template-columns: minmax(120px, 1fr) auto; align-items: center; gap: 6px; }
.failure { margin-top: 12px; }
@media (max-width: 720px) {
  .voice-create { grid-template-columns: 1fr; }
  .voice-toolbar { align-items: stretch; flex-direction: column; }
  .voice-toolbar-actions { flex-wrap: wrap; }
}
</style>
