<template>
  <div class="account-stats-page">
    <div class="page-header">
      <div>
        <h2 class="page-title">{{ tt('accountStats.title') }}</h2>
        <p class="page-subtitle">{{ tt('accountStats.updatedAt') }}{{ formatDateTime(overview.dataUpdatedAt) }}</p>
      </div>
      <el-tag v-if="overview.latestVideoSyncedAt" type="info" effect="plain">
        {{ tt('accountStats.videoData') }}{{ formatDateTime(overview.latestVideoSyncedAt) }}
      </el-tag>
    </div>

    <el-skeleton v-if="loading" :rows="12" animated />
    <template v-else>
      <el-row :gutter="16" class="ranking-row">
        <el-col :xs="24" :lg="12">
          <el-card shadow="never" class="ranking-panel">
            <template #header>
              <div class="panel-title">
                <span>{{ tt('accountStats.followerTop10') }}</span>
                <Icon icon="ep:trophy" class="panel-icon" />
              </div>
            </template>
            <div v-if="overview.topFollowerAccounts.length" class="ranking-list">
              <div v-for="item in overview.topFollowerAccounts" :key="`follower-${item.accountId}`" class="ranking-item">
                <span class="ranking-number" :class="rankClass(item.rank)">{{ item.rank }}</span>
                <el-avatar :size="36" :src="item.avatarUrl">
                  <Icon icon="ep:user" />
                </el-avatar>
                <div class="ranking-account">
                  <strong>{{ accountName(item) }}</strong>
                  <span>{{ item.username ? `@${item.username}` : tt('accountStats.account') }}</span>
                </div>
                <strong class="ranking-value">{{ formatCount(item.metricValue) }}</strong>
              </div>
            </div>
            <el-empty v-else :description="tt('accountStats.noFollowerData')" :image-size="72" />
          </el-card>
        </el-col>

        <el-col :xs="24" :lg="12">
          <el-card shadow="never" class="ranking-panel">
            <template #header>
              <div class="panel-title">
                <span>{{ tt('accountStats.recentVideoTop10') }}</span>
                <Icon icon="ep:trend-charts" class="panel-icon" />
              </div>
            </template>
            <div v-if="overview.topLatestVideoViewAccounts.length" class="ranking-list">
              <div v-for="item in overview.topLatestVideoViewAccounts" :key="`view-${item.accountId}-${item.latestVideoId || item.rank}`" class="ranking-item">
                <span class="ranking-number" :class="rankClass(item.rank)">{{ item.rank }}</span>
                <el-avatar :size="36" :src="item.avatarUrl">
                  <Icon icon="ep:user" />
                </el-avatar>
                <div class="ranking-account">
                  <strong>{{ accountName(item) }}</strong>
                  <span>{{ item.username ? `@${item.username}` : tt('accountStats.recentPublicVideos') }}</span>
                  <span v-if="item.latestVideoId">{{ tt('accountStats.video') }} {{ item.latestVideoId }}</span>
                  <span v-if="item.latestVideoCreateTime">{{ tt('accountStats.publishedAt') }}{{ formatVideoTime(item.latestVideoCreateTime) }}</span>
                </div>
                <strong class="ranking-value">{{ formatCount(item.metricValue) }}</strong>
              </div>
            </div>
            <el-empty v-else :description="tt('accountStats.noVideoViewData')" :image-size="72" />
          </el-card>
        </el-col>
      </el-row>

      <section class="accounts-section">
        <div class="section-heading">
          <h3>{{ tt('accountStats.accountData') }}</h3>
          <span>{{ overview.accounts.length }} {{ tt('accountStats.authorizedAccountCount') }}</span>
        </div>
        <el-empty v-if="!overview.accounts.length" :description="tt('accountStats.noAuthorizedAccounts')" :image-size="100" />
        <div v-else class="account-grid">
          <el-card v-for="account in overview.accounts" :key="account.accountId" shadow="hover" class="account-card">
            <div class="account-card-header">
              <el-avatar :size="48" :src="account.avatarUrl">
                <Icon icon="ep:user" />
              </el-avatar>
              <div class="account-heading">
                <strong>{{ accountName(account) }}</strong>
                <span>{{ account.username ? `@${account.username}` : tt('accountStats.account') }}</span>
              </div>
              <el-tag v-if="account.statsAvailable" type="success" effect="light" size="small">{{ tt('accountStats.synced') }}</el-tag>
              <el-tag v-else type="warning" effect="light" size="small">{{ tt('accountStats.pendingSync') }}</el-tag>
            </div>
            <div class="metrics-grid">
              <div class="metric-item">
                <span>{{ tt('accountStats.followers') }}</span>
                <strong>{{ formatCount(account.followerCount) }}</strong>
              </div>
              <div class="metric-item">
                <span>{{ tt('accountStats.following') }}</span>
                <strong>{{ formatCount(account.followingCount) }}</strong>
              </div>
              <div class="metric-item">
                <span>{{ tt('accountStats.likes') }}</span>
                <strong>{{ formatCount(account.likesCount) }}</strong>
              </div>
              <div class="metric-item">
                <span>{{ tt('accountStats.videos') }}</span>
                <strong>{{ formatCount(account.videoCount) }}</strong>
              </div>
            </div>
            <div class="recent-videos">
              <div class="recent-videos-heading">
                <span>{{ tt('accountStats.recentVideos') }}</span>
              </div>
              <div v-if="account.recentVideos?.length" class="recent-video-list">
                <div v-for="(video, index) in account.recentVideos" :key="video.videoId || `${account.accountId}-${index}`" class="recent-video-item">
                  <span class="recent-video-index">{{ index + 1 }}</span>
                  <span class="recent-video-time">{{ formatVideoTime(video.createTime) }}</span>
                  <strong>{{ formatCount(video.viewCount) }} {{ tt('accountStats.views') }}</strong>
                </div>
              </div>
              <span v-else class="recent-video-empty">{{ tt('accountStats.noPublicVideoData') }}</span>
            </div>
            <div class="account-card-footer">
              <span>{{ tt('accountStats.statsUpdatedAt') }}</span>
              <span class="footer-time">{{ formatDateTime(account.statsUpdatedAt) }}</span>
            </div>
          </el-card>
        </div>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import {
  TkTiktokAccountStatsApi,
  type TkTiktokAccountStatsOverviewVO,
  type TkTiktokAccountStatsRankVO,
  type TkTiktokAccountStatsVO
} from '@/api/tk/tiktokAccountStats'
import { useTkI18n } from '@/hooks/web/useTkI18n'
import { formatDate } from '@/utils/formatTime'

