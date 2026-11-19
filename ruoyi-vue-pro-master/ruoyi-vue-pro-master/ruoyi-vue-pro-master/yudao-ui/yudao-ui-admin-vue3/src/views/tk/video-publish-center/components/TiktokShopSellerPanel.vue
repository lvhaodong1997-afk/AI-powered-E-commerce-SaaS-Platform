<template>
  <section class="shop-workbench seller-access" v-loading="loading">
    <div class="shop-toolbar">
      <h2 class="shop-heading">{{ tt('shop.sellerEntry') }}</h2>
      <el-tag size="small" :type="environmentType">{{ tt(environmentLabel) }}</el-tag>
    </div>
    <el-alert v-if="!loading && !capabilities?.sellerConfigured" :title="capabilities?.sellerConfigurationMessage || tt('shop.sellerNotConfigured')" type="warning" :closable="false" />
    <el-alert v-else-if="needsReauth" :title="tt('shop.sellerReauthorize')" type="warning" :closable="false" />
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <div class="shop-toolbar">
      <el-select :model-value="accountId" :placeholder="tt('shop.sellerAccount')" :aria-label="tt('shop.sellerAccount')" @change="selectAccountById">
        <el-option v-for="account in accounts" :key="account.id" :value="account.id" :label="`${account.displayName || account.id} · ${account.market}`" />
      </el-select>
      <el-select :model-value="shopId" :disabled="!sellerAuthorized" :placeholder="tt('shop.selectShop')" :aria-label="tt('shop.selectShop')" @change="selectShopById">
        <el-option v-for="shop in visibleShops" :key="shop.id" :value="shop.id" :label="`${shop.name || shop.shopId} · ${shop.region}`" />
      </el-select>
      <el-tag v-if="selectedAccount" :type="shopAccountState(selectedAccount.authStatus).type">{{ tt(shopAccountState(selectedAccount.authStatus).label) }}</el-tag>
      <el-button v-hasPermi="['tk:tiktok-shop-account:authorize']" :type="needsReauth ? 'primary' : 'default'" :disabled="!capabilities?.sellerConfigured" @click="connect">{{ tt(needsReauth ? 'shop.reconnectSeller' : 'shop.connectSeller') }}</el-button>
      <el-tooltip :content="tt('shop.refreshSeller')"><el-button :aria-label="tt('shop.refreshSeller')" :icon="Refresh" @click="load" /></el-tooltip>
    </div>
    <p v-if="!loading && capabilities?.sellerConfigured && !accounts.length" class="shop-muted">{{ tt('shop.noSellerAccounts') }}</p>
    <p v-if="selectedAccount && !visibleShops.length" class="shop-muted">{{ tt('shop.noSellerShops') }}</p>
    <details v-if="accounts.length" class="shop-details">
      <summary>{{ tt('shop.accountDetails') }}</summary>
      <el-table :data="accounts" size="small">
        <el-table-column prop="id" label="ID" width="90" />
        <el-table-column :label="tt('shop.sellerAccount')"><template #default="{ row }">{{ row.displayName || '-' }}</template></el-table-column>
        <el-table-column prop="market" :label="tt('shop.market')" width="90" />
        <el-table-column :label="tt('shop.environment')" width="110"><template #default="{ row }">{{ tt(shopEnvironmentLabel(row.environment)) }}</template></el-table-column>
        <el-table-column :label="tt('common.status')" min-width="130"><template #default="{ row }">{{ tt(shopAccountState(row.authStatus).label) }}</template></el-table-column>
        <el-table-column prop="grantedScopes" :label="tt('shop.scopes')" min-width="180" show-overflow-tooltip />
        <el-table-column :label="tt('shop.accessExpiry')" width="170"><template #default="{ row }">{{ row.authStatus === 'AUTHORIZED' ? formatShopDate(row.accessTokenExpireTime, locale) : '-' }}</template></el-table-column>
        <el-table-column width="130"><template #default="{ row }"><el-button v-hasPermi="['tk:tiktok-shop-account:authorize']" link type="danger" @click.stop="disconnect(row)">{{ tt('shop.disconnect') }}</el-button></template></el-table-column>
      </el-table>
      <h3 class="shop-heading">{{ tt('shop.sellerShops') }}</h3>
      <el-table :data="visibleShops" size="small">
        <el-table-column prop="shopId" :label="tt('shop.shop')" min-width="160" />
        <el-table-column :label="tt('shop.selectShop')"><template #default="{ row }">{{ row.name || row.shopId }}</template></el-table-column>
        <el-table-column prop="region" :label="tt('shop.market')" width="90" />
        <el-table-column :label="tt('common.status')" min-width="130"><template #default="{ row }">{{ tt(sellerAuthorized ? shopAccountState(row.status).label : 'shop.sellerShopUnavailable') }}</template></el-table-column>
        <el-table-column :label="tt('shop.environment')" width="110"><template #default="{ row }">{{ tt(shopEnvironmentLabel(row.environment)) }}</template></el-table-column>
      </el-table>
    </details>
    <section v-if="sellerAuthorized && selectedShop" class="shop-band catalog">
      <TiktokShopSellerProductActions v-if="canAuthorize()" :key="selectedShop.id" :shop="selectedShop" :products="products" :scopes="accounts.find((row) => row.id === selectedShop?.accountId)?.grantedScopes" @changed="loadCatalog" @reconnect="connect" />
      <div class="section-head">
        <strong>{{ tt('shop.sellerProducts') }}</strong>
        <div>
          <span v-if="productTotal != null" class="sub-text">{{ totalRowsLabel(productTotal) }}</span>
          <el-button :disabled="!productCursor.canBack()" @click="pageProducts('back')">{{ tt('shop.previousPage') }}</el-button>
          <el-button :disabled="!productNext" @click="pageProducts('next')">{{ tt('shop.nextProducts') }}</el-button>
        </div>
      </div>
      <el-alert v-if="productHint" :title="tt(productHint)" type="info" :closable="false" />
      <el-table v-else :data="products" size="small">
        <el-table-column prop="id" label="ID" min-width="140" />
        <el-table-column prop="title" :label="tt('shop.product')" min-width="180" />
        <el-table-column prop="status" :label="tt('common.status')" width="120" />
      </el-table>
      <p v-if="!productHint && !products.length && !catalogLoading" class="sub-text">{{ tt('shop.noSellerProducts') }}</p>
    </section>
    <section v-if="sellerAuthorized && selectedShop" class="shop-band catalog">
      <div class="section-head">
        <strong>{{ tt('shop.sellerOrders') }}</strong>
        <div>
          <span v-if="orderTotal != null" class="sub-text">{{ totalRowsLabel(orderTotal) }}</span>
          <el-button :disabled="!orderCursor.canBack()" @click="pageOrders('back')">{{ tt('shop.previousPage') }}</el-button>
          <el-button :disabled="!orderNext" @click="pageOrders('next')">{{ tt('shop.nextProducts') }}</el-button>
        </div>
      </div>
      <el-alert v-if="orderHint" :title="tt(orderHint)" type="info" :closable="false" />
      <el-table v-else :data="orders" size="small">
        <el-table-column prop="id" label="ID" min-width="140" />
        <el-table-column prop="status" :label="tt('common.status')" width="120" />
        <el-table-column prop="createTime" :label="tt('shop.orderTime')" width="180" />
        <el-table-column :label="tt('shop.orderAmount')" width="140"><template #default="{ row }">{{ [row.totalAmount, row.currency].filter(Boolean).join(' ') || '-' }}</template></el-table-column>
      </el-table>
      <p v-if="!orderHint && !orders.length && !catalogLoading" class="sub-text">{{ tt('shop.noSellerOrders') }}</p>
    </section>
  </section>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import { TiktokShopApi, type SellerAccount, type SellerOrder, type SellerProduct, type SellerShop, type ShopCapabilities } from '@/api/tk/tiktokShop'
