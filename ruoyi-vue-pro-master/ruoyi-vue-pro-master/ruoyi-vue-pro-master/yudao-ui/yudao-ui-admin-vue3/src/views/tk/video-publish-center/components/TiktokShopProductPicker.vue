<template>
  <div class="shop-product-picker">
    <div class="product-controls">
      <el-radio-group v-model="source" @change="reset">
        <el-radio-button value="SHOWCASE">{{ tt('shop.showcase') }}</el-radio-button>
        <el-radio-button value="SHOP">{{ tt('shop.sellerStore') }}</el-radio-button>
      </el-radio-group>
      <el-tooltip :content="tt('common.refresh')"><el-button :aria-label="tt('common.refresh')" :loading="loading" :icon="Refresh" @click="() => load()" /></el-tooltip>
    </div>
    <div v-if="source === 'SHOP'" class="product-controls product-search">
      <el-input v-model="keyword" clearable :aria-label="tt('shop.searchProducts')" :placeholder="tt('shop.searchProducts')" @keyup.enter="reset" />
      <el-button @click="reset">{{ tt('shop.searchProducts') }}</el-button>
    </div>
    <p v-if="selected" class="selected-product">{{ tt('shop.productSelected') }}: {{ selected.title || selected.id }}</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <div v-loading="loading" class="product-results">
      <ul v-if="products.length" class="product-list">
        <li v-for="row in products" :key="row.id" class="product-row" :class="{ 'is-selected': selected?.id === row.id }">
          <div class="product-image"><img v-if="row.imageUrl" :src="row.imageUrl" alt="" loading="lazy" /><el-icon v-else><Goods /></el-icon></div>
          <div class="product-name"><strong>{{ row.title || row.id }}</strong><small v-if="row.title">{{ row.id }}</small></div>
          <el-button v-if="row.available && row.selectionProof" size="small" :type="selected?.id === row.id ? 'success' : 'primary'" plain
            :aria-pressed="selected?.id === row.id" :disabled="disabled" @click="select(row)">{{ tt(selected?.id === row.id ? 'shop.productSelected' : 'shop.selectProduct') }}</el-button>
          <small v-else class="product-unavailable">{{ tt('shop.unavailable') }}</small>
        </li>
      </ul>
      <p v-else-if="!loading && !error" class="shop-muted">{{ tt('shop.sellerNoRows') }}</p>
    </div>
    <el-button v-if="nextPageToken" text :loading="loading" @click="() => load(true)">{{ tt('shop.nextProducts') }}</el-button>
  </div>
</template>
<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Goods, Refresh } from '@element-plus/icons-vue'
import { TiktokShopApi, type ShopProduct, type ShopProductSource } from '@/api/tk/tiktokShop'
import { useTkI18n } from '@/hooks/web/useTkI18n'
const props = defineProps<{ accountId?: number; disabled?: boolean }>()
const emit = defineEmits<{ select: [product: ShopProduct, source: ShopProductSource]; changed: [] }>()
const { tt } = useTkI18n()
const source = ref<ShopProductSource>('SHOWCASE'), keyword = ref(''), loading = ref(false), error = ref('')
const products = ref<ShopProduct[]>([]), nextPageToken = ref(''), selected = ref<ShopProduct>()
let revision = 0
watch(() => props.accountId, reset)
onMounted(() => { void load() })
onBeforeUnmount(() => { revision++ })
function reset() { revision++; products.value = []; nextPageToken.value = ''; selected.value = undefined; emit('changed'); if (props.accountId) void load() }
async function load(append = false) {
  if (!props.accountId || props.disabled) return
  const requestRevision = revision
  loading.value = true; error.value = ''
  try {
    const page = await TiktokShopApi.products(props.accountId, { source: source.value, pageSize: 20, pageToken: append ? nextPageToken.value : undefined, titleKeyword: source.value === 'SHOP' ? keyword.value || undefined : undefined })
    if (requestRevision !== revision) return
    products.value = append ? [...products.value, ...page.products] : page.products
    nextPageToken.value = page.nextPageToken || ''
  } catch (reason) { if (requestRevision === revision) error.value = reason instanceof Error ? reason.message : tt('shop.operationFailed') } finally { if (requestRevision === revision) loading.value = false }
}
function select(product: ShopProduct) { if (!product.available || !product.selectionProof) return; selected.value = product; emit('select', product, source.value) }
</script>
<style scoped>
.shop-product-picker { width: 100%; min-width: 0; line-height: 1.45; }
.product-controls { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 8px; }
.product-search .el-input { flex: 1 1 140px; width: auto; min-width: 0; }
.product-results { min-height: 42px; }
.product-list { list-style: none; margin: 0; padding: 0; max-height: 230px; overflow-y: auto; }
.product-row { display: grid; grid-template-columns: 38px minmax(0, 1fr) auto; align-items: center; gap: 8px; padding: 7px 4px; border-bottom: 1px solid var(--el-border-color-lighter); }
.product-row.is-selected { background: var(--el-color-success-light-9); }
.product-image { width: 38px; height: 38px; display: grid; place-items: center; background: var(--el-fill-color-light); overflow: hidden; border-radius: 4px; }
.product-image img { width: 100%; height: 100%; object-fit: contain; }
.product-name strong, .product-name small { display: block; overflow-wrap: anywhere; }
.product-name small, .product-unavailable { color: var(--el-text-color-secondary); }
.selected-product { margin: 8px 0; color: var(--el-color-success-dark-2); overflow-wrap: anywhere; }
</style>
