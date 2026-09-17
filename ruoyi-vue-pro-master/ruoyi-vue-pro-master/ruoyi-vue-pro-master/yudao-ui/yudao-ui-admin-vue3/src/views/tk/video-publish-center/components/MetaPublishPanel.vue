<template>
  <section v-loading="state.loading" class="meta-panel" aria-label="Meta 社交发布">
    <ContentWrap>
      <div class="section-head">
        <div><h3>Instagram / Facebook 发布</h3><p>上传视频或选择系统成片，为每个平台设置独立文案。</p></div>
        <el-button :disabled="state.loading" @click="reload">刷新配置</el-button>
      </div>
      <el-alert v-if="state.loadError" :title="state.loadError" type="error" :closable="false" />
      <el-alert v-else-if="state.initialized && !state.enabled" title="Meta 发布配置待完成" description="管理员启用并完成平台配置后，可在此连接账号和发布视频。" type="info" :closable="false" />
      <template v-if="state.enabled">
        <p class="hint">可用平台：{{ state.platforms.map(platformLabel).join('、') || '暂无' }}；支持媒体：{{ state.mediaTypes.map(type => type === 'VIDEO' ? 'MP4 视频' : 'JPEG 图片').join('、') || '暂无' }}</p>
        <el-alert v-if="!state.mediaTypes.includes('VIDEO')" title="当前服务端尚不支持视频，请完成视频发布配置后重试。" type="warning" :closable="false" />
      </template>
    </ContentWrap>

    <template v-if="state.enabled">
      <ContentWrap>
        <div class="section-head">
          <h3>平台账号</h3>
          <div class="actions">
            <el-button v-if="can('tk:social-account:authorize')" :disabled="state.authBusy || !state.platforms.includes('INSTAGRAM')" @click="run(() => controller.connect('INSTAGRAM'))">连接 Instagram</el-button>
            <el-button v-if="can('tk:social-account:authorize')" :disabled="state.authBusy || !state.platforms.includes('FACEBOOK_PAGE')" @click="run(() => controller.connect('FACEBOOK_PAGE'))">连接 Facebook Page</el-button>
            <el-button v-if="can('tk:social-account:query')" :loading="state.accountsLoading" @click="run(controller.refreshAccounts)">刷新账号</el-button>
          </div>
        </div>
        <div v-if="state.authBusy || state.auth.message" class="auth-status" role="status">
          <span>{{ state.auth.message || authLabel }}</span>
          <el-button v-if="state.authBusy" link @click="controller.cancelAuth">取消等待</el-button>
        </div>
        <el-table v-if="can('tk:social-account:query')" v-loading="state.accountsLoading" :data="accountRows" stripe>
          <el-table-column label="账号" min-width="180"><template #default="{ row }">{{ accountLabel(row) }}</template></el-table-column>
          <el-table-column label="平台" min-width="140"><template #default="{ row }">{{ platformLabel(row.platform) }}</template></el-table-column>
          <el-table-column label="状态" min-width="130"><template #default="{ row }">{{ socialStatusLabel(row.status) }}</template></el-table-column>
          <el-table-column prop="tokenExpiresAt" label="授权到期时间" min-width="170"><template #default="{ row }">{{ row.tokenExpiresAt || '以平台校验结果为准' }}</template></el-table-column>
          <el-table-column prop="lastValidatedAt" label="最近校验" min-width="170" />
          <el-table-column prop="failReason" label="异常说明" min-width="170" show-overflow-tooltip />
          <el-table-column label="操作" min-width="360">
            <template #default="{ row }">
              <el-button v-if="can('tk:social-account:query') && isSocialAccountAuthorized(row.status)" link @click="openInsights(row)">测试数据分析</el-button>
              <el-button v-if="can('tk:social-account:update')" link :disabled="state.busyAccounts.includes(row.id)" @click="accountAction('validate', row)">校验</el-button>
              <el-button v-if="can('tk:social-account:authorize')" link :disabled="state.authBusy" @click="run(() => controller.connect(row.platform))">重新授权</el-button>
              <el-button v-if="can('tk:social-account:update')" link type="warning" :disabled="state.busyAccounts.includes(row.id)" @click="accountAction('unbind', row)">解绑</el-button>
              <el-button v-if="can('tk:social-account:update')" link type="danger" :disabled="state.busyAccounts.includes(row.id)" @click="accountAction('delete', row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination v-if="state.accounts.length > 10" v-model:current-page="accountPage" :page-size="10" :total="state.accounts.length" layout="prev, pager, next" />
        <el-alert v-if="!can('tk:social-account:query')" title="需要账号查询权限才能选择发布目标。" type="info" :closable="false" />
      </ContentWrap>

      <ContentWrap v-if="can('tk:social-publish:create') && can('tk:social-account:query')">
        <h3>新建发布任务</h3>
        <el-form label-position="top" :disabled="state.submitting">
          <el-form-item label="任务标题" required><el-input v-model="state.draft.title" :maxlength="255" show-word-limit placeholder="便于查找的任务名称" /></el-form-item>
          <el-form-item label="发布目标（可多选，每个账号独立执行）" required>
            <el-select v-model="state.draft.accountIds" multiple :multiple-limit="20" filterable class="full-width" placeholder="选择 Instagram / Facebook Page 账号（最多 20 个）">
              <el-option v-for="account in state.accounts" :key="account.id" :value="account.id" :label="`${platformLabel(account.platform)} · ${accountLabel(account)}`" :disabled="!isSocialAccountAuthorized(account.status) || !state.platforms.includes(account.platform)">
                {{ platformLabel(account.platform) }} · {{ accountLabel(account) }} · {{ socialStatusLabel(account.status) }}
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="视频 / 图片">
            <div class="media-box">
              <div class="actions">
                <el-button :loading="state.uploading" :disabled="!state.mediaTypes.length" @click="fileInput?.click()">上传 MP4 / JPEG</el-button>
                <input ref="fileInput" class="file-input" type="file" :accept="acceptedMedia" @change="uploadFile" />
                <MetaGeneratedPicker :disabled="state.uploading || state.submitting || !state.mediaTypes.includes('VIDEO')" :facebook-selected="facebookSelected" @select="selectGenerated" />
                <el-button v-if="state.media || state.draft.generationTaskId" :disabled="state.uploading" @click="controller.clearMedia">移除媒体</el-button>
              </div>
              <p class="hint">MP4 最大 {{ state.maxVideoSizeMb }} MB，JPEG 最大 8 MB。Instagram 必须选择媒体；Facebook 可只填写正文。服务端将进一步校验视频格式和平台限制。</p>
              <el-alert v-if="facebookSelected" title="Facebook 视频将发布为 Reels" description="本发布流程支持 9:16 竖屏、分辨率至少 540×960、帧率 23–60 fps、时长 4–60 秒。系统成片与上传视频都需通过服务端校验。" type="info" :closable="false" />
              <p v-if="instagramSelected" class="hint">Instagram 视频：本发布流程支持 23–60 fps，其他格式要求由服务端进一步校验。</p>
              <el-tag v-if="state.draft.generationTaskId" type="success">系统成片 #{{ state.draft.generationTaskId }}{{ generationTitle ? ` · ${generationTitle}` : '' }}</el-tag>
              <el-tag v-else-if="state.media" type="success">{{ state.media.mediaType === 'VIDEO' ? '视频' : '图片' }}：{{ state.media.fileName }}（上传完成）</el-tag>
              <p v-if="state.media?.mediaType === 'VIDEO'" class="hint">分辨率 {{ state.media.width ?? '待校验' }} × {{ state.media.height ?? '待校验' }} · 时长 {{ state.media.durationSeconds ?? '待校验' }} 秒 · 帧率 {{ state.media.frameRate ?? '待校验' }} fps</p>
            </div>
          </el-form-item>
          <div class="copy-grid">
            <el-form-item label="Instagram 文案"><el-input v-model="state.draft.instagramCaption" type="textarea" :rows="4" :maxlength="2200" show-word-limit placeholder="Instagram caption" /></el-form-item>
            <el-form-item label="Facebook 正文"><el-input v-model="state.draft.facebookMessage" type="textarea" :rows="4" :maxlength="5000" show-word-limit placeholder="Facebook message" /></el-form-item>
          </div>
          <el-button type="primary" :loading="state.submitting" :disabled="state.uploading || !state.draft.accountIds.length" @click="run(controller.submit)">创建异步发布任务</el-button>
          <p v-if="state.lastTaskId" class="hint" role="status">已创建任务 #{{ state.lastTaskId }}。排队或平台处理中时无需重复提交，下方会自动刷新。</p>
        </el-form>
      </ContentWrap>

      <ContentWrap v-if="can('tk:social-publish:query')">
        <div class="section-head"><h3>发布任务</h3><el-button @click="run(controller.refreshTasks)">刷新任务</el-button></div>
        <el-alert v-if="state.pollError" :title="state.pollError" type="warning" :closable="false" />
        <el-select v-model="state.taskQuery.status" clearable placeholder="全部状态" class="status-filter" @change="searchTasks">
          <el-option v-for="status in statuses" :key="status" :value="status" :label="socialStatusLabel(status)" />
        </el-select>
        <el-table :data="state.tasks" stripe>
          <el-table-column prop="id" label="任务 ID" width="100" />
          <el-table-column prop="title" label="标题" min-width="180" />
          <el-table-column label="状态" min-width="140"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ socialStatusLabel(row.status) }}</el-tag></template></el-table-column>
          <el-table-column label="结果" min-width="220"><template #default="{ row }">目标 {{ row.targetCount }} · 成功 {{ row.successCount }} · 失败 {{ row.failedCount }} · 待完成 {{ row.pendingCount }}</template></el-table-column>
          <el-table-column prop="createTime" label="创建时间" min-width="170" />
          <el-table-column label="操作" min-width="180"><template #default="{ row }">
            <el-button link @click="run(() => controller.openDetails(row.id))">查看明细</el-button>
            <el-button link :disabled="state.syncing.includes(row.id)" @click="run(() => controller.sync(row.id))">核对状态</el-button>
          </template></el-table-column>
        </el-table>
        <Pagination :total="state.taskTotal" v-model:page="state.taskQuery.pageNo" v-model:limit="state.taskQuery.pageSize" @pagination="run(controller.refreshTasks)" />
      </ContentWrap>

      <ContentWrap v-if="state.detailTaskId && can('tk:social-publish:query')">
        <div class="section-head"><h3>任务 #{{ state.detailTaskId }} · 发布明细</h3><el-button @click="run(controller.refreshDetails)">刷新明细</el-button></div>
        <el-alert title="结果未知时，请先到 Instagram / Facebook 人工核验是否已发布。此状态不允许重试，避免重复发布。" type="warning" :closable="false" />
        <el-table :data="state.details" stripe>
          <el-table-column label="目标账号" min-width="180"><template #default="{ row }">{{ platformLabel(row.platform) }} · {{ row.accountName || `#${row.socialAccountId}` }}</template></el-table-column>
          <el-table-column label="状态" min-width="150"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ socialStatusLabel(row.status) }}</el-tag></template></el-table-column>
          <el-table-column prop="platformStatus" label="平台状态" min-width="150" />
          <el-table-column label="说明" min-width="230"><template #default="{ row }">{{ row.status === 'UNKNOWN' ? '请在平台人工核验；禁止重复提交。' : row.errorMessage || '-' }}<span v-if="row.errorCode">（{{ row.errorCode }}）</span></template></el-table-column>
          <el-table-column prop="retryCount" label="重试次数" width="95" />
          <el-table-column label="操作" min-width="260"><template #default="{ row }">
            <el-link v-if="safeUrl(row.publishUrl)" :href="safeUrl(row.publishUrl)" target="_blank" rel="noopener noreferrer">查看发布</el-link>
            <el-button v-if="row.status === 'REAUTH_REQUIRED' && can('tk:social-account:authorize')" link :disabled="state.authBusy" @click="run(() => controller.connect(row.platform))">重新授权</el-button>
            <el-button v-if="canRetrySocialDetail(row.status) && can('tk:social-publish:retry')" link type="primary" :disabled="state.retrying.includes(row.id)" @click="retry(row)">{{ row.status === 'REAUTH_REQUIRED' ? '授权后重试' : '重试' }}</el-button>
          </template></el-table-column>
        </el-table>
        <Pagination :total="state.detailTotal" v-model:page="state.detailQuery.pageNo" v-model:limit="state.detailQuery.pageSize" @pagination="run(controller.refreshDetails)" />
      </ContentWrap>
    </template>

    <el-dialog :model-value="state.pagePickerVisible" title="选择当前授权的 Facebook 主页" width="min(650px, 95vw)" :close-on-click-modal="false" :close-on-press-escape="!state.binding" :show-close="!state.binding" @close="controller.cancelAuth">
      <el-empty v-if="!state.pages.length" description="当前授权没有可绑定主页，请检查账号的主页管理权限。" />
      <el-checkbox-group v-model="state.pageIds" :disabled="state.binding" class="page-choices">
        <el-checkbox v-for="page in state.pages" :key="page.id" :value="page.id">{{ page.name }} · {{ page.id }}（{{ page.tasks.join('、') }}）</el-checkbox>
      </el-checkbox-group>
      <template #footer><el-button :disabled="state.binding" @click="controller.cancelAuth">取消</el-button><el-button type="primary" :loading="state.binding" :disabled="!state.pageIds.length" @click="run(controller.bindPages)">绑定所选主页</el-button></template>
    </el-dialog>

    <el-dialog v-model="insightVisible" title="Meta 数据分析测试" width="min(760px, 95vw)" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="账号"><el-input :model-value="insightAccount ? `${platformLabel(insightAccount.platform)} · ${accountLabel(insightAccount)}` : ''" readonly /></el-form-item>
        <div class="copy-grid">
          <el-form-item label="指标"><el-input v-model="insightMetric" maxlength="512" /></el-form-item>
          <el-form-item label="周期"><el-input v-model="insightPeriod" maxlength="512" /></el-form-item>
        </div>
        <el-button type="primary" :loading="insightLoading" @click="run(runInsights)">请求 Insights</el-button>
      </el-form>
      <pre v-if="insightResult" class="insight-result">{{ insightResult }}</pre>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onActivated, onBeforeUnmount, onDeactivated, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { SocialPublishApi, type SocialAccount, type SocialDetail, type SocialStatus } from '@/api/tk/socialPublish'
