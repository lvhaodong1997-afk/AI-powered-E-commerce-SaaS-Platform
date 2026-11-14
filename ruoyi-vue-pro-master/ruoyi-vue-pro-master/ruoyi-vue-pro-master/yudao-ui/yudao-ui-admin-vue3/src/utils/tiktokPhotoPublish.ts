export function validatePhotoSelection(files: ArrayLike<{ name: string; size: number }>, currentCount: number) {
  if (!files.length || currentCount + files.length > 35) throw new Error('每次图文发布需要 1-35 张图片')
  for (const file of Array.from(files)) {
    if (!/\.(jpe?g|webp|png|gif|heic|heif)$/i.test(file.name)) throw new Error('图片仅支持 JPEG、WebP、PNG、GIF、HEIC 和 HEIF')
    if (file.size <= 0 || file.size > 20 * 1024 * 1024) throw new Error('单张图片不能超过 20MB')
  }
}

export function movePhoto<T extends { id: number }>(photos: T[], coverIndex: number, from: number, to: number) {
  const coverId = photos[coverIndex]?.id
  const result = [...photos]
  if (from >= 0 && from < result.length && to >= 0 && to < result.length) {
    result.splice(to, 0, result.splice(from, 1)[0])
  }
  return { photos: result, coverIndex: Math.max(0, result.findIndex((p) => p.id === coverId)) }
}

export function removePhoto<T extends { id: number }>(photos: T[], coverIndex: number, index: number) {
  const coverId = photos[coverIndex]?.id
  const result = photos.filter((_, i) => i !== index)
  return { photos: result, coverIndex: Math.max(0, result.findIndex((p) => p.id === coverId)) }
}

export function photoPublishSource(photos: { id: number; status?: string; mediaId?: number }[], coverIndex: number) {
  if (!photos.length || photos.length > 35 || !Number.isInteger(coverIndex) || coverIndex < 0 || coverIndex >= photos.length) {
    throw new Error('请上传 1-35 张图片并选择封面')
  }
  const ids = photos.map((p) => {
    if (p.status && p.status !== 'READY') throw new Error('请等待全部图片转换成功后再发布')
    const id = p.status ? p.mediaId : p.id
    if (!Number.isSafeInteger(id) || !id || id < 1) throw new Error('图片转换结果无效，请重试')
    return id
  })
  return {
    mediaType: 'PHOTO' as const,
    uploadedPhotoIds: ids,
    photoCoverIndex: coverIndex,
    generationTaskId: undefined,
    uploadedVideoId: undefined
  }
}

export interface PhotoUploadStatus {
  uploadId: string
  status: string
  failReason?: string
  mediaId?: number
  fileUrl?: string
  width?: number
  height?: number
}

/** Poll durable server conversion; the browser never needs to decode the original. */
export async function pollPhotoUpload(operations: {
  start: () => Promise<PhotoUploadStatus>
  status: (id: string) => Promise<PhotoUploadStatus>
  cancel: (id: string) => Promise<unknown>
}, options: {
  alive: () => boolean
  onUpdate?: (status: PhotoUploadStatus) => void
  sleep?: (ms: number) => Promise<void>
  maxPolls?: number
}): Promise<PhotoUploadStatus> {
  const sleep = options.sleep || ((ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms)))
  let state: PhotoUploadStatus | undefined
  const checkAlive = async () => {
    if (options.alive()) return
    if (state?.uploadId && state.status !== 'READY') {
      try { await operations.cancel(state.uploadId) } catch { /* session may already have completed */ }
    }
    throw new Error('图片上传已取消')
  }
  await checkAlive()
  state = await operations.start()
  for (let poll = 0; ; poll++) {
    await checkAlive()
    if (!state.uploadId) throw new Error('图片上传会话无效，请重新上传')
    options.onUpdate?.(state)
    if (state.status === 'READY') {
      if (!Number.isSafeInteger(state.mediaId) || !state.mediaId || state.mediaId < 1 || !state.fileUrl) {
        throw new Error('图片转换结果无效，请重试')
      }
      return state
    }
    if (['FAILED', 'CANCELLED', 'EXPIRED'].includes(state.status)) {
      throw new Error(state.failReason || '图片转换失败，请重试或重新上传')
    }
    if (!['UPLOADING', 'PROCESSING'].includes(state.status)) throw new Error('图片上传状态异常，请重新上传')
    if (poll >= (options.maxPolls ?? 120)) throw new Error('图片转换查询超时，可点击重试继续查询')
    await sleep(Math.min(1000 + poll * 250, 5000))
    await checkAlive()
    state = await operations.status(state.uploadId)
  }
}

export function photoCreatorErrorMessage(error: any): string {
  if ([408, 504, 524].includes(error?.response?.status) || ['ECONNABORTED', 'ETIMEDOUT'].includes(error?.code)) {
    return '读取 TikTok 账号发布权限超时，请点击重试；已上传的图片无需重新上传'
  }
  return (typeof error === 'string' ? error : error?.message) || '无法读取账号发布权限，请点击重试'
}

async function decodePhoto(file: File): Promise<{
  image: CanvasImageSource; width: number; height: number; close: () => void
}> {
  if (typeof createImageBitmap === 'function') {
    try {
      const bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' })
      return { image: bitmap, width: bitmap.width, height: bitmap.height, close: () => bitmap.close() }
    } catch {
      // Some browser decoders reject images that HTMLImageElement can still read.
    }
  }
  const image = new Image()
  const url = URL.createObjectURL(file)
  try {
    await new Promise<void>((resolve, reject) => {
      image.onload = () => resolve()
      image.onerror = () => reject(new Error(`无法读取图片“${file.name}”，请用图片编辑器重新导出为 JPEG 或 WebP 后再上传`))
      image.src = url
    })
    return { image, width: image.naturalWidth, height: image.naturalHeight, close: () => URL.revokeObjectURL(url) }
  } catch (error) {
    URL.revokeObjectURL(url)
    throw error
  } finally {
    image.onload = null
    image.onerror = null
  }
}

/** Browser decoding honors EXIF orientation; upload a bounded, widely supported JPEG. */
export async function prepareTikTokPhoto(file: File): Promise<File> {
  validatePhotoSelection([file], 0)
  const decoded = await decodePhoto(file)
  try {
    const scale = Math.min(1, 1920 / Math.max(decoded.width, decoded.height), 1080 / Math.min(decoded.width, decoded.height))
    const canvas = document.createElement('canvas')
    canvas.width = Math.max(1, Math.floor(decoded.width * scale))
    canvas.height = Math.max(1, Math.floor(decoded.height * scale))
    const ctx = canvas.getContext('2d')
    if (!ctx) throw new Error('浏览器无法处理图片，请使用最新版 Chrome 或 Edge')
    ctx.fillStyle = '#ffffff'
    ctx.fillRect(0, 0, canvas.width, canvas.height)
    ctx.drawImage(decoded.image, 0, 0, canvas.width, canvas.height)
    const blob = await new Promise<Blob>((resolve, reject) => canvas.toBlob(
      (value) => value ? resolve(value) : reject(new Error('图片转换失败')), 'image/jpeg', 0.92))
    return new File([blob], file.name.replace(/\.[^.]+$/, '.jpg'), { type: 'image/jpeg' })
  } finally { decoded.close() }
}
