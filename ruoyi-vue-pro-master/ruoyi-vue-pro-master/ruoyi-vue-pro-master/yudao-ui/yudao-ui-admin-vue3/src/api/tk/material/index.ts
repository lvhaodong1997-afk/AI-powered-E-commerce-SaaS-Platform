import request from '@/config/axios'

export const MATERIAL_VIDEO_UPLOAD_TIMEOUT = 15 * 60 * 1000

export interface TkMaterialLibraryVO {
  id?: number
  tenantId?: number
  companyId?: number
  name: string
  category?: string
  scene?: string
  materialPurpose?: 'ECOMMERCE' | 'LEAD_GENERATION'
  tags?: string
  description?: string
  coverUrl?: string
  previewVideoUrl?: string
  videoCount?: number
  totalSize?: number
  defaulted?: boolean
  status?: number
  createTime?: string
}

export interface TkMaterialVideoVO {
  id: number
  tenantId: number
  companyId: number
  libraryId: number
  fileName: string
  fileUrl: string
  coverUrl?: string
  duration?: number
  size?: number
  resolution?: string
  format?: string
  tags?: string
  usagePhase?: string
  segmentType?: string
  status: string
  failReason?: string
  createTime?: string
}

export interface TkMaterialVideoSegmentSummaryVO {
  segmentType: string
  count: number
}

export interface TkUploadSessionRespVO {
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

export const TkMaterialApi = {
  getLibraryPage: async (params: any) => {
    return await request.get({ url: '/tk/material-library/page', params })
  },
  getLibrary: async (id: number) => {
    return await request.get({ url: '/tk/material-library/get', params: { id } })
  },
  createLibrary: async (data: TkMaterialLibraryVO) => {
    return await request.post({ url: '/tk/material-library/create', data })
  },
  updateLibrary: async (data: TkMaterialLibraryVO) => {
    return await request.put({ url: '/tk/material-library/update', data })
  },
  deleteLibrary: async (id: number) => {
    return await request.delete({ url: '/tk/material-library/delete', params: { id } })
  },
  getVideoPage: async (params: any) => {
    return await request.get({ url: '/tk/material-video/page', params })
  },
  getSegmentSummary: async (libraryId: number): Promise<TkMaterialVideoSegmentSummaryVO[]> => {
    return await request.get({ url: '/tk/material-video/segment-summary', params: { libraryId } })
  },
  uploadVideo: async (data: FormData, option: any = {}) => {
    return await request.upload({
      url: '/tk/material-video/upload',
      data,
      timeout: MATERIAL_VIDEO_UPLOAD_TIMEOUT,
      ...option
    })
  },
  createMaterialVideoUploadSession: async (data: {
    libraryId: number
    fileName: string
    fileSize: number
    contentType?: string
  }) => {
    return await request.post<TkUploadSessionRespVO>({
      url: '/tk/upload/material-video/session/create',
      data,
      timeout: MATERIAL_VIDEO_UPLOAD_TIMEOUT
    })
  },
  uploadMaterialVideoChunk: async (data: FormData, option: any = {}) => {
    return await request.upload({
      url: '/tk/upload/material-video/chunk',
      data,
      timeout: MATERIAL_VIDEO_UPLOAD_TIMEOUT,
      ...option
    })
  },
  completeMaterialVideoUpload: async (data: {
    uploadId: string
    libraryId?: number
    fileName?: string
    fileSize?: number
    contentType?: string
    objectKey?: string
    fileUrl?: string
    tags?: string
    usagePhase?: string
    segmentType?: string
  }) => {
    return await request.post<number>({
      url: '/tk/upload/material-video/session/complete',
      data,
      timeout: MATERIAL_VIDEO_UPLOAD_TIMEOUT
    })
  },
  cancelMaterialVideoUpload: async (uploadId: string) => {
    return await request.delete({
      url: `/tk/upload/material-video/session/${uploadId}`,
      timeout: MATERIAL_VIDEO_UPLOAD_TIMEOUT
    })
  },
  updateVideoUsagePhase: async (data: { ids: number[]; usagePhase: string }) => {
    return await request.put({ url: '/tk/material-video/usage-phase', data })
  },
  updateVideoSegmentType: async (data: { ids: number[]; segmentType: string }) => {
    return await request.put({ url: '/tk/material-video/segment-type/update', data })
  },
  deleteVideo: async (id: number) => {
    return await request.delete({ url: '/tk/material-video/delete', params: { id } })
  }
}