import type { TkGenerationTaskVO } from '@/api/tk/generation'
import { hasPermission } from '@/directives/permission/hasPermi'
import { accountLabel, canRetrySocialDetail, createMetaPublishController, isSocialAccountAuthorized, platformLabel, socialStatusLabel } from './metaPublishController'
import MetaGeneratedPicker from './MetaGeneratedPicker.vue'

const props = defineProps<{ generationTaskId?: number }>()
const can = (permission: string) => hasPermission([permission])
const controller = createMetaPublishController(SocialPublishApi, can, {
  openPopup: () => window.open('', '_blank', 'popup,width=620,height=780'),
  setTimeout: (fn, delay) => window.setTimeout(fn, delay), clearTimeout: window.clearTimeout,
  now: Date.now, idempotencyKey: () => crypto.randomUUID(), notify: (text) => ElMessage.success(text)
})
const state = controller.state
const fileInput = ref<HTMLInputElement>(), generationTitle = ref(''), accountPage = ref(1)
const insightVisible = ref(false), insightLoading = ref(false), insightAccount = ref<SocialAccount>(), insightMetric = ref(''), insightPeriod = ref('day'), insightResult = ref('')
const accountRows = computed(() => state.accounts.slice((accountPage.value - 1) * 10, accountPage.value * 10))
const facebookSelected = computed(() => state.accounts.some(account => account.platform === 'FACEBOOK_PAGE' && state.draft.accountIds.includes(account.id)))
const instagramSelected = computed(() => state.accounts.some(account => account.platform === 'INSTAGRAM' && state.draft.accountIds.includes(account.id)))
const acceptedMedia = computed(() => [state.mediaTypes.includes('VIDEO') ? '.mp4,video/mp4' : '', state.mediaTypes.includes('IMAGE') ? '.jpg,.jpeg,image/jpeg' : ''].filter(Boolean).join(','))
const authLabel = computed(() => ({ PENDING: '等待授权', PROCESSING: '正在处理授权', PAGES_READY: '请选择主页', SUCCESS: '授权成功', FAILED: '授权失败', EXPIRED: '授权已过期' })[state.auth.status])
const statuses: SocialStatus[] = ['PENDING', 'PROCESSING', 'SUCCESS', 'PARTIAL_SUCCESS', 'FAILED', 'REAUTH_REQUIRED', 'UNKNOWN']
const statusType = (status: string) => status === 'SUCCESS' ? 'success' : status === 'FAILED' ? 'danger' : ['UNKNOWN', 'REAUTH_REQUIRED', 'PARTIAL_SUCCESS'].includes(status) ? 'warning' : 'info'
function safeUrl(value?: string) { try { const url = new URL(value || ''); return ['https:', 'http:'].includes(url.protocol) ? url.href : undefined } catch { return undefined } }
function openInsights(account: SocialAccount) {
  insightAccount.value = account
  insightMetric.value = account.platform === 'INSTAGRAM' ? 'reach' : 'page_impressions'
  insightPeriod.value = 'day'
  insightResult.value = ''
  insightVisible.value = true
}
async function runInsights() {
  if (!insightAccount.value) return
  if (!insightMetric.value.trim() || !insightPeriod.value.trim()) throw new Error('请填写指标和周期')
  insightLoading.value = true
  try {
    const response = await SocialPublishApi.insights(insightAccount.value.id, { metric: insightMetric.value.trim(), period: insightPeriod.value.trim() })
    insightResult.value = JSON.stringify(response, null, 2)
  } finally { insightLoading.value = false }
}
async function run(action: () => Promise<unknown>) {
  try { await action() } catch (reason) {
    if (reason === 'cancel' || reason === 'close') return
    ElMessage.error(reason instanceof Error ? reason.message : '操作失败，请重试')
  }
}
async function uploadFile(event: Event) {
  const input = event.target as HTMLInputElement, file = input.files?.[0]
  if (file) await run(() => controller.upload(file))
  input.value = ''
}
function selectGenerated(task: TkGenerationTaskVO) {
  if (!task.id) return
  void run(async () => { controller.selectGenerated(task.id!); generationTitle.value = task.title || '' })
}
function accountAction(action: 'validate' | 'unbind' | 'delete', account: SocialAccount) {
  void run(async () => {
    if (action !== 'validate') await ElMessageBox.confirm(`确认${action === 'unbind' ? '解绑' : '删除'}账号「${accountLabel(account)}」？`, '账号管理', { type: 'warning' })
    await controller.accountAction(action, account.id)
  })
}
function retry(detail: SocialDetail) {
  void run(async () => {
    if (detail.status === 'REAUTH_REQUIRED') await ElMessageBox.confirm('请先完成此账号的重新授权，再提交重试。确认已授权？', '授权后重试', { type: 'warning' })
    await controller.retry(detail)
  })
}
function searchTasks() { state.taskQuery.pageNo = 1; void run(controller.refreshTasks) }
function reload() { controller.stop(); void controller.start() }
watch(() => [props.generationTaskId, state.enabled] as const, ([id, enabled]) => {
  if (id && enabled && state.mediaTypes.includes('VIDEO') && can('tk:social-publish:create')) void run(async () => controller.selectGenerated(id))
})
onMounted(controller.start)
onActivated(controller.start)
onDeactivated(controller.stop)
onBeforeUnmount(controller.stop)
</script>

<style scoped>
.meta-panel { display: flex; flex-direction: column; gap: 12px; }
.section-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; margin-bottom: 12px; }
h3 { margin: 0 0 12px; font-size: 16px; }
p { margin: 6px 0 12px; }
.hint, .section-head p { color: var(--el-text-color-secondary); font-size: 13px; }
.actions, .auth-status { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.auth-status { padding: 12px 0; }
.full-width, .media-box { width: 100%; }
.file-input { display: none; }
.copy-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.status-filter { width: 200px; margin: 12px 0; }
.insight-result { max-height: 420px; overflow: auto; margin: 16px 0 0; padding: 12px; background: var(--el-fill-color-light); white-space: pre-wrap; word-break: break-word; }
.page-choices { display: flex; flex-direction: column; align-items: flex-start; }
.page-choices :deep(.el-checkbox) { height: auto; min-height: 32px; white-space: normal; }
@media (max-width: 760px) { .copy-grid { grid-template-columns: 1fr; } }
</style>
