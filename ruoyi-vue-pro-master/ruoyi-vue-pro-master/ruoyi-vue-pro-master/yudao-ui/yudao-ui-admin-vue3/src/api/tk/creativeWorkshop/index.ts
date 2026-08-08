import request from '@/config/axios'

export interface TkCreativeGenerateReqVO {
  prompt: string
  mode: string
  model: string
  ratio: string
  duration: string
  count: number
  style: string
}

export interface TkCreativeGenerateRespVO {
  taskId?: number | string
  status?: string
  previewUrl?: string
}

export const TkCreativeWorkshopApi = {
  generate: async (data: TkCreativeGenerateReqVO): Promise<TkCreativeGenerateRespVO> => {
    // TODO: 后续接入真实 AI 创意工坊接口时替换为实际地址和返回结构。
    return await request.post({ url: '/tk/creative-workshop/generate-placeholder', data })
  }
}
