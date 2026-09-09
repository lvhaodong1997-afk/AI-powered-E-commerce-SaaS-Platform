<template>
  <div class="account-stats-page">
    <div class="page-header">
      <div class="page-heading">
        <h2 class="page-title">{{ tt('accountStats.title') }}</h2>
        <p class="page-subtitle"
          >{{ tt('accountStats.updatedAt') }}{{ formatDateTime(overview.dataUpdatedAt) }}</p
        >
      </div>
      <div class="page-actions">
        <el-tag v-if="overview.latestVideoSyncedAt" type="info" effect="plain">
          {{ tt('accountStats.videoData') }}{{ formatDateTime(overview.latestVideoSyncedAt) }}
        </el-tag>
        <el-button
          type="primary"
          plain
          :loading="refreshing"
          :disabled="loading"
          @click="loadOverview()"
        >
          <Icon icon="ep:refresh" class="button-icon" />
          {{ tt('common.refresh') }}
        </el-button>
      </div>
    </div>

    <el-alert
      v-if="hasStaleAccountData"
      :title="tt('accountStats.dataExpired')"
      type="warning"
      show-icon
      :closable="false"
    />
    <el-alert v-if="loadError" type="error" show-icon :closable="false">
      <template #default>
        <div class="alert-content">
          <span>{{ tt('accountStats.loadFailed') }}</span>
          <el-button link type="danger" :loading="refreshing" @click="loadOverview()">
            {{ tt('accountStats.retryLoad') }}
          </el-button>
        </div>
      </template>
    </el-alert>

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
              <div
                v-for="item in overview.topFollowerAccounts"
                :key="`follower-${item.accountId}`"
                class="ranking-item"
              >
                <span class="ranking-number" :class="rankClass(item.rank)">{{ item.rank }}</span>
                <el-avatar :size="36" :src="item.avatarUrl">
                  <Icon icon="ep:user" />
                </el-avatar>
                <div class="ranking-account">
                  <strong>{{ accountName(item) }}</strong>
                  <span>{{
                    item.username ? `@${item.username}` : tt('accountStats.account')
                  }}</span>
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
              <div
                v-for="item in overview.topLatestVideoViewAccounts"
                :key="`view-${item.accountId}-${item.latestVideoId || item.rank}`"
                class="ranking-item"
              >
                <span class="ranking-number" :class="rankClass(item.rank)">{{ item.rank }}</span>
                <el-avatar :size="36" :src="item.avatarUrl">
                  <Icon icon="ep:user" />
                </el-avatar>
                <el-image
                  v-if="item.latestVideoCoverImageUrl"
                  class="ranking-video-cover"
                  :src="item.latestVideoCoverImageUrl"
                  fit="cover"
                  :alt="rankVideoTitle(item)"
                >
                  <template #error><Icon icon="ep:video-camera" /></template>
                </el-image>
                <div class="ranking-account">
                  <strong>{{ accountName(item) }}</strong>
                  <span>{{
                    item.username ? `@${item.username}` : tt('accountStats.recentPublicVideos')
                  }}</span>
                  <span class="ranking-video-title" :title="rankVideoTitle(item)">{{
                    rankVideoTitle(item)
                  }}</span>
                  <span v-if="item.latestVideoCreateTime"
                    >{{ tt('accountStats.publishedAt')
                    }}{{ formatVideoTime(item.latestVideoCreateTime) }}</span
                  >
                </div>
                <div class="ranking-value-wrap">
                  <strong class="ranking-value">{{ formatCount(item.metricValue) }}</strong>
                  <el-link
                    v-if="item.latestVideoShareUrl"
                    class="video-link"
                    :href="item.latestVideoShareUrl"
                    target="_blank"
                    rel="noopener noreferrer"
                    :title="tt('accountStats.openVideo')"
                    :aria-label="tt('accountStats.openVideo')"
                    underline="never"
                  >
                    <Icon icon="ep:top-right" />
                  </el-link>
                </div>
              </div>
            </div>
            <el-empty v-else :description="tt('accountStats.noVideoViewData')" :image-size="72" />
          </el-card>
        </el-col>
      </el-row>

      <section class="accounts-section">
        <div class="section-heading">
          <div>
            <h3>{{ tt('accountStats.accountData') }}</h3>
            <span class="section-description">
              {{ filteredAccounts.length }} / {{ overview.accounts.length }}
              {{ tt('accountStats.accountCount') }}
            </span>
          </div>
          <el-input
            v-model="searchKeyword"
            class="account-search"
            clearable
            :aria-label="tt('accountStats.searchAccounts')"
            :placeholder="tt('accountStats.searchPlaceholder')"
          >
            <template #prefix><Icon icon="ep:search" /></template>
          </el-input>
        </div>
        <el-empty
          v-if="!overview.accounts.length"
          :description="tt('accountStats.noAuthorizedAccounts')"
          :image-size="100"
        />
        <el-empty
          v-else-if="!filteredAccounts.length"
          :description="tt('accountStats.noSearchResults')"
          :image-size="100"
        >
          <el-button type="primary" plain @click="clearSearch">{{
            tt('accountStats.clearSearch')
          }}</el-button>
        </el-empty>
        <div v-else class="account-grid">
          <el-card
            v-for="account in filteredAccounts"
            :key="account.accountId"
            shadow="hover"
            class="account-card"
            :class="{ 'is-stale': isStatsStale(account.statsUpdatedAt) }"
          >
            <div class="account-card-header">
              <el-avatar :size="48" :src="account.avatarUrl">
                <Icon icon="ep:user" />
              </el-avatar>
              <div class="account-heading">
                <strong>{{ accountName(account) }}</strong>
                <span>{{
                  account.username ? `@${account.username}` : tt('accountStats.account')
                }}</span>
              </div>
              <el-tag
                v-if="account.statsAvailable && isStatsStale(account.statsUpdatedAt)"
                type="warning"
                effect="light"
                size="small"
              >
                {{ tt('accountStats.expired') }}
              </el-tag>
              <el-tag
                v-else-if="account.statsAvailable"
                type="success"
                effect="light"
                size="small"
                >{{ tt('accountStats.synced') }}</el-tag
              >
              <el-tag v-else type="warning" effect="light" size="small">{{
                tt('accountStats.pendingSync')
              }}</el-tag>
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
                <el-button
                  v-if="account.recentVideos && account.recentVideos.length > 2"
                  link
                  type="primary"
                  size="small"
                  :aria-expanded="isVideosExpanded(account.accountId)"
                  @click="toggleRecentVideos(account.accountId)"
                >
                  <Icon
                    :icon="isVideosExpanded(account.accountId) ? 'ep:arrow-up' : 'ep:arrow-down'"
                  />
                  {{
                    isVideosExpanded(account.accountId)
                      ? tt('accountStats.collapseVideos')
                      : tt('accountStats.expandVideos')
                  }}
                </el-button>
              </div>
              <div v-if="account.recentVideos?.length" class="recent-video-list">
                <div
                  v-for="(video, index) in visibleRecentVideos(account)"
                  :key="video.videoId || `${account.accountId}-${index}`"
                  class="recent-video-item"
                >
                  <el-image
                    class="recent-video-cover"
                    :src="video.coverImageUrl"
                    fit="cover"
                    :alt="recentVideoTitle(video)"
                  >
                    <template #error><Icon icon="ep:video-camera" /></template>
                  </el-image>
                  <div class="recent-video-content">
                    <div class="recent-video-title" :title="recentVideoTitle(video)">
                      <el-link
                        v-if="video.shareUrl"
                        :href="video.shareUrl"
                        target="_blank"
                        rel="noopener noreferrer"
                        underline="never"
                      >
                        {{ recentVideoTitle(video) }}
                      </el-link>
                      <span v-else>{{ recentVideoTitle(video) }}</span>
                    </div>
                    <div class="recent-video-meta">
                      <span>{{ formatVideoTime(video.createTime) }}</span>
                      <strong
                        >{{ formatCount(video.viewCount) }} {{ tt('accountStats.views') }}</strong
                      >
                    </div>
                  </div>
                  <el-link
                    v-if="video.shareUrl"
                    class="video-link recent-video-open"
                    :href="video.shareUrl"
                    target="_blank"
                    rel="noopener noreferrer"
                    :title="tt('accountStats.openVideo')"
                    :aria-label="tt('accountStats.openVideo')"
                    underline="never"
                  >
                    <Icon icon="ep:top-right" />
                  </el-link>
                </div>
              </div>
              <span v-else class="recent-video-empty">{{
                tt('accountStats.noPublicVideoData')
              }}</span>
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
import { checkPermi } from '@/utils/permission'

