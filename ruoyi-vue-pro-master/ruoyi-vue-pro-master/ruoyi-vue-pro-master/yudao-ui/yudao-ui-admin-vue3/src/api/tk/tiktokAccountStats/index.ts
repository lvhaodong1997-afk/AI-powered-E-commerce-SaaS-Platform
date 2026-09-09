import request from '@/config/axios'

export interface TkTiktokAccountStatsRankVO {
  rank: number
  accountId: number
  displayName?: string
  username?: string
  avatarUrl?: string
  metricValue?: number
  latestVideoViewCount?: number
  latestVideoCreateTime?: number
  latestVideoId?: string
  latestVideoTitle?: string
  latestVideoCoverImageUrl?: string
  latestVideoShareUrl?: string
}

export interface TkTiktokAccountRecentVideoVO {
  videoId?: string
  title?: string
  coverImageUrl?: string
  shareUrl?: string
  viewCount?: number
  createTime?: number
}

export interface TkTiktokAccountStatsVO {
  accountId: number
  displayName?: string
  username?: string
  openId?: string
  avatarUrl?: string
  followerCount?: number
  followingCount?: number
  likesCount?: number
  videoCount?: number
  latestVideoViewCount?: number
  latestVideoCreateTime?: number
  latestVideoId?: string
  recentVideos?: TkTiktokAccountRecentVideoVO[]
  statsUpdatedAt?: string
  statsAvailable?: boolean
}

export interface TkTiktokAccountStatsOverviewVO {
  topFollowerAccounts: TkTiktokAccountStatsRankVO[]
  topLatestVideoViewAccounts: TkTiktokAccountStatsRankVO[]
  accounts: TkTiktokAccountStatsVO[]
  dataUpdatedAt?: string
  latestVideoSyncedAt?: string
}

export interface TkTiktokAccountStatsSyncVO {
  accountId: number
  syncedCount?: number
  truncated?: boolean
  failReason?: string
}

export const TkTiktokAccountStatsApi = {
  getOverview: async (): Promise<TkTiktokAccountStatsOverviewVO> => {
    return await request.get({ url: '/tk/tiktok-account-stats/overview' })
  },
  syncAllAccounts: async (): Promise<TkTiktokAccountStatsSyncVO[]> => {
    return await request.post({ url: '/tk/tiktok-account-stats/sync' })
  }
}
