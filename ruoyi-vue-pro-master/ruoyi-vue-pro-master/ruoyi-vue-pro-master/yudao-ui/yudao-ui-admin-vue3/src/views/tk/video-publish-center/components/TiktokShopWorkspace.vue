<template>
  <div class="shop-workspace">
    <ContentWrap class="shop-switch">
      <el-radio-group v-model="mode">
        <el-radio-button value="creator">{{ tt('shop.creatorEntry') }}</el-radio-button>
        <el-radio-button value="seller">{{ tt('shop.sellerEntry') }}</el-radio-button>
      </el-radio-group>
    </ContentWrap>
    <TiktokShopPublishPanel v-if="mode === 'creator'" />
    <TiktokShopSellerPanel v-else />
  </div>
</template>
<script setup lang="ts">
import { ref } from 'vue'
import TiktokShopPublishPanel from './TiktokShopPublishPanel.vue'
import TiktokShopSellerPanel from './TiktokShopSellerPanel.vue'
import { useTkI18n } from '@/hooks/web/useTkI18n'
const { tt } = useTkI18n()
const mode = ref<'creator' | 'seller'>('creator')
</script>
<style scoped>.shop-switch { margin-bottom: 12px; }</style>
