import request from '@/config/axios'

export interface TkVoiceProfileVO {
  id: number
  name: string
  voiceCode?: string
  sourceType?: string
  mimoVoiceMode?: string
  mimoVoicePrompt?: string
  mimoSampleUrl?: string
  tags?: string
  sort?: number
  remark?: string
  provider: string
  model?: string
  sampleFileUrl?: string
  previewFileUrl?: string
  status: 'CLONING' | 'READY' | 'FAILED' | 'DISABLED'
  enabled: boolean
  language?: string
  errorMessage?: string
  createTime?: string
}

export const TkVoiceProfileApi = {
  getList: async (): Promise<TkVoiceProfileVO[]> => {
    return await request.get({ url: '/tk/voice-profile/list' })
  },
  create: async (name: string, consentConfirmed: boolean, file: File): Promise<number> => {
    const data = new FormData()
    data.append('file', file)
    return await request.upload({
      url: '/tk/voice-profile/create',
      data,
      params: { name, consentConfirmed },
      timeout: 300_000
    })
  },
  createMimoDesign: async (name: string, prompt: string, tags?: string): Promise<number> => {
    return await request.post({
      url: '/tk/voice-profile/mimo-design',
      data: { name, prompt, tags },
      timeout: 180_000
    })
  },
  createMimoClone: async (
    name: string,
    consentConfirmed: boolean,
    sampleUrl: string,
    tags?: string
  ): Promise<number> => {
    return await request.post({
      url: '/tk/voice-profile/mimo-clone',
      data: { name, consentConfirmed, sampleUrl, tags },
      timeout: 180_000
    })
  },
  retry: async (id: number) => {
    return await request.post({ url: '/tk/voice-profile/retry', params: { id }, timeout: 180_000 })
  },
  updateEnabled: async (id: number, enabled: boolean) => {
    return await request.put({ url: '/tk/voice-profile/enabled', params: { id, enabled } })
  },
  batchUpdateEnabled: async (ids: number[], enabled: boolean) => {
    return await request.put({ url: '/tk/voice-profile/enabled-batch', data: { ids, enabled } })
  },
  updateTags: async (id: number, tags?: string) => {
    return await request.put({ url: '/tk/voice-profile/tags', params: { id, tags } })
  },
  delete: async (id: number) => {
    return await request.delete({ url: '/tk/voice-profile/delete', params: { id } })
  },
  batchDelete: async (ids: number[]) => {
    return await request.delete({ url: '/tk/voice-profile/delete-batch', data: { ids } })
  }
}
