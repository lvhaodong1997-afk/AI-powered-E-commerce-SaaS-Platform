import request from '@/config/axios'
import type { TkChunkUploadSession } from '@/utils/tkChunkUpload'

export type ShopProductSource = 'SHOP' | 'SHOWCASE'
export type ShopAuthType = 'CREATOR' | 'SELLER'
export type ShopCreatorView = 'marketplace' | 'samples' | 'orders'
export type ShopSellerView = 'PRODUCTS' | 'ORDERS' | 'PACKAGES' | 'RETURNS' | 'STATEMENTS' | 'PROMOTIONS'
export type ShopAnalyticsView = 'VIDEOS' | 'LIVES'
export interface ShopSellerPage { rows: Record<string, string>[]; nextPageToken?: string; latestAvailableDate?: string }
export interface ShopSellerParams { shopId: number; cursor?: string }
export interface ShopPhotoCreate {
  accountId: number; title: string; productId: string; productSource: ShopProductSource; selectionProof: string;
  linkTitle: string; idempotencyKey: string; confirm: true; isAiGenerated: boolean
}
export interface ShopPhoto {
  id: number; title: string; productId: string; stage: string; photoId?: string; postStatus?: string; failCode?: string; requestId?: string
}
export interface ShopCreatorRow {
  id: string
  title?: string
  status?: string
  productId?: string
  price?: string
  currency?: string
  createdAt?: number
}
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
export interface SellerWriteCapabilities { sandbox: boolean; granted: boolean; canCreate: boolean }
export interface SellerChoice { id: string; name: string }
export interface SellerCategory {
  supported: boolean
  attributes: { id: string; name: string; required: boolean; custom: boolean; options: SellerChoice[] }[]
}
export interface SellerProductWriteResult {
  productId: string
  result?: 'VERIFIED' | 'SUBMITTED' | 'UNVERIFIED'
  requestId?: string
  status?: string
  auditStatus?: string
  skus?: { id: string; sellerSku: string; price: string; currency: string }[]
}
export interface SellerCreateProduct {
  title: string; description: string; categoryId: string; sellerSku: string; warehouseId: string
  price: string; weight: string; length: string; width: string; height: string; quantity: number
  draft: boolean; images: string[]; attributes: Record<string, string>; idempotencyKey: string
}
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
  refreshRetryAfter?: number
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
export interface ShopPublishSelection { accountId: number; product: ShopProduct }
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
const sellerWrite = (id: number) => `${base}/seller/shops/${id}/product-writing`
export const TiktokShopApi = {
  capabilities: () => request.get<ShopCapabilities>({ url: `${base}/capabilities` }),
  accounts: () => request.get<ShopAccount[]>({ url: `${base}/accounts` }),
  authorize: (data: { companyId?: number; authType?: ShopAuthType } = {}) => request.post<ShopAuthSession>({ url: `${base}/auth/sessions`, data }),
  disconnect: (id: number) => request.delete<boolean>({ url: `${base}/accounts/${id}` }),
  sellerOperations: (params: ShopSellerParams & { view: ShopSellerView }) =>
    request.get<ShopSellerPage>({ url: `${base}/seller/operations`, params }),
  sellerAnalytics: (params: ShopSellerParams & { view: ShopAnalyticsView; start: string; end: string; liveAccountType?: string }) =>
    request.get<ShopSellerPage>({ url: `${base}/seller/analytics`, params }),
  publishPhotos: (value: ShopPhotoCreate, files: File[]) => {
    const data = new FormData()
    data.append('request', new Blob([JSON.stringify(value)], { type: 'application/json' }))
    files.forEach((file) => data.append('files', file))
    return request.post<ShopPhoto>({ url: `${base}/photos`, data, timeout: 300_000, skipAuthReplay: true })
  },
  photoHistory: (accountId: number, pageNo: number) =>
    request.get<{ list: ShopPhoto[]; total: number }>({ url: `${base}/photos`, params: { accountId, pageNo } }),
  photoStatus: (accountId: number, id: number) => request.get<ShopPhoto>({ url: `${base}/photos/${id}/status`, params: { accountId } }),
  creatorPage: (id: number, view: ShopCreatorView, params: { pageToken?: string; keyword?: string }) =>
    request.get<{ rows: ShopCreatorRow[]; nextPageToken?: string }>({ url: `${base}/accounts/${id}/creator/${view}`, params }),
  addToShowcase: (id: number, productId: string) => request.post<boolean>({
    url: `${base}/accounts/${id}/creator/showcase/${encodeURIComponent(productId)}`, skipAuthReplay: true
  }),
  removeFromShowcase: (id: number, productId: string) => request.delete<boolean>({
    url: `${base}/accounts/${id}/creator/showcase/${encodeURIComponent(productId)}`, skipAuthReplay: true
  }),
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
  sellerWriteCapabilities: (id: number) => request.get<SellerWriteCapabilities>({ url: `${sellerWrite(id)}/capabilities` }),
  sellerCategories: (id: number, keyword: string) =>
    request.get<SellerChoice[]>({ url: `${sellerWrite(id)}/categories`, params: { keyword } }),
  sellerCategory: (id: number, categoryId: string) =>
    request.get<SellerCategory>({ url: `${sellerWrite(id)}/categories/${encodeURIComponent(categoryId)}`, timeout: 65_000 }),
  sellerWarehouses: (id: number) => request.get<SellerChoice[]>({ url: `${sellerWrite(id)}/warehouses` }),
  sellerUploadImage: (id: number, file: File) => {
    const data = new FormData()
    data.append('file', file)
    return request.post<{ uri: string }>({ url: `${sellerWrite(id)}/images`, data, timeout: 45_000, skipAuthReplay: true })
  },
  sellerCreateProduct: (id: number, data: SellerCreateProduct) => request.post<SellerProductWriteResult>({
    url: `${sellerWrite(id)}/products`, data, timeout: 150_000, skipAuthReplay: true
  }),
  sellerProductDetail: (id: number, productId: string) => request.get<SellerProductWriteResult>({
    url: `${sellerWrite(id)}/products/${encodeURIComponent(productId)}`, timeout: 40_000
  }),
  sellerUpdatePrice: (id: number, productId: string, data: { skuId: string; currency: string; expected: string; price: string }) =>
    request.post<SellerProductWriteResult>({ url: `${sellerWrite(id)}/products/${encodeURIComponent(productId)}/price`, data, timeout: 110_000, skipAuthReplay: true }),
}
