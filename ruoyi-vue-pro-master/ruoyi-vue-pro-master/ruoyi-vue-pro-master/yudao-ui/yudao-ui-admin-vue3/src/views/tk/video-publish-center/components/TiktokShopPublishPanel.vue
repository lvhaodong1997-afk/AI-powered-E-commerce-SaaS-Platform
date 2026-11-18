<template>
  <ContentWrap class="shop-panel">
    <div class="section-head"><div><h3>{{ tt('shop.title') }} <el-tag size="small" :type="creatorEnvironment === 'shop.envTest' ? 'warning' : 'success'">{{ tt(creatorEnvironment) }}</el-tag></h3><span class="sub-text">{{ tt('shop.description') }}</span></div><el-button @click="load" :loading="loading">{{ tt('common.refresh') }}</el-button></div>
    <el-alert v-if="!loading && !capabilities?.configured" :title="tt('shop.notConfigured')" type="warning" :closable="false" />
    <el-alert v-else-if="capabilities && !capabilities.publishingConfigured" :title="tt('shop.publishingNotConfigured')" type="info" :closable="false" />
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <section class="accounts"><div class="section-head"><strong>{{ tt('shop.account') }}</strong><div><el-button v-hasPermi="['tk:tiktok-shop-account:authorize']" type="primary" plain @click="connect">{{ tt('shop.connect') }}</el-button><el-button @click="loadAccounts">{{ tt('shop.refreshAccounts') }}</el-button></div></div><el-table :data="accounts" size="small"><el-table-column prop="id" label="ID" width="90" /><el-table-column :label="tt('shop.account')"><template #default="{ row }">{{ row.displayName || row.username || '-' }}</template></el-table-column><el-table-column prop="market" :label="tt('shop.market')" width="90" /><el-table-column prop="authStatus" :label="tt('common.status')" /><el-table-column prop="grantedScopes" :label="tt('shop.scopes')" min-width="180" show-overflow-tooltip /><el-table-column prop="accessTokenExpireTime" :label="tt('shop.accessExpiry')" width="170" /><el-table-column prop="refreshTokenExpireTime" :label="tt('shop.refreshExpiry')" width="170" /><el-table-column width="130"><template #default="{ row }"><el-button v-hasPermi="['tk:tiktok-shop-account:authorize']" link type="danger" @click="disconnect(row)">{{ tt('shop.disconnect') }}</el-button></template></el-table-column></el-table></section>
    <el-divider />
    <el-form v-if="canCreate" label-width="130px" class="shop-form">
      <el-form-item :label="tt('shop.account')"><el-select v-model="accountId" @change="resetDependent"><el-option v-for="account in accounts" :key="account.id" :label="`${account.displayName || account.username || 'Account'} (#${account.id})`" :value="account.id" /></el-select></el-form-item>
      <el-form-item :label="tt('shop.titleLabel')"><el-input v-model="form.title" type="textarea" maxlength="2200" show-word-limit @input="invalidate" /></el-form-item>
      <el-form-item :label="tt('shop.anchorLabel')"><el-input v-model="form.productLinkTitle" maxlength="29" @input="invalidate" /><span class="sub-text">{{ tt('shop.anchorHint') }}</span></el-form-item>
      <el-form-item :label="tt('shop.aiGenerated')"><el-checkbox v-model="form.isAiGenerated" @change="invalidate">{{ tt('shop.aiGenerated') }}</el-checkbox></el-form-item>
      <el-form-item :label="tt('shop.selectVideo')"><el-select v-model="mediaMode" @change="resetMedia"><el-option value="generated" :label="tt('shop.selectVideo')" /><el-option value="upload" :label="tt('shop.uploadVideo')" /></el-select><el-select v-if="mediaMode === 'generated'" v-model="form.generationTaskId" @change="invalidate" class="ml-10"><el-option v-for="video in generatedVideos" :key="video.id" :value="video.id" :label="`${video.title || 'Video'} (#${video.id})`" /></el-select><input v-else type="file" accept="video/mp4,video/quicktime,video/webm" @change="upload" /><el-progress v-if="uploading" :percentage="uploadPercent" /><el-button v-if="uploading" @click="cancelUpload">{{ tt('shop.cancelUpload') }}</el-button></el-form-item>
      <el-form-item v-if="draftProduct" :label="tt('shop.product')">
        {{ draftProduct.title }} · {{ draftProduct.id }}
      </el-form-item>
      <TiktokShopProductPicker v-if="readyToCreate" :key="accountId" :account-id="accountId" @select="selectProduct" @changed="resetProduct" />
      <el-form-item v-else-if="!authorizedAccount" :label="tt('shop.product')"><span class="sub-text">{{ tt('shop.accountRequired') }}</span></el-form-item>
      <el-button type="primary" :loading="creating" :disabled="!readyToCreate || uploading" @click="createTask">{{ tt('shop.create') }}</el-button>
    </el-form>
    <TiktokShopPublishConfirm :task="activeTask" :account="confirmationAccount" :submitting="confirming" :submitted="submitted" @submit="confirmTask" />
    <section v-if="canReadHistory" class="history"><div class="section-head"><strong>{{ tt('shop.history') }}</strong><el-button @click="loadHistory">{{ tt('shop.refreshHistory') }}</el-button></div><el-table :data="history" size="small" @row-click="selectHistory"><el-table-column prop="id" label="ID" width="80" /><el-table-column prop="stage" :label="tt('common.status')" /><el-table-column prop="title" :label="tt('shop.titleLabel')" min-width="160" /><el-table-column prop="updateTime" label="Updated" /></el-table></section>
    <el-alert v-if="activeTask?.stage === 'RECOVERY_REQUIRED'" :title="`${tt('shop.recovery')}${activeTask.requestId ? ` ${tt('shop.requestId')}: ${activeTask.requestId}` : ''}`" type="warning" :closable="false" />
  </ContentWrap>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import { TiktokShopApi, type ShopAccount, type ShopCapabilities, type ShopCreate, type ShopProduct, type ShopPublishSelection, type ShopTask } from '@/api/tk/tiktokShop'