import { acceptShopOAuthMessage, createCatalogCursor, createShopRevision, shopAuthErrorKey, shopEnvironmentLabel, shopOAuthTimeoutMs } from './tiktokShopBehavior'
import { useTkI18n } from '@/hooks/web/useTkI18n'
import { Refresh } from '@element-plus/icons-vue'
import { formatShopDate, shopAccountState } from '../../tiktok-shop/shopPresentation'
import '../../tiktok-shop/shopWorkbench.css'
import { hasPermission } from '@/directives/permission/hasPermi'
import TiktokShopSellerProductActions from './TiktokShopSellerProductActions.vue'
const { tt, locale } = useTkI18n()
const capabilities = ref<ShopCapabilities>()
const accounts = ref<SellerAccount[]>([])
const shops = ref<SellerShop[]>([])
const products = ref<SellerProduct[]>([])
const orders = ref<SellerOrder[]>([])
const accountId = ref<number>()
const shopId = ref<number>()
const error = ref('')
const loading = ref(false)
const catalogLoading = ref(false)
const productHint = ref('')
const orderHint = ref('')
const productTotal = ref<number>()
const orderTotal = ref<number>()
const productNext = ref<string>()
const orderNext = ref<string>()
const productCursor = createCatalogCursor()
const orderCursor = createCatalogCursor()
const revision = createShopRevision()
let popup: Window | undefined, oauthSession: number | undefined, oauthTimer: number | undefined
const canQuery = () => hasPermission(['tk:tiktok-shop-account:query'])
const canAuthorize = () => hasPermission(['tk:tiktok-shop-account:authorize'])
const selectedAccount = computed(() => accounts.value.find((account) => account.id === accountId.value))
const selectedShop = computed(() => shops.value.find((shop) => shop.id === shopId.value))
const visibleShops = computed(() => shops.value.filter((shop) => !accountId.value || shop.accountId === accountId.value))
const environmentLabel = computed(() => shopEnvironmentLabel(selectedAccount.value?.environment || capabilities.value?.sellerEnvironment))
const environmentType = computed(() => environmentLabel.value === 'shop.envTest' ? 'warning' : 'success')
const sellerAuthorized = computed(() => selectedAccount.value?.authStatus === 'AUTHORIZED')
const needsReauth = computed(() => !!selectedAccount.value && !sellerAuthorized.value)
function totalRowsLabel(total?: number) {
  if (total == null) return ''
  return tt('shop.totalRows').replace(/\{\{total\}\}/g, String(total))
}
function clearCatalog() {
  catalogLoading.value = false
  products.value = []; orders.value = []; productHint.value = ''; orderHint.value = ''
  productTotal.value = undefined; orderTotal.value = undefined; productNext.value = undefined; orderNext.value = undefined
  productCursor.reset(); orderCursor.reset()
}
function resetSelection() {
  revision.invalidate()
  shopId.value = undefined
  clearCatalog()
}
async function load() {
  loading.value = true
  error.value = ''
  try {
    capabilities.value = await TiktokShopApi.capabilities()
    if (!capabilities.value.sellerConfigured || !canQuery()) {
      accounts.value = []; shops.value = []; resetSelection(); return
    }
    await refreshLists()
  } catch {
    error.value = tt('shop.capabilityFailed')
  } finally {
    loading.value = false
  }
}
async function refreshLists() {
  if (!capabilities.value?.sellerConfigured || !canQuery()) return
  const requestRevision = revision.begin()
  const [nextAccounts, nextShops] = await Promise.all([TiktokShopApi.sellerAccounts(), TiktokShopApi.sellerShops()])
  if (!revision.current(requestRevision)) return
  accounts.value = nextAccounts
  shops.value = nextShops
  if (!accounts.value.some((account) => account.id === accountId.value)) accountId.value = accounts.value[0]?.id
  if (!sellerAuthorized.value || !visibleShops.value.some((shop) => shop.id === shopId.value)) resetSelection()
}
function selectAccount(account?: SellerAccount) {
  if (!account || account.id === accountId.value) return
  accountId.value = account.id
  resetSelection()
}
function selectAccountById(id: number) { selectAccount(accounts.value.find((account) => account.id === id)) }
function selectShopById(id: number) { selectShop(visibleShops.value.find((shop) => shop.id === id)) }
function selectShop(shop?: SellerShop) {
  if (!shop || !sellerAuthorized.value || shop.accountId !== accountId.value) return
  if (shop.id === shopId.value) return
  shopId.value = shop.id
  revision.invalidate()
  clearCatalog()
  void loadCatalog()
}
async function loadCatalog() {
  const shop = selectedShop.value
  const account = selectedAccount.value
  if (!shop || account?.authStatus !== 'AUTHORIZED' || shop.accountId !== account.id || !capabilities.value?.sellerConfigured) return
  const requestRevision = revision.begin()
  catalogLoading.value = true
  productHint.value = account.canReadProducts ? '' : 'shop.sellerMissingProductScope'
  orderHint.value = account.canReadOrders ? '' : 'shop.sellerMissingOrderScope'
  try {
    if (account.canReadProducts) {
      const page = await TiktokShopApi.sellerProducts(shop.id, { companyId: shop.companyId, pageSize: 20, pageToken: productCursor.token() })
      if (!revision.current(requestRevision) || shopId.value !== shop.id) return
      products.value = page.products || []
      productTotal.value = page.total
      productNext.value = page.nextPageToken
    } else {
      products.value = []; productNext.value = undefined; productTotal.value = undefined
    }
    if (account.canReadOrders) {
      const page = await TiktokShopApi.sellerOrders(shop.id, { companyId: shop.companyId, pageSize: 20, pageToken: orderCursor.token() })
      if (!revision.current(requestRevision) || shopId.value !== shop.id) return
      orders.value = page.orders || []
      orderTotal.value = page.total
      orderNext.value = page.nextPageToken
    } else {
      orders.value = []; orderNext.value = undefined; orderTotal.value = undefined
    }
  } catch {
    if (revision.current(requestRevision)) error.value = tt('shop.operationFailed')
  } finally {
    if (revision.current(requestRevision)) catalogLoading.value = false
  }
}
function pageProducts(direction: 'back' | 'next') {
  if (direction === 'next' && productNext.value) productCursor.next(productNext.value)
  else if (direction === 'back' && productCursor.canBack()) productCursor.back()
  else return
  products.value = []; productNext.value = undefined
  void loadCatalog()
}
function pageOrders(direction: 'back' | 'next') {
  if (direction === 'next' && orderNext.value) orderCursor.next(orderNext.value)
  else if (direction === 'back' && orderCursor.canBack()) orderCursor.back()
  else return
  orders.value = []; orderNext.value = undefined
  void loadCatalog()
}
function clearOAuth() {
  window.removeEventListener('message', onOAuth)
  if (oauthTimer) window.clearTimeout(oauthTimer)
  oauthTimer = undefined
  popup?.close()
  popup = undefined
  oauthSession = undefined
}
async function connect() {
  if (!capabilities.value?.sellerConfigured) return
  const origin = capabilities.value.callbackOrigin
  if (!origin || !/^https:\/\/[^/]+$/i.test(origin)) { error.value = tt('shop.invalidCapability'); return }
  clearOAuth()
  popup = window.open('about:blank', 'tiktok-shop-seller-auth', 'width=520,height=720') || undefined
  if (!popup) { error.value = tt('shop.popupBlocked'); return }
  window.addEventListener('message', onOAuth)
  try {
    const session = await TiktokShopApi.authorize({ authType: 'SELLER' })
    oauthSession = session.sessionId
    popup.location.href = session.authorizationUrl
    oauthTimer = window.setTimeout(() => { clearOAuth() }, shopOAuthTimeoutMs(session))
  } catch {
    clearOAuth()
    error.value = tt('shop.authorizationFailed')
  }
}
function onOAuth(event: MessageEvent) {
  if (!popup || oauthSession === undefined || !acceptShopOAuthMessage(event, { callbackOrigin: capabilities.value?.callbackOrigin || '', sessionId: oauthSession, authType: 'SELLER' }, popup)) return
  const status = event.data.status
  clearOAuth()
  if (status === 'SUCCESS') void refreshLists()
  else error.value = tt(shopAuthErrorKey(event.data.errorCode))
}
async function disconnect(account: SellerAccount) {
  await ElMessageBox.confirm(tt('shop.disconnectConfirm'), tt('shop.disconnect'))
  await TiktokShopApi.disconnectSeller(account.id)
  if (accountId.value === account.id) { accountId.value = undefined; resetSelection() }
  await refreshLists()
}
onMounted(() => { void load() })
onBeforeUnmount(() => { revision.invalidate(); clearOAuth() })
</script>
<style scoped>
.section-head { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 8px; }
.shop-details > .shop-heading { margin: 16px 0 8px; }
.seller-access > .el-alert { margin: 8px 0; }
.sub-text { color: var(--el-text-color-secondary); font-size: 12px; }
</style>