defineOptions({ name: 'TkTiktokAccountStats' })

const DATA_STALE_AFTER_MS = 24 * 60 * 60 * 1000
const message = useMessage()
const { tt } = useTkI18n()
const canSync = checkPermi(['tk:tiktok-content-display:sync'])
const loading = ref(true)
const refreshing = ref(false)
const loadError = ref(false)
const hasLoaded = ref(false)
const searchKeyword = ref('')
const expandedAccounts = reactive<Record<number, boolean>>({})
const overview = ref<TkTiktokAccountStatsOverviewVO>({
  topFollowerAccounts: [],
  topLatestVideoViewAccounts: [],
  accounts: []
})

const filteredAccounts = computed(() => {
  const keyword = searchKeyword.value.trim().toLocaleLowerCase()
  if (!keyword) {
    return overview.value.accounts
  }
  return overview.value.accounts.filter((account) =>
    [account.displayName, account.username]
      .filter(Boolean)
      .some((value) => value!.toLocaleLowerCase().includes(keyword))
  )
})

const hasStaleAccountData = computed(() =>
  overview.value.accounts.some(
    (account) => account.statsAvailable && isStatsStale(account.statsUpdatedAt)
  )
)

const accountName = (account: TkTiktokAccountStatsRankVO | TkTiktokAccountStatsVO) =>
  account.displayName || account.username || `${tt('accountStats.account')} #${account.accountId}`

