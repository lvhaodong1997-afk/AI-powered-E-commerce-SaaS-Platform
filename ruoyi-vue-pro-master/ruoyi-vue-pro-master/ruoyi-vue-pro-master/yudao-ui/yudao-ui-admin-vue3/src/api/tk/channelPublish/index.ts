import request from '@/config/axios'
import type { SocialVideoUploadSession } from '@/api/tk/socialPublish'

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
export interface ChannelVideoUpload {
  id: string
  fileName: string
  status: string
  metadataStatus: string
  fileSize: number
  durationSeconds?: number
}
export type ChannelVideoSource = { generationTaskId: string; mediaId?: never } | { mediaId: string; generationTaskId?: never }
interface VideoFileInfo { fileName: string; fileSize: number; contentType: string }
export type ChannelSubmission = ChannelVideoSource & {
  accountId: string
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
    capabilities: () => request.get<{ enabled: boolean; localUpload: boolean; maxUploadBytes: number }>({ url: base + '/capabilities' }),
    uploadSession: (data: VideoFileInfo) => request.post<SocialVideoUploadSession>({ url: base + '/media/video-upload-session', data }),
    uploadComplete: (data: VideoFileInfo & { uploadId: string; objectKey: string }) =>
      request.post<ChannelVideoUpload>({ url: base + '/media/video-upload-complete', data }),
    uploadedVideo: (id: string) => request.get<ChannelVideoUpload>({ url: base + '/media/get', params: { id } }),
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
