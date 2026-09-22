<template>
  <section v-loading="state.loading" class="meta-panel" :aria-label="mt('meta.title')">
    <ContentWrap>
      <div class="section-head">
        <div><h3>{{ mt('meta.title') }}</h3><p>{{ mt('meta.description') }}</p></div>
        <el-button :disabled="state.loading" @click="reload">{{ mt('meta.refreshConfig') }}</el-button>
      </div>
      <el-alert v-if="state.loadError" :title="translateText(state.loadError)" type="error" :closable="false" />
      <el-alert v-else-if="state.initialized && !state.enabled" :title="mt('meta.configPending')" :description="mt('meta.configPendingDescription')" type="info" :closable="false" />
      <template v-if="state.enabled">
        <p class="hint">{{ mt('meta.availablePlatforms') }}: {{ state.platforms.map(platformLabel).join(locale === 'en' ? ', ' : '、') || mt('meta.none') }}; {{ mt('meta.supportedMedia') }}: {{ state.mediaTypes.map(mediaTypeLabel).join(locale === 'en' ? ', ' : '、') || mt('meta.none') }}</p>
        <el-alert v-if="!state.mediaTypes.includes('VIDEO')" :title="mt('meta.videoUnsupported')" type="warning" :closable="false" />
      </template>
    </ContentWrap>

    <template v-if="state.enabled">
      <ContentWrap>
        <div class="section-head">
          <h3>{{ mt('meta.platformAccounts') }}</h3>
          <div class="actions">
            <el-button v-if="can('tk:social-account:authorize')" :disabled="state.authBusy || !state.platforms.includes('INSTAGRAM')" @click="run(() => controller.connect('INSTAGRAM'))">{{ mt('meta.connectInstagram') }}</el-button>
            <el-button v-if="can('tk:social-account:authorize')" :disabled="state.authBusy || !state.platforms.includes('FACEBOOK_PAGE')" @click="run(() => controller.connect('FACEBOOK_PAGE'))">{{ mt('meta.connectFacebook') }}</el-button>
            <el-button v-if="can('tk:social-account:query')" :loading="state.accountsLoading" @click="run(controller.refreshAccounts)">{{ mt('meta.refreshAccounts') }}</el-button>
          </div>
        </div>
        <div v-if="state.authBusy || state.auth.message" class="auth-status" role="status">
          <span>{{ translateText(state.auth.message || authLabel) }}</span>
          <el-button v-if="state.authBusy" link @click="controller.cancelAuth">{{ mt('meta.cancelWaiting') }}</el-button>
        </div>
        <el-table v-if="can('tk:social-account:query')" v-loading="state.accountsLoading" :data="accountRows" stripe>
          <el-table-column :label="mt('meta.account')" min-width="180"><template #default="{ row }">{{ accountName(row) }}</template></el-table-column>
          <el-table-column :label="mt('meta.platform')" min-width="140"><template #default="{ row }">{{ platformLabel(row.platform) }}</template></el-table-column>
          <el-table-column :label="mt('meta.status')" min-width="130"><template #default="{ row }">{{ statusLabel(row.status) }}</template></el-table-column>
          <el-table-column v-for="metric in accountMetrics" :key="metric.key" :label="metric.label" min-width="115">
            <template #default="{ row }">
              <el-tooltip :content="accountMetricHint(row.id, metric.key)" placement="top">
                <span>{{ formatMetricValue(accountMetric(row.id, metric.key), locale) }}{{ accountMetric(row.id, metric.key)?.stale ? ` (${mt('meta.staleValue')})` : '' }}</span>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column :label="mt('meta.statsSync')" min-width="150"><template #default="{ row }">
            {{ statsStatusLabel(state.stats[statsKey('account', row.id)]?.syncStatus, locale) }}
            <div class="hint">{{ translateText(state.statsErrors[statsKey('account', row.id)] || state.stats[statsKey('account', row.id)]?.errorMessage) }}</div>
          </template></el-table-column>
          <el-table-column :label="mt('meta.statsUpdated')" min-width="170"><template #default="{ row }">{{ statsDate(state.stats[statsKey('account', row.id)]?.lastSuccessTime) }}</template></el-table-column>
          <el-table-column :label="mt('meta.tokenExpires')" min-width="170"><template #default="{ row }">{{ row.tokenExpiresAt ? formatTimestamp(row.tokenExpiresAt) : mt('meta.platformValidationFallback') }}</template></el-table-column>
          <el-table-column :label="mt('meta.lastValidated')" min-width="170"><template #default="{ row }">{{ formatTimestamp(row.lastValidatedAt) }}</template></el-table-column>
          <el-table-column prop="failReason" :label="mt('meta.failureReason')" min-width="170" show-overflow-tooltip />
          <el-table-column :label="mt('meta.actions')" min-width="360">
            <template #default="{ row }">
              <el-button link @click="run(() => controller.openStats('account', row.id))">{{ mt('meta.viewData') }}</el-button>
              <el-button v-if="can('tk:social-account:update')" link :disabled="!controller.canSyncStats('account', row.id)" :loading="state.statsSyncing[statsKey('account', row.id)]" @click="run(() => controller.syncStats('account', row.id))">{{ mt('meta.syncData') }}</el-button>
              <el-button v-if="can('tk:social-account:query') && isSocialAccountAuthorized(row.status)" link @click="openInsights(row)">{{ mt('meta.testInsights') }}</el-button>
              <el-button v-if="can('tk:social-account:update')" link :disabled="state.busyAccounts.includes(row.id)" @click="accountAction('validate', row)">{{ mt('meta.validate') }}</el-button>
              <el-button v-if="can('tk:social-account:authorize')" link :disabled="state.authBusy" @click="run(() => controller.connect(row.platform))">{{ mt('meta.reauthorize') }}</el-button>
              <el-button v-if="can('tk:social-account:update')" link type="warning" :disabled="state.busyAccounts.includes(row.id)" @click="accountAction('unbind', row)">{{ mt('meta.unbind') }}</el-button>
              <el-button v-if="can('tk:social-account:update')" link type="danger" :disabled="state.busyAccounts.includes(row.id)" @click="accountAction('delete', row)">{{ mt('meta.delete') }}</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination v-if="state.accounts.length > 10" v-model:current-page="accountPage" :page-size="10" :total="state.accounts.length" layout="prev, pager, next" />
        <el-alert v-if="!can('tk:social-account:query')" :title="mt('meta.accountQueryPermission')" type="info" :closable="false" />
      </ContentWrap>

      <ContentWrap v-if="can('tk:social-publish:create') && can('tk:social-account:query')">
        <h3>{{ mt('meta.newTask') }}</h3>
        <el-form label-position="top" :disabled="state.submitting">
          <el-form-item :label="mt('meta.taskTitle')" required><el-input v-model="state.draft.title" :maxlength="255" show-word-limit :placeholder="mt('meta.taskTitlePlaceholder')" /></el-form-item>
          <el-form-item :label="mt('meta.publishTargets')" required>
            <el-select v-model="state.draft.accountIds" multiple :multiple-limit="20" filterable class="full-width" :placeholder="mt('meta.publishTargetsPlaceholder')">
              <el-option v-for="account in state.accounts" :key="account.id" :value="account.id" :label="`${platformLabel(account.platform)} · ${accountName(account)}`" :disabled="!isSocialAccountAuthorized(account.status) || !state.platforms.includes(account.platform)">
                {{ platformLabel(account.platform) }} · {{ accountName(account) }} · {{ statusLabel(account.status) }}
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item :label="mt('meta.media')">
            <div class="media-box">
              <div class="actions">
                <el-button :loading="state.uploading" :disabled="!state.mediaTypes.length" @click="fileInput?.click()">{{ mt('meta.uploadMedia') }}</el-button>
                <input ref="fileInput" class="file-input" type="file" :accept="acceptedMedia" @change="uploadFile" />
                <MetaGeneratedPicker :disabled="state.uploading || state.submitting || !state.mediaTypes.includes('VIDEO')" :facebook-selected="facebookSelected" @select="selectGenerated" />
                <el-button v-if="state.media || state.draft.generationTaskId" :disabled="state.uploading" @click="controller.clearMedia">{{ mt('meta.removeMedia') }}</el-button>
              </div>
              <el-progress v-if="state.uploading" :percentage="state.uploadPercent" :status="state.uploadPercent === 100 ? 'success' : undefined" />
              <p class="hint">{{ formatMessage('meta.mediaHint', { max: state.maxVideoSizeMb }) }}</p>
              <el-alert v-if="facebookSelected" :title="mt('meta.facebookVideoTitle')" :description="mt('meta.facebookVideoDescription')" type="info" :closable="false" />
              <p v-if="instagramSelected" class="hint">{{ mt('meta.instagramVideoHint') }}</p>
              <el-tag v-if="state.draft.generationTaskId" type="success">{{ mt('meta.generatedVideo') }} #{{ state.draft.generationTaskId }}{{ generationTitle ? ` · ${generationTitle}` : '' }}</el-tag>
              <el-tag v-else-if="state.media" :type="mediaReady ? 'success' : 'warning'">{{ mediaTypeLabel(state.media.mediaType) }}: {{ state.media.fileName }} ({{ mediaStateLabel(state.media) }})</el-tag>
              <template v-if="state.media?.mediaType === 'VIDEO'">
                <p class="hint">{{ mt('meta.resolution') }} {{ state.media.width ?? mt('meta.pendingDetection') }} × {{ state.media.height ?? mt('meta.pendingDetection') }} · {{ mt('meta.duration') }} {{ rounded(state.media.durationSeconds) }} s · {{ mt('meta.frameRate') }} {{ rounded(state.media.frameRate) }} fps</p>
                <p class="hint">{{ mt('meta.videoCodec') }} {{ state.media.videoCodec || mt('meta.pendingDetection') }} · {{ mt('meta.audioCodec') }} {{ state.media.audioCodec || mt('meta.noAudio') }} · {{ mt('meta.source') }} {{ state.media.metadataSource || mt('meta.pendingDetection') }} · {{ metadataLabel(state.media.metadataStatus) }}</p>
                <p v-if="state.media.normalized" class="hint">{{ mt('meta.normalizedHint') }}</p>
                <el-alert v-if="state.media.metadataError || state.mediaError" :title="translateText(state.media.metadataError || state.mediaError)" type="error" :closable="false" />
                <el-button link :loading="state.mediaChecking" @click="run(() => controller.refreshMedia())">{{ mt('meta.refreshDetection') }}</el-button>
              </template>
            </div>
          </el-form-item>
          <div class="copy-grid">
            <el-form-item :label="mt('meta.instagramCaption')"><el-input v-model="state.draft.instagramCaption" type="textarea" :rows="4" :maxlength="2200" show-word-limit placeholder="Instagram caption" /></el-form-item>
            <el-form-item :label="mt('meta.facebookMessage')"><el-input v-model="state.draft.facebookMessage" type="textarea" :rows="4" :maxlength="5000" show-word-limit placeholder="Facebook message" /></el-form-item>
          </div>
          <el-button type="primary" :loading="state.submitting" :disabled="state.uploading || !mediaReady || !state.draft.accountIds.length" @click="run(controller.submit)">{{ mt('meta.createTask') }}</el-button>
          <p v-if="state.lastTaskId" class="hint" role="status">{{ formatMessage('meta.taskCreated', { id: state.lastTaskId }) }}</p>
        </el-form>
      </ContentWrap>

      <ContentWrap v-if="can('tk:social-publish:query')">
        <div class="section-head"><h3>{{ mt('meta.taskList') }}</h3><el-button @click="run(controller.refreshTasks)">{{ mt('meta.refreshTasks') }}</el-button></div>
        <el-alert v-if="state.pollError" :title="translateText(state.pollError)" type="warning" :closable="false" />
        <el-select v-model="state.taskQuery.status" clearable :placeholder="mt('meta.allStatuses')" class="status-filter" @change="searchTasks">
          <el-option v-for="status in statuses" :key="status" :value="status" :label="statusLabel(status)" />
        </el-select>
        <el-table :data="state.tasks" stripe>
          <el-table-column prop="id" :label="mt('meta.taskId')" width="100" />
          <el-table-column prop="title" :label="mt('meta.titleColumn')" min-width="180" />
          <el-table-column :label="mt('meta.status')" min-width="140"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
          <el-table-column :label="mt('meta.result')" min-width="220"><template #default="{ row }">{{ taskResult(row) }}</template></el-table-column>
          <el-table-column :label="mt('meta.createdTime')" min-width="170"><template #default="{ row }">{{ formatTimestamp(row.createTime) }}</template></el-table-column>
          <el-table-column :label="mt('meta.actions')" min-width="180"><template #default="{ row }">
            <el-button link @click="run(() => controller.openDetails(row.id))">{{ mt('meta.viewDetails') }}</el-button>
            <el-button link :disabled="state.syncing.includes(row.id)" @click="run(() => controller.sync(row.id))">{{ mt('meta.verifyStatus') }}</el-button>
          </template></el-table-column>
        </el-table>
        <Pagination :total="state.taskTotal" v-model:page="state.taskQuery.pageNo" v-model:limit="state.taskQuery.pageSize" @pagination="run(controller.refreshTasks)" />
      </ContentWrap>

      <ContentWrap v-if="state.detailTaskId && can('tk:social-publish:query')">
        <div class="section-head"><h3>#{{ state.detailTaskId }} · {{ mt('meta.taskList') }}</h3><el-button @click="run(controller.refreshDetails)">{{ mt('meta.refreshDetails') }}</el-button></div>
        <el-alert :title="mt('meta.unknownWarning')" type="warning" :closable="false" />
        <el-table :data="state.details" stripe>
          <el-table-column :label="mt('meta.targetAccount')" min-width="180"><template #default="{ row }">{{ platformLabel(row.platform) }} · {{ row.accountName || `#${row.socialAccountId}` }}</template></el-table-column>
          <el-table-column :label="mt('meta.status')" min-width="150"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
          <el-table-column prop="platformStatus" :label="mt('meta.platformStatus')" min-width="150" />
          <el-table-column :label="mt('meta.descriptionLabel')" min-width="230"><template #default="{ row }">{{ detailDescription(row) }}<span v-if="row.errorCode"> ({{ row.errorCode }})</span></template></el-table-column>
          <el-table-column prop="retryCount" :label="mt('meta.retryCount')" width="95" />
          <el-table-column :label="mt('meta.actions')" min-width="260"><template #default="{ row }">
            <el-link v-if="safeUrl(row.publishUrl)" :href="safeUrl(row.publishUrl)" target="_blank" rel="noopener noreferrer">{{ mt('meta.viewPublished') }}</el-link>
            <el-button v-if="row.status === 'SUCCESS'" link @click="run(() => controller.openStats('detail', row.id))">{{ mt('meta.viewData') }}</el-button>
            <el-button v-if="row.status === 'REAUTH_REQUIRED' && can('tk:social-account:authorize')" link :disabled="state.authBusy" @click="run(() => controller.connect(row.platform))">{{ mt('meta.reauthorize') }}</el-button>
            <el-button v-if="canRetrySocialDetail(row.status) && can('tk:social-publish:retry')" link type="primary" :disabled="state.retrying.includes(row.id)" @click="retry(row)">{{ row.status === 'REAUTH_REQUIRED' ? mt('meta.retryAfterAuth') : mt('meta.retry') }}</el-button>
          </template></el-table-column>
        </el-table>
        <Pagination :total="state.detailTotal" v-model:page="state.detailQuery.pageNo" v-model:limit="state.detailQuery.pageSize" @pagination="run(controller.refreshDetails)" />
      </ContentWrap>
    </template>

    <el-dialog :model-value="state.pagePickerVisible" :title="mt('meta.selectFacebookPage')" width="min(650px, 95vw)" :close-on-click-modal="false" :close-on-press-escape="!state.binding" :show-close="!state.binding" @close="controller.cancelAuth">
      <el-empty v-if="!state.pages.length" :description="mt('meta.noBindablePage')" />
      <el-checkbox-group v-model="state.pageIds" :disabled="state.binding" class="page-choices">
        <el-checkbox v-for="page in state.pages" :key="page.id" :value="page.id">{{ page.name }} · {{ page.id }}（{{ page.tasks.join('、') }}）</el-checkbox>
      </el-checkbox-group>
      <template #footer><el-button :disabled="state.binding" @click="controller.cancelAuth">{{ mt('meta.cancel') }}</el-button><el-button type="primary" :loading="state.binding" :disabled="!state.pageIds.length" @click="run(controller.bindPages)">{{ mt('meta.bindSelectedPage') }}</el-button></template>
    </el-dialog>

    <MetaMediaStatsDrawer :target="state.statsTarget" :snapshot="state.stats[drawerKey]" :loading="state.statsLoading[drawerKey]" :syncing="state.statsSyncing[drawerKey]" :error="state.statsErrors[drawerKey]"
      :can-sync="!!state.statsTarget && can(state.statsTarget.kind === 'account' ? 'tk:social-account:update' : 'tk:social-publish:query')"
      :sync-allowed="!!state.statsTarget && controller.canSyncStats(state.statsTarget.kind, state.statsTarget.id)"
      @close="controller.closeStats" @refresh="drawerAction(false)" @sync="drawerAction(true)" />
    <el-dialog v-model="insightVisible" :title="mt('meta.insightsTest')" width="min(760px, 95vw)" destroy-on-close>
      <el-form label-position="top">
        <el-form-item :label="mt('meta.account')"><el-input :model-value="insightAccount ? `${platformLabel(insightAccount.platform)} · ${accountName(insightAccount)}` : ''" readonly /></el-form-item>
        <div class="copy-grid">
          <el-form-item :label="mt('meta.metric')"><el-input v-model="insightMetric" maxlength="512" /></el-form-item>
          <el-form-item :label="mt('meta.period')"><el-input v-model="insightPeriod" maxlength="512" /></el-form-item>
        </div>
        <el-button type="primary" :loading="insightLoading" @click="run(runInsights)">{{ mt('meta.requestInsights') }}</el-button>
      </el-form>
      <pre v-if="insightResult" class="insight-result">{{ insightResult }}</pre>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onActivated, onBeforeUnmount, onDeactivated, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { SocialPublishApi, type SocialAccount, type SocialDetail, type SocialMedia, type SocialStatus, type SocialTask, type SocialTime } from '@/api/tk/socialPublish'
