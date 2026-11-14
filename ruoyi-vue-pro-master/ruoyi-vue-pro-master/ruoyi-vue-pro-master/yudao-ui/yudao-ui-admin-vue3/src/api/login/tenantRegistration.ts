import request from '@/config/axios'
import type { TokenType } from './types'
export interface TenantRegistrationConfig {
  enabled: boolean
  smsAvailable: boolean
  resendAfterSeconds: number
  codeExpireSeconds: number
}
export interface TenantRegistrationBody {
  requestId: string
  tenantName: string
  username: string
  nickname: string
  password: string
  mobile: string
  smsRequestId: string
  smsCode: string
  inviteCode?: string
  captchaVerification: string
}
export interface SmsChallenge {
  smsRequestId: string
  deliveryStatus: 'PENDING' | 'SENT' | 'UNKNOWN'
  expiresInSeconds: number
  resendAfterSeconds: number
}
export const getTenantRegistrationConfig = () =>
  request.get<TenantRegistrationConfig>({
    url: '/system/auth/tenant-register/config',
    headers: { isToken: false }
  })
export const sendTenantRegistrationSms = (data: {
  mobile: string
  requestId: string
  captchaVerification: string
}) =>
  request.post<SmsChallenge>({
    url: '/system/auth/tenant-register/sms-code',
    data,
    headers: { isToken: false },
    skipAuthReplay: true
  })
export const registerTenant = (data: TenantRegistrationBody) =>
  request.post<TokenType & { tenantId: number; tenantName: string }>({
    url: '/system/auth/tenant-register',
    data,
    headers: { isToken: false },
    skipAuthReplay: true
  })
