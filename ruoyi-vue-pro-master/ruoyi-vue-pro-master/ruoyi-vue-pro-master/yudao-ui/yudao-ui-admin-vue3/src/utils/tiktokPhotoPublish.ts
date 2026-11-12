export function validatePhotoSelection(files: ArrayLike<{ name: string; size: number }>, currentCount: number) {
  if (!files.length || currentCount + files.length > 35) throw new Error('每次图文发布需要 1-35 张图片')
  for (const file of Array.from(files)) {
    if (!/\.(jpe?g|webp)$/i.test(file.name)) throw new Error('图片仅支持 JPEG 和 WebP')
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

export function photoPublishSource(photos: { id: number }[], coverIndex: number) {
  if (!photos.length || photos.length > 35 || !Number.isInteger(coverIndex) || coverIndex < 0 || coverIndex >= photos.length) {
    throw new Error('请上传 1-35 张图片并选择封面')
  }
  return {
    mediaType: 'PHOTO' as const,
    uploadedPhotoIds: photos.map((p) => p.id),
    photoCoverIndex: coverIndex,
    generationTaskId: undefined,
    uploadedVideoId: undefined
  }
}

/** Browser decoding honors EXIF orientation; upload a bounded, widely supported JPEG. */
export async function prepareTikTokPhoto(file: File): Promise<File> {
  validatePhotoSelection([file], 0)
  const bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' })
  try {
    const scale = Math.min(1, 1920 / Math.max(bitmap.width, bitmap.height), 1080 / Math.min(bitmap.width, bitmap.height))
    const canvas = document.createElement('canvas')
    canvas.width = Math.max(1, Math.floor(bitmap.width * scale))
    canvas.height = Math.max(1, Math.floor(bitmap.height * scale))
    const ctx = canvas.getContext('2d')
    if (!ctx) throw new Error('浏览器无法处理图片，请使用最新版 Chrome 或 Edge')
    ctx.fillStyle = '#ffffff'
    ctx.fillRect(0, 0, canvas.width, canvas.height)
    ctx.drawImage(bitmap, 0, 0, canvas.width, canvas.height)
    const blob = await new Promise<Blob>((resolve, reject) => canvas.toBlob(
      (value) => value ? resolve(value) : reject(new Error('图片转换失败')), 'image/jpeg', 0.92))
    return new File([blob], file.name.replace(/\.[^.]+$/, '.jpg'), { type: 'image/jpeg' })
  } finally { bitmap.close() }
}
