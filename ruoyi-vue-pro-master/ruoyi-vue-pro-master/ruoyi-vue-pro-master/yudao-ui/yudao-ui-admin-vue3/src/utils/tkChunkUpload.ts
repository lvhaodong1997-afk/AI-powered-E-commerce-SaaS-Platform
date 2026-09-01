export interface TkChunkUploadSession {
  uploadId: string
  uploadMode?: 'local' | 'oss'
  chunkSize?: number
  totalChunks?: number
  uploadedSize?: number
  uploadedChunks?: number[]
  uploadUrl?: string
  publicUrl?: string
  objectKey?: string
  accessKeyId?: string
  policy?: string
  signature?: string
  successActionStatus?: string
}

export interface TkChunkUploadProgress {
  percent: number
  chunkIndex: number
  totalChunks: number
}

export interface TkChunkUploadOptions<TSession extends TkChunkUploadSession, TResult> {
  createSession: () => Promise<TSession>
  uploadChunk: (
    uploadId: string,
    chunkIndex: number,
    chunk: Blob,
    onProgress?: (loaded: number, total: number) => void
  ) => Promise<unknown>
  complete: (uploadId: string) => Promise<TResult>
  cancel?: (uploadId: string) => Promise<unknown>
  uploadOss?: (
    file: File,
    session: TSession,
    onProgress?: (loaded: number, total: number) => void
  ) => Promise<void>
  chunkSize?: number
  retryCount?: number
  onProgress?: (progress: TkChunkUploadProgress) => void
  onRetry?: (attempt: number, error: unknown) => void
}

const DEFAULT_CHUNK_SIZE = 1 * 1024 * 1024
const DEFAULT_RETRY_COUNT = 2

const sleep = (ms: number) =>
  new Promise<void>((resolve) => {
    const timer = globalThis.setTimeout
    timer(resolve, ms)
  })

/** Prefer the server's business message so upload failures are actionable. */
export const getTkUploadErrorMessage = (error: any): string => {
  if (typeof error === 'string' && error.trim()) return error.trim()
  const candidates = [
    error?.msg,
    error?.message,
    error?.response?.data?.msg,
    error?.response?.data?.message,
    error?.response?.data?.data?.msg,
    error?.response?.data?.data?.message
  ]
  const message = candidates.find((value) => typeof value === 'string' && value.trim())
  return message ? message.trim() : '上传失败'
}

const uploadedBytesForChunks = (file: File, chunks: Set<number>, chunkSize: number) => {
  let bytes = 0
  chunks.forEach((index) => {
    const start = index * chunkSize
    if (start < file.size) bytes += Math.min(chunkSize, file.size - start)
  })
  return bytes
}

const uploadWithRetry = async <T>(
  operation: () => Promise<T>,
  retryCount: number,
  onRetry?: (attempt: number, error: unknown) => void
): Promise<T> => {
  let lastError: unknown
  for (let attempt = 0; attempt <= retryCount; attempt++) {
    try {
      return await operation()
    } catch (error) {
      lastError = error
      if (attempt >= retryCount) break
      onRetry?.(attempt + 1, error)
      await sleep(800 * (attempt + 1))
    }
  }
  throw lastError
}

export const uploadFileInChunks = async <TSession extends TkChunkUploadSession, TResult>(
  file: File,
  options: TkChunkUploadOptions<TSession, TResult>
): Promise<{ result: TResult; uploadId: string; session: TSession }> => {
  const retryCount = options.retryCount ?? DEFAULT_RETRY_COUNT
  const fallbackChunkSize = options.chunkSize || DEFAULT_CHUNK_SIZE
  let session: TSession | undefined

  try {
    session = await options.createSession()
    const uploadId = session.uploadId
    const actualChunkSize = session.chunkSize || fallbackChunkSize
    const totalChunks = session.totalChunks || Math.ceil(file.size / actualChunkSize)
    const uploadedChunks = new Set(session.uploadedChunks || [])
    let completedBytes = session.uploadedSize ?? uploadedBytesForChunks(file, uploadedChunks, actualChunkSize)
    options.onProgress?.({
      percent: file.size ? Math.min(98, Math.round((completedBytes * 100) / file.size)) : 0,
      chunkIndex: -1,
      totalChunks
    })

    if (session.uploadMode === 'oss' && options.uploadOss) {
      await uploadWithRetry(
        () => options.uploadOss!(file, session!, (loaded, total) => {
          if (!total) return
          options.onProgress?.({
            percent: Math.min(98, Math.round((loaded * 100) / total)),
            chunkIndex: -1,
            totalChunks
          })
        }),
        retryCount,
        options.onRetry
      )
    } else {
      for (let chunkIndex = 0; chunkIndex < totalChunks; chunkIndex++) {
        const start = chunkIndex * actualChunkSize
        const end = Math.min(start + actualChunkSize, file.size)
        if (uploadedChunks.has(chunkIndex)) continue
        const chunk = file.slice(start, end)
        await uploadWithRetry(
          () =>
            options.uploadChunk(uploadId, chunkIndex, chunk, (loaded, total) => {
              if (!total) return
              options.onProgress?.({
                percent: Math.min(98, Math.round(((completedBytes + Math.min(chunk.size, loaded)) * 100) / file.size)),
                chunkIndex,
                totalChunks
              })
            }),
          retryCount,
          options.onRetry
        )
        completedBytes += chunk.size
        options.onProgress?.({
          percent: Math.min(98, Math.round((completedBytes * 100) / file.size)),
          chunkIndex,
          totalChunks
        })
      }
    }

    const result = await options.complete(uploadId)
    options.onProgress?.({ percent: 100, chunkIndex: totalChunks - 1, totalChunks })
    return { result, uploadId, session }
  } catch (error) {
    if (session?.uploadId && options.cancel) {
      await options.cancel(session.uploadId).catch(() => undefined)
    }
    throw error
  }
}