const rankVideoTitle = (item: TkTiktokAccountStatsRankVO) =>
  item.latestVideoTitle ||
  (item.latestVideoId
    ? `${tt('accountStats.video')} ${item.latestVideoId}`
    : tt('accountStats.untitledVideo'))

const recentVideoTitle = (video: NonNullable<TkTiktokAccountStatsVO['recentVideos']>[number]) =>
  video.title ||
  (video.videoId
    ? `${tt('accountStats.video')} ${video.videoId}`
    : tt('accountStats.untitledVideo'))

const formatCount = (value?: number | null) =>
  value == null ? '--' : new Intl.NumberFormat('en-US').format(value)
const formatDateTime = (value?: string | null) => (value ? formatDate(value) : '--')
const formatVideoTime = (value?: number | null) => (value == null ? '--' : formatDate(value * 1000))
const rankClass = (rank: number) => (rank <= 3 ? `rank-${rank}` : '')
const isStatsStale = (value?: string | null) => {
  if (!value) {
    return false
  }
  const timestamp = Date.parse(value)
  return Number.isFinite(timestamp) && Date.now() - timestamp > DATA_STALE_AFTER_MS
}
const isVideosExpanded = (accountId: number) => Boolean(expandedAccounts[accountId])
const visibleRecentVideos = (account: TkTiktokAccountStatsVO) => {
  const videos = account.recentVideos || []
  return isVideosExpanded(account.accountId) ? videos.slice(0, 5) : videos.slice(0, 2)
}
const toggleRecentVideos = (accountId: number) => {
  expandedAccounts[accountId] = !isVideosExpanded(accountId)
}
const clearSearch = () => {
  searchKeyword.value = ''
}

const applyOverview = (data: TkTiktokAccountStatsOverviewVO) => {
  overview.value = {
    topFollowerAccounts: data.topFollowerAccounts || [],
    topLatestVideoViewAccounts: data.topLatestVideoViewAccounts || [],
    accounts: data.accounts || [],
    dataUpdatedAt: data.dataUpdatedAt,
    latestVideoSyncedAt: data.latestVideoSyncedAt
  }
}

