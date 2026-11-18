<template>
  <div v-if="capabilities?.sandbox" class="seller-write">
    <div class="write-toolbar">
      <el-tag type="warning">{{ label('美国沙盒', 'US sandbox') }}</el-tag>
      <el-button v-if="!capabilities.granted" type="primary" plain @click="$emit('reconnect')">
        {{ label('重新授权商品权限', 'Reconnect product permissions') }}
      </el-button>
      <template v-else>
        <el-button type="primary" :disabled="!capabilities.canCreate" @click="openCreate">
          {{ label('新增商品', 'Add product') }}
        </el-button>
        <el-button :disabled="!activeProducts.length" @click="openPrice">{{ label('修改售价', 'Update price') }}</el-button>
      </template>
    </div>
    <el-alert v-if="!capabilities.granted" title="Product Modify · seller.product.write" type="info" :closable="false" />
    <el-alert v-else-if="!capabilities.canCreate" :title="label('此店铺的刊登模式尚未确认', 'Listing mode is unverified for this shop')" type="info" :closable="false" />
    <el-alert v-if="created" type="success" :closable="false" :title="[label('商品', 'Product'), created.productId, created.status, created.auditStatus, created.requestId].filter(Boolean).join(' · ')" />
  </div>

  <el-dialog v-model="createOpen" :title="label('新增沙盒商品', 'Add sandbox product')" width="min(720px, 94vw)" :close-on-click-modal="false" :show-close="!busy">
    <el-form label-position="top" :disabled="busy || !!pendingCreate">
      <el-form-item :label="label('目标店铺', 'Target shop')">{{ shop.name || shop.shopId }} · {{ shop.shopId }}</el-form-item>
      <el-form-item :label="label('商品标题（英语）', 'Product title (English)')" required><el-input v-model="form.title" maxlength="255" /></el-form-item>
      <el-form-item :label="label('商品描述（英语）', 'Description (English)')" required><el-input v-model="form.description" type="textarea" maxlength="1500" :rows="3" /></el-form-item>
      <el-form-item :label="label('类目', 'Category')" required>
        <el-select v-model="form.categoryId" filterable remote :remote-method="findCategories" :loading="categoryLoading" :placeholder="label('输入英文类目关键词', 'Enter an English category keyword')" @change="loadCategory">
          <el-option v-for="option in categories" :key="option.id" :label="option.name" :value="option.id" />
        </el-select>
      </el-form-item>
      <el-alert v-if="category && !category.supported" :title="label('该类目有此表单不支持的必填项，请选择常规类目', 'Select a category without additional mandatory documentation')" type="warning" :closable="false" />
      <template v-for="attribute in category?.attributes || []" :key="attribute.id">
        <el-form-item v-if="attribute.required" :label="attribute.name" required>
          <el-select v-if="attribute.options.length" v-model="form.attributes[attribute.id]" filterable :allow-create="attribute.custom">
            <el-option v-for="option in attribute.options" :key="option.id" :label="option.name" :value="option.id" />
          </el-select>
          <el-input v-else v-model="form.attributes[attribute.id]" maxlength="100" />
        </el-form-item>
      </template>
      <el-form-item :label="label('商品图片（JPG / PNG）', 'Product images (JPG / PNG)')" required>
        <input type="file" accept="image/jpeg,image/png" multiple :disabled="busy || uploading || !!pendingCreate" @change="upload" />
        <span v-if="uploading">{{ label('上传中…', 'Uploading…') }}</span>
        <div v-for="(image, index) in form.images" :key="image + index" class="uploaded-image">
          <span>{{ imageNames[index] }}</span>
          <el-button link type="danger" @click="removeImage(index)">{{ label('移除', 'Remove') }}</el-button>
        </div>
      </el-form-item>
      <div class="write-grid">
        <el-form-item label="SKU" required><el-input v-model="form.sellerSku" maxlength="50" /></el-form-item>
        <el-form-item :label="label('仓库', 'Warehouse')" required>
          <el-select v-if="warehouses.length" v-model="form.warehouseId" filterable>
            <el-option v-for="warehouse in warehouses" :key="warehouse.id" :label="warehouse.name" :value="warehouse.id" />
          </el-select>
          <el-input v-else v-model="form.warehouseId" placeholder="Warehouse ID" inputmode="numeric" maxlength="32" />
        </el-form-item>
        <el-form-item :label="label('售价（USD）', 'Price (USD)')" required><el-input v-model="form.price" inputmode="decimal" /></el-form-item>
        <el-form-item :label="label('库存数量', 'Stock quantity')" required><el-input-number v-model="form.quantity" :min="0" :max="999999" :precision="0" /></el-form-item>
        <el-form-item :label="label('包裹重量（lb）', 'Package weight (lb)')" required><el-input v-model="form.weight" inputmode="decimal" /></el-form-item>
        <el-form-item v-for="field in dimensions" :key="field.key" :label="label(field.zh, field.en)" required><el-input v-model="form[field.key]" inputmode="decimal" /></el-form-item>
      </div>
      <el-checkbox v-model="form.draft">{{ label('保存为草稿', 'Save as draft') }}</el-checkbox>
    </el-form>
    <el-alert v-if="error" :title="error" type="warning" :closable="false" />
    <template #footer>
      <el-button :disabled="busy || uploading" @click="createOpen = false">{{ label('关闭', 'Close') }}</el-button>
      <el-button type="primary" :loading="busy" :disabled="!canSubmitCreate" @click="submitCreate">
        {{ pendingCreate ? label('用原请求重试', 'Retry same request') : form.draft ? label('保存草稿', 'Save draft') : label('提交平台审核', 'Submit for review') }}
      </el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="priceOpen" :title="label('修改沙盒商品售价', 'Update sandbox SKU price')" width="min(500px, 94vw)" :close-on-click-modal="false" :show-close="!busy">
    <el-form label-position="top" :disabled="busy">
      <el-form-item :label="label('目标店铺', 'Target shop')">{{ shop.name || shop.shopId }} · {{ shop.shopId }}</el-form-item>
      <el-form-item :label="label('在售商品', 'Active product')">
        <el-select v-model="productId" filterable @change="loadProduct">
          <el-option v-for="product in activeProducts" :key="product.id" :label="product.title || product.id" :value="product.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="SKU">
        <el-select v-model="skuId" :loading="detailLoading" @change="price = ''">
          <el-option v-for="sku in details?.skus || []" :key="sku.id" :label="sku.sellerSku || sku.id" :value="sku.id" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="selectedSku" :label="label('当前售价', 'Current price')">{{ selectedSku.price }} {{ selectedSku.currency }}</el-form-item>
      <el-form-item :label="label('新售价（USD）', 'New price (USD)')"><el-input v-model="price" inputmode="decimal" :disabled="!selectedSku || !!priceResult" /></el-form-item>
    </el-form>
    <el-alert v-if="error" :title="error" type="warning" :closable="false" />
    <template #footer>
      <el-button :disabled="busy || detailLoading || !productId" @click="loadProduct(productId)">{{ label('刷新售价', 'Refresh price') }}</el-button>
      <el-button type="primary" :loading="busy" :disabled="!canSubmitPrice" @click="submitPrice">{{ label('确认改价', 'Confirm price') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { TiktokShopApi, type SellerCategory, type SellerChoice, type SellerCreateProduct, type SellerProduct, type SellerProductWriteResult, type SellerShop, type SellerWriteCapabilities } from '@/api/tk/tiktokShop'
import { useTkI18n } from '@/hooks/web/useTkI18n'

const props = defineProps<{ shop: SellerShop; products: SellerProduct[]; scopes?: string }>()
const emit = defineEmits<{ (e: 'changed'): void; (e: 'reconnect'): void }>()
const { isEn } = useTkI18n()
const label = (zh: string, en: string) => isEn.value ? en : zh
const capabilities = ref<SellerWriteCapabilities>()
const createOpen = ref(false), priceOpen = ref(false), busy = ref(false), uploading = ref(false)
const categories = ref<SellerChoice[]>([]), warehouses = ref<SellerChoice[]>([]), category = ref<SellerCategory>()
const categoryLoading = ref(false), detailLoading = ref(false)
const details = ref<SellerProductWriteResult>(), created = ref<SellerProductWriteResult>()
const productId = ref(''), skuId = ref(''), price = ref(''), error = ref('')
const imageNames = ref<string[]>([])
const pendingCreate = ref<SellerCreateProduct>()
const priceResult = ref(false)
const form = ref<SellerCreateProduct>({ title: '', description: '', categoryId: '', warehouseId: '', sellerSku: '', price: '', weight: '', length: '', width: '', height: '', quantity: 1, draft: false, images: [], attributes: {}, idempotencyKey: '' })
const dimensions = [
  { key: 'length', zh: '长度（in）', en: 'Length (in)' }, { key: 'width', zh: '宽度（in）', en: 'Width (in)' }, { key: 'height', zh: '高度（in）', en: 'Height (in)' }
] as const
const activeProducts = computed(() => props.products.filter((product) => product.status === 'ACTIVATE'))
const selectedSku = computed(() => details.value?.skus?.find((sku) => sku.id === skuId.value))
const money = (value: string) => /^\d{1,7}(\.\d{1,2})?$/.test(value) && Number(value) > 0
const positive = (value: string) => /^\d{1,4}(\.\d{1,3})?$/.test(value) && Number(value) > 0
const canSubmitCreate = computed(() => !uploading.value && (!!pendingCreate.value || (
  category.value?.supported && form.value.title.trim() && form.value.description.trim() && form.value.sellerSku.trim() &&
  /^\d{1,32}$/.test(form.value.warehouseId) && form.value.images.length && money(form.value.price) &&
  Number.isInteger(form.value.quantity) && positive(form.value.weight) && dimensions.every(({ key }) => positive(form.value[key])) &&
  category.value.attributes.every((attribute) => !attribute.required || form.value.attributes[attribute.id]?.trim())
)))
const canSubmitPrice = computed(() => !priceResult.value && !detailLoading.value && details.value?.status === 'ACTIVATE' &&
  selectedSku.value?.currency === 'USD' && money(selectedSku.value.price) && money(price.value) && Number(price.value) !== Number(selectedSku.value.price))
let capabilityRevision = 0, categoryRevision = 0, searchRevision = 0, detailRevision = 0
watch(() => props.scopes, async () => {
  const revision = ++capabilityRevision
  try {
    const value = await TiktokShopApi.sellerWriteCapabilities(props.shop.id)
    if (revision === capabilityRevision) capabilities.value = value
  } catch { if (revision === capabilityRevision) capabilities.value = undefined }
}, { immediate: true })

function failure() { error.value = label('操作未完成，请检查平台错误或刷新后再试。', 'Operation incomplete. Check the platform error or refresh before retrying.') }
async function openCreate() {
  error.value = ''; createOpen.value = true
  try { warehouses.value = await TiktokShopApi.sellerWarehouses(props.shop.id) } catch { warehouses.value = [] }
}
async function findCategories(keyword: string) {
  const revision = ++searchRevision
  if (!keyword.trim()) { categories.value = []; categoryLoading.value = false; return }
  categoryLoading.value = true
  try {
    const value = await TiktokShopApi.sellerCategories(props.shop.id, keyword.trim())
    if (revision === searchRevision) categories.value = value
  } catch { if (revision === searchRevision) failure() }
  finally { if (revision === searchRevision) categoryLoading.value = false }
}
async function loadCategory(id: string) {
  const revision = ++categoryRevision
  category.value = undefined; form.value.attributes = {}; error.value = ''
  if (!id) return
  try {
    const value = await TiktokShopApi.sellerCategory(props.shop.id, id)
    if (revision === categoryRevision) category.value = value
  } catch { if (revision === categoryRevision) failure() }
}
async function upload(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || []); input.value = ''
  if (files.length + form.value.images.length > 9 || files.some((file) => !['image/jpeg', 'image/png'].includes(file.type) || file.size > 10 * 1024 * 1024)) {
    error.value = label('最多 9 张 JPG/PNG，每张不超过 10 MB。', 'Use up to 9 JPG/PNG files, 10 MB each.'); return
  }
  uploading.value = true; error.value = ''
  try {
    for (const file of files) {
      const result = await TiktokShopApi.sellerUploadImage(props.shop.id, file)
      form.value.images.push(result.uri); imageNames.value.push(file.name)
    }
  } catch { failure() }
  finally { uploading.value = false }
}
function removeImage(index: number) { form.value.images.splice(index, 1); imageNames.value.splice(index, 1) }
async function submitCreate() {
  if (busy.value || !canSubmitCreate.value) return
  busy.value = true; error.value = ''
  const data = pendingCreate.value || { ...form.value, images: [...form.value.images], attributes: { ...form.value.attributes }, idempotencyKey: crypto.randomUUID() }
  try {
    created.value = await TiktokShopApi.sellerCreateProduct(props.shop.id, data)
    createOpen.value = false; pendingCreate.value = undefined; form.value.title = ''; form.value.sellerSku = ''
    emit('changed')
  } catch (problem) {
    const code = String(problem)
    if (/TIKTOK_\d+|(?:^|: )(?:INVALID_PRODUCT_FIELDS|INVALID_SELLER_PRODUCT_REQUEST|INVALID_PRODUCT|INVALID_PRICE|INVALID_PACKAGE|INVALID_IMAGE_URI|INVALID_ATTRIBUTE|REQUIRED_ATTRIBUTE_MISSING|CATEGORY_NOT_SUPPORTED|PRODUCT_WRITE_SCOPE_REQUIRED|SELLER_AUTHORIZATION_CHANGED)$/.test(code)) {
      pendingCreate.value = undefined; failure()
    } else {
      pendingCreate.value = data
      error.value = label('暂无建品结果。重试会沿用原请求编号，避免重复创建。', 'Creation status is unknown. Retrying reuses the same request ID.')
    }
  } finally { busy.value = false }
}
function openPrice() {
  priceOpen.value = true; error.value = ''; productId.value = ''; skuId.value = ''; price.value = ''; details.value = undefined; priceResult.value = false
}
async function loadProduct(id: string) {
  const revision = ++detailRevision
  details.value = undefined; skuId.value = ''; price.value = ''; error.value = ''; priceResult.value = false
  detailLoading.value = true
  try {
    const data = await TiktokShopApi.sellerProductDetail(props.shop.id, id)
    if (revision !== detailRevision) return
    details.value = data
    if (data.skus?.length === 1) skuId.value = data.skus[0].id
  } catch { if (revision === detailRevision) failure() }
  finally { if (revision === detailRevision) detailLoading.value = false }
}
async function submitPrice() {
  const sku = selectedSku.value
  if (busy.value || !sku || !canSubmitPrice.value) return
  busy.value = true; error.value = ''
  try {
    const result = await TiktokShopApi.sellerUpdatePrice(props.shop.id, productId.value, {
      skuId: sku.id, currency: sku.currency, expected: sku.price, price: price.value
    })
    if (result.result === 'VERIFIED') {
      priceOpen.value = false; ElMessage.success(label('已回查并确认新售价', 'New price verified')); emit('changed')
    } else {
      priceResult.value = true
      error.value = [label('尚未确认改价结果，请刷新售价核对。', 'Price is unverified. Refresh before trying again.'), result.requestId].filter(Boolean).join(' ')
    }
  } catch { priceResult.value = true; failure() }
  finally { busy.value = false }
}
</script>

<style scoped>
.seller-write { display: grid; gap: 8px; margin-bottom: 12px; }
.write-toolbar { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.write-toolbar :deep(.el-button) { margin-left: 0; }
.write-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); column-gap: 12px; }
.uploaded-image { display: flex; gap: 8px; min-width: 0; width: 100%; overflow-wrap: anywhere; }
:deep(.el-select) { width: 100%; }
</style>
