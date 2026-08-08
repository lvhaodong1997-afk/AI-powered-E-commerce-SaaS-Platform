import request from '@/config/axios'

export interface TkCreditBalanceVO {
  tenantId?: number
  totalCredits?: number
  remainingCredits?: number
  frozenCredits?: number
  warningThreshold?: number
  lowBalance?: boolean
}

export interface TkTenantCreditSaveReqVO {
  tenantId: number
  totalCredits: number
  warningThreshold?: number
}

export interface TkTenantCreditRechargeReqVO {
  tenantId: number
  credits: number
  remark?: string
}

export const TkCreditApi = {
  getBalance: async () => {
    return await request.get<TkCreditBalanceVO>({ url: '/tk/credit/balance' })
  },
  getTenantBalance: async (tenantId: number) => {
    return await request.get<TkCreditBalanceVO>({
      url: '/tk/credit/tenant-balance',
      params: { tenantId }
    })
  },
  saveTenantCredit: async (data: TkTenantCreditSaveReqVO) => {
    return await request.put({ url: '/tk/credit/tenant-credit', data })
  },
  rechargeTenantCredit: async (data: TkTenantCreditRechargeReqVO) => {
    return await request.post({ url: '/tk/credit/tenant-credit/recharge', data })
  }
}
