import request from '@/config/axios'

export interface AudioAudiencePresetVO {
  id?: number
  version?: number
  companyId?: number
  audienceCode: string
  configStatus?: 'READY' | 'RESET' | 'INCOMPLETE'
  ttsProvider?: string
  voiceId?: number
  voiceCode?: string
  voiceProfileId?: number
  mimoVoiceMode?: string
  mimoVoiceCode?: string
  mimoVoicePrompt?: string
  mimoVoiceSampleUrl?: string
  bgmEnabled: boolean
  bgmAssetId?: number
  bgmVolume?: number
}
export const AudioAudiencePresetApi = {
  getList: async (): Promise<AudioAudiencePresetVO[]> =>
    request.get({ url: '/tk/audio-audience-preset/list' }),
  get: async (id: number): Promise<AudioAudiencePresetVO> =>
    request.get({ url: '/tk/audio-audience-preset/get', params: { id } }),
  save: async (data: AudioAudiencePresetVO): Promise<number> =>
    request.post({ url: '/tk/audio-audience-preset/save', data }),
  reset: async (id: number, version: number) =>
    request.delete({ url: '/tk/audio-audience-preset/reset', params: { id, version } })
}
