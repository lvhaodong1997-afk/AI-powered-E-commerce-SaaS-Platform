import request from '@/config/axios'

export type SocialPlatform = 'INSTAGRAM' | 'FACEBOOK_PAGE'
export type SocialMediaType = 'IMAGE' | 'VIDEO'
export type SocialStatus = 'PENDING' | 'PROCESSING' | 'SUCCESS' | 'PARTIAL_SUCCESS' | 'FAILED' | 'REAUTH_REQUIRED' | 'UNKNOWN'
export interface SocialPage<T> { list: T[]; total: number }
export interface SocialPageQuery { pageNo: number; pageSize: number; status?: string }
export interface SocialCapabilities {
  enabled: boolean
  mediaTypes: SocialMediaType[]
  platforms: SocialPlatform[]
  maxVideoBytes?: number
  maxVideoSizeMb?: number
}
export interface SocialAccount {
  id: number
  platform: SocialPlatform
  accountType?: string
  externalAccountId?: string
  accountName?: string
  username?: string
  status: string
  tokenExpiresAt?: string
  lastValidatedAt?: string
  failReason?: string
}
export interface SocialAuthSession {
  status: 'PENDING' | 'PROCESSING' | 'PAGES_READY' | 'SUCCESS' | 'FAILED' | 'EXPIRED'
  message?: string
}
export interface FacebookPage { id: string; name: string; tasks: string[] }
export interface SocialMedia {
  id: number
  fileName: string
  publicUrl: string
  mediaType: SocialMediaType
  fileSize: number
  status: string
  width?: number
  height?: number
  durationSeconds?: number
  frameRate?: number
}
export interface SocialCreate {
  accountIds: number[]
  mediaId?: number
  generationTaskId?: number
  title: string
  instagramCaption?: string
  facebookMessage?: string
  idempotencyKey: string
}
export interface SocialTask {
  id: number
  title: string
  status: SocialStatus
  targetCount: number
  successCount: number
  failedCount: number
  pendingCount: number
  createTime?: string
  instagramCaption?: string
  facebookMessage?: string
  mediaId?: number
  generationTaskId?: number
}
export interface SocialDetail {
  id: number
  publishTaskId: number
  platform: SocialPlatform
  socialAccountId: number
  accountName?: string
  status: SocialStatus
  platformStatus?: string
  externalContainerId?: string
  externalMediaId?: string
  externalPostId?: string
  publishUrl?: string
  retryCount: number
  errorCode?: string
  errorMessage?: string
  publishedTime?: string
}

export const SocialPublishApi = {
  capabilities: () => request.get<SocialCapabilities>({ url: '/tk/social-publish/capabilities' }),
  authorize: (platform: SocialPlatform) => request.post<{ authorizeUrl: string; sessionId: string }>({
    url: `/tk/social-auth/${platform === 'INSTAGRAM' ? 'instagram' : 'facebook'}/redirect-url`, data: {}
  }),
  session: (sessionId: string) => request.get<SocialAuthSession>({ url: '/tk/social-auth/session', params: { sessionId } }),
  facebookPages: (sessionId: string) => request.get<FacebookPage[]>({ url: '/tk/social-account/facebook-pages', params: { sessionId } }),
  bindPages: (data: { sessionId: string; pageIds: string[] }) => request.post<boolean>({ url: '/tk/social-account/facebook-pages/bind', data }),
  accountPage: (params: SocialPageQuery & { platform?: SocialPlatform }) => request.get<SocialPage<SocialAccount>>({ url: '/tk/social-account/page', params }),
  validate: (id: number) => request.post<boolean>({ url: '/tk/social-account/validate', params: { id } }),
  unbind: (id: number) => request.delete<boolean>({ url: '/tk/social-account/unbind', params: { id } }),
  delete: (id: number) => request.delete<boolean>({ url: '/tk/social-account/delete', params: { id } }),
  upload: (file: Blob) => {
    const data = new FormData()
    data.append('file', file)
    // post unwraps CommonResult; FormData lets the browser supply its boundary.
    return request.post<SocialMedia>({ url: '/tk/social-publish/media/upload', data, timeout: 180_000 })
  },
  create: (data: SocialCreate) => request.post<number>({ url: '/tk/social-publish/create', data, timeout: 180_000 }),
  taskPage: (params: SocialPageQuery) => request.get<SocialPage<SocialTask>>({ url: '/tk/social-publish/task-page', params }),
  detailPage: (params: SocialPageQuery & { taskId: number }) => request.get<SocialPage<SocialDetail>>({ url: '/tk/social-publish/detail-page', params }),
  retry: (detailId: number) => request.post<boolean>({ url: '/tk/social-publish/retry', params: { detailId } }),
  sync: (taskId: number) => request.post<boolean>({ url: '/tk/social-publish/status/sync', params: { taskId } })
}
