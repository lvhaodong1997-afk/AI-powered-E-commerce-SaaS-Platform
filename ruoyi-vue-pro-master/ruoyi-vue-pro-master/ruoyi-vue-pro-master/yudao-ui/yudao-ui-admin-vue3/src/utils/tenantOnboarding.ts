export interface SmsAttempt {
  mobile: string
  requestId: string
  smsRequestId: string
  code: string
  resendAt: number
  expiresAt: number
  deliveryStatus: string
}
export const newRequestId = () => crypto.randomUUID()
export function changePhone(
  previous: SmsAttempt | undefined,
  mobile: string,
  makeId = newRequestId
): SmsAttempt {
  if (previous?.mobile === mobile) return previous
  return {
    mobile,
    requestId: makeId(),
    smsRequestId: '',
    code: '',
    resendAt: 0,
    expiresAt: 0,
    deliveryStatus: ''
  }
}
export function applySmsChallenge(
  previous: SmsAttempt,
  mobile: string,
  response: {
    smsRequestId: string
    deliveryStatus: string
    resendAfterSeconds?: number
    expiresInSeconds?: number
  },
  now = Date.now()
): SmsAttempt {
  if (previous.mobile !== mobile) return previous
  return {
    ...previous,
    smsRequestId: response.smsRequestId,
    deliveryStatus: response.deliveryStatus,
    resendAt: now + Math.max(0, response.resendAfterSeconds ?? 60) * 1000,
    expiresAt: now + Math.max(0, response.expiresInSeconds ?? 300) * 1000
  }
}
export function prepareSmsSend(previous: SmsAttempt, now = Date.now(), makeId = newRequestId) {
  return ['SENT', 'UNKNOWN'].includes(previous.deliveryStatus) && now >= previous.resendAt
    ? changePhone(undefined, previous.mobile, makeId)
    : previous
}
export function registrationAttempt<T extends Record<string, unknown>>(
  previous: { requestId: string; payload: T } | undefined,
  payload: T,
  makeId = newRequestId
) {
  const stable = Object.fromEntries(
    Object.entries(payload).filter(([key]) => key !== 'captchaVerification')
  ) as T
  return previous && JSON.stringify(previous.payload) === JSON.stringify(stable)
    ? previous
    : { requestId: makeId(), payload: stable }
}
export interface QuoteDraft {
  idempotencyKey: string
  packageCode: string
  paymentMethod: string
  quantity?: 1 | 5 | 10
}
export function quoteAttempt(
  previous: QuoteDraft | undefined,
  selection: Omit<QuoteDraft, 'idempotencyKey'>,
  makeId = newRequestId
): QuoteDraft {
  return previous ?? { ...selection, idempotencyKey: makeId() }
}
export function parsePaymentDraft(raw: string | null): QuoteDraft | undefined {
  try {
    const value = JSON.parse(raw || 'null')
    if (
      !value ||
      typeof value.idempotencyKey !== 'string' ||
      !/^[a-f0-9-]{36}$/.test(value.idempotencyKey) ||
      !/^(P[0-9]{1,8}|VERIFY_1)$/.test(value.packageCode) ||
      !['alipay', 'wechat_pay'].includes(value.paymentMethod) ||
      (value.quantity !== undefined &&
        !(value.packageCode === 'VERIFY_1' ? [1, 5, 10] : [1]).includes(value.quantity))
    )
      return
    return {
      idempotencyKey: value.idempotencyKey,
      packageCode: value.packageCode,
      paymentMethod: value.paymentMethod,
      ...(value.quantity === undefined ? {} : { quantity: value.quantity })
    }
  } catch {
    return undefined
  }
}
export function safeProviderUrl(value: unknown, kind: 'redirect' | 'qr'): string {
  if (typeof value !== 'string' || value.length > 4096) return ''
  try {
    const url = new URL(value)
    const hosts =
      kind === 'qr'
        ? ['qr.stripe.com', 'q.stripe.com']
        : ['checkout.stripe.com', 'payments.stripe.com', 'hooks.stripe.com', 'pay.stripe.com']
    if (
      url.protocol !== 'https:' ||
      url.username ||
      url.password ||
      (url.port && url.port !== '443') ||
      !hosts.includes(url.hostname) ||
      [...url.searchParams.keys()].some((key) => /secret/i.test(key))
    )
      return ''
    return url.href
  } catch {
    return ''
  }
}
export interface PaymentState {
  orderNo: string
  status: string
  creditStatus: string
  version?: number
}
export function mergePaymentOrder<T extends PaymentState>(current: T | undefined, incoming: T): T {
  if (!current) return incoming
  if (
    current.orderNo !== incoming.orderNo ||
    (incoming.version ?? 0) < (current.version ?? 0) ||
    (current.status === 'refunded' && incoming.status !== 'refunded')
  )
    return current
  return incoming
}
export const isPaidAndCredited = (order: Pick<PaymentState, 'status' | 'creditStatus'>) =>
  order.status === 'paid' && order.creditStatus === 'CREDITED'
export const shouldPollOrder = (order: Pick<PaymentState, 'status' | 'creditStatus'>) =>
  ['quoted', 'payment_pending', 'refund_pending'].includes(order.status) ||
  (order.status === 'paid' && order.creditStatus === 'PENDING')
export function money(amount: number, currency: string, locale = 'zh-CN') {
  return new Intl.NumberFormat(locale, { style: 'currency', currency }).format(amount / 100)
}
export function expiryMillis(value: string | number) {
  return typeof value === 'number' ? (value < 1e12 ? value * 1000 : value) : Date.parse(value)
}

// Keep the established backend alias for existing tenant administrators.
export const isTenantAdministrator = (level: unknown) =>
  level === 'TENANT_ADMIN' || level === 'COMPANY_ADMIN'
