import request from '@/config/axios'
import type { TkChunkUploadSession } from '@/utils/tkChunkUpload'

export type ShopProductSource = 'SHOP' | 'SHOWCASE'
export interface ShopCapabilities {
  enabled: boolean
  configured: boolean
  publishingConfigured: boolean
  market: string
  requiredScope: string
  supportsProducts: boolean
  supportsPrecheck: boolean
  maxVideoBytes: number
  supportedVideoFormats: string[]
  callbackOrigin: string
}
export interface ShopAccount {
  id: number
  companyId: number
  displayName?: string
  username?: string
  market: string
  authStatus: string
  grantedScopes: string
  accessTokenExpireTime?: string
  refreshTokenExpireTime?: string
}
export interface ShopProduct {
  id: string
  title: string
  imageUrl?: string
  price?: string
  maximumPrice?: string
  currency?: string
  availability?: string
  available: boolean
  selectionProof?: string
}
export interface ShopCreate {
  accountId: number
  generationTaskId?: number
  mediaId?: number
  title: string
  productId: string
  productSource: ShopProductSource
  selectionProof: string
  productLinkTitle: string
  idempotencyKey: string
  isAiGenerated: boolean
}
export interface ShopTask {
  id: number
  accountId: number
  generationTaskId?: number
  mediaId?: number
  fileSize: number
  confirmedBy?: number
  title: string
  productId: string
  productSource: ShopProductSource
  productLinkTitle: string
  stage: string
  requestHash: string
  precheckResult?: string
  videoId?: string
  requestId?: string
  failCode?: string
  failReason?: string
  version: number
  confirmedVersion?: number
  isAiGenerated: boolean
  confirmedAt?: string
  createTime?: string
  updateTime?: string
}
export interface ShopMedia { id: number; fileSize: number; fileName: string; mimeType: string; status: string }
const base = '/tk/tiktok-shop'
export const TiktokShopApi = {
  capabilities: () => request.get<ShopCapabilities>({ url: `${base}/capabilities` }),
  accounts: () => request.get<ShopAccount[]>({ url: `${base}/accounts` }),
  authorize: () => request.post<{ sessionId: number; authorizationUrl: string; expireTime: string }>({ url: `${base}/auth/sessions`, data: {} }),
  disconnect: (id: number) => request.delete<boolean>({ url: `${base}/accounts/${id}` }),
  products: (id: number, params: { source: ShopProductSource; pageSize: number; pageToken?: string; titleKeyword?: string }) =>
    request.get<{ products: ShopProduct[]; nextPageToken?: string }>({ url: `${base}/accounts/${id}/products`, params }),
  create: (data: ShopCreate) => request.post<ShopTask>({ url: `${base}/publish/tasks`, data, timeout: 120_000, skipAuthReplay: true }),
  tasks: (params: { pageNo: number; pageSize: number; accountId?: number }) => request.get<{ list: ShopTask[]; total: number }>({ url: `${base}/publish/tasks`, params }),
  get: (id: number) => request.get<ShopTask>({ url: `${base}/publish/tasks/${id}` }),
  confirm: (id: number, data: { confirm: true; version: number; requestHash: string }) => request.post<ShopTask>({ url: `${base}/publish/tasks/${id}/confirm`, data, skipAuthReplay: true }),
  createMediaSession: (data: { fileName: string; fileSize: number; contentType: string }) => request.post<TkChunkUploadSession>({ url: `${base}/media/sessions`, data }),
  uploadChunk: (id: string, chunkIndex: number, chunk: Blob, signal: AbortSignal, onProgress?: (loaded: number, total: number) => void) => {
    const data = new FormData()
    data.append('file', chunk)
    return request.post({ url: `${base}/media/sessions/${encodeURIComponent(id)}/chunks`, params: { chunkIndex }, data, signal, timeout: 180_000,
      onUploadProgress: (event: { loaded: number; total?: number }) => onProgress?.(event.loaded, event.total || chunk.size) })
  },
  completeMedia: (id: string, signal: AbortSignal) => request.post<ShopMedia>({ url: `${base}/media/sessions/${encodeURIComponent(id)}/complete`, signal, timeout: 120_000 }),
  cancelMedia: (id: string) => request.delete({ url: `${base}/media/sessions/${encodeURIComponent(id)}` })
}