const loadOverview = async (sync = false) => {
  if (!hasLoaded.value) {
    loading.value = true
  }
  refreshing.value = true
  loadError.value = false
  try {
    if (sync) {
      const results = await TkTiktokAccountStatsApi.syncAllAccounts()
      const failures = results.filter((item) => item.failReason)
      if (failures.length) {
        message.warning(`${failures.length} 个账号同步失败：${failures[0].failReason}`)
      }
    }
    applyOverview(await TkTiktokAccountStatsApi.getOverview())
    hasLoaded.value = true
  } catch {
    loadError.value = true
    message.error(tt('accountStats.loadFailed'))
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

onMounted(() => loadOverview(canSync))
</script>

<style scoped>
.account-stats-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 4px;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.page-heading {
  min-width: 0;
}

.page-title {
  margin: 0;
  font-size: 24px;
  line-height: 32px;
  color: var(--el-text-color-primary);
}

.page-subtitle {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.page-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 10px;
}

.button-icon {
  margin-right: 4px;
}

.alert-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
}

.ranking-row {
  row-gap: 16px;
}

.ranking-panel {
  height: 100%;
  border-color: var(--el-border-color-light);
}

.panel-title {
  display: flex;
  font-size: 17px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  align-items: center;
  justify-content: space-between;
}

.panel-icon {
  font-size: 18px;
  color: var(--el-color-primary);
}

.ranking-list {
  display: flex;
  flex-direction: column;
}

.ranking-item {
  display: flex;
  align-items: center;
  min-height: 64px;
  gap: 10px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.ranking-item:last-child {
  border-bottom: 0;
}

.ranking-number {
  display: inline-flex;
  width: 24px;
  height: 24px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  align-items: center;
  justify-content: center;
}

.ranking-number.rank-1,
.ranking-number.rank-2,
.ranking-number.rank-3 {
  font-weight: 700;
  color: #fff;
  border-radius: 50%;
}

.ranking-number.rank-1 {
  background: #f3b51b;
}

.ranking-number.rank-2 {
  background: #9aa5b1;
}

.ranking-number.rank-3 {
  background: #c9854b;
}

.ranking-video-cover {
  width: 38px;
  height: 50px;
  color: var(--el-text-color-placeholder);
  background: var(--el-fill-color-light);
  border-radius: 4px;
  flex: 0 0 38px;
}

.ranking-account {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.ranking-account strong,
.ranking-account span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ranking-account strong {
  font-size: 14px;
  color: var(--el-text-color-primary);
}

.ranking-account span {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.ranking-video-title {
  color: var(--el-text-color-primary) !important;
}

.ranking-value-wrap {
  display: flex;
  align-items: center;
  gap: 6px;
}

.ranking-value {
  font-size: 14px;
  color: var(--el-color-primary);
  white-space: nowrap;
}

.video-link {
  font-size: 15px;
  color: var(--el-color-primary);
  flex: 0 0 auto;
}

.accounts-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.section-heading > div:first-child {
  display: flex;
  align-items: baseline;
  min-width: 0;
  gap: 10px;
}

.section-heading h3 {
  margin: 0;
  font-size: 18px;
  color: var(--el-text-color-primary);
}

.section-description {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.account-search {
  width: 280px;
}

.account-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.account-card {
  min-width: 0;
  border-color: var(--el-border-color-light);
}

.account-card.is-stale {
  border-color: var(--el-color-warning-light-5);
}

.account-card-header {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 12px;
}

.account-heading {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.account-heading strong,
.account-heading span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.account-heading strong {
  font-size: 15px;
  color: var(--el-text-color-primary);
}

.account-heading span {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.metrics-grid {
  display: grid;
  padding: 12px 0;
  margin-top: 18px;
  border-top: 1px solid var(--el-border-color-lighter);
  border-bottom: 1px solid var(--el-border-color-lighter);
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.metric-item {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 6px;
  padding: 0 10px;
  border-right: 1px solid var(--el-border-color-lighter);
}

.metric-item:first-child {
  padding-left: 0;
}

.metric-item:last-child {
  padding-right: 0;
  border-right: 0;
}

.metric-item span,
.account-card-footer span {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.metric-item strong {
  overflow: hidden;
  font-size: 14px;
  color: var(--el-text-color-primary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.account-card-footer {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-top: 12px;
}

.recent-videos {
  padding-top: 12px;
  margin-top: 14px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.recent-videos-heading {
  display: flex;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.recent-videos-heading .el-button {
  margin-right: -8px;
}

.recent-video-list {
  display: flex;
  flex-direction: column;
  margin-top: 8px;
}

.recent-video-item {
  display: grid;
  min-height: 58px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  grid-template-columns: 44px minmax(0, 1fr) auto;
  align-items: center;
  gap: 9px;
}

.recent-video-cover {
  width: 44px;
  height: 56px;
  color: var(--el-text-color-placeholder);
  background: var(--el-fill-color-light);
  border-radius: 4px;
}

.recent-video-content {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 5px;
}

.recent-video-title {
  overflow: hidden;
  font-size: 13px;
  color: var(--el-text-color-primary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recent-video-title .el-link {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}

.recent-video-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-width: 0;
  gap: 8px;
}

.recent-video-meta span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recent-video-meta strong {
  font-size: 12px;
  color: var(--el-text-color-primary);
  white-space: nowrap;
}

.recent-video-open {
  margin-right: 2px;
}

.recent-video-empty {
  display: block;
  padding-top: 8px;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.footer-time {
  margin-left: auto;
  white-space: nowrap;
}

@media (width <= 1200px) {
  .account-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (width <= 680px) {
  .page-header,
  .section-heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .page-actions {
    justify-content: flex-start;
  }

  .section-heading > div:first-child {
    width: 100%;
    justify-content: space-between;
  }

  .account-search {
    width: 100%;
  }

  .account-grid {
    grid-template-columns: 1fr;
  }

  .metrics-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px 0;
  }

  .metric-item:nth-child(2) {
    border-right: 0;
  }

  .metric-item:nth-child(3),
  .metric-item:nth-child(4) {
    padding-top: 10px;
  }

  .metric-item:nth-child(3) {
    padding-left: 0;
  }

  .metric-item:nth-child(4) {
    padding-right: 0;
  }

  .alert-content {
    align-items: flex-start;
    flex-direction: column;
    gap: 4px;
  }
}
</style>
