import request from '@/config/axios'

export interface TkBgmAssetVO {
  id: number
  tenantId?: number
  companyId?: number
  name: string
  sourceType: 'SYSTEM' | 'USER'
  style?: string
  fileUrl: string
  duration?: number
  format?: string
  status?: number
  createTime?: string
}

export const TkBgmAssetApi = {
  getList: async (): Promise<TkBgmAssetVO[]> => {
    return await request.get({ url: '/tk/bgm/list' })
  },
  getSystemList: async (): Promise<TkBgmAssetVO[]> => {
    return await request.get({ url: '/tk/bgm/system-list' })
  },
  upload: async (name: string, style: string | undefined, file: File): Promise<number> => {
    const data = new FormData()
    data.append('file', file)
    return await request.upload({
      url: '/tk/bgm/upload',
      data,
      params: { name, style },
      timeout: 180_000
    })
  },
  delete: async (id: number) => {
    return await request.delete({ url: '/tk/bgm/delete', params: { id } })
  }
}
