<template>
  <el-dialog v-model="visible" :title="text('管理 BGM', 'Manage BGM')" width="820px" @closed="stop">
    <el-upload
      :auto-upload="false"
      :show-file-list="false"
      accept=".mp3,.wav,.m4a"
      :on-change="upload"
      ><el-button type="primary" :loading="uploading">{{
        text('上传 BGM', 'Upload BGM')
      }}</el-button></el-upload
    >
    <el-tabs v-model="tab"
      ><el-tab-pane :label="text('我的上传', 'My uploads')" name="USER" /><el-tab-pane
        :label="text('系统曲库', 'System music')"
        name="SYSTEM"
    /></el-tabs>
    <el-table :data="assets.filter((item) => item.sourceType === tab)" v-loading="pending">
      <el-table-column prop="name" :label="text('音乐名称', 'Music')" min-width="180" />
      <el-table-column prop="format" :label="text('格式', 'Format')" width="80" />
      <el-table-column :label="text('使用情况', 'Usage')" min-width="130"
        ><template #default="{ row }">{{
          row.referencedPresetCount
            ? `${row.referencedPresetCount} ${text('个人群预设使用中', 'preset references')}`
            : text('未使用', 'Unused')
        }}</template></el-table-column
      >
      <el-table-column :label="text('操作', 'Actions')" width="175"
        ><template #default="{ row }">
          <el-button text :disabled="!row.fileUrl" @click="preview(row)">{{
            text('试听', 'Preview')
          }}</el-button>
          <el-button
            v-if="row.canDelete"
            type="danger"
            text
            :disabled="pending"
            @click="remove(row)"
            >{{ text('删除', 'Delete') }}</el-button
          >
        </template></el-table-column
      >
    </el-table>
    <el-alert
      :closable="false"
      type="info"
      :title="
        text(
          '仅可删除自己上传的音乐。删除被引用的曲目会使对应预设需要重新配置，已创建任务保留音频快照。',
          'Only your own uploads can be deleted. Referencing presets will need reconfiguration; existing tasks retain their audio snapshots.'
        )
      "
    />
  </el-dialog>
</template>
<script setup lang="ts">
import { TkBgmAssetApi, type TkBgmAssetVO } from '@/api/tk/bgm'
import { audienceCategories } from '../audioSettings.mjs'
const props = defineProps<{
  modelValue: boolean
  isEn: boolean
  assets: TkBgmAssetVO[]
  volume: number
}>()
const emit = defineEmits(['update:modelValue', 'changed', 'deleted'])
const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})
const text = (zh: string, en: string) => (props.isEn ? en : zh)
const message = useMessage()
const tab = ref('USER'),
  pending = ref(false),
  uploading = ref(false)
let audio: HTMLAudioElement | undefined
const stop = () => {
  audio?.pause()
  audio = undefined
}
const preview = async (row: TkBgmAssetVO) => {
  stop()
  audio = new Audio(row.fileUrl)
  audio.volume = Math.max(0, Math.min(1, props.volume))
  try {
    await audio.play()
  } catch {
    message.error(text('试听失败', 'Preview failed'))
  }
}
const remove = async (row: TkBgmAssetVO) => {
  if (!row.canDelete || pending.value) return
  pending.value = true
  try {
    const references = await TkBgmAssetApi.references(row.id)
    const names = references
      .map((item) => {
        const category = audienceCategories.find((category) => category.code === item.audienceCode)
        return props.isEn ? category?.en : category?.zh
      })
      .filter(Boolean)
      .join('、')
    try {
      await message.confirm(
        references.length
          ? text(
              `删除后以下预设将失效，需重新配置：${names}。确认解绑并删除？`,
              `These presets will need reconfiguration: ${names}. Unbind and delete?`
            )
          : text(`删除“${row.name}”？`, `Delete “${row.name}”?`)
      )
    } catch {
      return
    }
    await TkBgmAssetApi.delete(row.id, references.length > 0)
    stop()
    emit('deleted', row.id)
    emit('changed')
  } finally {
    pending.value = false
  }
}
const upload = async (file: { raw?: File }) => {
  const raw = file.raw
  if (!raw || uploading.value) return
  if (!/\.(mp3|wav|m4a)$/i.test(raw.name) || raw.size > 20 * 1024 * 1024) {
    message.warning(text('支持 MP3/WAV/M4A，最大 20MB', 'MP3/WAV/M4A, max 20MB'))
    return
  }
  uploading.value = true
  try {
    await TkBgmAssetApi.upload(raw.name, undefined, raw)
    emit('changed')
    message.success(text('上传成功', 'Uploaded'))
  } finally {
    uploading.value = false
  }
}
watch(
  () => props.volume,
  (volume) => {
    if (audio) audio.volume = Math.max(0, Math.min(1, volume))
  }
)
onBeforeUnmount(stop)
</script>
