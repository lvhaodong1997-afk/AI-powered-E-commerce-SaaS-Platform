import axios from 'axios'
import { TiktokShopApi, type ShopMedia } from '@/api/tk/tiktokShop'
import { uploadFileInChunks, type TkChunkUploadSession } from '@/utils/tkChunkUpload'

/** Owned-media transfer only. Cancellation prevents late completions from selecting media. */
export function createShopUpload(file: File, onProgress?: (percent: number) => void) {
  const abort = new AbortController()
  let sessionId = '', deleted = false
  const check = () => { if (abort.signal.aborted) throw new Error('SHOP_UPLOAD_CANCELLED') }
  const remove = async () => {
    if (sessionId && !deleted) { deleted = true; await TiktokShopApi.cancelMedia(sessionId).catch(() => undefined) }
  }
  const result = uploadFileInChunks<TkChunkUploadSession, ShopMedia>(file, {
    createSession: async () => {
      const session = await TiktokShopApi.createMediaSession({ fileName: file.name, fileSize: file.size, contentType: file.type })
      sessionId = session.uploadId
      if (abort.signal.aborted) { await remove(); check() }
      return session
    },
    uploadChunk: (id, index, chunk, progress) => {
      check()
      return TiktokShopApi.uploadChunk(id, index, chunk, abort.signal, progress)
    },
    uploadOss: async (ownedFile, session, progress) => {
      check()
      if (!session.uploadUrl || !session.objectKey || !session.policy || !session.signature || !session.accessKeyId) throw new Error('SHOP_UPLOAD_SESSION_INVALID')
      const data = new FormData()
      data.append('key', session.objectKey)
      data.append('policy', session.policy)
      data.append('OSSAccessKeyId', session.accessKeyId)
      data.append('signature', session.signature)
      data.append('success_action_status', session.successActionStatus || '200')
      if (file.type) data.append('Content-Type', file.type)
      data.append('file', ownedFile)
      await axios.post(session.uploadUrl, data, { signal: abort.signal, timeout: 30 * 60_000,
        onUploadProgress: (event) => progress?.(event.loaded, event.total || file.size) })
      check()
    },
    complete: async (id) => {
      check()
      const media = await TiktokShopApi.completeMedia(id, abort.signal)
      check()
      if (!media.id || media.status !== 'READY') throw new Error('SHOP_UPLOAD_NOT_READY')
      return media
    },
    cancel: remove,
    onProgress: (progress) => { if (!abort.signal.aborted) onProgress?.(progress.percent) }
  }).then((upload) => { check(); return upload.result })
  return { result, cancel() { abort.abort(); void remove() } }
}
