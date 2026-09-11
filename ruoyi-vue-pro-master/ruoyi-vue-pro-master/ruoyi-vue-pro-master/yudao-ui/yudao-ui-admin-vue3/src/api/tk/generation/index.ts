import request from '@/config/axios'

export interface TkGenerationTaskVO {
  id?: number
  tenantId?: number
  creator?: string
  creatorName?: string
  dailyUserVideoNo?: number
  businessTraceId?: string
  companyId?: number
  sourceUrl?: string
  productId?: number
  libraryId: number
  templateId?: number
  voiceId?: number
  ttsProvider?: string
  voiceCode?: string
  voiceProfileId?: number
  voiceEnabled?: boolean
  mimoVoiceMode?: string
  mimoVoiceCode?: string
  mimoVoicePrompt?: string
  mimoVoiceSampleUrl?: string
  targetLanguage?: string
  materialPurpose?: string
  productCategoryCode?: string
  clipPlanMode?: string
  generationRouteCode?: string
  generationRouteConfig?: string
  referenceAnalysisId?: number
  scriptOptionId?: number
  scriptTitle?: string
  scriptOptionIds?: number[]
  videosPerScript?: number
  openingVideoUrl?: string
  openingVideoName?: string
  openingUploadId?: string
  openingProcessMode?: 'NATIVE' | 'STANDARD'
  openingDurationMs?: number
  openingClipStartSecond?: number
  openingClipEndSecond?: number
  referenceDuration?: number
  targetDuration?: number
  clipSeconds?: number
  segmentDurationConfig?: string
  promptText?: string
  scriptText?: string
  segmentTimeline?: string
  audioUrl?: string
  bgmEnabled?: boolean
  bgmAssetId?: number
  bgmSourceType?: string
  bgmUrl?: string
  bgmVolume?: number
  subtitleUrl?: string
  subtitleEnabled?: boolean
  subtitleStyle?: string
  subtitlePositionMode?: string
  subtitleKeywordEnabled?: boolean
  subtitleKeywords?: string
  subtitleKeywordMode?: string
  subtitleKaraokeEnabled?: boolean
  subtitleActiveColor?: string
  subtitleKeywordColor?: string
  subtitleFontSize?: string
  subtitleTimelineUrl?: string
  subtitleVisualAnalysisUrl?: string
  subtitleLayoutUrl?: string
  subtitleAssUrl?: string
  clipPlan?: string
  status?: string
  progress?: number
  outputUrl?: string
  failReason?: string
  failCode?: string
  currentStep?: string
  currentStepCode?: string
  currentStepCompleted?: number
  currentStepTotal?: number
  precheckResult?: string
  retryCount?: number
  lastRetryTime?: string
  batchId?: number
  scriptIndex?: number
  videoIndex?: number
  workerId?: string
  heartbeatTime?: string
  latestPublishDetailId?: number
  latestPublishAccountName?: string
  latestPublishUrl?: string
  latestPublishUrlRegisteredTime?: string
  stepStartedAt?: string
  stepFinishedAt?: string
  title?: string
  createTime?: string | number
}

export interface TkGenerationTaskSummaryVO {
  id?: number
  tenantId?: number
  creator?: string
  creatorName?: string
  dailyUserVideoNo?: number
  businessTraceId?: string
  companyId?: number
  sourceUrl?: string
  libraryId?: number
  materialPurpose?: string
  productCategoryCode?: string
  generationRouteCode?: string
  ttsProvider?: string
  mimoVoiceMode?: string
  voiceEnabled?: boolean
  bgmEnabled?: boolean
  subtitleEnabled?: boolean
  openingVideoName?: string
  openingProcessMode?: 'NATIVE' | 'STANDARD'
  openingDurationMs?: number
  referenceDuration?: number
  targetDuration?: number
  status?: string
  progress?: number
  outputUrl?: string
  failReason?: string
  failCode?: string
  currentStep?: string
  currentStepCode?: string
  currentStepCompleted?: number
  currentStepTotal?: number
  retryCount?: number
  batchId?: number
  scriptIndex?: number
  videoIndex?: number
  workerId?: string
  heartbeatTime?: string
  latestPublishDetailId?: number
  latestPublishAccountName?: string
  latestPublishUrl?: string
  latestPublishUrlRegisteredTime?: string
  title?: string
  scriptTitle?: string
  createTime?: string | number
}

