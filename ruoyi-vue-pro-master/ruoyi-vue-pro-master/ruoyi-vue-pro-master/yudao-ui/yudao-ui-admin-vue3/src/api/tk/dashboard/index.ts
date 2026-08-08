import request from '@/config/axios'

export interface DashboardQuery {
  startTime?: string
  endTime?: string
  libraryId?: number
  targetLanguage?: string
  status?: string
  generationMode?: string
}

export interface DashboardOverview {
  generationTaskCount: number
  successVideoCount: number
  failedVideoCount: number
  runningTaskCount: number
  successRate: number
  averageDurationSeconds: number
  consumedCredits: number
  materialLibraryCount: number
  materialVideoCount: number
  availableMaterialVideoCount: number
  parsingMaterialVideoCount: number
  failedMaterialVideoCount: number
  authorizedAccountCount: number
  abnormalAccountCount: number
}

export interface DashboardTrendItem {
  day: string
  totalCount: number
  successCount: number
  failedCount: number
  runningCount: number
  consumedCredits: number
  averageDurationSeconds: number
}

export interface DashboardFailureReason {
  code: string
  label: string
  count: number
}

export interface DashboardFailureStep {
  step: string
  count: number
}

export interface DashboardFailureTask {
  id: number
  businessTraceId?: string
  libraryId?: number
  title?: string
  status?: string
  failCode?: string
  failReason?: string
  currentStep?: string
  retryCount?: number
  createTime?: string
}

export interface DashboardFailureAnalysis {
  reasons: DashboardFailureReason[]
  steps: DashboardFailureStep[]
  recentFailures: DashboardFailureTask[]
}

export interface DashboardLibraryHealth {
  libraryId: number
  libraryName: string
  libraryStatus?: number
  videoCount: number
  availableVideoCount: number
  parsingVideoCount: number
  failedVideoCount: number
  generationCount: number
  lastUsedTime?: string
  healthStatus: string
}

export interface DashboardMaterialHealth {
  libraryCount: number
  materialVideoCount: number
  availableVideoCount: number
  parsingVideoCount: number
  failedVideoCount: number
  libraries: DashboardLibraryHealth[]
}

export interface DashboardQueueHealth {
  pendingCount: number
  runningCount: number
  staleRunningCount: number
  averagePendingSeconds: number
  averageRunningSeconds: number
  attentionTasks: DashboardFailureTask[]
}

export interface DashboardFailureDiagnosisItem {
  category: string
  label: string
  count: number
  actionStatus: string
  actionHint: string
}

export interface DashboardFailureDiagnosis {
  items: DashboardFailureDiagnosisItem[]
}

export interface DashboardSlowTaskItem {
  taskId: number
  title?: string
  status?: string
  currentStep?: string
  failCode?: string
  failReason?: string
  durationSeconds: number
  durationType?: string
  createTime?: string
  heartbeatTime?: string
}

export interface DashboardSlowTasks {
  items: DashboardSlowTaskItem[]
}

export const TkDashboardApi = {
  getSummary: async () => {
    return await request.get({ url: '/tk/dashboard/summary' })
  },
  getOverview: async (params: DashboardQuery) => {
    return await request.get<DashboardOverview>({ url: '/tk/dashboard/overview', params })
  },
  getGenerationTrend: async (params: DashboardQuery) => {
    return await request.get<{ items: DashboardTrendItem[] }>({
      url: '/tk/dashboard/generation-trend',
      params
    })
  },
  getFailureAnalysis: async (params: DashboardQuery) => {
    return await request.get<DashboardFailureAnalysis>({
      url: '/tk/dashboard/failure-analysis',
      params
    })
  },
  getMaterialHealth: async (params: DashboardQuery) => {
    return await request.get<DashboardMaterialHealth>({
      url: '/tk/dashboard/material-health',
      params
    })
  },
  getQueueHealth: async (params: DashboardQuery) => {
    return await request.get<DashboardQueueHealth>({
      url: '/tk/dashboard/queue-health',
      params
    })
  },
  getFailureDiagnosis: async (params: DashboardQuery) => {
    return await request.get<DashboardFailureDiagnosis>({
      url: '/tk/dashboard/failure-diagnosis',
      params
    })
  },
  getSlowTasks: async (params: DashboardQuery) => {
    return await request.get<DashboardSlowTasks>({
      url: '/tk/dashboard/slow-tasks',
      params
    })
  }
}
