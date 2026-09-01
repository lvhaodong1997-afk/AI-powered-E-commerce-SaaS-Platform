import request from '@/config/axios'

export interface TkTiktokOverviewVO {
  authorizedAccountCount: number
  pendingPublishCount: number
  failedPublishCount: number
  tokenAbnormalCount: number
}

export interface TkCompanySimpleVO {
  id: number
  name: string
}

export interface TkCompanyVO {
  id?: number
  tenantId?: number
  name: string
  status?: number
  contactName?: string
  contactPhone?: string
  createTime?: string
}

export interface TkTiktokAccountVO {
  id: number
  tenantId?: number
  businessTraceId?: string
  companyId: number
  openId: string
  displayName?: string
  username?: string
  avatarUrl?: string
  scopes?: string
  accessTokenExpireTime?: string
  refreshTokenExpireTime?: string
  tokenStatus: 'VALID' | 'AUTO_REFRESH' | 'EXPIRED' | string
  authStatus: string
  defaultPrivacyLevel?: string
  allowComment?: boolean
  allowDuet?: boolean
  allowStitch?: boolean
  commercialContent?: boolean
  brandContent?: boolean
  aigcContent?: boolean
  labels?: string
  lastAuthTime?: string
  lastPublishTime?: string
  failReasonCode?: string
  failReason?: string
  status?: number
  createTime?: string
}

export interface TkTiktokAccountGroupVO {
  id?: number
  tenantId?: number
  companyId?: number
  name: string
  scene?: string
  labels?: string
  remark?: string
  status?: number
  accountIds?: number[]
  accountCount?: number
  createTime?: string
}

export interface TkTiktokPublishTaskVO {
  id: number
  tenantId?: number
  businessTraceId?: string
  companyId: number
  generationTaskId?: number
  uploadedVideoId?: number
  sourceType?: string
  title: string
  caption?: string
  videoUrl: string
  coverUrl?: string
  coverTimestampMs?: number
  postMode: string
  privacyLevel?: string
  accountCount: number
  successCount: number
  failedCount: number
  pendingCount: number
  status: string
  failReasonCode?: string
  failReason?: string
  createTime?: string
}

export interface TkTiktokPublishDetailVO {
  id: number
  tenantId?: number
  companyId: number
  publishTaskId: number
  generationTaskId?: number
  uploadedVideoId?: number
  sourceType?: string
  accountId?: number
  accountDisplayName?: string
  publishId?: string
  publishUrl?: string
  tiktokStatus?: string
  status: string
  postMode: string
  privacyLevel?: string
  coverUrl?: string
  coverTimestampMs?: number
  allowComment?: boolean
  allowDuet?: boolean
  allowStitch?: boolean
  commercialContent?: boolean
  brandContent?: boolean
  aigcContent?: boolean
  failReasonCode?: string
  failReason?: string
  retryCount?: number
  publishUrlRegisteredTime?: string
  lastSyncTime?: string
  createTime?: string
}

export interface TkTiktokPublishUrlVO {
  generationTaskId: number
  publishTaskId?: number
  publishDetailId?: number
  accountId?: number
  accountDisplayName?: string
  publishUrl: string
  publishUrlRegisteredTime?: string
}

export const TkVideoPublishCenterApi = {
  getOverview: async () => {
    return await request.get({ url: '/tk/video-publish-center/overview' })
  }
}

export const TkCompanyApi = {
  getSimpleList: async () => {
    return await request.get<TkCompanySimpleVO[]>({ url: '/tk/company/simple-list' })
  },
  getPage: async (params: any) => {
    return await request.get({ url: '/tk/company/page', params })
  },
  get: async (id: number) => {
    return await request.get<TkCompanyVO>({ url: '/tk/company/get', params: { id } })
  },
  create: async (data: TkCompanyVO) => {
    return await request.post({ url: '/tk/company/create', data })
  },
  update: async (data: TkCompanyVO) => {
    return await request.put({ url: '/tk/company/update', data })
  },
  delete: async (id: number) => {
    return await request.delete({ url: '/tk/company/delete', params: { id } })
  }
}

export const TkTiktokAccountApi = {
  getPage: async (params: any) => {
    return await request.get({ url: '/tk/tiktok-account/page', params })
  },
  authorizeByRedirect: async (data: any) => {
    return await request.post({ url: '/tk/tiktok-auth/redirect-url', data })
  },
  authorizeByQrCode: async (data: any) => {
    return await request.post({ url: '/tk/tiktok-auth/qrcode/start', data })
  },
  getQrCodeStatus: async (clientTicket: string) => {
    return await request.get({ url: '/tk/tiktok-auth/qrcode/status', params: { clientTicket } })
  },
  updateDefaultConfig: async (data: Partial<TkTiktokAccountVO>) => {
    return await request.post({ url: '/tk/tiktok-account/default-config', data })
  },
  unbind: async (id: number) => {
    return await request.delete({ url: '/tk/tiktok-account/unbind', params: { id } })
  },
  delete: async (id: number) => {
    return await request.delete({ url: '/tk/tiktok-account/delete', params: { id } })
  }
}

