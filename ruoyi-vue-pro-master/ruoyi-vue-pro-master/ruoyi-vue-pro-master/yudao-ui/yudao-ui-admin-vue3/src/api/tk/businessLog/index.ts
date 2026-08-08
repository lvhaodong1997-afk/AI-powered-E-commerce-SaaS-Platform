import request from '@/config/axios'

export interface TkBusinessLogVO {
  id: number
  tenantId?: number
  businessTraceId?: string
  bizType?: string
  bizId?: number
  level?: string
  action?: string
  status?: string
  message?: string
  detailJson?: string
  operatorId?: number
  createTime?: string
}

export const TkBusinessLogApi = {
  getPage: async (params: any) => {
    return await request.get({ url: '/tk/business-log/page', params })
  }
}