defineOptions({ name: 'TkTiktokAccountStats' })

const message = useMessage()
const { tt } = useTkI18n()
const loading = ref(true)
const overview = ref<TkTiktokAccountStatsOverviewVO>({
  topFollowerAccounts: [],
  topLatestVideoViewAccounts: [],
  accounts: []
})

const accountName = (account: TkTiktokAccountStatsRankVO | TkTiktokAccountStatsVO) =>
  account.displayName || account.username || `${tt('accountStats.account')} #${account.accountId}`

const formatCount = (value?: number | null) => value == null ? '--' : new Intl.NumberFormat('en-US').format(value)
const formatDateTime = (value?: string | null) => value ? formatDate(value) : '--'
const formatVideoTime = (value?: number | null) => value == null ? '--' : formatDate(value * 1000)
const rankClass = (rank: number) => rank <= 3 ? `rank-${rank}` : ''

const loadOverview = async () => {
  loading.value = true
  try {
    const data = await TkTiktokAccountStatsApi.getOverview()
    overview.value = {
      topFollowerAccounts: data.topFollowerAccounts || [],
      topLatestVideoViewAccounts: data.topLatestVideoViewAccounts || [],
      accounts: data.accounts || [],
      dataUpdatedAt: data.dataUpdatedAt,
      latestVideoSyncedAt: data.latestVideoSyncedAt
    }
  } catch {
    message.error(tt('accountStats.loadFailed'))
  } finally {
    loading.value = false
  }
}

onMounted(loadOverview)
</script>

