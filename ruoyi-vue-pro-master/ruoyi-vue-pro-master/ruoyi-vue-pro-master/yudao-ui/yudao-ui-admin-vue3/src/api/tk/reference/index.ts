import request from '@/config/axios'

export type AnalysisProvider = 'GEMINI' | 'DASHSCOPE_VIDEO'

export interface TkReferenceAnalyzeReqVO {
  companyId?: number
  sourceUrl: string
  libraryId: number
  referenceDuration?: number
  targetLanguage?: string
  materialPurpose?: string
  analysisProvider?: AnalysisProvider
  forceRefresh?: boolean
}

export interface TkReferenceScriptOptionVO {
  id?: number
  tenantId?: number
  analysisId?: number
  companyId?: number
  libraryId?: number
  optionNo?: number
  title?: string
  points?: string
  displayTitleZh?: string
  displayPointsZh?: string
  estimatedConversionRate?: number | string
  conversionLevel?: string
  scriptText?: string
  segmentTimeline?: string
  displayScriptZh?: string
  selected?: boolean
  createTime?: string
}

export interface TkReferenceAnalysisVO {
  id?: number
  tenantId?: number
  creator?: string
  creatorName?: string
  businessTraceId?: string
  companyId?: number
  libraryId?: number
  sourceUrl?: string
  targetLanguage?: string
  referenceDuration?: number
  materialPurpose?: string
  analysisProvider?: AnalysisProvider
  analysisModel?: string
  sourceDomain?: string
  resolvedVideoUrl?: string
  coverUrl?: string
  productName?: string
  videoDuration?: number
  publishTime?: string
  coreSellingPoints?: string
  targetAudience?: string
  usageScenarios?: string
  videoStructure?: string
  analysisResult?: string
  displayAnalysisResultZh?: string
  sellingPoints?: string
  displaySellingPointsZh?: string
  status?: string
  failReason?: string
  analysisStageStatus?: string
  sellingPointStageStatus?: string
  scriptStageStatus?: string
  sellingPointCount?: number
  scriptOptionCount?: number
  scriptOptions?: TkReferenceScriptOptionVO[]
  createTime?: string
}

export const TkReferenceApi = {
  analyze: async (data: TkReferenceAnalyzeReqVO) => {
    return await request.post<TkReferenceAnalysisVO>({ url: '/tk/reference/analyze', data })
  },
  regenerateScriptOptions: async (id: number, params?: { referenceDuration?: number }) => {
    return await request.post<TkReferenceAnalysisVO>({
      url: `/tk/reference/${id}/script-options/regenerate`,
      params
    })
  },
  getLatest: async (params: {
    libraryId: number
    sourceUrl: string
    targetLanguage?: string
    materialPurpose?: string
    analysisProvider?: AnalysisProvider
  }) => {
    return await request.get<TkReferenceAnalysisVO>({ url: '/tk/reference/latest', params })
  },
  getAnalysis: async (id: number) => {
    return await request.get<TkReferenceAnalysisVO>({ url: `/tk/reference/${id}` })
  },
  getAnalysisPage: async (params: any) => {
    return await request.get({ url: '/tk/reference/page', params })
  }
}
