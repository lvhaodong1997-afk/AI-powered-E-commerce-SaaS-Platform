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
  canDelete?: boolean
  referencedPresetCount?: number
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
  referenceCount: async (id: number): Promise<number> =>
    request.get({ url: '/tk/bgm/reference-count', params: { id } }),
  references: async (
    id: number
  ): Promise<Array<{ id: number; audienceCode: string; version: number }>> =>
    request.get({ url: '/tk/bgm/references', params: { id } }),
  delete: async (id: number, unbindPresets = false) => {
    return await request.delete({ url: '/tk/bgm/delete', params: { id, unbindPresets } })
  }
}
