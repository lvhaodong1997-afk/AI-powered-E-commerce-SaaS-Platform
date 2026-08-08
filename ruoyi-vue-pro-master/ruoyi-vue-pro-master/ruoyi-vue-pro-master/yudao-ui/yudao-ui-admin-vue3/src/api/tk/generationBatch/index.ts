import request from '@/config/axios'
import type { TkGenerationTaskSummaryVO } from '@/api/tk/generation'

export interface TkGenerationBatchVO {
  id: number
  tenantId?: number
  batchNo?: string
  name?: string
  companyId?: number
  libraryId?: number
  sourceUrl?: string
  targetLanguage?: string
  scriptCount?: number
  videosPerScript?: number
  expectedVideoCount?: number
  createdTaskCount?: number
  successTaskCount?: number
  failedTaskCount?: number
  runningTaskCount?: number
  progressPercent?: number
  status?: string
  failSummary?: string
  createTime?: string
  updateTime?: string
}

export interface TkGenerationStepLogVO {
  id: number
  taskId: number
  batchId?: number
  stepCode?: string
  stepName?: string
  status?: string
  startTime?: string
  endTime?: string
  durationMillis?: number
  failCode?: string
  failReason?: string
  retryCount?: number
  workerId?: string
}

export interface TkGenerationBatchDetailVO {
  batch: TkGenerationBatchVO
  tasks: TkGenerationTaskSummaryVO[]
  stepLogs: TkGenerationStepLogVO[]
}

export interface TkGenerationBatchPageReqVO {
  pageNo: number
  pageSize: number
  keyword?: string
  status?: string
  libraryId?: number
}

export const TkGenerationBatchApi = {
  getBatchPage: async (params: TkGenerationBatchPageReqVO) => {
    return await request.get({ url: '/tk/generation-batch/page', params })
  },
  getBatchDetail: async (id: number): Promise<TkGenerationBatchDetailVO> => {
    return await request.get({ url: '/tk/generation-batch/get', params: { id } })
  },
  retryFailedTasks: async (id: number): Promise<number> => {
    return await request.post({ url: '/tk/generation-batch/retry-failed', params: { id } })
  }
}