import type { TkGenerationTaskVO } from '@/api/tk/generation'
import { hasPermission } from '@/directives/permission/hasPermi'
import { useTkI18n } from '@/hooks/web/useTkI18n'
import { translateMetaText } from '@/locales/tk/metaPublishMessages'
import { formatTimestamp } from '@/utils/formatTime'
import { accountLabel, canRetrySocialDetail, createMetaPublishController, isSocialAccountAuthorized, platformLabel, socialStatusLabel } from './metaPublishController'
import MetaGeneratedPicker from './MetaGeneratedPicker.vue'
import MetaMediaStatsDrawer from './MetaMediaStatsDrawer.vue'
import { availabilityLabel, formatMetricValue, formatSocialDate, periodLabel, scopeLabel, statsKey, statsStatusLabel } from './metaStats'

const props = defineProps<{ generationTaskId?: number }>()
const { locale, tt } = useTkI18n()
const can = (permission: string) => hasPermission([permission])
const mt = (key: string) => tt(key)
const translateText = (text?: string) => translateMetaText(text, locale.value)
const formatMessage = (key: string, params: Record<string, string | number>) => Object.entries(params)
  .reduce((result, [name, value]) => result.replaceAll(`{{${name}}}`, String(value)), mt(key))
const controller = createMetaPublishController(SocialPublishApi, can, {
  openPopup: () => window.open('', '_blank', 'popup,width=620,height=780'),
  setTimeout: (fn, delay) => window.setTimeout(fn, delay), clearTimeout: window.clearTimeout,
  now: Date.now, idempotencyKey: () => crypto.randomUUID(), notify: (text) => ElMessage.success(translateText(text))
})
const state = controller.state
const accountMetrics = computed(() => [
  { key: 'followers', label: mt('meta.followers') }, { key: 'following', label: mt('meta.following') }, { key: 'mediaCount', label: mt('meta.mediaCount') }
])
const statsDate = (value?: SocialTime | null) => formatSocialDate(value, locale.value)
const accountName = (account: SocialAccount) => translateText(accountLabel(account))
const statusLabel = (status: string) => translateText(socialStatusLabel(status))
const mediaTypeLabel = (type: string) => type === 'VIDEO' ? mt('meta.video') : type === 'IMAGE' ? mt('meta.image') : type
const mediaStateLabel = (media: SocialMedia) => mediaReady.value ? mt('meta.ready') : media.status === 'FAILED' ? mt('meta.detectionFailed') : mt('meta.waitingDetection')
const accountMetric = (id: number, key: string) => state.stats[statsKey('account', id)]?.metrics.find(metric => metric.key === key)
function accountMetricHint(id: number, key: string) {
  const metric = accountMetric(id, key)
  return metric ? [availabilityLabel(metric.availability, locale.value), metric.sourceMetric, scopeLabel(metric.scope, locale.value), periodLabel(metric.period, locale.value), statsDate(metric.fetchedAt), translateText(metric.errorMessage)].filter(Boolean).join(' · ') : mt('meta.noStatsData')
}
const drawerKey = computed(() => state.statsTarget ? statsKey(state.statsTarget.kind, state.statsTarget.id) : '')
function drawerAction(sync: boolean) {
  const target = state.statsTarget
  if (target) void run(() => sync ? controller.syncStats(target.kind, target.id) : controller.refreshStats(target.kind, target.id))
}
const mediaReady = computed(() => !state.media || state.media.mediaType !== 'VIDEO' || (state.media.status === 'READY' && state.media.metadataStatus === 'VERIFIED'))
const rounded = (value?: number) => value == null ? mt('meta.pendingDetection') : Number(value.toFixed(2))
const metadataLabel = (status?: string) => ({ PENDING: mt('meta.pendingDetection'), INSPECTING: mt('meta.detecting'), VERIFIED: mt('meta.verified'), FAILED: mt('meta.detectionFailed'), UNVERIFIED: mt('meta.unverified') }[status || ''] || mt('meta.pendingDetection'))
const fileInput = ref<HTMLInputElement>(), generationTitle = ref(''), accountPage = ref(1)
const insightVisible = ref(false), insightLoading = ref(false), insightAccount = ref<SocialAccount>(), insightMetric = ref(''), insightPeriod = ref('day'), insightResult = ref('')
const accountRows = computed(() => state.accounts.slice((accountPage.value - 1) * 10, accountPage.value * 10))
watch(accountRows, rows => {
  if (!state.enabled || !can('tk:social-account:query')) return
  for (const row of rows) {
    const key = statsKey('account', row.id)
    if (!state.stats[key] && !state.statsLoading[key] && !state.statsErrors[key]) void run(() => controller.refreshStats('account', row.id))
  }
})
const facebookSelected = computed(() => state.accounts.some(account => account.platform === 'FACEBOOK_PAGE' && state.draft.accountIds.includes(account.id)))
const instagramSelected = computed(() => state.accounts.some(account => account.platform === 'INSTAGRAM' && state.draft.accountIds.includes(account.id)))
const acceptedMedia = computed(() => [state.mediaTypes.includes('VIDEO') ? '.mp4,video/mp4' : '', state.mediaTypes.includes('IMAGE') ? '.jpg,.jpeg,image/jpeg' : ''].filter(Boolean).join(','))
const authLabel = computed(() => ({ PENDING: mt('meta.authWaiting'), PROCESSING: mt('meta.authProcessing'), PAGES_READY: mt('meta.authSelectPage'), SUCCESS: mt('meta.authSuccess'), FAILED: mt('meta.authFailed'), EXPIRED: mt('meta.authExpired') })[state.auth.status] || '')
const statuses: SocialStatus[] = ['PENDING', 'PROCESSING', 'SUCCESS', 'PARTIAL_SUCCESS', 'FAILED', 'REAUTH_REQUIRED', 'UNKNOWN']
const statusType = (status: string) => status === 'SUCCESS' ? 'success' : status === 'FAILED' ? 'danger' : ['UNKNOWN', 'REAUTH_REQUIRED', 'PARTIAL_SUCCESS'].includes(status) ? 'warning' : 'info'
function safeUrl(value?: string) { try { const url = new URL(value || ''); return ['https:', 'http:'].includes(url.protocol) ? url.href : undefined } catch { return undefined } }
function taskResult(row: SocialTask) {
  return `${mt('meta.targetCount')} ${row.targetCount} · ${mt('meta.successCount')} ${row.successCount} · ${mt('meta.failedCount')} ${row.failedCount} · ${mt('meta.pendingCount')} ${row.pendingCount}`
}
function detailDescription(row: SocialDetail) {
  return row.status === 'UNKNOWN' ? mt('meta.manualVerification') : translateText(row.errorMessage) || '-'
}
function openInsights(account: SocialAccount) {
  insightAccount.value = account
  insightMetric.value = account.platform === 'INSTAGRAM' ? 'reach' : 'page_impressions'
  insightPeriod.value = 'day'
  insightResult.value = ''
  insightVisible.value = true
}
async function runInsights() {
  if (!insightAccount.value) return
  if (!insightMetric.value.trim() || !insightPeriod.value.trim()) throw new Error(mt('meta.insightsFieldsRequired'))
  insightLoading.value = true
  try {
    const response = await SocialPublishApi.insights(insightAccount.value.id, { metric: insightMetric.value.trim(), period: insightPeriod.value.trim() })
    insightResult.value = JSON.stringify(response, null, 2)
  } finally { insightLoading.value = false }
}
async function run(action: () => Promise<unknown>) {
  try { await action() } catch (reason) {
    if (reason === 'cancel' || reason === 'close') return
    if (reason instanceof Error && (reason as Error & { displayedByAxios?: boolean }).displayedByAxios) return
    ElMessage.error(translateText(reason instanceof Error ? reason.message : mt('meta.operationFailed')))
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
    if (action !== 'validate') await ElMessageBox.confirm(formatMessage(action === 'unbind' ? 'meta.confirmUnbind' : 'meta.confirmDelete', { account: accountName(account) }), mt('meta.accountManagement'), { type: 'warning' })
    await controller.accountAction(action, account.id)
  })
}
function retry(detail: SocialDetail) {
  void run(async () => {
    if (detail.status === 'REAUTH_REQUIRED') await ElMessageBox.confirm(mt('meta.retryAfterAuthConfirm'), mt('meta.retryAfterAuthTitle'), { type: 'warning' })
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
