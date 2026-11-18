<template>
  <main class="shop-workspace">
    <header class="workspace-heading">
      <Icon icon="ep:shop" />
      <h1>TikTok Shop</h1>
    </header>
    <template v-if="tabs.length">
      <el-tabs v-if="tabs.length > 1" v-model="activeTab">
        <el-tab-pane v-for="tab in tabs" :key="tab.name" :label="tt(tab.label)" :name="tab.name" />
      </el-tabs>
      <TiktokShopCreatorPanel v-if="activeTab === 'creator' && canReadCreator" @publish="openPublish" />
      <TiktokShopPublishPanel v-else-if="activeTab === 'publish' && canReadAccount" :initial-selection="selection" @selection-consumed="selection = undefined" />
      <TiktokShopPhotoPanel v-else-if="activeTab === 'photos' && canUsePhotos" />
      <TiktokShopSellerAccessPanel v-else-if="activeTab === 'seller-access' && canReadAccount" />
      <TiktokShopSellerPanel v-else-if="activeTab === 'seller' || activeTab === 'analytics'" :key="activeTab" :analytics="activeTab === 'analytics'" />
    </template>
    <el-empty v-else :description="tt('shop.workspaceNoAccess')" />
  </main>
</template>

<script setup lang="ts">
import { computed, defineAsyncComponent, ref, watch } from 'vue'
import type { ShopPublishSelection } from '@/api/tk/tiktokShop'
import { hasPermission } from '@/directives/permission/hasPermi'
import { useTkI18n } from '@/hooks/web/useTkI18n'
import TiktokShopPublishPanel from '../video-publish-center/components/TiktokShopPublishPanel.vue'

defineOptions({ name: 'TkTiktokShopWorkspace' })
const { tt } = useTkI18n()
const canReadAccount = computed(() => hasPermission(['tk:tiktok-shop-account:query']))
const canReadCreator = computed(() => canReadAccount.value && hasPermission(['tk:tiktok-shop-creator:query']))
const canUsePhotos = computed(() => canReadAccount.value && (hasPermission(['tk:tiktok-shop-publish:query']) || (
  hasPermission(['tk:tiktok-shop-publish:create']) && hasPermission(['tk:tiktok-shop-publish:confirm']))))
const tabs = computed(() => [
  { name: 'publish', label: 'shop.workspacePublish', visible: canReadAccount.value },
  { name: 'creator', label: 'shop.workspaceCreator', visible: canReadCreator.value },
  { name: 'photos', label: 'shop.workspacePhotos', visible: canUsePhotos.value },
  { name: 'seller-access', label: 'shop.sellerEntry', visible: canReadAccount.value },
  { name: 'seller', label: 'shop.workspaceSeller', visible: canReadAccount.value && hasPermission(['tk:tiktok-shop-seller:query']) },
  { name: 'analytics', label: 'shop.workspaceAnalytics', visible: canReadAccount.value && hasPermission(['tk:tiktok-shop-analytics:query']) }
].filter((tab) => tab.visible))
const activeTab = ref('')
watch(tabs, (value) => {
  if (!value.some((tab) => tab.name === activeTab.value)) activeTab.value = value[0]?.name || ''
}, { immediate: true })
const selection = ref<ShopPublishSelection>()
const TiktokShopCreatorPanel = defineAsyncComponent(() => import('./TiktokShopCreatorPanel.vue'))
const TiktokShopSellerPanel = defineAsyncComponent(() => import('./TiktokShopSellerPanel.vue'))
const TiktokShopSellerAccessPanel = defineAsyncComponent(() => import('../video-publish-center/components/TiktokShopSellerPanel.vue'))
const TiktokShopPhotoPanel = defineAsyncComponent(() => import('./TiktokShopPhotoPanel.vue'))
function openPublish(value: ShopPublishSelection) {
  selection.value = value
  activeTab.value = 'publish'
}
</script>

<style scoped>
.workspace-heading {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 0 14px;
  color: var(--el-text-color-primary);
}
.workspace-heading h1 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}
</style>
