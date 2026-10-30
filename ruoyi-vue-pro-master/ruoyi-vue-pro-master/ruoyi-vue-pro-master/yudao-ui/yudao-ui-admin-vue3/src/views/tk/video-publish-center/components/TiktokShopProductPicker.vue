<template>
  <div class="shop-product-picker">
    <el-radio-group v-model="source" @change="reset">
      <el-radio-button value="SHOWCASE">{{ tt('shop.showcase') }}</el-radio-button>
      <el-radio-button value="SHOP">{{ tt('shop.shop') }}</el-radio-button>
    </el-radio-group>
    <el-input v-if="source === 'SHOP'" v-model="keyword" clearable class="keyword" :placeholder="tt('shop.searchProducts')" @keyup.enter="reset" />
    <el-button :loading="loading" @click="() => load()">{{ tt('common.refresh') }}</el-button>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-table v-loading="loading" :data="products" size="small" @row-click="select">
      <el-table-column width="64">
        <template #default="{ row }"><el-image v-if="row.imageUrl" :src="row.imageUrl" fit="cover" class="product-image" /><span v-else>—</span></template>
      </el-table-column>
      <el-table-column prop="title" :label="tt('shop.product')" min-width="180" />
      <el-table-column prop="id" label="ID" width="130" />
      <el-table-column prop="availability" :label="tt('common.status')" width="120">
        <template #default="{ row }"><el-tag :type="row.available ? 'success' : 'info'">{{ row.available ? row.availability || tt('shop.authorized') : tt('shop.unavailable') }}</el-tag></template>
      </el-table-column>
    </el-table>
    <el-button v-if="nextPageToken" text :loading="loading" @click="() => load(true)">{{ tt('shop.nextProducts') }}</el-button>
    <div v-if="selected" class="selected-product">{{ selected.title }} · {{ selected.id }}</div>
  </div>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue'
import { TiktokShopApi, type ShopProduct, type ShopProductSource } from '@/api/tk/tiktokShop'
import { useTkI18n } from '@/hooks/web/useTkI18n'
const props = defineProps<{ accountId?: number; disabled?: boolean }>()
const emit = defineEmits<{ select: [product: ShopProduct, source: ShopProductSource]; changed: [] }>()
const { tt } = useTkI18n()
const source = ref<ShopProductSource>('SHOWCASE'), keyword = ref(''), loading = ref(false), error = ref('')
const products = ref<ShopProduct[]>([]), nextPageToken = ref(''), selected = ref<ShopProduct>()
let revision = 0
watch(() => props.accountId, reset)
function reset() { revision++; products.value = []; nextPageToken.value = ''; selected.value = undefined; emit('changed'); if (props.accountId) void load() }
async function load(append = false) {
  if (!props.accountId || props.disabled) return
  const requestRevision = revision
  loading.value = true; error.value = ''
  try {
    const page = await TiktokShopApi.products(props.accountId, { source: source.value, pageSize: source.value === 'SHOP' ? 100 : 20, pageToken: append ? nextPageToken.value : undefined, titleKeyword: source.value === 'SHOP' ? keyword.value || undefined : undefined })
    if (requestRevision !== revision) return
    products.value = append ? [...products.value, ...page.products] : page.products
    nextPageToken.value = page.nextPageToken || ''
  } catch (reason) { if (requestRevision === revision) error.value = reason instanceof Error ? reason.message : tt('shop.operationFailed') } finally { if (requestRevision === revision) loading.value = false }
}
function select(product: ShopProduct) { if (!product.available || !product.selectionProof) return; selected.value = product; emit('select', product, source.value) }
</script>
<style scoped>.keyword { width: 220px; margin: 0 8px; }.product-image { width: 42px; height: 42px; }.selected-product { margin-top: 8px; color: var(--el-color-success); }</style>
