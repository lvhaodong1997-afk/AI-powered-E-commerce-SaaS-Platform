<template>
  <el-drawer :model-value="!!target" :title="target?.kind === 'account' ? mt('meta.accountData') : mt('meta.mediaData')" size="min(920px, 96vw)" @close="$emit('close')">
    <div v-loading="loading">
      <p>{{ platform }} · #{{ target?.id }} · {{ statsStatusLabel(snapshot?.syncStatus, locale) }}</p>
      <p>{{ mt('meta.recentSuccess') }}: {{ date(snapshot?.lastSuccessTime) }} · {{ mt('meta.recentAttempt') }}: {{ date(snapshot?.lastAttemptTime) }}</p>
      <p v-if="snapshot?.nextSyncTime">{{ mt('meta.nextSync') }}: {{ date(snapshot.nextSyncTime) }}</p>
      <el-alert v-if="error || snapshot?.errorMessage || snapshot?.errorCode" :title="translateText(error || snapshot?.errorMessage || snapshot?.errorCode)" type="warning" :closable="false" />
      <div class="stats-actions">
        <el-button :loading="loading" :disabled="syncing" @click="$emit('refresh')">{{ mt('meta.refreshResult') }}</el-button>
        <el-button v-if="canSync" :loading="syncing" :disabled="!syncAllowed" @click="$emit('sync')">{{ mt('meta.syncData') }}</el-button>
      </div>
      <p class="stats-note">{{ mt('meta.statsNote') }}</p>
      <el-table :data="snapshot?.metrics || []" stripe>
        <el-table-column :label="mt('meta.metricLabel')" min-width="130"><template #default="{ row }">{{ metricLabel(row, locale) }}</template></el-table-column>
        <el-table-column :label="mt('meta.value')" min-width="110"><template #default="{ row }">{{ formatMetricValue(row, locale) }}<small v-if="row.stale"> ({{ mt('meta.oldValue') }})</small></template></el-table-column>
        <el-table-column :label="mt('meta.availability')" min-width="170"><template #default="{ row }">
          {{ availabilityLabel(row.availability, locale) }}
          <div v-if="row.stale">{{ mt('meta.oldValue') }}: {{ translateText(row.errorMessage || error || snapshot?.errorMessage) || mt('meta.noAvailableValue') }}</div>
          <div v-else-if="row.errorMessage">{{ translateText(row.errorMessage) }}</div>
          <div v-if="row.errorCode">{{ row.errorCode }}</div>
        </template></el-table-column>
        <el-table-column prop="sourceMetric" :label="mt('meta.platformMetric')" min-width="190" />
        <el-table-column :label="mt('meta.scope')" min-width="120"><template #default="{ row }">{{ scopeLabel(row.scope, locale) }}</template></el-table-column>
        <el-table-column :label="mt('meta.periodLabel')" min-width="120"><template #default="{ row }">{{ periodLabel(row.period, locale) }}</template></el-table-column>
        <el-table-column :label="mt('meta.unit')" min-width="80"><template #default="{ row }">{{ unitLabel(row.unit, locale) }}</template></el-table-column>
        <el-table-column :label="mt('meta.collectedAt')" min-width="170"><template #default="{ row }">{{ date(row.fetchedAt) }}</template></el-table-column>
      </el-table>
      <el-empty v-if="!loading && !snapshot?.metrics.length" :description="mt('meta.noStats')" />
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { SocialStats, SocialTime } from '@/api/tk/socialPublish'
import { useTkI18n } from '@/hooks/web/useTkI18n'
import { translateMetaText } from '@/locales/tk/metaPublishMessages'
import { availabilityLabel, formatMetricValue, formatSocialDate, metricLabel, periodLabel, scopeLabel, statsStatusLabel, unitLabel } from './metaStats'

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
const { locale, tt } = useTkI18n()
const mt = (key: string) => tt(key)
const translateText = (text?: string) => translateMetaText(text, locale.value)
const platform = computed(() => props.snapshot?.platform === 'FACEBOOK_PAGE' ? 'Facebook Page' : props.snapshot?.platform === 'INSTAGRAM' ? 'Instagram' : 'Meta')
const date = (value?: SocialTime | null) => formatSocialDate(value, locale.value)
</script>

<style scoped>
.stats-actions { display: flex; gap: 8px; margin: 16px 0; }
.stats-note, small { color: var(--el-text-color-secondary); }
</style>
