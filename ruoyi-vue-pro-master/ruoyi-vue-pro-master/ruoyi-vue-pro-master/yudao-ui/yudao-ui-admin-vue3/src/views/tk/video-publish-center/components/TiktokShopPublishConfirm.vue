<template>
  <section v-if="task" class="shop-band confirm-section">
    <div class="confirm-heading"><h3 class="shop-heading">{{ tt('shop.confirmTitle') }} #{{ task.id }}</h3><el-tag size="small" :type="shopTaskState(task.stage).type">{{ tt(shopTaskState(task.stage).label) }}</el-tag></div>
    <dl class="confirm-summary">
      <dt>{{ tt('shop.account') }}</dt><dd>{{ accountLabel }}</dd>
      <dt>{{ tt('shop.titleLabel') }}</dt><dd>{{ task.title }}</dd>
      <dt>{{ tt('shop.product') }}</dt><dd>{{ task.productLinkTitle }} · {{ task.productId }}</dd>
      <template v-if="task.videoId"><dt>{{ tt('shop.videoId') }}</dt><dd><a v-if="videoLink" :href="videoLink" target="_blank" rel="noopener noreferrer">{{ task.videoId }}</a><span v-else>{{ task.videoId }}</span></dd></template>
    </dl>
    <el-alert v-if="precheck.qualityAdvisory" :title="tt('shop.qualityAdvisory')" type="warning" :closable="false" />
    <el-alert v-if="precheckNotice" :title="tt(precheckNotice)" :type="precheck.failed ? 'error' : 'info'" :closable="false" />
    <div v-for="issue in [...precheck.violationIssues, ...precheck.qualityIssues]" :key="`${issue.code}-${issue.risk}`" class="issue">{{ issue.code || 'ISSUE' }} · {{ issue.risk || '' }} · {{ issue.suggestions || '' }}</div>
    <details class="shop-details">
      <summary>{{ tt('shop.taskDetails') }}</summary>
      <dl class="confirm-summary">
        <dt>{{ tt('common.status') }}</dt><dd>{{ task.stage }}</dd>
        <dt>{{ tt('shop.videoLocked') }}</dt><dd>{{ snapshotLabel }}</dd>
        <dt>AI</dt><dd>{{ task.isAiGenerated }}</dd>
        <dt>{{ tt('shop.versionHash') }}</dt><dd>{{ task.version }} · {{ task.requestHash }}</dd>
        <template v-if="task.requestId"><dt>{{ tt('shop.requestId') }}</dt><dd>{{ task.requestId }}</dd></template>
      </dl>
    </details>
    <div v-if="task.stage === 'AWAITING_CONFIRMATION'" class="confirm-actions">
      <el-checkbox v-model="confirmed" :disabled="!canConfirm">{{ tt('shop.confirm') }}</el-checkbox>
      <el-button type="primary" :loading="submitting" :disabled="!confirmed || !canConfirm || submitted" @click="$emit('submit')">{{ tt('shop.post') }}</el-button>
    </div>
  </section>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { type ShopAccount, type ShopTask } from '@/api/tk/tiktokShop'
import { canConfirmShopTask, normalizeShopPrecheck, shopPrecheckNotice } from './tiktokShopBehavior'
import { useTkI18n } from '@/hooks/web/useTkI18n'
import { shopTaskState } from '../../tiktok-shop/shopPresentation'
const props = defineProps<{ task?: ShopTask; account?: ShopAccount; submitting?: boolean; submitted?: boolean }>()
defineEmits<{ submit: [] }>()
const { tt } = useTkI18n(), confirmed = ref(false)
const precheck = computed(() => normalizeShopPrecheck(props.task?.precheckResult))
const precheckNotice = computed(() => shopPrecheckNotice(props.task))
const canConfirm = computed(() => props.account?.authStatus === 'AUTHORIZED' && canConfirmShopTask(props.task))
const accountLabel = computed(() => props.account ? `${props.account.displayName || props.account.username || 'Account'} (#${props.account.id})` : `#${props.task?.accountId || '-'}`)
const snapshotLabel = computed(() => `${props.task?.generationTaskId ? `generation #${props.task.generationTaskId}` : `media #${props.task?.mediaId || '-'}`} · ${props.task?.fileSize || 0} bytes`)
const videoLink = computed(() => props.task?.videoId && /^\d+$/.test(props.task.videoId) ? `https://www.tiktok.com/video/${props.task.videoId}` : '')
watch(() => `${props.task?.id || ''}:${props.task?.requestHash || ''}:${props.task?.version || ''}`, () => { confirmed.value = false })
</script>
<style scoped>
.confirm-section { overflow-wrap: anywhere; }
.confirm-heading, .confirm-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 8px 12px; }
.confirm-summary { display: grid; grid-template-columns: minmax(0, 120px) minmax(0, 1fr); gap: 6px 10px; line-height: 1.55; font-size: 13px; }
.confirm-summary dt, .issue { color: var(--el-text-color-secondary); }
.confirm-summary dd { margin: 0; }
.confirm-section :deep(.el-checkbox) { max-width: 100%; height: auto; white-space: normal; }
.confirm-section :deep(.el-checkbox__label) { white-space: normal; }
.issue, .confirm-section > .el-alert { margin: 6px 0; }
</style>
