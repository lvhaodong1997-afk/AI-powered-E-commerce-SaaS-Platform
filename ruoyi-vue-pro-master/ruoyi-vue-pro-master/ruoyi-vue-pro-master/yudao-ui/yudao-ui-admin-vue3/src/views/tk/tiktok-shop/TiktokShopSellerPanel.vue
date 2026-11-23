<template>
  <section v-loading="loadingAccounts">
    <div class="shop-toolbar"><h2 class="shop-heading">{{ tt(analytics ? 'shop.workspaceAnalytics' : 'shop.workspaceSeller') }}</h2></div>
    <div class="shop-toolbar">
      <el-select v-model="accountId" :placeholder="tt('shop.sellerAccount')" :aria-label="tt('shop.sellerAccount')">
        <el-option v-for="account in accounts" :key="account.id" :value="account.id"
          :label="`${account.displayName || account.id} · ${tt(shopAccountState(account.authStatus).label)}`" />
      </el-select>
      <el-select v-model="shopId" :loading="loadingShops" :disabled="!authorized" :placeholder="tt('shop.sellerStore')" :aria-label="tt('shop.sellerStore')">
          <el-option v-for="shop in shops" :key="shop.id" :value="shop.id" :label="`${shop.name} (#${shop.id})`" />
      </el-select>
      <el-tooltip :content="tt('shop.refreshAccounts')"><el-button :aria-label="tt('shop.refreshAccounts')" :icon="Refresh" @click="loadAccounts" /></el-tooltip>
    </div>
    <section class="shop-band shop-results">
      <el-tabs v-model="view" :aria-label="tt('shop.sellerReport')">
        <el-tab-pane v-for="item in views" :key="item" :name="item" :label="tt(`shop.sellerView.${item}`)" />
      </el-tabs>
      <div class="shop-toolbar">
        <template v-if="analytics">
          <el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD" :clearable="false" unlink-panels
            :start-placeholder="tt('shop.sellerStart')" :end-placeholder="tt('shop.sellerEnd')" />
          <el-select v-if="view === 'LIVES'" v-model="liveAccountType" :aria-label="tt('shop.sellerAccountType')">
            <el-option v-for="item in liveTypes" :key="item" :value="item" :label="tt(`shop.sellerView.${item}`)" />
          </el-select>
        </template>
        <el-button type="primary" :loading="loading" :disabled="!authorized || !shopId" @click="query()">{{ tt('shop.sellerQuery') }}</el-button>
        <span v-if="page?.latestAvailableDate" class="shop-muted">{{ tt('shop.sellerDataThrough') }} {{ page.latestAvailableDate }}</span>
      </div>
      <el-alert v-if="error" type="error" :title="error" :closable="false" show-icon />
      <el-table v-else v-loading="loading" :data="page?.rows || []" :empty-text="tt(!authorized ? 'shop.sellerNoAccount' : !shopId ? 'shop.sellerNoStore' : !page ? 'shop.sellerAwaitingQuery' : 'shop.sellerNoRows')" size="small">
        <el-table-column v-for="key in fields[view]" :key="key" :label="tt(`shop.sellerField.${analytics && key === 'amount' ? 'gmv' : key}`)" :min-width="key === 'title' ? 210 : 114"
          :align="numericFields.has(key) ? 'right' : 'left'" show-overflow-tooltip>
          <template #default="{ row }">{{ display(row, key) }}</template>
        </el-table-column>
      </el-table>
      <footer v-if="!error && page && (cursors.length > 1 || page.nextPageToken)" class="shop-toolbar page-controls">
        <el-button :disabled="loading || cursors.length < 2" @click="previous">{{ tt('shop.creatorPrevious') }}</el-button>
        <el-button :disabled="loading || !page?.nextPageToken" @click="next">{{ tt('shop.nextProducts') }}</el-button>
      </footer>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { isAxiosError } from 'axios'
import { SHOP_REAUTH_REQUIRED_CODE, TiktokShopApi, type SellerAccount, type SellerShop, type ShopAnalyticsView, type ShopSellerPage, type ShopSellerView } from '@/api/tk/tiktokShop'
import { useTkI18n } from '@/hooks/web/useTkI18n'
import { formatShopDate, shopAccountState } from './shopPresentation'

