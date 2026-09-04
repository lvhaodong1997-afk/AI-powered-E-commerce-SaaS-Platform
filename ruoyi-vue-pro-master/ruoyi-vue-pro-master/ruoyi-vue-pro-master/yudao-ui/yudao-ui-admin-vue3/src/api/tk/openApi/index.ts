import request from '@/config/axios'

export interface OpenApiPageResult<T> {
  list: T[]
  total: number
}

export interface OpenApiClientVO {
  clientId: string
  clientName: string
  authCallbackUrl?: string
  publishCallbackUrl?: string
  allowedIps?: string
  permissions?: string
  rateLimitPerMinute?: number
  dailyQuota?: number
  status?: number
  remark?: string
  createTime?: string
  updateTime?: string
}

export interface OpenApiClientSaveReq {
  clientId?: string
  clientName: string
  authCallbackUrl?: string
  publishCallbackUrl?: string
  allowedIps?: string
  permissions?: string
  rateLimitPerMinute?: number
  dailyQuota?: number
  status?: number
  remark?: string
}

export interface OpenApiClientUpdateReq extends OpenApiClientSaveReq {
  clientId: string
}

export interface OpenApiClientPageReq {
  pageNo: number
  pageSize: number
  clientId?: string
  clientName?: string
  status?: number
}

export interface OpenApiCredentialResp {
  clientId?: string
  clientSecret?: string
  callbackSecret?: string
}

export interface OpenApiUsageVO {
  requestDate: string
  clientId: string
  requestCount?: number
  successCount?: number
  failureCount?: number
  averageDurationMs?: number
}

export interface OpenApiUsageReq {
  clientId?: string
  startDate?: string
  endDate?: string
}

export interface OpenApiEventVO {
  eventId: string
  clientId: string
  eventType?: string
  resourceType?: string
  resourceId?: string
  callbackUrl?: string
  payloadJson?: string
  status?: string
  attemptCount?: number
  nextRetryTime?: string
  lastHttpStatus?: number
  lastError?: string
  deliveredTime?: string
  createTime?: string
  updateTime?: string
}

export interface OpenApiEventPageReq {
  pageNo: number
  pageSize: number
  clientId?: string
  eventType?: string
  status?: string
  createTimeStart?: string
  createTimeEnd?: string
}

export const TkOpenApiApi = {
  createClient: async (data: OpenApiClientSaveReq): Promise<OpenApiCredentialResp> => {
    return await request.post({ url: '/tk/open-api/client/create', data })
  },
  updateClient: async (data: OpenApiClientUpdateReq): Promise<boolean> => {
    return await request.put({ url: '/tk/open-api/client/update', data })
  },
  updateClientStatus: async (clientId: string, status: number): Promise<boolean> => {
    return await request.put({ url: '/tk/open-api/client/status', data: { clientId, status } })
  },
  deleteClient: async (clientId: string): Promise<boolean> => {
    return await request.delete({ url: '/tk/open-api/client/delete', params: { clientId } })
  },
  rotateSecret: async (
    clientId: string,
    type: 'CLIENT' | 'CALLBACK'
  ): Promise<OpenApiCredentialResp> => {
    return await request.post({
      url: '/tk/open-api/client/rotate-secret',
      params: { clientId, type }
    })
  },
  getClient: async (clientId: string): Promise<OpenApiClientVO> => {
    return await request.get({ url: '/tk/open-api/client/get', params: { clientId } })
  },
  getClientPage: async (
    params: OpenApiClientPageReq
  ): Promise<OpenApiPageResult<OpenApiClientVO>> => {
    return await request.get({ url: '/tk/open-api/client/page', params })
  },
  getUsage: async (params: OpenApiUsageReq): Promise<OpenApiUsageVO[]> => {
    return await request.get({ url: '/tk/open-api/operations/usage', params })
  },
  getEventPage: async (params: OpenApiEventPageReq): Promise<OpenApiPageResult<OpenApiEventVO>> => {
    return await request.get({ url: '/tk/open-api/operations/event/page', params })
  },
  getEvent: async (eventId: string): Promise<OpenApiEventVO> => {
    return await request.get({ url: '/tk/open-api/operations/event/get', params: { eventId } })
  },
  replayEvent: async (eventId: string): Promise<boolean> => {
    return await request.post({ url: '/tk/open-api/operations/event/replay', params: { eventId } })
  }
}
