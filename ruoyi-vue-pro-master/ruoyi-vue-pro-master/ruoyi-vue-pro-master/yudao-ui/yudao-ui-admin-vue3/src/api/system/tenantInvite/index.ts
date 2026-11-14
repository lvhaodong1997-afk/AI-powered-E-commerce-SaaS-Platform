import request from '@/config/axios'
export interface MyTenantInvite {
  inviteCode: string
  inviteUrl: string
  invitedTenantCount: number
}
export interface TenantInviteRecord {
  tenantName: string
  registerTime: string
}
export const getMyTenantInvite = () =>
  request.get<MyTenantInvite>({ url: '/system/tenant-invite/my' })
export const getTenantInviteRecords = (params: PageParam) =>
  request.get<{ list: TenantInviteRecord[]; total: number }>({
    url: '/system/tenant-invite/records',
    params
  })
