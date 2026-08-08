import request from '@/config/axios'

export interface TkGenerationRouteVO {
  id?: number
  tenantId?: number
  materialPurpose?: string
  productCategoryCode?: string
  routeCode?: string
  routeName?: string
  routeConfig?: string
  routeVersion?: number
  trafficWeight?: number
  abGroup?: string
  lastPublishTime?: string
  enabled?: boolean
  remark?: string
  createTime?: string
  updateTime?: string
}

export interface TkGenerationRouteHistoryVO {
  id?: number
  routeId?: number
  routeVersion?: number
  materialPurpose?: string
  productCategoryCode?: string
  routeCode?: string
  routeName?: string
  routeConfig?: string
  trafficWeight?: number
  abGroup?: string
  lastPublishTime?: string
  enabled?: boolean
  changeReason?: string
  createTime?: string
}

export interface TkGenerationRouteStatisticsVO {
  materialPurpose?: string
  productCategoryCode?: string
  routeCode?: string
  routeName?: string
  generationCount?: number
  successCount?: number
  failedCount?: number
  runningCount?: number
  successRate?: number
  averageDurationSeconds?: number
}

export const TkGenerationRouteApi = {
  getPage: async (params: any) => {
    return await request.get<{ list: TkGenerationRouteVO[]; total: number }>({
      url: '/tk/generation-route/page',
      params
    })
  },
  get: async (id: number) => {
    return await request.get<TkGenerationRouteVO>({
      url: '/tk/generation-route/get',
      params: { id }
    })
  },
  update: async (data: TkGenerationRouteVO) => {
    return await request.put<boolean>({
      url: '/tk/generation-route/update',
      data
    })
  },
  getHistoryPage: async (params: any) => {
    return await request.get<{ list: TkGenerationRouteHistoryVO[]; total: number }>({
      url: '/tk/generation-route/history/page',
      params
    })
  },
  getStatistics: async (params: any) => {
    return await request.get<TkGenerationRouteStatisticsVO[]>({
      url: '/tk/generation-route/statistics',
      params
    })
  }
}
