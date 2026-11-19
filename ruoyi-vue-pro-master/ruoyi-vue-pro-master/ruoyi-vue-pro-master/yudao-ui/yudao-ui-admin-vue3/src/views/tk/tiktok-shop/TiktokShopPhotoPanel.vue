<template>
  <section v-loading="loading">
    <div class="shop-toolbar"><h2 class="shop-heading">{{ tt('shop.workspacePhotos') }}</h2></div>
    <el-alert v-if="error" type="error" :title="error" :closable="false" show-icon />
    <el-alert v-if="capabilities && !capabilities.publishingConfigured" type="warning" :title="tt('shop.publishingNotConfigured')" :closable="false" />
    <div class="shop-toolbar">
      <el-select v-model="accountId" :placeholder="tt('shop.account')" :aria-label="tt('shop.account')" :disabled="submitting || reviewing">
        <el-option v-for="item in accounts" :key="item.id" :value="item.id" :label="`${item.displayName || item.username || item.id} · ${item.market}`" />
      </el-select>
      <el-tag v-if="selectedAccount" :type="shopAccountState(selectedAccount.authStatus).type">{{ tt(shopAccountState(selectedAccount.authStatus).label) }}</el-tag>
      <el-tooltip :content="tt('shop.refreshAccounts')"><el-button :aria-label="tt('shop.refreshAccounts')" :disabled="submitting || reviewing" :icon="Refresh" @click="initialize" /></el-tooltip>
    </div>
    <el-form v-if="canCreate" label-position="top" class="shop-editor shop-band" :disabled="submitting || reviewing">
      <section>
      <h3 class="shop-heading photo-heading">{{ tt('shop.contentSection') }}</h3>
      <el-form-item :label="tt('shop.photoFiles')">
        <el-upload accept="image/jpeg,image/png" multiple :auto-upload="false" :show-file-list="false" :limit="9" :on-change="addFile" :on-exceed="invalidImages" v-model:file-list="uploadFiles">
          <el-button>{{ tt('shop.photoPick') }}</el-button>
        </el-upload>
        <div v-if="images.length" class="photo-grid">
          <figure v-for="(image, index) in images" :key="image.url">
            <img :src="image.url" :alt="image.file.name" />
            <figcaption>{{ index + 1 }} · {{ image.file.name }}</figcaption>
            <el-button text type="danger" @click="removeFile(index)">{{ tt('shop.photoRemove') }}</el-button>
          </figure>
        </div>
      </el-form-item>
      <el-form-item :label="tt('shop.photoCaption')"><el-input v-model="title" type="textarea" :rows="3" maxlength="5000" show-word-limit /></el-form-item>
      <el-form-item><el-checkbox v-model="isAiGenerated">{{ tt('shop.photoAi') }}</el-checkbox></el-form-item>
      </section>
      <section>
      <h3 class="shop-heading photo-heading">{{ tt('shop.productSection') }}</h3>
      <TiktokShopProductPicker v-if="ready" :key="accountId" :account-id="accountId" :disabled="submitting || reviewing" @select="selectProduct" @changed="product = undefined" />
      <p v-else-if="!authorizedAccount" class="shop-muted">{{ tt(selectedAccount ? shopAccountState(selectedAccount.authStatus).label : 'shop.noSelection') }}</p>
      <el-form-item :label="tt('shop.photoLinkTitle')" class="photo-heading"><el-input v-model="linkTitle" maxlength="29" show-word-limit /></el-form-item>
      <el-button type="primary" :disabled="!valid || Boolean(result)" :loading="submitting" @click="review">{{ tt('shop.photoReview') }}</el-button>
      </section>
    </el-form>
    <el-alert v-if="result" :title="statusLabel(result)" :description="[result.photoId, result.failCode, result.requestId].filter(Boolean).join(' · ')" :type="result.stage === 'SUBMITTED' ? 'success' : 'warning'" :closable="false" />
    <section v-if="canRead && accountId" class="shop-band shop-results">
      <div class="shop-toolbar"><h3 class="shop-heading">{{ tt('shop.photoHistory') }}</h3><span class="shop-spacer" /><el-button @click="loadHistory">{{ tt('common.refresh') }}</el-button></div>
      <el-table v-loading="loadingHistory" :data="history" size="small">
        <el-table-column prop="title" :label="tt('shop.photoCaption')" min-width="160" show-overflow-tooltip />
        <el-table-column prop="productId" :label="tt('shop.product')" min-width="130" />
        <el-table-column prop="photoId" :label="tt('shop.photoId')" min-width="145" />
        <el-table-column :label="tt('common.status')" min-width="160"><template #default="{ row }"><el-tag size="small" :type="photoTone(row)">{{ statusLabel(row) }}</el-tag></template></el-table-column>
        <el-table-column prop="failCode" :label="tt('shop.photoCode')" min-width="120" />
        <el-table-column min-width="135"><template #default="{ row }">
          <el-button v-if="row.photoId && ready" link type="primary" :loading="refreshing === row.id" :disabled="Boolean(refreshing) && refreshing !== row.id" @click="refreshStatus(row)">{{ tt('shop.photoStatus') }}</el-button>
        </template></el-table-column>
      </el-table>
      <el-pagination v-if="total > 20" v-model:current-page="pageNo" :total="total" :page-size="20" layout="prev, pager, next" @current-change="loadHistory" />
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { ElMessageBox, type UploadFile, type UploadUserFile } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { TiktokShopApi, type ShopAccount, type ShopCapabilities, type ShopPhoto, type ShopProduct, type ShopProductSource } from '@/api/tk/tiktokShop'
import { hasPermission } from '@/directives/permission/hasPermi'
import { useTkI18n } from '@/hooks/web/useTkI18n'
import TiktokShopProductPicker from '../video-publish-center/components/TiktokShopProductPicker.vue'
import { shopAccountState } from './shopPresentation'