export interface TkGenerationOpeningUploadSessionVO {
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

export interface TkGenerationOpeningUploadStatusVO {
  uploadId: string
  chunkSize?: number
  totalChunks?: number
  fileSize?: number
  uploadedSize?: number
  uploadedChunks?: number[]
  status?: string
}

export interface TkGenerationOpeningUploadCompleteVO {
  uploadId: string
  fileName: string
  fileUrl: string
  fileSize?: number
  status?: string
}

export interface TkGenerationTaskStatusVO {
  id: number
  title?: string
  scriptTitle?: string
  status?: string
  progress?: number
  outputUrl?: string
  failReason?: string
  failCode?: string
  currentStep?: string
  currentStepCode?: string
  currentStepCompleted?: number
  currentStepTotal?: number
  batchId?: number
  scriptIndex?: number
  videoIndex?: number
  productCategoryCode?: string
  generationRouteCode?: string
  heartbeatTime?: string
  stepStartedAt?: string
  stepFinishedAt?: string
}

export interface TkAudioExportTaskVO {
  id?: number
  status?: 'PROCESSING' | 'SUCCESS' | 'FAILED'
  audioUrl?: string
  failReason?: string
}

export interface TkAudioExportTaskCreateVO {
  companyId?: number
  requestId: string
  scriptText: string
  ttsProvider?: string
  voiceCode?: string
  voiceProfileId?: number
  mimoVoiceMode?: string
  mimoVoiceCode?: string
  mimoVoicePrompt?: string
  mimoVoiceSampleUrl?: string
  targetLanguage?: string
}

export interface TkGenerationPrecheckIssueVO {
  code: string
  message: string
  title?: string
  actionHint?: string
  segmentType?: string
  segmentName?: string
  requiredDuration?: number
  actualDuration?: number
  missingDuration?: number
}

export interface TkGenerationPrecheckRespVO {
  passed: boolean
  warnings: TkGenerationPrecheckIssueVO[]
  errors: TkGenerationPrecheckIssueVO[]
  materialSummary: {
    availableCount: number
    totalDuration: number
    targetDuration: number
  }
  phaseSummary?: {
    attentionCount: number
    attentionDuration: number
    productShowCount: number
    productShowDuration: number
    resultEffectCount: number
    resultEffectDuration: number
    generalCount: number
    generalDuration: number
  }
  segmentSummary?: Array<{
    segmentType: string
    segmentName: string
    count: number
    duration: number
    keySegment: boolean
    requiredDuration?: number
    missingDuration?: number
  }>
}

export const TkGenerationApi = {
  createOpeningUploadSession: async (data: {
    libraryId: number
    fileName: string
    fileSize: number
    contentType?: string
  }): Promise<TkGenerationOpeningUploadSessionVO> => {
    return await request.post({
      url: '/tk/generation/opening/session/create',
      data,
      timeout: 15 * 60 * 1000
    })
  },
  getOpeningUploadSession: async (uploadId: string): Promise<TkGenerationOpeningUploadStatusVO> => {
    return await request.get({
      url: `/tk/generation/opening/session/${uploadId}`,
      timeout: 15 * 60 * 1000
    })
  },
  uploadOpeningChunk: async (data: FormData, uploadId: string, chunkIndex: number, option: any = {}) => {
    return await request.upload({
      url: '/tk/generation/opening/chunk',
      data,
      params: { uploadId, chunkIndex },
      timeout: 15 * 60 * 1000,
      ...option
    })
  },
  completeOpeningUpload: async (uploadId: string): Promise<TkGenerationOpeningUploadCompleteVO> => {
    return await request.post({
      url: '/tk/generation/opening/session/complete',
      data: { uploadId },
      timeout: 15 * 60 * 1000
    })
  },
  cancelOpeningUpload: async (uploadId: string) => {
    return await request.delete({
      url: `/tk/generation/opening/session/${uploadId}`,
      timeout: 15 * 60 * 1000
    })
  },
  precheckGeneration: async (data: TkGenerationTaskVO): Promise<TkGenerationPrecheckRespVO> => {
    return await request.post({ url: '/tk/generation/precheck', data })
  },
  createGeneration: async (data: TkGenerationTaskVO) => {
    return await request.post({ url: '/tk/generation/create', data })
  },
  createGenerationBatch: async (data: TkGenerationTaskVO): Promise<number[]> => {
    return await request.post({ url: '/tk/generation/create-batch', data })
  },
  createGenerationWithOpening: async (data: FormData): Promise<number> => {
    const response = await request.upload<{ data: number }>({
      url: '/tk/generation/create-with-opening',
      data
    })
    return response.data
  },
  createAudioExport: async (data: TkAudioExportTaskCreateVO): Promise<TkAudioExportTaskVO> => {
    return await request.post({ url: '/tk/generation/audio-export', data })
  },
  previewVoice: async (
    data: Pick<
      TkGenerationTaskVO,
      | 'ttsProvider'
      | 'voiceCode'
      | 'voiceProfileId'
      | 'targetLanguage'
      | 'mimoVoiceMode'
      | 'mimoVoiceCode'
      | 'mimoVoicePrompt'
      | 'mimoVoiceSampleUrl'
    >
  ): Promise<Blob> => {
    return (await request.postOriginal({
      url: '/tk/generation/voice-preview',
      data,
      responseType: 'blob'
    })) as unknown as Blob
  },
  getGenerationPage: async (params: any) => {
    return await request.get({ url: '/tk/generation/page', params })
  },
  getGenerationSummaryPage: async (params: any) => {
    return await request.get({ url: '/tk/generation/page-summary', params })
  },
  getGenerationStatusBatch: async (ids: number[]): Promise<TkGenerationTaskStatusVO[]> => {
    return await request.get({ url: '/tk/generation/status-batch', params: { ids: ids.join(',') } })
  },
  getGeneration: async (id: number) => {
    return await request.get({ url: '/tk/generation/get', params: { id } })
  },
  retryGeneration: async (id: number) => {
    return await request.post({ url: '/tk/generation/retry', params: { id } })
  }
}
