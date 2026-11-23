<template>
  <section class="shop-workbench publish-panel" v-loading="loading">
    <div class="shop-toolbar">
      <h2 class="shop-heading">{{ tt('shop.workspacePublish') }}</h2>
      <el-tag size="small" :type="creatorEnvironment === 'shop.envTest' ? 'warning' : 'info'">{{ tt(creatorEnvironment) }}</el-tag>
    </div>
    <el-alert v-if="!loading && !capabilities?.configured" :title="tt('shop.notConfigured')" type="warning" :closable="false" />
    <el-alert v-else-if="capabilities && !capabilities.publishingConfigured" :title="tt('shop.publishingNotConfigured')" type="info" :closable="false" />
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <div class="shop-toolbar">
      <el-select v-model="accountId" :placeholder="tt('shop.noSelection')" :aria-label="tt('shop.account')" @change="resetDependent">
        <el-option v-for="account in accounts" :key="account.id" :label="`${account.displayName || account.username || `#${account.id}`} · ${account.market}`" :value="account.id" />
      </el-select>
      <el-tag v-if="selectedAccount" :type="shopAccountState(selectedAccount.authStatus).type" effect="light">{{ tt(shopAccountState(selectedAccount.authStatus).label) }}</el-tag>
      <el-button v-hasPermi="['tk:tiktok-shop-account:authorize']" v-if="!authorizedAccount" :disabled="!capabilities?.configured" @click="connect">{{ tt('shop.connect') }}</el-button>
      <el-tooltip :content="tt('common.refresh')"><el-button :aria-label="tt('common.refresh')" :icon="Refresh" @click="load" /></el-tooltip>
    </div>
    <details v-if="accounts.length" class="shop-details">
      <summary>{{ tt('shop.accountDetails') }}</summary>
      <el-button v-hasPermi="['tk:tiktok-shop-account:authorize']" v-if="authorizedAccount" :disabled="!capabilities?.configured" @click="connect">{{ tt('shop.connect') }}</el-button>
      <el-table :data="accounts" size="small">
        <el-table-column :label="tt('shop.account')" min-width="150"><template #default="{ row }">{{ row.displayName || row.username || `#${row.id}` }}</template></el-table-column>
        <el-table-column :label="tt('common.status')" min-width="145"><template #default="{ row }">{{ tt(shopAccountState(row.authStatus).label) }}</template></el-table-column>
        <el-table-column prop="grantedScopes" :label="tt('shop.scopes')" min-width="170" show-overflow-tooltip />
        <el-table-column :label="tt('shop.accessExpiry')" min-width="155"><template #default="{ row }">{{ formatShopDate(row.accessTokenExpireTime, locale) }}</template></el-table-column>
        <el-table-column :label="tt('shop.refreshExpiry')" min-width="155"><template #default="{ row }">{{ formatShopDate(row.refreshTokenExpireTime, locale) }}</template></el-table-column>
        <el-table-column :label="tt('shop.creatorRenewal')" min-width="145"><template #default="{ row }">{{ row.authStatus !== 'AUTHORIZED' ? '-' : tt(row.refreshRetryAfter ? 'shop.creatorRenewalPending' : 'shop.creatorRenewalAutomatic') }}</template></el-table-column>
        <el-table-column width="120"><template #default="{ row }"><el-button v-hasPermi="['tk:tiktok-shop-account:authorize']" link type="danger" @click="disconnect(row)">{{ tt('shop.disconnect') }}</el-button></template></el-table-column>
      </el-table>
    </details>
    <div class="shop-editor shop-band">
      <el-form v-if="canCreate" label-position="top">
        <h3 class="shop-heading editor-heading">{{ tt('shop.contentSection') }}</h3>
        <el-form-item :label="tt('shop.videoSource')">
          <el-radio-group v-model="mediaMode" @change="resetMedia">
            <el-radio-button value="generated">{{ tt('shop.selectVideo') }}</el-radio-button>
            <el-radio-button value="upload">{{ tt('shop.uploadVideo') }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="tt(mediaMode === 'generated' ? 'shop.selectVideo' : 'shop.uploadVideo')">
          <el-select v-if="mediaMode === 'generated'" v-model="form.generationTaskId"
            :disabled="!generatedVideos.length" :placeholder="tt(generatedVideos.length ? 'shop.selectVideo' : 'shop.noGeneratedVideos')"
            @change="invalidate">
            <el-option v-for="video in generatedVideos" :key="video.id" :value="video.id" :label="`${video.title || 'Video'} (#${video.id})`" />
          </el-select>
          <input v-else type="file" :aria-label="tt('shop.uploadVideo')" accept="video/mp4,video/quicktime,video/webm" @change="upload" />
          <el-progress v-if="uploading" :percentage="uploadPercent" />
          <el-button v-if="uploading" @click="cancelUpload">{{ tt('shop.cancelUpload') }}</el-button>
        </el-form-item>
        <el-form-item :label="tt('shop.titleLabel')"><el-input v-model="form.title" type="textarea" :rows="4" maxlength="2200" show-word-limit @input="invalidate" /></el-form-item>
        <el-form-item><el-checkbox v-model="form.isAiGenerated" @change="invalidate">{{ tt('shop.aiGenerated') }}</el-checkbox></el-form-item>
      </el-form>
      <div>
        <el-form v-if="canCreate" label-position="top">
          <h3 class="shop-heading editor-heading">{{ tt('shop.productSection') }}</h3>
          <p v-if="draftProduct" class="shop-muted">{{ tt('shop.productSelected') }}: {{ draftProduct.title || draftProduct.id }}</p>
          <TiktokShopProductPicker v-if="readyToCreate" :key="accountId" :account-id="accountId" @select="selectProduct" @changed="resetProduct" />
          <p v-else-if="!authorizedAccount" class="shop-muted">{{ tt(selectedAccount ? shopAccountState(selectedAccount.authStatus).label : 'shop.noSelection') }}</p>
          <el-form-item :label="tt('shop.anchorLabel')" class="anchor-field"><el-input v-model="form.productLinkTitle" maxlength="29" show-word-limit @input="invalidate" /><span class="shop-muted">{{ tt('shop.anchorHint') }}</span></el-form-item>
          <el-button type="primary" :loading="creating" :disabled="!readyToCreate || uploading" @click="createTask">{{ tt('shop.create') }}</el-button>
        </el-form>
        <section v-if="activeTask" ref="taskDetails" tabindex="-1" class="task-result">
          <TiktokShopPublishConfirm :task="activeTask" :account="confirmationAccount" :submitting="confirming" :submitted="submitted" @submit="confirmTask" />
          <el-alert v-if="activeTask.stage === 'RECOVERY_REQUIRED'" :title="`${tt('shop.recovery')}${activeTask.requestId ? ` ${tt('shop.requestId')}: ${activeTask.requestId}` : ''}`" type="warning" :closable="false" />
        </section>
      </div>
    </div>
    <section v-if="canReadHistory" class="shop-band shop-results">
      <div class="shop-toolbar"><h3 class="shop-heading">{{ tt('shop.history') }}</h3><span class="shop-spacer" /><el-button @click="loadHistory">{{ tt('shop.refreshHistory') }}</el-button></div>
      <el-table :data="history" size="small">
        <el-table-column prop="title" :label="tt('shop.titleLabel')" min-width="180" show-overflow-tooltip />
        <el-table-column :label="tt('common.status')" min-width="150"><template #default="{ row }"><el-tag size="small" :type="shopTaskState(row.stage).type">{{ tt(shopTaskState(row.stage).label) }}</el-tag></template></el-table-column>
        <el-table-column :label="tt('shop.updatedAt')" min-width="165"><template #default="{ row }">{{ formatShopDate(row.updateTime, locale) }}</template></el-table-column>
        <el-table-column min-width="120"><template #default="{ row }"><el-button link type="primary" @click="selectHistory(row)">{{ tt('shop.taskDetails') }} #{{ row.id }}</el-button></template></el-table-column>
      </el-table>
    </section>
  </section>
</template>
<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { TiktokShopApi, type ShopAccount, type ShopCapabilities, type ShopCreate, type ShopProduct, type ShopPublishSelection, type ShopTask } from '@/api/tk/tiktokShop'
import { TkGenerationApi, type TkGenerationTaskVO } from '@/api/tk/generation'
import { acceptShopOAuthMessage, createShopPoller, createShopRevision, createShopSubmitter, shopAuthErrorKey, shopEnvironmentLabel, shopOAuthTimeoutMs, validateShopForm } from './tiktokShopBehavior'
import { createShopUpload } from '@/utils/tiktokShopUpload'
import TiktokShopProductPicker from './TiktokShopProductPicker.vue'
import TiktokShopPublishConfirm from './TiktokShopPublishConfirm.vue'
import { useTkI18n } from '@/hooks/web/useTkI18n'
import { hasPermission } from '@/directives/permission/hasPermi'
import { formatShopDate, shopAccountState, shopTaskState } from '../../tiktok-shop/shopPresentation'
import '../../tiktok-shop/shopWorkbench.css'
const props = defineProps<{ initialSelection?: ShopPublishSelection }>()
const emit = defineEmits<{ selectionConsumed: [] }>()
const draftProduct = ref<ShopProduct>()
const canCreate = computed(() => hasPermission(['tk:tiktok-shop-publish:create']))
const canReadHistory = computed(() => hasPermission(['tk:tiktok-shop-publish:query']))
const { tt, locale } = useTkI18n(), capabilities = ref<ShopCapabilities>(), accounts = ref<ShopAccount[]>([]), error = ref(''), loading = ref(false)
const taskDetails = ref<HTMLElement>()
const accountId = ref<number>(), mediaMode = ref<'generated' | 'upload'>('generated'), history = ref<ShopTask[]>([]), activeTask = ref<ShopTask>(), creating = ref(false), confirming = ref(false), submitted = ref(false), uploadPercent = ref(0), uploading = ref(false)
const generatedVideos = ref<Pick<TkGenerationTaskVO, 'id' | 'title' | 'outputUrl'>[]>([])
const generatedVideoRequiredCode = 1060000024
const form = reactive<Partial<ShopCreate>>({ title: '', productLinkTitle: '', productSource: 'SHOWCASE', isAiGenerated: true })
let uploadHandle: ReturnType<typeof createShopUpload> | undefined, popup: Window | undefined, oauthSession: number | undefined, oauthTimer: number | undefined
const revision = createShopRevision(), selectedAccount = computed(() => accounts.value.find((account) => account.id === accountId.value)), creatorEnvironment = computed(() => shopEnvironmentLabel(capabilities.value?.creatorEnvironment))
const authorizedAccount = computed(() => selectedAccount.value?.authStatus === 'AUTHORIZED')
const readyToCreate = computed(() => canCreate.value && authorizedAccount.value && capabilities.value?.publishingConfigured)
const confirmationAccount = computed(() => accounts.value.find((account) => account.id === activeTask.value?.accountId))
const submitter = createShopSubmitter({ create: (data) => TiktokShopApi.create(data), confirm: (id, data) => TiktokShopApi.confirm(id, data), get: (id) => TiktokShopApi.get(id) })
const poller = createShopPoller(async () => { const task = activeTask.value; const requestRevision = revision.begin(); if (!task || ['SUCCESS', 'FAILED', 'RECOVERY_REQUIRED'].includes(task.stage)) return false; const fresh = await TiktokShopApi.get(task.id); if (!revision.current(requestRevision) || activeTask.value?.id !== task.id) return false; activeTask.value = fresh; return !['SUCCESS', 'FAILED', 'RECOVERY_REQUIRED'].includes(fresh.stage) }, () => { error.value = tt('shop.operationFailed') }, { set: window.setTimeout, clear: window.clearTimeout })
async function load() {
  loading.value = true
  error.value = ''
  try {
    capabilities.value = await TiktokShopApi.capabilities()
    if (!capabilities.value.configured) return
    await loadAccounts()
    await loadHistory()
    if (capabilities.value.publishingConfigured && hasPermission(['tk:generation:query'])) {
      const page = await TkGenerationApi.getGenerationPage({ pageNo: 1, pageSize: 20, status: 'SUCCESS' })
      const videos: typeof generatedVideos.value = page.list || []
      generatedVideos.value = videos.filter((video) => video.id != null && video.outputUrl?.trim())
    }
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : tt('shop.capabilityFailed')
  } finally {
    loading.value = false
  }
}
async function loadAccounts() {
  const wasAuthorized = authorizedAccount.value
  accounts.value = await TiktokShopApi.accounts()
  if (wasAuthorized && !authorizedAccount.value) resetDependent()
  if (!accounts.value.some((account) => account.id === accountId.value)) {
    if (accountId.value) resetDependent()
    accountId.value = (accounts.value.find((account) => account.authStatus === 'AUTHORIZED') || accounts.value[0])?.id
  }
  const selection = props.initialSelection
  if (selection && canCreate.value && accounts.value.some((account) => account.id === selection.accountId && account.authStatus === 'AUTHORIZED')) {
    accountId.value = selection.accountId
    selectProduct(selection.product, 'SHOWCASE')
    draftProduct.value = selection.product
  }
  if (selection) emit('selectionConsumed')
}
async function loadHistory() { if (!canReadHistory.value) return; const page = await TiktokShopApi.tasks({ pageNo: 1, pageSize: 20 }); history.value = page.list || [] }
function clearOAuth() { window.removeEventListener('message', onOAuth); if (oauthTimer) window.clearTimeout(oauthTimer); oauthTimer = undefined; popup?.close(); popup = undefined; oauthSession = undefined }
async function connect() { const origin = capabilities.value?.callbackOrigin; if (!origin || !/^https:\/\/[^/]+$/i.test(origin)) { error.value = tt('shop.invalidCapability'); return }; clearOAuth(); popup = window.open('about:blank', 'tiktok-shop-auth', 'width=520,height=720') || undefined; if (!popup) { error.value = tt('shop.popupBlocked'); return }; window.addEventListener('message', onOAuth); try { const session = await TiktokShopApi.authorize({ authType: 'CREATOR' }); oauthSession = session.sessionId; popup.location.href = session.authorizationUrl; oauthTimer = window.setTimeout(() => { clearOAuth() }, shopOAuthTimeoutMs(session)) } catch (reason) { clearOAuth(); error.value = reason instanceof Error ? reason.message : tt('shop.authorizationFailed') } }
function onOAuth(event: MessageEvent) { if (!popup || oauthSession === undefined || !acceptShopOAuthMessage(event, { callbackOrigin: capabilities.value?.callbackOrigin || '', sessionId: oauthSession, authType: 'CREATOR' }, popup)) return; const status = event.data.status; clearOAuth(); if (status === 'SUCCESS') void loadAccounts(); else error.value = tt(shopAuthErrorKey(event.data.errorCode)) }
async function disconnect(account: ShopAccount) { await ElMessageBox.confirm(tt('shop.disconnectConfirm'), tt('shop.disconnect')); await TiktokShopApi.disconnect(account.id); await loadAccounts() }
function resetMedia() { cancelUpload(); form.generationTaskId = undefined; form.mediaId = undefined; invalidate() }
function resetDependent() { resetProduct() }
function resetProduct() { draftProduct.value = undefined; form.productId = undefined; form.selectionProof = undefined; invalidate() }
function invalidate() { revision.invalidate(); activeTask.value = undefined; submitted.value = false }
function selectProduct(product: ShopProduct, source: 'SHOP' | 'SHOWCASE') { draftProduct.value = undefined; form.productId = product.id; form.productSource = source; form.selectionProof = product.selectionProof; invalidate() }
async function upload(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file || !capabilities.value?.publishingConfigured || !canCreate.value) return
  cancelUpload()
  if (file.size > capabilities.value.maxVideoBytes) { error.value = tt('shop.videoTooLarge'); return }
  uploadPercent.value = 0
  const handle = createShopUpload(file, (percent) => { if (uploadHandle === handle) uploadPercent.value = percent })
  uploadHandle = handle; uploading.value = true
  try {
    const media = await handle.result
    if (uploadHandle === handle) { form.mediaId = media.id; form.generationTaskId = undefined; invalidate() }
  } catch (reason) {
    if (uploadHandle === handle) error.value = reason instanceof Error ? reason.message : tt('shop.operationFailed')
  } finally {
    if (uploadHandle === handle) { uploading.value = false; uploadHandle = undefined }
  }
}
function cancelUpload() { revision.invalidate(); const handle = uploadHandle; uploadHandle = undefined; handle?.cancel(); uploading.value = false }
async function createTask() {
  if (!readyToCreate.value || uploading.value || creating.value) return
  const payload = { ...form, accountId: accountId.value } as Omit<ShopCreate, 'idempotencyKey'>
  if (payload.generationTaskId != null && !generatedVideos.value.some((video) => video.id === payload.generationTaskId)) {
    form.generationTaskId = undefined
    invalidate()
    error.value = tt('shop.generatedVideoUnavailable')
    return
  }
  const invalid = validateShopForm(payload)
  if (invalid) {
    error.value = tt(invalid)
    return
  }
  const requestRevision = revision.begin()
  error.value = ''
  creating.value = true
  try {
    const task = await submitter.create(payload)
    if (revision.current(requestRevision)) {
      activeTask.value = task
      poller.start()
      await loadHistory()
    }
  } catch (reason) {
    if (!revision.current(requestRevision)) return
    const response = (reason as { response?: { status?: number; data?: { code?: number } } } | undefined)?.response
    if (payload.generationTaskId != null && response?.status === 400 && response.data?.code === generatedVideoRequiredCode) {
      generatedVideos.value = generatedVideos.value.filter((video) => video.id !== payload.generationTaskId)
      form.generationTaskId = undefined
      invalidate()
      error.value = tt('shop.generatedVideoUnavailable')
    } else {
      error.value = reason instanceof Error ? reason.message : tt('shop.operationFailed')
    }
  } finally {
    creating.value = false
  }
}
async function confirmTask() { if (!activeTask.value || confirmationAccount.value?.authStatus !== 'AUTHORIZED' || confirming.value || submitted.value) return; const snapshot = activeTask.value; const requestRevision = revision.begin(); confirming.value = true; try { const task = await submitter.confirm(snapshot); if (revision.current(requestRevision) && activeTask.value?.id === snapshot.id) { activeTask.value = task; submitted.value = true; poller.start(); await loadHistory() } } catch { if (revision.current(requestRevision)) error.value = tt('shop.recovery') } finally { confirming.value = false } }
async function selectHistory(task: ShopTask) {
  const requestRevision = revision.begin()
  try {
    const fresh = await TiktokShopApi.get(task.id)
    if (!revision.current(requestRevision)) return
    activeTask.value = fresh
    if (!['SUCCESS', 'FAILED', 'RECOVERY_REQUIRED'].includes(fresh.stage)) poller.start()
    await nextTick()
    if (revision.current(requestRevision)) taskDetails.value?.focus()
  } catch (reason) {
    if (revision.current(requestRevision)) error.value = reason instanceof Error ? reason.message : tt('shop.operationFailed')
  }
}
onMounted(() => { window.addEventListener('message', onOAuth); void load() })
onBeforeUnmount(() => { poller.stop(); cancelUpload(); clearOAuth() })
</script>
<style scoped>
.editor-heading { margin-bottom: 14px; }
.anchor-field, .task-result, .publish-panel > .el-alert { margin-top: 12px; }
.publish-panel input[type='file'] { max-width: 100%; }
.publish-panel :deep(.el-progress) { width: 100%; margin-top: 6px; }
.publish-panel :deep(.el-radio-group) { max-width: 100%; }
</style>