const { tt } = useTkI18n()
const canCreate = computed(() => hasPermission(['tk:tiktok-shop-publish:create']) && hasPermission(['tk:tiktok-shop-publish:confirm']))
const canRead = computed(() => hasPermission(['tk:tiktok-shop-publish:query']))
const capabilities = ref<ShopCapabilities>(), accounts = ref<ShopAccount[]>([]), accountId = ref<number>()
const selectedAccount = computed(() => accounts.value.find((a) => a.id === accountId.value))
const authorizedAccount = computed(() => selectedAccount.value?.authStatus === 'AUTHORIZED')
const ready = computed(() => capabilities.value?.publishingConfigured && authorizedAccount.value)
const title = ref(''), linkTitle = ref(''), isAiGenerated = ref(false)
const product = ref<ShopProduct>(), source = ref<ShopProductSource>('SHOWCASE')
const images = ref<{ file: File; url: string; uid: number }[]>([]), uploadFiles = ref<UploadUserFile[]>([])
const error = ref(''), loading = ref(false), submitting = ref(false), reviewing = ref(false), loadingHistory = ref(false)
const refreshing = ref<number>(), result = ref<ShopPhoto>(), history = ref<ShopPhoto[]>([]), total = ref(0), pageNo = ref(1)
const valid = computed(() => ready.value && Boolean(product.value?.selectionProof && linkTitle.value.trim() && images.value.length > 0))
let key = '', revision = 0, historyRevision = 0

onMounted(initialize)
onUnmounted(() => { revision++; historyRevision++; clearImages() })
watch([title, linkTitle, isAiGenerated, product, source, images], () => { key = ''; result.value = undefined })
watch([accountId, ready], ([account, canPublish], [previousAccount]) => {
  if (account !== previousAccount || !canPublish) { product.value = undefined; key = ''; result.value = undefined }
  if (account !== previousAccount) { pageNo.value = 1; history.value = []; total.value = 0; void loadHistory() }
})

