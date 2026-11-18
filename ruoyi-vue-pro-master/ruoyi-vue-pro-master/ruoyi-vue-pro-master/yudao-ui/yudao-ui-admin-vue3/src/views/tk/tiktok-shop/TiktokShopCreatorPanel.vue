<template>
  <section class="creator-panel" v-loading="initializing">
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <div class="creator-toolbar">
      <el-select v-model="accountId" :placeholder="tt('shop.account')" :aria-label="tt('shop.account')" @change="resetPage">
        <el-option v-for="account in accounts" :key="account.id" :value="account.id"
          :label="`${account.displayName || account.username || account.id} · ${account.market}`" />
      </el-select>
      <el-tooltip :content="tt('common.refresh')">
        <el-button :aria-label="tt('common.refresh')" :loading="initializing || loading" @click="initialize">
          <Icon icon="ep:refresh" />
        </el-button>
      </el-tooltip>
    </div>
    <template v-if="accountId">
      <el-tabs v-model="view" @tab-change="resetPage">
        <el-tab-pane :label="tt('shop.creatorMarketplace')" name="marketplace" />
        <el-tab-pane :label="tt('shop.showcase')" name="showcase" />
        <el-tab-pane :label="tt('shop.creatorSamples')" name="samples" />
        <el-tab-pane :label="tt('shop.creatorOrders')" name="orders" />
      </el-tabs>
      <form v-if="view === 'marketplace'" class="creator-toolbar" @submit.prevent="search">
        <el-input v-model="keyword" maxlength="200" :placeholder="tt('shop.searchProducts')" :aria-label="tt('shop.searchProducts')" clearable />
        <el-button type="primary" native-type="submit" :loading="loading">{{ tt('shop.searchProducts') }}</el-button>
      </form>
      <el-table :data="rows" v-loading="loading" row-key="id" size="small">
        <el-table-column prop="id" label="ID" min-width="170" show-overflow-tooltip />
        <el-table-column v-if="isProduct" prop="title" :label="tt('shop.product')" min-width="210" show-overflow-tooltip />
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
        <el-button :disabled="loading || cursors.length <= 1" @click="previousPage">{{ tt('shop.creatorPrevious') }}</el-button>
        <el-button :disabled="loading || !nextCursor" @click="nextPage">{{ tt('shop.nextProducts') }}</el-button>
      </div>
    </template>
    <el-empty v-else-if="!initializing && !error" :description="tt('shop.accountRequired')" />
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { TiktokShopApi, type ShopAccount, type ShopCreatorRow, type ShopCreatorView, type ShopProduct, type ShopPublishSelection } from '@/api/tk/tiktokShop'
import { hasPermission } from '@/directives/permission/hasPermi'
import { useTkI18n } from '@/hooks/web/useTkI18n'
import { createShopRevision } from '../video-publish-center/components/tiktokShopBehavior'

const { tt, locale } = useTkI18n()
const emit = defineEmits<{ publish: [selection: ShopPublishSelection] }>()
const canPublish = computed(() => hasPermission(['tk:tiktok-shop-publish:create']))
const canManageShowcase = computed(() => hasPermission(['tk:tiktok-shop-creator:showcase']))
const changingShowcase = ref(false)
const accounts = ref<ShopAccount[]>([])
const accountId = ref<number>()
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
    accounts.value = data.filter((account) => account.authStatus === 'AUTHORIZED')
    if (!accounts.value.some((account) => account.id === accountId.value)) accountId.value = accounts.value[0]?.id
    await resetPage()
  } catch (reason) {
    if (accountRevision.current(requestRevision)) error.value = errorText(reason)
  } finally {
    if (accountRevision.current(requestRevision)) initializing.value = false
  }
}

async function loadPage(stack: (string | undefined)[]) {
  const requestRevision = revision.begin()
  const account = accountId.value
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
    if (revision.current(requestRevision)) error.value = errorText(reason)
  } finally {
    if (revision.current(requestRevision)) loading.value = false
  }
}

function errorText(reason: unknown) { return reason instanceof Error ? reason.message : tt('shop.operationFailed') }
function formatDate(seconds: number) { return new Date(seconds * 1000).toLocaleString(locale.value) }
function resetPage() { cursors.value = [undefined]; return loadPage(cursors.value) }
function search() { appliedKeyword.value = keyword.value.trim(); return resetPage() }
function previousPage() { if (cursors.value.length > 1) return loadPage(cursors.value.slice(0, -1)) }
function nextPage() { if (nextCursor.value) return loadPage([...cursors.value, nextCursor.value]) }
function publish(product: ShopProduct) {
  if (accountId.value && canPublish.value && product.available && product.selectionProof) {
    emit('publish', { accountId: accountId.value, product })
  }
}

async function changeShowcase(row: ShopCreatorRow, add: boolean) {
  const account = accounts.value.find((item) => item.id === accountId.value)
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
    if (account.id !== accountId.value || dataset !== view.value || !canManageShowcase.value) return
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
.creator-toolbar, .creator-pager { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin: 12px 0; }
.creator-toolbar :is(.el-select, .el-input) { width: min(100%, 300px); }
.creator-pager { justify-content: flex-end; }
.creator-pager :deep(.el-button + .el-button) { margin-left: 0; }
</style>