export const TkTiktokAccountGroupApi = {
  getPage: async (params: any) => {
    return await request.get({ url: '/tk/tiktok-account-group/page', params })
  },
  create: async (data: TkTiktokAccountGroupVO) => {
    return await request.post({ url: '/tk/tiktok-account-group', data })
  },
  update: async (data: TkTiktokAccountGroupVO) => {
    return await request.put({ url: '/tk/tiktok-account-group', data })
  },
  delete: async (id: number) => {
    return await request.delete({ url: '/tk/tiktok-account-group', params: { id } })
  }
}

export const TkTiktokPublishApi = {
  uploadMedia: async (data: FormData, option: any = {}) => {
    const res = await request.upload<{ data: TkTiktokPublishMediaVO }>({
      url: '/tk/tiktok-publish/media/upload',
      data,
      timeout: 15 * 60 * 1000,
      ...option
    })
    return res.data as TkTiktokPublishMediaVO
  },
  create: async (data: {
    generationTaskId?: number
    uploadedVideoId?: number
    coverTimestampMs?: number
    accountIds?: number[]
    groupIds?: number[]
    title?: string
    caption?: string
    postMode?: string
    privacyLevel?: string
    allowComment?: boolean
    allowDuet?: boolean
    allowStitch?: boolean
    commercialContent?: boolean
    brandContent?: boolean
    aigcContent?: boolean
  }) => {
    return await request.post({ url: '/tk/tiktok-publish/create', data })
  },
  getTaskPage: async (params: any) => {
    return await request.get({ url: '/tk/tiktok-publish/task-page', params })
  },
  getDetailPage: async (params: any) => {
    return await request.get({ url: '/tk/tiktok-publish/detail-page', params })
  },
  retry: async (detailId: number) => {
    return await request.post({ url: '/tk/tiktok-publish/retry', params: { detailId } })
  },
  syncStatus: async (taskId: number) => {
    return await request.post({ url: '/tk/tiktok-publish/status/sync', params: { taskId } })
  },
  registerPublishUrl: async (data: {
    generationTaskId: number
    publishDetailId?: number
    publishUrl: string
  }): Promise<TkTiktokPublishUrlVO> => {
    return await request.post({ url: '/tk/tiktok-publish/publish-url/register', data })
  }
}

export interface TkTiktokPublishMediaVO {
  id: number
  fileName: string
  fileUrl: string
  coverUrl?: string
  fileSize: number
  mimeType?: string
  status: string
}

export interface TkTiktokMediaUploadSessionVO {
  uploadId: string
  uploadMode?: 'local' | 'oss'
  chunkSize?: number
  totalChunks?: number
  uploadedSize?: number
  uploadedChunks?: number[]
  uploadUrl?: string
  publicUrl?: string
  objectKey?: string
  accessKeyId?: string
  policy?: string
  signature?: string
  successActionStatus?: string
  expiration?: string
}

export interface TkTiktokMediaUploadCompleteVO {
  uploadedVideoId: number
}

export const TkTiktokMediaUploadApi = {
  createMediaUploadSession: async (data: {
    fileName: string
    fileSize: number
    contentType?: string
  }) => {
    return await request.post<TkTiktokMediaUploadSessionVO>({
      url: '/tk/tiktok-publish/media/session/create',
      data
    })
  },
  getMediaUploadSession: async (uploadId: string) => {
    return await request.get<TkTiktokMediaUploadSessionVO>({
      url: `/tk/tiktok-publish/media/session/${uploadId}`
    })
  },
  uploadMediaChunk: async (data: FormData, uploadId: string, chunkIndex: number, option: any = {}) => {
    return await request.upload({
      url: '/tk/tiktok-publish/media/chunk',
      data,
      params: { uploadId, chunkIndex },
      timeout: 15 * 60 * 1000,
      ...option
    })
  },
  completeMediaUpload: async (data: {
    uploadId: string
    fileName?: string
    fileSize?: number
    contentType?: string
  }) => {
    return await request.post<TkTiktokMediaUploadCompleteVO>({
      url: '/tk/tiktok-publish/media/session/complete',
      data
    })
  },
  cancelMediaUpload: async (uploadId: string) => {
    return await request.delete({
      url: `/tk/tiktok-publish/media/session/${uploadId}`
    })
  }
}