function fail(reason: unknown) { error.value = reason instanceof Error ? reason.message : tt('shop.operationFailed') }
function statusLabel(row: ShopPhoto) {
  if (row.postStatus) return row.postStatus
  return tt(`shop.photoStage.${row.stage}`)
}
function photoTone(row: ShopPhoto) {
  if (row.stage === 'FAILED') return 'danger'
  if (!row.postStatus && row.stage === 'SUBMITTED') return 'primary'
  return row.stage === 'UNKNOWN' ? 'warning' : 'info'
}
async function initialize() {
  const current = ++revision
  loading.value = true; error.value = ''
  try {
    const [settings, rows] = await Promise.all([TiktokShopApi.capabilities(), TiktokShopApi.accounts()])
    if (current !== revision) return
    capabilities.value = settings; accounts.value = rows
    if (!rows.some((a) => a.id === accountId.value)) accountId.value = (rows.find((a) => a.authStatus === 'AUTHORIZED') || rows[0])?.id
  } catch (reason) { if (current === revision) fail(reason) }
  finally { if (current === revision) loading.value = false }
}
function selectProduct(value: ShopProduct, itemSource: ShopProductSource) { product.value = value; source.value = itemSource }
function invalidImages() { error.value = tt('shop.photoInvalidFiles') }
function addFile(item: UploadFile) {
  const file = item.raw
  if (!file || !['image/jpeg', 'image/png'].includes(file.type) || !file.size || file.size > 10 * 1024 * 1024) {
    uploadFiles.value = uploadFiles.value.filter((entry) => entry.uid !== item.uid); invalidImages(); return
  }
  error.value = ''; images.value = [...images.value, { file, url: URL.createObjectURL(file), uid: item.uid }]
}
function removeFile(index: number) {
  const image = images.value[index]
  if (!image) return
  URL.revokeObjectURL(image.url); images.value = images.value.filter((_, i) => i !== index)
  uploadFiles.value = uploadFiles.value.filter((item) => item.uid !== image.uid)
}
function clearImages() { images.value.forEach((image) => URL.revokeObjectURL(image.url)); images.value = []; uploadFiles.value = [] }
async function loadHistory() {
  const current = ++historyRevision, account = accountId.value
  loadingHistory.value = false
  if (!account || !canRead.value) return
  loadingHistory.value = true
  try {
    const page = await TiktokShopApi.photoHistory(account, pageNo.value)
    if (current === historyRevision) { history.value = page.list; total.value = page.total }
  } catch (reason) { if (current === historyRevision) fail(reason) }
  finally { if (current === historyRevision) loadingHistory.value = false }
}
async function refreshStatus(row: ShopPhoto) {
  const account = accountId.value, current = historyRevision
  if (!account || refreshing.value) return
  refreshing.value = row.id; error.value = ''
  try {
    const updated = await TiktokShopApi.photoStatus(account, row.id)
    if (account === accountId.value && current === historyRevision) {
      history.value = history.value.map((item) => item.id === row.id ? updated : item)
      if (result.value?.id === row.id) result.value = updated
    }
  } catch (reason) { if (account === accountId.value) fail(reason) }
  finally { refreshing.value = undefined }
}
async function review() {
  const account = accountId.value, selected = product.value
  if (!canCreate.value || !valid.value || !account || !selected?.selectionProof || submitting.value || reviewing.value || result.value) return
  error.value = ''; reviewing.value = true
  try {
    const name = accounts.value.find((item) => item.id === account)?.displayName || account
    await ElMessageBox.confirm(`${name} · ${selected.title || selected.id} · ${images.value.length} ${tt('shop.photoFiles')}`, tt('shop.photoConfirm'), { type: 'warning' })
  } catch { return } finally { reviewing.value = false }
  if (!canCreate.value || !valid.value || account !== accountId.value || selected !== product.value) return
  submitting.value = true
  try {
    key ||= globalThis.crypto.randomUUID()
    result.value = await TiktokShopApi.publishPhotos({ accountId: account, title: title.value, productId: selected.id, productSource: source.value,
      selectionProof: selected.selectionProof, linkTitle: linkTitle.value, idempotencyKey: key, confirm: true, isAiGenerated: isAiGenerated.value }, images.value.map((image) => image.file))
    await loadHistory()
  } catch { error.value = tt('shop.photoUncertain'); await loadHistory() }
  finally { submitting.value = false }
}
</script>

<style scoped>
.photo-heading { margin: 0 0 14px; }
.el-form-item.photo-heading { margin-top: 12px; }
.photo-grid { flex: 0 0 100%; display: grid; grid-template-columns: repeat(auto-fill,minmax(108px,1fr)); gap: 8px; margin-top: 8px; }
.photo-grid figure { min-width: 0; margin: 0; border: 1px solid var(--el-border-color); border-radius: 6px; overflow: hidden; }
.photo-grid img { display: block; width: 100%; aspect-ratio: 1; object-fit: contain; background: var(--el-fill-color-light); }
.photo-grid figcaption { padding: 0 6px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; font-size: 12px; }
.el-alert { margin: 12px 0; }
.el-pagination { justify-content: flex-end; margin-top: 12px; }
</style>