<style scoped>
.account-stats-page { display: flex; flex-direction: column; gap: 18px; padding: 4px; }
.page-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.page-title { margin: 0; color: var(--el-text-color-primary); font-size: 24px; line-height: 32px; }
.page-subtitle { margin: 6px 0 0; color: var(--el-text-color-secondary); font-size: 13px; }
.ranking-row { row-gap: 16px; }
.ranking-panel { height: 100%; border-color: var(--el-border-color-light); }
.panel-title { display: flex; align-items: center; justify-content: space-between; color: var(--el-text-color-primary); font-size: 17px; font-weight: 600; }
.panel-icon { color: var(--el-color-primary); font-size: 18px; }
.ranking-list { display: flex; flex-direction: column; }
.ranking-item { display: flex; align-items: center; min-height: 52px; gap: 10px; border-bottom: 1px solid var(--el-border-color-lighter); }
.ranking-item:last-child { border-bottom: 0; }
.ranking-number { display: inline-flex; align-items: center; justify-content: center; width: 24px; height: 24px; color: var(--el-text-color-secondary); font-size: 13px; }
.ranking-number.rank-1, .ranking-number.rank-2, .ranking-number.rank-3 { border-radius: 50%; color: #fff; font-weight: 700; }
.ranking-number.rank-1 { background: #f3b51b; }
.ranking-number.rank-2 { background: #9aa5b1; }
.ranking-number.rank-3 { background: #c9854b; }
.ranking-account { display: flex; flex: 1; min-width: 0; flex-direction: column; gap: 3px; }
.ranking-account strong, .ranking-account span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ranking-account strong { color: var(--el-text-color-primary); font-size: 14px; }
.ranking-account span { color: var(--el-text-color-secondary); font-size: 12px; }
.ranking-value { color: var(--el-color-primary); font-size: 14px; white-space: nowrap; }
.accounts-section { display: flex; flex-direction: column; gap: 12px; }
.section-heading { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; }
.section-heading h3 { margin: 0; color: var(--el-text-color-primary); font-size: 18px; }
.section-heading span { color: var(--el-text-color-secondary); font-size: 13px; }
.account-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; }
.account-card { min-width: 0; border-color: var(--el-border-color-light); }
.account-card-header { display: flex; align-items: center; min-width: 0; gap: 12px; }
.account-heading { display: flex; flex: 1; min-width: 0; flex-direction: column; gap: 4px; }
.account-heading strong, .account-heading span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.account-heading strong { color: var(--el-text-color-primary); font-size: 15px; }
.account-heading span { color: var(--el-text-color-secondary); font-size: 12px; }
.metrics-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); margin-top: 18px; padding: 12px 0; border-top: 1px solid var(--el-border-color-lighter); border-bottom: 1px solid var(--el-border-color-lighter); }
.metric-item { display: flex; min-width: 0; flex-direction: column; gap: 6px; padding: 0 10px; border-right: 1px solid var(--el-border-color-lighter); }
.metric-item:first-child { padding-left: 0; }
.metric-item:last-child { padding-right: 0; border-right: 0; }
.metric-item span, .account-card-footer span { color: var(--el-text-color-secondary); font-size: 12px; }
.metric-item strong { overflow: hidden; color: var(--el-text-color-primary); font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }
.account-card-footer { display: flex; align-items: center; gap: 8px; padding-top: 12px; }
.recent-videos { margin-top: 14px; padding-top: 12px; border-top: 1px solid var(--el-border-color-lighter); }
.recent-videos-heading { display: flex; align-items: center; justify-content: space-between; gap: 8px; color: var(--el-text-color-secondary); font-size: 12px; }
.recent-videos-heading strong { color: var(--el-color-primary); font-size: 13px; }
.recent-video-list { display: flex; flex-direction: column; margin-top: 8px; }
.recent-video-item { display: grid; grid-template-columns: 20px 1fr auto; align-items: center; min-height: 28px; gap: 8px; color: var(--el-text-color-secondary); font-size: 12px; }
.recent-video-index { color: var(--el-text-color-placeholder); }
.recent-video-time { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.recent-video-item strong { color: var(--el-text-color-primary); font-size: 12px; }
.recent-video-empty { display: block; padding-top: 8px; color: var(--el-text-color-placeholder); font-size: 12px; }
.footer-time { margin-left: auto; white-space: nowrap; }
@media (max-width: 1200px) { .account-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 680px) { .page-header, .section-heading { align-items: flex-start; flex-direction: column; } .account-grid { grid-template-columns: 1fr; } .metrics-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px 0; } .metric-item:nth-child(2) { border-right: 0; } .metric-item:nth-child(3), .metric-item:nth-child(4) { padding-top: 10px; } .metric-item:nth-child(3) { padding-left: 0; } .metric-item:nth-child(4) { padding-right: 0; } }
</style>
