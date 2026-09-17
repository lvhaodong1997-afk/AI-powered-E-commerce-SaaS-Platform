<template>
  <el-drawer :model-value="!!target" :title="target?.kind === 'account' ? '账号数据' : '作品数据'" size="min(920px, 96vw)" @close="$emit('close')">
    <div v-loading="loading">
      <p>{{ platform }} · #{{ target?.id }} · {{ statsStatusLabel(snapshot?.syncStatus) }}</p>
      <p>最近成功：{{ date(snapshot?.lastSuccessTime) }} · 最近尝试：{{ date(snapshot?.lastAttemptTime) }}</p>
      <p v-if="snapshot?.nextSyncTime">下次自动同步：{{ date(snapshot.nextSyncTime) }}</p>
      <el-alert v-if="error || snapshot?.errorMessage || snapshot?.errorCode" :title="error || snapshot?.errorMessage || snapshot?.errorCode || ''" type="warning" :closable="false" />
      <div class="stats-actions">
        <el-button :loading="loading" :disabled="syncing" @click="$emit('refresh')">刷新结果</el-button>
        <el-button v-if="canSync" :loading="syncing" :disabled="!syncAllowed" @click="$emit('sync')">同步数据</el-button>
      </div>
      <p class="stats-note">同步请求由后台执行。冷却期间不可重复请求；“—”表示暂无可用值，0 表示平台返回的真实零值。</p>
      <el-table :data="snapshot?.metrics || []" stripe>
        <el-table-column label="指标" min-width="130"><template #default="{ row }">{{ metricLabel(row) }}</template></el-table-column>
        <el-table-column label="数值" min-width="110"><template #default="{ row }">{{ formatMetricValue(row) }}<small v-if="row.stale">（上次值）</small></template></el-table-column>
        <el-table-column label="可用性 / 说明" min-width="170"><template #default="{ row }">
          {{ availabilityLabel(row.availability) }}
          <div v-if="row.stale">旧值：{{ row.errorMessage || error || snapshot?.errorMessage || '本次未取得新数据' }}</div>
          <div v-else-if="row.errorMessage">{{ row.errorMessage }}</div>
          <div v-if="row.errorCode">{{ row.errorCode }}</div>
        </template></el-table-column>
        <el-table-column prop="sourceMetric" label="平台来源指标" min-width="190" />
        <el-table-column prop="scope" label="统计范围" min-width="120" />
        <el-table-column prop="period" label="统计周期" min-width="120" />
        <el-table-column prop="unit" label="单位" min-width="80" />
        <el-table-column label="采集时间" min-width="170"><template #default="{ row }">{{ date(row.fetchedAt) }}</template></el-table-column>
      </el-table>
      <el-empty v-if="!loading && !snapshot?.metrics.length" description="暂无统计，请同步或稍后刷新；不会将缺失数据记为 0。" />
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { SocialStats, SocialTime } from '@/api/tk/socialPublish'
import { formatDate } from '@/utils/formatTime'
import { availabilityLabel, formatMetricValue, metricLabel, statsStatusLabel } from './metaStats'

const props = defineProps<{
  target?: { kind: 'account' | 'detail'; id: number }
  snapshot?: SocialStats
  loading?: boolean
  syncing?: boolean
  error?: string
  canSync: boolean
  syncAllowed: boolean
}>()
defineEmits<{ close: []; refresh: []; sync: [] }>()
const platform = computed(() => props.snapshot?.platform === 'FACEBOOK_PAGE' ? 'Facebook Page' : props.snapshot?.platform === 'INSTAGRAM' ? 'Instagram' : 'Meta')
const date = (value?: SocialTime | null) => value == null ? '—' : formatDate(value)
</script>

<style scoped>
.stats-actions { display: flex; gap: 8px; margin: 16px 0; }
.stats-note, small { color: var(--el-text-color-secondary); }
</style>
