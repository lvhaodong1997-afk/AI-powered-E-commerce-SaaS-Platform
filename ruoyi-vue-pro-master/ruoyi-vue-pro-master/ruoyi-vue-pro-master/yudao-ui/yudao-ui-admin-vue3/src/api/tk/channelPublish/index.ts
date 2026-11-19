import request from '@/config/axios'

export type Channel = 'x' | 'youtube'
export interface ChannelAccount { id: string; name: string; username?: string; status: string }
export interface ChannelRecord {
  id: string
  title: string
  accountName: string
  status: string
  stage?: string
  url?: string
  errorCode?: string
  createTime?: number | string
}
export interface ChannelSubmission {
  accountId: string
  generationTaskId: string
  idempotencyKey: string
  text: string
  title?: string
  privacy?: string
  forKids?: boolean
  synthetic?: boolean
}

export function channelApi(channel: Channel) {
  const base = '/tk/channel-publish/' + channel
  return {
    capabilities: () => request.get<{ enabled: boolean }>({ url: base + '/capabilities' }),
    accounts: () => request.get<ChannelAccount[]>({ url: base + '/accounts' }),
    authorize: () => request.post<{ authorizeUrl: string; sessionId: string }>({ url: base + '/authorize' }),
    session: (sessionId: string) => request.get<string>({ url: base + '/session', params: { sessionId } }),
    disconnect: (id: string) => request.delete({ url: base + '/accounts/' + id }),
    submit: (data: ChannelSubmission) => request.post<string>({ url: base + '/create', data, timeout: 180000 }),
    history: (params: { pageNo: number; pageSize: number }) =>
      request.get<{ list: ChannelRecord[]; total: number }>({ url: base + '/history', params }),
    resume: (id: string) => request.post({ url: base + '/resume/' + id })
  }
}
