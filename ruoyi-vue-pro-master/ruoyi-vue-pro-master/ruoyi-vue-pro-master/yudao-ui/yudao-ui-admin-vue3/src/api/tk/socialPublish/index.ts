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
export type SocialInsights = Record<string, unknown>
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
export interface SocialVideoUploadSession {
  uploadId: string
  uploadUrl: string
  publicUrl: string
  objectKey: string
  accessKeyId: string
  policy: string
  signature: string
  successActionStatus: string
  expiration: string
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
  insights: (id: number, params: { metric: string; period: string }) => request.get<SocialInsights>({ url: '/tk/social-account/insights', params: { id, ...params } }),
  validate: (id: number) => request.post<boolean>({ url: '/tk/social-account/validate', params: { id } }),
  unbind: (id: number) => request.delete<boolean>({ url: '/tk/social-account/unbind', params: { id } }),
  delete: (id: number) => request.delete<boolean>({ url: '/tk/social-account/delete', params: { id } }),
  videoUploadSession: (data: { fileName: string; fileSize: number; contentType: string }) =>
    request.post<SocialVideoUploadSession>({ url: '/tk/social-publish/media/video-upload-session', data, timeout: 30_000 }),
  videoUploadComplete: (data: {
    uploadId: string; fileName: string; fileSize: number; contentType: string; objectKey: string
    width: number; height: number; durationSeconds: number; frameRate: number
  }) => request.post<SocialMedia>({ url: '/tk/social-publish/media/video-upload-complete', data, timeout: 30_000 }),
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

export function uploadSocialVideoToOss(session: SocialVideoUploadSession, file: Blob,
                                       onProgress?: (percent: number) => void): Promise<void> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('POST', session.uploadUrl)
    xhr.upload.onprogress = event => {
      if (event.lengthComputable) onProgress?.(Math.min(99, Math.round(event.loaded / event.total * 100)))
    }
    xhr.onerror = () => reject(new Error('OSS 上传失败，请检查网络后重试'))
    xhr.onabort = () => reject(new Error('OSS 上传已取消'))
    xhr.onload = () => {
      if ([200, 201, 204].includes(xhr.status)) {
        onProgress?.(100)
        resolve()
      } else reject(new Error(`OSS 上传失败（HTTP ${xhr.status}）`))
    }
    const form = new FormData()
    form.append('key', session.objectKey)
    form.append('policy', session.policy)
    form.append('OSSAccessKeyId', session.accessKeyId)
    form.append('Signature', session.signature)
    form.append('success_action_status', session.successActionStatus)
    form.append('Content-Type', 'video/mp4')
    form.append('file', file)
    xhr.send(form)
  })
}
