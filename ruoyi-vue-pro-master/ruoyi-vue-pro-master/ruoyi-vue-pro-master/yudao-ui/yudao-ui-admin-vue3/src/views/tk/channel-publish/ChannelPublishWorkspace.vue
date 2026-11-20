<template>
  <main v-loading="loading" class="channel-workspace">
    <header class="channel-heading">
      <Icon :icon="channel === 'x' ? 'simple-icons:x' : 'simple-icons:youtube'" :size="22" />
      <h1>{{ label }} 发布</h1>
      <el-tag v-if="!loading" :type="enabled ? 'success' : 'info'">{{ enabled ? '已启用' : '未启用' }}</el-tag>
      <TenantVisit v-if="isPlatformAdmin" />
    </header>
    <el-alert v-if="needsTenant" type="warning" title="请选择要发布的租户" :closable="false" class="tenant-notice" />
    <el-alert v-if="loadError" type="error" :title="loadError" :closable="false" />
    <section v-if="!needsTenant" class="channel-section" aria-labelledby="channel-create">
      <div class="section-heading">
        <h2 id="channel-create">新建发布</h2>
        <el-button v-if="canAuthorize" :disabled="!enabled" :loading="authorizing" @click="authorize">
          <Icon icon="ep:link" class="mr-1" />绑定{{ isYoutube ? '频道' : '账号' }}
        </el-button>
      </div>
      <el-form label-position="top" :disabled="!enabled || submitting" @submit.prevent="submit">
        <el-form-item :label="isYoutube ? 'YouTube 频道' : 'X 账号'" required>
          <div class="inline-fields">
            <el-select v-model="form.accountId" clearable filterable :placeholder="accountPlaceholder" class="account-select">
              <el-option v-for="account in accounts" :key="account.id" :value="account.id" :label="accountLabel(account)" />
            </el-select>
            <el-button v-if="selectedAccount && canAuthorize" @click="disconnect">解绑</el-button>
          </div>
        </el-form-item>
        <el-form-item label="视频成品" required>
          <div class="video-field">
            <div class="inline-fields">
              <el-tooltip v-if="canPublish" content="当前环境未启用本地上传" :disabled="localUpload || !enabled">
                <span>
                  <el-button :disabled="!localUpload || uploading" :loading="uploading" @click="fileInput?.click()">
                    <Icon icon="ep:upload" class="mr-1" />上传本地视频
                  </el-button>
                </span>
              </el-tooltip>
              <input ref="fileInput" type="file" accept=".mp4,video/mp4" hidden @change="uploadFile" />
              <MetaGeneratedPicker :disabled="!enabled || submitting || uploading" @select="selectVideo" />
              <el-button v-if="video || uploadedVideo" link :disabled="uploading" @click="clearVideo">移除</el-button>
            </div>
            <el-progress v-if="uploading" :percentage="uploadPercent" aria-label="视频上传进度" />
            <div v-if="video || uploadedVideo" class="inline-fields">
              <span class="selected-title">{{ uploadedVideo?.fileName || video?.title || video?.id }}</span>
              <el-tag v-if="uploadedVideo" :type="uploadReady ? 'success' : uploadError ? 'danger' : 'info'">
                {{ uploadReady ? '检测通过' : uploadError ? '未就绪' : '检测中' }}
              </el-tag>
            </div>
            <div v-if="uploadError" class="inline-fields" role="status">
              <span class="upload-error">{{ uploadError }}</span>
              <el-button v-if="canRefreshUpload" link type="primary" @click="refreshUpload">刷新检测状态</el-button>
            </div>
            <video v-if="previewUrl" :key="previewUrl" :src="previewUrl" controls playsinline preload="metadata" class="channel-preview" />
          </div>
        </el-form-item>
        <el-form-item v-if="isYoutube" label="标题" required>
          <el-input v-model="form.title" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item :label="isYoutube ? '描述' : '帖子正文'">
          <el-input v-model="form.text" type="textarea" :rows="3" :maxlength="isYoutube ? 5000 : 280" show-word-limit />
        </el-form-item>
        <template v-if="isYoutube">
          <el-form-item label="可见性" required>
            <el-radio-group v-model="form.privacy">
              <el-radio value="private">私密</el-radio>
              <el-radio value="unlisted">不公开</el-radio>
              <el-radio value="public">公开</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="是否面向儿童" required>
            <el-radio-group v-model="form.forKids"><el-radio :value="true">是</el-radio><el-radio :value="false">否</el-radio></el-radio-group>
          </el-form-item>
          <el-form-item label="是否包含需要披露的逼真合成或显著修改内容" required>
            <el-radio-group v-model="form.synthetic"><el-radio :value="true">是</el-radio><el-radio :value="false">否</el-radio></el-radio-group>
          </el-form-item>
        </template>
        <el-button v-if="canPublish" type="primary" native-type="submit" :loading="submitting" :disabled="!readyToSubmit">
          <Icon icon="ep:promotion" class="mr-1" />确认发布
        </el-button>
      </el-form>
    </section>
    <section v-if="!needsTenant" class="channel-section" aria-labelledby="channel-history">
      <div class="section-heading">
        <h2 id="channel-history">发布记录</h2>
        <el-tooltip content="刷新记录">
          <el-button aria-label="刷新记录" :loading="historyLoading" @click="loadHistory">
            <Icon icon="ep:refresh" />
          </el-button>
        </el-tooltip>
      </div>
      <el-table v-loading="historyLoading" :data="records" empty-text="暂无发布记录">
        <el-table-column prop="title" label="视频" min-width="150" show-overflow-tooltip />
        <el-table-column prop="accountName" label="账号" min-width="110" show-overflow-tooltip />
        <el-table-column label="状态" min-width="120">
          <template #default="{ row }">
            <el-tag :type="row.status === 'SUCCESS' ? 'success' : row.status === 'FAILED' ? 'danger' : row.status === 'UNKNOWN' ? 'warning' : 'info'">
              {{ recordState(row) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="160">
          <template #default="{ row }">{{ row.createTime ? formatDate(new Date(row.createTime)) : '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" min-width="110">
          <template #default="{ row }">
            <el-link v-if="row.url" :href="row.url" target="_blank" rel="noopener noreferrer" type="primary">查看发布</el-link>
            <el-button v-else-if="row.status === 'REAUTH_REQUIRED' && canPublish && enabled" link type="primary" :loading="resuming === row.id" @click="resume(row.id)">继续发布</el-button>
            <span v-else-if="row.status === 'UNKNOWN'">平台内核实</span>
          </template>
        </el-table-column>
      </el-table>
      <Pagination v-if="total" :total="total" v-model:page="query.pageNo" v-model:limit="query.pageSize" @pagination="loadHistory" />
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { hasPermission } from '@/directives/permission/hasPermi'
import TenantVisit from '@/layout/components/TenantVisit/index.vue'
import { useUserStore } from '@/store/modules/user'
import { getVisitTenantId } from '@/utils/auth'
import { formatDate } from '@/utils/formatTime'
import { channelApi, type Channel, type ChannelAccount, type ChannelRecord, type ChannelSubmission, type ChannelVideoUpload } from '@/api/tk/channelPublish'
import { uploadSocialVideoToOss } from '@/api/tk/socialPublish'
import type { TkGenerationTaskVO } from '@/api/tk/generation'
import MetaGeneratedPicker from '../video-publish-center/components/MetaGeneratedPicker.vue'

const props = defineProps<{ channel: Channel }>()
const api = channelApi(props.channel)
const isYoutube = props.channel === 'youtube'
const label = isYoutube ? 'YouTube' : 'X'
const userStore = useUserStore()
const isPlatformAdmin = computed(() => userStore.getUser?.tkUserLevel === 'PLATFORM_ADMIN')
const needsTenant = computed(() => isPlatformAdmin.value && !getVisitTenantId())
const canAuthorize = computed(() => hasPermission(['tk:' + props.channel + '-publish:authorize']))
const canPublish = computed(() => hasPermission(['tk:' + props.channel + '-publish:create']))
const loading = ref(true), loadError = ref(''), enabled = ref(false), submitting = ref(false)
const authorizing = ref(false), historyLoading = ref(false), resuming = ref('')
const accounts = ref<ChannelAccount[]>([]), records = ref<ChannelRecord[]>([]), total = ref(0)
const accountPlaceholder = computed(() => {
  if (!enabled.value || accounts.value.length > 0) return isYoutube ? '选择频道' : '选择账号'
  return isYoutube ? '尚未绑定频道' : '尚未绑定账号'
})
const video = ref<TkGenerationTaskVO>(), query = reactive({ pageNo: 1, pageSize: 10 })
const uploadedVideo = ref<ChannelVideoUpload>(), fileInput = ref<HTMLInputElement>()
const localUpload = ref(false), maxUploadBytes = ref(0), uploading = ref(false), uploadPercent = ref(0)
const localPreview = ref(''), uploadError = ref(''), canRefreshUpload = ref(false)
const previewUrl = computed(() => localPreview.value || video.value?.outputUrl)
const uploadReady = computed(() => uploadedVideo.value?.status === 'READY' && uploadedVideo.value.metadataStatus === 'VERIFIED'
  && !uploadError.value)
const form = reactive({ accountId: '', title: '', text: '', privacy: 'private', forKids: undefined as boolean | undefined, synthetic: undefined as boolean | undefined })
const selectedAccount = computed(() => accounts.value.find((account) => account.id === form.accountId))
const readyToSubmit = computed(() => enabled.value && canPublish.value && !uploading.value && (video.value?.id || uploadReady.value)
  && selectedAccount.value?.status === 'AUTHORIZED'
  && (!isYoutube || (form.title.trim() && form.forKids !== undefined && form.synthetic !== undefined)))
let authTimer: number | undefined, historyTimer: number | undefined, pollBusy = false, stopped = false
let uploadTimer: number | undefined, uploadVersion = 0
let pending: { snapshot: string; key: string } | undefined

const accountLabel = (account: ChannelAccount) => (account.username ? '@' + account.username : account.name)
  + (account.status === 'AUTHORIZED' ? '' : '（需重新授权）')
function recordState(row: ChannelRecord) {
  if (row.status === 'PENDING' || row.status === 'PROCESSING') {
    if (row.stage === 'X_PROCESSING' || row.stage === 'YT_PROCESSING') return '平台处理中'
    if (row.stage === 'X_READY') return '等待发帖'
    return row.stage ? '上传中' : '排队中'
  }
  const states: Record<string, string> = { SUCCESS: '已发布', FAILED: '失败', UNKNOWN: '待核实', REAUTH_REQUIRED: '需要重新授权' }
  return states[row.status] || row.status
}
function selectVideo(task: TkGenerationTaskVO) {
  if (uploading.value || submitting.value) return
  clearVideo()
  video.value = task
  if (isYoutube && !form.title) form.title = (task.title || '').slice(0, 100)
}
function clearVideo() {
  uploadVersion++
  window.clearTimeout(uploadTimer)
  if (localPreview.value) URL.revokeObjectURL(localPreview.value)
  localPreview.value = ''; uploadError.value = ''; canRefreshUpload.value = false
  uploadedVideo.value = undefined; video.value = undefined
}
function checkUpload(result: ChannelVideoUpload) {
  uploadedVideo.value = result
  if (result.status === 'READY' && result.metadataStatus === 'VERIFIED') {
    if (!isYoutube && (result.fileSize > maxUploadBytes.value || result.durationSeconds == null
      || result.durationSeconds < 0.5 || result.durationSeconds > 140)) {
      uploadError.value = 'X 视频须为 0.5 至 140 秒，且不超过 512 MB'
    }
    return true
  }
  if (result.status === 'FAILED' || result.metadataStatus === 'FAILED') {
    uploadError.value = '视频未通过检测，请更换 MP4 文件'
    return true
  }
  return false
}
async function pollUpload(id: string, version: number, deadline: number) {
  if (stopped || version !== uploadVersion) return
  try {
    const result = await api.uploadedVideo(id)
    if (stopped || version !== uploadVersion) return
    if (checkUpload(result)) return
    if (Date.now() < deadline) {
      uploadTimer = window.setTimeout(() => void pollUpload(id, version, deadline), 3000)
      return
    }
    uploadError.value = '视频仍在检测中'
  } catch {
    if (stopped || version !== uploadVersion) return
    uploadError.value = '暂时无法获取检测状态'
  }
  canRefreshUpload.value = true
}
function refreshUpload() {
  if (!uploadedVideo.value) return
  uploadError.value = ''; canRefreshUpload.value = false
  void pollUpload(uploadedVideo.value.id, uploadVersion, Date.now() + 5 * 60 * 1000)
}
async function uploadFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || !localUpload.value || !canPublish.value || needsTenant.value || uploading.value || submitting.value) return
  if (!/\.mp4$/i.test(file.name) || !file.size || file.size > maxUploadBytes.value) {
    ElMessage.warning(`请选择不超过 ${maxUploadBytes.value / (1024 * 1024)} MB 的非空 MP4 视频`)
    return
  }
  clearVideo()
  const version = uploadVersion
  const info = { fileName: file.name, fileSize: file.size, contentType: 'video/mp4' }
  localPreview.value = URL.createObjectURL(file)
  uploading.value = true; uploadPercent.value = 0
  try {
    const session = await api.uploadSession(info)
    if (stopped || version !== uploadVersion) return
    await uploadSocialVideoToOss(session, file, (progress) => {
      if (!stopped && version === uploadVersion) uploadPercent.value = progress
    })
    if (stopped || version !== uploadVersion) return
    const result = await api.uploadComplete({ ...info, uploadId: session.uploadId, objectKey: session.objectKey })
    if (stopped || version !== uploadVersion) return
    if (isYoutube && !form.title) form.title = file.name.replace(/\.mp4$/i, '').slice(0, 100)
    if (!checkUpload(result)) void pollUpload(result.id, version, Date.now() + 5 * 60 * 1000)
  } catch {
    if (stopped || version !== uploadVersion) return
    clearVideo()
    uploadError.value = '视频上传未完成，请重新选择文件上传'
  } finally { uploading.value = false }
}
async function loadAccounts() {
  accounts.value = await api.accounts()
  if (!form.accountId) form.accountId = accounts.value.find((account) => account.status === 'AUTHORIZED')?.id || ''
}
async function loadHistory() {
  if (historyLoading.value || stopped) return
  historyLoading.value = true
  try {
    const result = await api.history({ ...query })
    records.value = result.list; total.value = result.total
  } finally { historyLoading.value = false }
}
function endAuthorization() { window.clearInterval(authTimer); authorizing.value = false }
async function authorize() {
  if (needsTenant.value) return
  const popup = window.open('about:blank', '_blank', 'popup,width=560,height=720')
  if (!popup) { ElMessage.warning('请允许弹出授权窗口'); return }
  popup.opener = null
  authorizing.value = true
  try {
    const session = await api.authorize()
    if (stopped) { popup.close(); return }
    popup.location.replace(session.authorizeUrl)
    const started = Date.now()
    authTimer = window.setInterval(async () => {
      if (pollBusy || stopped) return
      if (Date.now() - started > 11 * 60 * 1000) { endAuthorization(); return }
      pollBusy = true
      try {
        const status = await api.session(session.sessionId)
        if (stopped || status === 'PENDING' || status === 'PROCESSING') return
        endAuthorization()
        if (status === 'SUCCESS') { await loadAccounts(); ElMessage.success('授权成功') }
        else ElMessage.warning('授权未完成，请重新绑定')
      } catch { endAuthorization() }
      finally { pollBusy = false }
    }, 2500)
  } catch { popup.close(); endAuthorization() }
}
async function disconnect() {
  const account = selectedAccount.value
  if (!account) return
  try { await ElMessageBox.confirm('解绑后，此账号的未完成任务将需要重新授权。', '解绑账号', { type: 'warning' }) }
  catch { return }
  await api.disconnect(account.id)
  form.accountId = ''; await loadAccounts()
}
async function submit() {
  if (!readyToSubmit.value || !selectedAccount.value || submitting.value) return
  const source = uploadedVideo.value ? { mediaId: uploadedVideo.value.id }
    : video.value?.id ? { generationTaskId: String(video.value.id) } : undefined
  if (!source) return
  submitting.value = true
  const values = { ...source, accountId: form.accountId, text: form.text,
    ...(isYoutube ? { title: form.title.trim(), privacy: form.privacy, forKids: form.forKids, synthetic: form.synthetic } : {}) }
  const snapshot = JSON.stringify(values)
  try {
    const visibility: Record<string, string> = { private: '私密', unlisted: '不公开', public: '公开' }
    const target = accountLabel(selectedAccount.value) + (isYoutube ? '（' + visibility[form.privacy] + '）' : '')
    try { await ElMessageBox.confirm('确认将此视频发布到 ' + target + '？', '确认发布') }
    catch { return }
    if (pending?.snapshot !== snapshot) pending = { snapshot, key: window.crypto.randomUUID() }
    const request: ChannelSubmission = { ...values, idempotencyKey: pending.key }
    await api.submit(request)
    pending = undefined; clearVideo(); form.title = ''; form.text = ''
    form.forKids = undefined; form.synthetic = undefined; form.privacy = 'private'
    ElMessage.success('发布任务已提交'); query.pageNo = 1; await loadHistory()
  } finally { submitting.value = false }
}
async function resume(id: string) {
  resuming.value = id
  try { await api.resume(id); await loadHistory() }
  finally { resuming.value = '' }
}
onMounted(async () => {
  try {
    const capabilities = await api.capabilities()
    enabled.value = capabilities.enabled; localUpload.value = capabilities.localUpload
    maxUploadBytes.value = capabilities.maxUploadBytes
    if (!needsTenant.value) await Promise.all([loadAccounts(), loadHistory()])
  } catch { loadError.value = '发布数据加载失败，请刷新后重试' }
  finally { loading.value = false }
  if (!stopped) historyTimer = window.setInterval(() => {
    if (records.value.some((record) => ['PENDING', 'PROCESSING'].includes(record.status))) void loadHistory().catch(() => {})
  }, 10000)
})
onBeforeUnmount(() => { stopped = true; clearVideo(); endAuthorization(); window.clearInterval(historyTimer) })
</script>

<style scoped>
.channel-workspace { padding: 20px 24px; background: var(--el-bg-color); }
.channel-heading, .section-heading, .inline-fields { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.channel-heading h1 { margin: 0; font-size: 22px; line-height: 1.4; }
.tenant-notice { margin-top: 16px; }
.section-heading { justify-content: space-between; margin-bottom: 12px; }
.section-heading h2 { margin: 0; font-size: 16px; }
.channel-section { padding: 20px 0; }
.channel-section + .channel-section { border-top: 1px solid var(--el-border-color-light); }
.channel-section .el-form { max-width: 680px; }
.account-select { width: 320px; max-width: 100%; }
.video-field { display: grid; gap: 8px; width: 100%; min-width: 0; }
.selected-title { overflow-wrap: anywhere; }
.upload-error { color: var(--el-color-danger); }
.channel-preview { display: block; flex-basis: 100%; width: min(220px, 100%); max-width: 220px; max-height: 320px; margin-top: 8px; background: var(--el-fill-color-dark); }
@media (max-width: 600px) { .channel-workspace { padding: 14px; } }
</style>
