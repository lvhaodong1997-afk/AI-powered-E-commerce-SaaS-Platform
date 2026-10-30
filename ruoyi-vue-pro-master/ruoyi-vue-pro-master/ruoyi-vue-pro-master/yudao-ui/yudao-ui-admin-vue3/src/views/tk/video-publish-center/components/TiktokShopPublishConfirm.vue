<template>
  <el-card v-if="task" shadow="never" class="confirm-card">
    <template #header><span>{{ tt('shop.confirmTitle') }}</span></template>
    <el-descriptions :column="1" border size="small">
      <el-descriptions-item label="Task ID">{{ task.id }}</el-descriptions-item>
      <el-descriptions-item :label="tt('shop.account')">{{ accountLabel }}</el-descriptions-item>
      <el-descriptions-item :label="tt('shop.videoLocked')">{{ snapshotLabel }}</el-descriptions-item>
      <el-descriptions-item :label="tt('shop.titleLabel')">{{ task.title }}</el-descriptions-item>
      <el-descriptions-item :label="tt('shop.product')">{{ task.productId }} · {{ task.productLinkTitle }}</el-descriptions-item>
      <el-descriptions-item label="AI">{{ task.isAiGenerated ? 'true' : 'false' }}</el-descriptions-item>
    </el-descriptions>
    <el-alert v-if="precheck.qualityAdvisory" :title="tt('shop.qualityAdvisory')" type="warning" :closable="false" />
    <el-alert v-if="!precheck.passed" :title="tt('shop.violationBlocked')" type="error" :closable="false" />
    <div v-for="issue in [...precheck.violationIssues, ...precheck.qualityIssues]" :key="`${issue.code}-${issue.risk}`" class="issue">{{ issue.code || 'ISSUE' }} · {{ issue.risk || '' }} · {{ issue.suggestions || '' }}</div>
    <el-checkbox v-model="confirmed" :disabled="!canConfirm">{{ tt('shop.confirm') }}</el-checkbox>
    <el-button type="primary" :loading="submitting" :disabled="!confirmed || !canConfirm || submitted" @click="$emit('submit')">{{ tt('shop.post') }}</el-button>
  </el-card>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { type ShopAccount, type ShopTask } from '@/api/tk/tiktokShop'
import { canConfirmShopTask, normalizeShopPrecheck } from './tiktokShopBehavior'
import { useTkI18n } from '@/hooks/web/useTkI18n'
const props = defineProps<{ task?: ShopTask; account?: ShopAccount; submitting?: boolean; submitted?: boolean }>()
defineEmits<{ submit: [] }>()
const { tt } = useTkI18n(), confirmed = ref(false)
const precheck = computed(() => normalizeShopPrecheck(props.task?.precheckResult))
const canConfirm = computed(() => canConfirmShopTask(props.task))
const accountLabel = computed(() => props.account ? `${props.account.displayName || props.account.username || 'Account'} (#${props.account.id})` : `#${props.task?.accountId || '-'}`)
const snapshotLabel = computed(() => `${props.task?.generationTaskId ? `generation #${props.task.generationTaskId}` : `media #${props.task?.mediaId || '-'}`} · ${props.task?.fileSize || 0} bytes`)
watch(() => `${props.task?.id || ''}:${props.task?.requestHash || ''}:${props.task?.version || ''}`, () => { confirmed.value = false })
</script>
<style scoped>.confirm-card { margin-top: 12px; }.issue { padding: 4px 0; color: var(--el-text-color-secondary); font-size: 12px; }</style>
