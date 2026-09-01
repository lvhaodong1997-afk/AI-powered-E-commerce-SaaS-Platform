import {
  TkTiktokMediaUploadApi,
  type TkTiktokMediaUploadSessionVO
} from '@/api/tk/videoPublishCenter'
import { uploadFileInChunks } from '@/utils/tkChunkUpload'
import axios from 'axios'

export const DEFAULT_TIKTOK_UPLOAD_CHUNK_SIZE = 1 * 1024 * 1024
export const UPLOAD_CHUNK_RETRY_COUNT = 2

export interface TikTokMediaUploadProgress {
  percent: number
  chunkIndex: number
  totalChunks: number
}

export interface TikTokMediaUploadOptions {
  onProgress?: (progress: TikTokMediaUploadProgress) => void
  chunkSize?: number
  retryCount?: number
}

const getUploadedVideoId = (result: any): number => {
  const uploadedVideoId = typeof result === 'number' ? result : result?.uploadedVideoId
  if (!uploadedVideoId) throw new Error('视频上传完成，但服务端未返回媒体 ID')
  return Number(uploadedVideoId)
}

export const createTikTokMediaUploadSession = TkTiktokMediaUploadApi.createMediaUploadSession
export const completeTikTokMediaUpload = TkTiktokMediaUploadApi.completeMediaUpload

const uploadTikTokMediaToOss = async (
  file: File,
  session: TkTiktokMediaUploadSessionVO,
  onProgress?: (loaded: number, total: number) => void
) => {
  if (!session.uploadUrl || !session.objectKey || !session.policy || !session.signature || !session.accessKeyId) {
    throw new Error('OSS 上传会话信息不完整')
  }
  const formData = new FormData()
  formData.append('key', session.objectKey)
  formData.append('policy', session.policy)
  formData.append('OSSAccessKeyId', session.accessKeyId)
  formData.append('signature', session.signature)
  formData.append('success_action_status', session.successActionStatus || '200')
  if (file.type) formData.append('Content-Type', file.type)
  formData.append('file', file)
  await axios.post(session.uploadUrl, formData, {
    onUploadProgress: (event) => onProgress?.(event.loaded, event.total || file.size)
  })
}

export const uploadTikTokMediaInChunks = async (
  file: File,
  options: TikTokMediaUploadOptions = {}
): Promise<{ uploadedVideoId: number; uploadId: string }> => {
  const upload = await uploadFileInChunks<TkTiktokMediaUploadSessionVO, any>(file, {
    createSession: () =>
      createTikTokMediaUploadSession({
        fileName: file.name,
        fileSize: file.size,
        contentType: file.type
      }),
    uploadChunk: async (uploadId, chunkIndex, chunk, onProgress) => {
      const formData = new FormData()
      formData.append('uploadId', uploadId)
      formData.append('chunkIndex', String(chunkIndex))
      formData.append('chunk', chunk)
      return TkTiktokMediaUploadApi.uploadMediaChunk(formData, uploadId, chunkIndex, {
        onUploadProgress: (event: ProgressEvent) => onProgress?.(event.loaded, event.total || chunk.size)
      })
    },
    complete: (uploadId) =>
      completeTikTokMediaUpload({
        uploadId,
        fileName: file.name,
        fileSize: file.size,
        contentType: file.type
      }),
    cancel: TkTiktokMediaUploadApi.cancelMediaUpload,
    uploadOss: uploadTikTokMediaToOss,
    chunkSize: options.chunkSize || DEFAULT_TIKTOK_UPLOAD_CHUNK_SIZE,
    retryCount: options.retryCount ?? UPLOAD_CHUNK_RETRY_COUNT,
    onProgress: options.onProgress
  })
  return { uploadedVideoId: getUploadedVideoId(upload.result), uploadId: upload.uploadId }
}
