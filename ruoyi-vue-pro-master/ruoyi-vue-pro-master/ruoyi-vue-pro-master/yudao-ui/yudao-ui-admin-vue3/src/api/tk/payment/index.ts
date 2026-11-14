import request from '@/config/axios'
export const PAYMENT_QUOTE_REJECTED_CODE = 1_060_000_053
export interface RechargePackage {
  code: string
  amountCents: number
  points: number
}
export interface RechargeCatalog {
  version: string
  enabled: boolean
  packages: RechargePackage[]
  paymentMethods: string[]
}
export interface PaymentOrder {
  quoteRejected?: boolean
  orderNo: string
  packageCode: string
  idempotencyKey?: string
  paymentMethod?: string
  quantity: number
  catalogVersion: string
  baseAmountCents: number
  points: number
  amountCents: number
  currency: string
  fx: string | number
  status: string
  creditStatus: string
  version: number
  expiresAt: string | number
  paidAt?: string
  redirectUrl?: string
  qrImageUrl?: string
}
export const PaymentApi = {
  catalog: () => request.get<RechargeCatalog>({ url: '/tk/recharge/packages' }),
  quote: (data: {
    packageCode: string
    quantity: 1 | 5 | 10
    paymentMethod: string
    idempotencyKey: string
  }) => request.post<PaymentOrder>({ url: '/tk/payments/quote', data, skipAuthReplay: true }),
  pay: (orderNo: string, data: { amountCents: number; currency: string }) =>
    request.post<PaymentOrder>({
      url: '/tk/payments/' + encodeURIComponent(orderNo) + '/pay',
      data,
      skipAuthReplay: true
    }),
  get: (orderNo: string) =>
    request.get<PaymentOrder>({ url: '/tk/payments/' + encodeURIComponent(orderNo) }),
  page: (params: PageParam) =>
    request.get<{ list: PaymentOrder[]; total: number }>({ url: '/tk/payments', params }),
  cancel: (orderNo: string) =>
    request.post<PaymentOrder>({
      url: '/tk/payments/' + encodeURIComponent(orderNo) + '/cancel',
      skipAuthReplay: true
    })
}
