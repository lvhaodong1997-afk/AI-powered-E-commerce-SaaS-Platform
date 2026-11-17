import request from '@/config/axios'
import type { TkChunkUploadSession } from '@/utils/tkChunkUpload'

export type ShopProductSource = 'SHOP' | 'SHOWCASE'
export type ShopAuthType = 'CREATOR' | 'SELLER'
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
  sellerEnabled?: boolean
  sellerConfigured?: boolean
  sellerEnvironment?: string
  creatorEnvironment?: string
  sellerConfigurationMessage?: string
  sellerRequiredScopes?: string[]
}
export interface SellerAccount {
  id: number
  companyId: number
  displayName?: string
  market: string
  authStatus: string
  environment?: string
  grantedScopes: string
  accessTokenExpireTime?: string
  refreshTokenExpireTime?: string
  canReadProducts: boolean
  canReadOrders: boolean
}
export interface SellerShop {
  id: number
  accountId: number
  companyId: number
  shopId: string
  name?: string
  region?: string
  status?: string
  environment?: string
}
export interface SellerProduct { id: string; title?: string; status?: string }
export interface SellerOrder { id: string; status?: string; createTime?: string; totalAmount?: string; currency?: string }
export interface SellerProductPage { products: SellerProduct[]; total?: number; nextPageToken?: string }
export interface SellerOrderPage { orders: SellerOrder[]; total?: number; nextPageToken?: string }
export interface ShopAuthSession { sessionId: number; authorizationUrl: string; expireTime?: string; expiresInSeconds?: number }
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
  authorize: (data: { companyId?: number; authType?: ShopAuthType } = {}) => request.post<ShopAuthSession>({ url: `${base}/auth/sessions`, data }),
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
  cancelMedia: (id: string) => request.delete({ url: `${base}/media/sessions/${encodeURIComponent(id)}` }),
  sellerAccounts: (params?: { companyId?: number }) => request.get<SellerAccount[]>({ url: `${base}/seller/accounts`, params }),
  sellerShops: (params?: { companyId?: number }) => request.get<SellerShop[]>({ url: `${base}/seller/shops`, params }),
  disconnectSeller: (id: number) => request.delete<boolean>({ url: `${base}/seller/accounts/${id}` }),
  sellerProducts: (id: number, params: { companyId?: number; pageSize?: number; pageToken?: string } = {}) =>
    request.get<SellerProductPage>({ url: `${base}/seller/shops/${id}/products`, params }),
  sellerOrders: (id: number, params: { companyId?: number; pageSize?: number; pageToken?: string } = {}) =>
    request.get<SellerOrderPage>({ url: `${base}/seller/shops/${id}/orders`, params }),
}