import { TkGenerationApi } from '@/api/tk/generation'
import { acceptShopOAuthMessage, createShopPoller, createShopRevision, createShopSubmitter, shopAuthErrorKey, shopEnvironmentLabel, shopOAuthTimeoutMs, validateShopForm } from './tiktokShopBehavior'
import { createShopUpload } from '@/utils/tiktokShopUpload'
import TiktokShopProductPicker from './TiktokShopProductPicker.vue'
import TiktokShopPublishConfirm from './TiktokShopPublishConfirm.vue'
import { useTkI18n } from '@/hooks/web/useTkI18n'
import { hasPermission } from '@/directives/permission/hasPermi'
const props = defineProps<{ initialSelection?: ShopPublishSelection }>()
const emit = defineEmits<{ selectionConsumed: [] }>()
const draftProduct = ref<ShopProduct>()
const canCreate = computed(() => hasPermission(['tk:tiktok-shop-publish:create']))
const canReadHistory = computed(() => hasPermission(['tk:tiktok-shop-publish:query']))
const { tt } = useTkI18n(), capabilities = ref<ShopCapabilities>(), accounts = ref<ShopAccount[]>([]), error = ref(''), loading = ref(false)
const accountId = ref<number>(), mediaMode = ref<'generated' | 'upload'>('generated'), generatedVideos = ref<any[]>([]), history = ref<ShopTask[]>([]), activeTask = ref<ShopTask>(), creating = ref(false), confirming = ref(false), submitted = ref(false), uploadPercent = ref(0), uploading = ref(false)
const form = reactive<Partial<ShopCreate>>({ title: '', productLinkTitle: '', productSource: 'SHOWCASE', isAiGenerated: true })
let uploadHandle: ReturnType<typeof createShopUpload> | undefined, popup: Window | undefined, oauthSession: number | undefined, oauthTimer: number | undefined
const revision = createShopRevision(), selectedAccount = computed(() => accounts.value.find((account) => account.id === accountId.value)), creatorEnvironment = computed(() => shopEnvironmentLabel(capabilities.value?.creatorEnvironment))
const authorizedAccount = computed(() => selectedAccount.value?.authStatus === 'AUTHORIZED')
const readyToCreate = computed(() => canCreate.value && authorizedAccount.value && capabilities.value?.publishingConfigured)
const confirmationAccount = computed(() => accounts.value.find((account) => account.id === activeTask.value?.accountId))
const submitter = createShopSubmitter({ create: (data) => TiktokShopApi.create(data), confirm: (id, data) => TiktokShopApi.confirm(id, data), get: (id) => TiktokShopApi.get(id) })
const poller = createShopPoller(async () => { const task = activeTask.value; const requestRevision = revision.begin(); if (!task || ['SUCCESS', 'FAILED', 'RECOVERY_REQUIRED'].includes(task.stage)) return false; const fresh = await TiktokShopApi.get(task.id); if (!revision.current(requestRevision) || activeTask.value?.id !== task.id) return false; activeTask.value = fresh; return !['SUCCESS', 'FAILED', 'RECOVERY_REQUIRED'].includes(fresh.stage) }, () => { error.value = tt('shop.operationFailed') }, { set: window.setTimeout, clear: window.clearTimeout })
async function load() { loading.value = true; error.value = ''; try { capabilities.value = await TiktokShopApi.capabilities(); if (!capabilities.value.configured) return; await loadAccounts(); await loadHistory(); if (capabilities.value.publishingConfigured && hasPermission(['tk:generation:query'])) generatedVideos.value = (await TkGenerationApi.getGenerationPage({ pageNo: 1, pageSize: 20, status: 'SUCCESS' })).list || [] } catch (reason) { error.value = reason instanceof Error ? reason.message : tt('shop.capabilityFailed') } finally { loading.value = false } }
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
async function createTask() { if (!readyToCreate.value || uploading.value || creating.value) return; const payload = { ...form, accountId: accountId.value } as Omit<ShopCreate, 'idempotencyKey'>; const invalid = validateShopForm(payload); if (invalid) { error.value = tt(invalid); return }; const requestRevision = revision.begin(); creating.value = true; try { const task = await submitter.create(payload); if (revision.current(requestRevision)) { activeTask.value = task; poller.start(); await loadHistory() } } catch (reason) { if (revision.current(requestRevision)) error.value = reason instanceof Error ? reason.message : tt('shop.operationFailed') } finally { creating.value = false } }
async function confirmTask() { if (!activeTask.value || confirmationAccount.value?.authStatus !== 'AUTHORIZED' || confirming.value || submitted.value) return; const snapshot = activeTask.value; const requestRevision = revision.begin(); confirming.value = true; try { const task = await submitter.confirm(snapshot); if (revision.current(requestRevision) && activeTask.value?.id === snapshot.id) { activeTask.value = task; submitted.value = true; poller.start(); await loadHistory() } } catch { if (revision.current(requestRevision)) error.value = tt('shop.recovery') } finally { confirming.value = false } }
async function selectHistory(task: ShopTask) { const requestRevision = revision.begin(); const fresh = await TiktokShopApi.get(task.id); if (revision.current(requestRevision)) { activeTask.value = fresh; if (!['SUCCESS', 'FAILED', 'RECOVERY_REQUIRED'].includes(fresh.stage)) poller.start() } }
onMounted(() => { window.addEventListener('message', onOAuth); void load() })
onBeforeUnmount(() => { poller.stop(); cancelUpload(); clearOAuth() })
</script>
<style scoped>.shop-panel { min-height: 400px; }.section-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }.accounts, .history { margin-top: 16px; }.shop-form :deep(.el-select) { min-width: 260px; }.ml-10 { margin-left: 10px; }.sub-text { color: var(--el-text-color-secondary); font-size: 12px; }</style>
