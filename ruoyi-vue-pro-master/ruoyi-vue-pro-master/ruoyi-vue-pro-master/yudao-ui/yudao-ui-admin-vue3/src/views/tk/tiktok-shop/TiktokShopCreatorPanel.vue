<template>
  <section class="creator-panel" v-loading="initializing">
    <div class="shop-toolbar"><h2 class="shop-heading">{{ tt('shop.workspaceCreator') }}</h2></div>
    <div class="shop-toolbar">
      <el-select v-model="accountId" :placeholder="tt('shop.account')" :aria-label="tt('shop.account')" @change="resetPage">
        <el-option v-for="account in accounts" :key="account.id" :value="account.id"
          :label="`${account.displayName || account.username || account.id} · ${account.market}`" />
      </el-select>
      <el-tag v-if="currentAccount" :type="shopAccountState(currentAccount.authStatus).type">{{ tt(shopAccountState(currentAccount.authStatus).label) }}</el-tag>
      <el-tooltip :content="tt('common.refresh')">
        <el-button :aria-label="tt('common.refresh')" :loading="initializing || loading" :icon="Refresh" @click="initialize" />
      </el-tooltip>
    </div>
    <div class="shop-band">
      <el-tabs v-model="view" @tab-change="resetPage">
        <el-tab-pane :label="tt('shop.creatorMarketplace')" name="marketplace" />
        <el-tab-pane :label="tt('shop.showcase')" name="showcase" />
        <el-tab-pane :label="tt('shop.creatorSamples')" name="samples" />
        <el-tab-pane :label="tt('shop.creatorOrders')" name="orders" />
      </el-tabs>
      <form v-if="view === 'marketplace'" class="shop-toolbar creator-search" @submit.prevent="search">
        <el-input v-model="keyword" maxlength="200" :placeholder="tt('shop.searchProducts')" :aria-label="tt('shop.searchProducts')" clearable />
        <el-button type="primary" native-type="submit" :loading="loading" :disabled="!selectedAccount">{{ tt('shop.searchProducts') }}</el-button>
      </form>
      <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
      <el-table :data="rows" :empty-text="tt(selectedAccount ? 'shop.sellerNoRows' : currentAccount ? shopAccountState(currentAccount.authStatus).label : 'shop.noSelection')" v-loading="loading" row-key="id" size="small">
        <el-table-column v-if="isProduct" :label="tt('shop.product')" min-width="235">
          <template #default="{ row }"><strong>{{ row.title || row.id }}</strong><div v-if="row.title" class="shop-muted">{{ row.id }}</div></template>
        </el-table-column>
        <el-table-column v-else prop="id" label="ID" min-width="170" show-overflow-tooltip />
        <el-table-column v-if="isProduct" :label="tt('shop.creatorPrice')" min-width="110">
          <template #default="{ row }">{{ row.price ? [row.price, row.currency].filter(Boolean).join(' ') : '–' }}</template>
        </el-table-column>
        <el-table-column v-if="view !== 'marketplace'" prop="status" :label="tt('common.status')" min-width="130" />
        <el-table-column v-if="view === 'samples'" prop="productId" :label="tt('shop.product')" min-width="170" show-overflow-tooltip />
        <el-table-column v-if="view === 'orders'" :label="tt('shop.creatorCreatedAt')" min-width="150">
          <template #default="{ row }">{{ row.createdAt ? formatDate(row.createdAt) : '–' }}</template>
        </el-table-column>
        <el-table-column v-if="view === 'showcase' && canPublish" min-width="150">
          <template #default="{ row }">
            <el-button v-if="row.product?.available && row.product?.selectionProof" type="primary" link @click="publish(row.product)">
              {{ tt('shop.creatorSelectForPublish') }}
            </el-button>
          </template>
        </el-table-column>
        <el-table-column v-if="isProduct && canManageShowcase" min-width="145">
          <template #default="{ row }">
            <el-button v-if="view === 'marketplace'" type="primary" link :disabled="changingShowcase" @click="changeShowcase(row, true)">
              {{ tt('shop.creatorShowcaseAdd') }}
            </el-button>
            <el-button v-else type="danger" link :disabled="changingShowcase" @click="changeShowcase(row, false)">
              {{ tt('shop.creatorShowcaseRemove') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="creator-pager">
        <el-button :disabled="!selectedAccount || loading || cursors.length <= 1" @click="previousPage">{{ tt('shop.creatorPrevious') }}</el-button>
        <el-button :disabled="!selectedAccount || loading || !nextCursor" @click="nextPage">{{ tt('shop.nextProducts') }}</el-button>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { isAxiosError } from 'axios'
import { TiktokShopApi, type ShopAccount, type ShopCreatorRow, type ShopCreatorView, type ShopProduct, type ShopPublishSelection } from '@/api/tk/tiktokShop'
import { hasPermission } from '@/directives/permission/hasPermi'
import { useTkI18n } from '@/hooks/web/useTkI18n'
import { createShopRevision } from '../video-publish-center/components/tiktokShopBehavior'
import { shopAccountState } from './shopPresentation'

const { tt, locale } = useTkI18n()
const emit = defineEmits<{ publish: [selection: ShopPublishSelection] }>()
const canPublish = computed(() => hasPermission(['tk:tiktok-shop-publish:create']))
const canManageShowcase = computed(() => hasPermission(['tk:tiktok-shop-creator:showcase']))
const changingShowcase = ref(false)
const accounts = ref<ShopAccount[]>([])
const accountId = ref<number>()
const currentAccount = computed(() => accounts.value.find((account) => account.id === accountId.value))
const selectedAccount = computed(() => currentAccount.value?.authStatus === 'AUTHORIZED' ? currentAccount.value : undefined)
const view = ref<ShopCreatorView | 'showcase'>('marketplace')
const isProduct = computed(() => view.value === 'marketplace' || view.value === 'showcase')
const keyword = ref('')
const appliedKeyword = ref('')
const rows = ref<(ShopCreatorRow & { product?: ShopProduct })[]>([])
const cursors = ref<(string | undefined)[]>([undefined])
const nextCursor = ref<string>()
const initializing = ref(false)
const loading = ref(false)
const error = ref('')
const revision = createShopRevision()
const accountRevision = createShopRevision()

async function initialize() {
  const requestRevision = accountRevision.begin()
  initializing.value = true
  error.value = ''
  try {
    const data = await TiktokShopApi.accounts()
    if (!accountRevision.current(requestRevision)) return
    accounts.value = data
    if (!currentAccount.value) accountId.value = (data.find((account) => account.authStatus === 'AUTHORIZED') || data[0])?.id
    await resetPage()
  } catch (reason) {
    if (accountRevision.current(requestRevision)) error.value = errorText(reason)
  } finally {
    if (accountRevision.current(requestRevision)) initializing.value = false
  }
}

async function loadPage(stack: (string | undefined)[]) {
  const requestRevision = revision.begin()
  const account = selectedAccount.value?.id
  const dataset = view.value
  const pageToken = stack[stack.length - 1]
  rows.value = []
  nextCursor.value = undefined
  error.value = ''
  loading.value = !!account
  if (!account) return
  try {
    const result = dataset === 'showcase'
      ? await TiktokShopApi.products(account, { source: 'SHOWCASE', pageSize: 20, pageToken }).then((page) => ({
          rows: page.products.map((product) => ({ id: product.id, title: product.title, price: product.price, currency: product.currency, status: product.available ? tt('shop.creatorAvailable') : tt('shop.unavailable'), product })),
          nextPageToken: page.nextPageToken
        }))
      : await TiktokShopApi.creatorPage(account, dataset, { pageToken, keyword: dataset === 'marketplace' ? appliedKeyword.value : undefined })
    if (!revision.current(requestRevision)) return
    rows.value = result.rows
    cursors.value = stack
    if (result.nextPageToken && !stack.includes(result.nextPageToken)) nextCursor.value = result.nextPageToken
  } catch (reason) {
    if (revision.current(requestRevision)) {
      const accessDenied = dataset === 'marketplace' && isAxiosError<{ code?: number }>(reason) &&
        reason.response?.status === 403 && reason.response.data?.code === 105005
      error.value = accessDenied ? tt('shop.creatorMarketplaceAccessDenied') : errorText(reason)
    }
  } finally {
    if (revision.current(requestRevision)) loading.value = false
  }
}

function errorText(reason: unknown) { return reason instanceof Error ? reason.message : tt('shop.operationFailed') }
function formatDate(seconds: number) { return new Date(seconds * 1000).toLocaleString(locale.value) }
function resetPage() { cursors.value = [undefined]; return loadPage(cursors.value) }
function search() { if (!selectedAccount.value) return; appliedKeyword.value = keyword.value.trim(); return resetPage() }
function previousPage() { if (cursors.value.length > 1) return loadPage(cursors.value.slice(0, -1)) }
function nextPage() { if (nextCursor.value) return loadPage([...cursors.value, nextCursor.value]) }
function publish(product: ShopProduct) {
  if (selectedAccount.value && canPublish.value && product.available && product.selectionProof) {
    emit('publish', { accountId: selectedAccount.value.id, product })
  }
}

async function changeShowcase(row: ShopCreatorRow, add: boolean) {
  const account = selectedAccount.value
  const dataset = view.value
  if (!account || !canManageShowcase.value || changingShowcase.value) return
  const action = tt(add ? 'shop.creatorShowcaseAdd' : 'shop.creatorShowcaseRemove')
  changingShowcase.value = true
  try {
    try {
      await ElMessageBox.confirm(`${account.displayName || account.username || account.id} · ${row.title || row.id}`, action)
    } catch {
      return
    }
    if (account.id !== selectedAccount.value?.id || dataset !== view.value || !canManageShowcase.value) return
    if (add) await TiktokShopApi.addToShowcase(account.id, row.id)
    else await TiktokShopApi.removeFromShowcase(account.id, row.id)
    if (account.id === accountId.value && dataset === view.value) {
      ElMessage.success(tt('shop.creatorShowcaseUpdated'))
      if (!add) await loadPage(cursors.value)
    }
  } catch {
    if (account.id === accountId.value && dataset === view.value) error.value = tt('shop.creatorShowcaseUncertain')
  } finally {
    changingShowcase.value = false
  }
}

onMounted(initialize)
onBeforeUnmount(() => { accountRevision.invalidate(); revision.invalidate() })
</script>

<style scoped>
.creator-panel { min-width: 0; padding: 4px 0 16px; }
.creator-pager { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin: 12px 0; }
.creator-search .el-input { width: min(100%, 320px); }
.creator-panel .el-alert { margin-bottom: 10px; }
.creator-pager { justify-content: flex-end; }
.creator-pager :deep(.el-button + .el-button) { margin-left: 0; }
</style>