type View = ShopSellerView | ShopAnalyticsView
const props = defineProps<{ analytics: boolean }>()
const { tt, locale } = useTkI18n()
const numericFields = new Set(['views', 'impressions', 'clicks', 'sold', 'amount'])
const views = computed<View[]>(() => props.analytics ? ['VIDEOS', 'LIVES'] : ['PRODUCTS', 'ORDERS', 'PACKAGES', 'RETURNS', 'STATEMENTS', 'PROMOTIONS'])
const fields: Record<View, string[]> = {
  PRODUCTS: ['id', 'title', 'status', 'time'], ORDERS: ['id', 'status', 'time', 'amount'],
  PACKAGES: ['id', 'status', 'carrier', 'tracking', 'time'], RETURNS: ['id', 'orderId', 'status', 'time', 'amount'],
  STATEMENTS: ['id', 'status', 'time', 'amount'], PROMOTIONS: ['id', 'title', 'type', 'status', 'time', 'endTime'],
  VIDEOS: ['id', 'title', 'time', 'views', 'impressions', 'clicks', 'sold', 'amount'],
  LIVES: ['id', 'title', 'time', 'endTime', 'views', 'impressions', 'clicks', 'sold', 'amount']
}
const liveTypes = ['OFFICIAL_ACCOUNTS', 'MARKETING_ACCOUNTS', 'AFFILIATE_ACCOUNTS']
const view = ref<View>(views.value[0])
const liveAccountType = ref(liveTypes[0])
const day = (offset: number) => { const date = new Date(); date.setDate(date.getDate() + offset); return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}` }
const range = ref<[string, string]>([day(-7), day(0)])
const accounts = ref<SellerAccount[]>([])
const accountId = ref<number>()
const authorized = computed(() => accounts.value.some((a) => a.id === accountId.value && a.authStatus === 'AUTHORIZED'))
const allShops = ref<SellerShop[]>([])
const shops = computed(() => allShops.value.filter((shop) => shop.accountId === accountId.value && shop.status === 'AUTHORIZED'))
const shopId = ref<number>(), page = ref<ShopSellerPage>(), cursors = ref<(string | undefined)[]>([])
const loading = ref(false), loadingAccounts = ref(false), loadingShops = loadingAccounts, error = ref('')
let accountRevision = 0, pageRevision = 0

function failure(reason: unknown) {
  if (isAxiosError<{ code?: number }>(reason)) {
    error.value = tt(reason.response?.status === 409 && reason.response.data?.code === SHOP_REAUTH_REQUIRED_CODE
      ? 'shop.sellerReauthorize' : 'shop.operationFailed')
  } else {
    error.value = reason instanceof Error ? reason.message : tt('shop.operationFailed')
  }
}
function clearPage() { pageRevision++; loading.value = false; page.value = undefined; cursors.value = [] }
watch([accountId, shopId, view, range, liveAccountType], () => { error.value = ''; clearPage() })
watch(shops, (available) => {
  if (!available.some((shop) => shop.id === shopId.value)) shopId.value = available[0]?.id
})
onMounted(loadAccounts)
onUnmounted(() => { accountRevision++; pageRevision++ })

async function loadAccounts() {
  const revision = ++accountRevision
  loadingAccounts.value = true; error.value = ''
  try {
    const [rows, available] = await Promise.all([TiktokShopApi.sellerAccounts(undefined, 'inline'), TiktokShopApi.sellerShops(undefined, 'inline')])
    if (revision !== accountRevision) return
    const prior = accountId.value
    accounts.value = rows; allShops.value = available
    accountId.value = rows.some((a) => a.id === prior) ? prior : (rows.find((a) => a.authStatus === 'AUTHORIZED') || rows[0])?.id
    clearPage()
  } catch (reason) { if (revision === accountRevision) failure(reason) }
  finally { if (revision === accountRevision) loadingAccounts.value = false }
}
async function query(trail: (string | undefined)[] = [undefined]) {
  if (!accountId.value || !shopId.value || !authorized.value || loading.value) return
  const revision = ++pageRevision
  const params = { shopId: shopId.value, cursor: trail[trail.length - 1] }
  loading.value = true; error.value = ''
  try {
    const result = props.analytics
      ? await TiktokShopApi.sellerAnalytics({ ...params, view: view.value as ShopAnalyticsView, start: range.value[0], end: range.value[1], liveAccountType: view.value === 'LIVES' ? liveAccountType.value : undefined })
      : await TiktokShopApi.sellerOperations({ ...params, view: view.value as ShopSellerView })
    if (revision === pageRevision) { page.value = result; cursors.value = trail }
  } catch (reason) {
    if (revision === pageRevision) {
      if (isAxiosError<{ code?: number }>(reason) && reason.response?.status === 403 && reason.response.data?.code === 105005) {
        error.value = tt(props.analytics ? 'shop.sellerAnalyticsDenied' : 'shop.sellerFeatureDenied')
      } else failure(reason)
    }
  }
  finally { if (revision === pageRevision) loading.value = false }
}
function next() { const token = page.value?.nextPageToken; if (token) void query([...cursors.value, token]) }
function previous() { if (cursors.value.length > 1) void query(cursors.value.slice(0, -1)) }
function display(row: Record<string, string>, key: string) {
  const value = row[key]
  if (!value) return '-'
  if (key === 'amount') return [value, row.currency].filter(Boolean).join(' ')
  if ((key === 'time' || key === 'endTime') && /^\d{10}(?:\d{3})?$/.test(value)) return formatShopDate(value, locale.value)
  return value
}
</script>

<style scoped>
.shop-toolbar :deep(.el-date-editor) { max-width: 100%; }
.page-controls { justify-content: flex-end; }
.el-alert { margin-bottom: 12px; }
</style>
