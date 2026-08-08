export interface TkGenerationOutputNameTask {
  id?: number | string
  title?: string
  creator?: number | string
  creatorName?: string
  dailyUserVideoNo?: number
  createTime?: string | number
  videoIndex?: number
  outputUrl?: string
}

const TASK_LABEL = '\u4efb\u52a1'
const VIDEO_INDEX_PREFIX = '\u7b2c'
const VIDEO_INDEX_SUFFIX = '\u6761'
const DEFAULT_DOWNLOAD_TITLE = 'TK\u89c6\u9891'
const CREATOR_LABEL = '\u751f\u6210\u7528\u6237'
const USER_ID_LABEL = '\u7528\u6237ID'

const pad2 = (value: string | number) => String(value).padStart(2, '0')
const pad3 = (value: string | number) => String(value).padStart(3, '0')

const normalizeDateParts = (value?: string | number) => {
  if (value == null || value === '') return undefined
  const normalizedValue =
    typeof value === 'number' || /^\d+$/.test(String(value))
      ? new Date(Number(value))
      : undefined
  if (normalizedValue && !Number.isNaN(normalizedValue.getTime())) {
    const year = normalizedValue.getFullYear()
    const month = normalizedValue.getMonth() + 1
    const day = normalizedValue.getDate()
    const hour = normalizedValue.getHours()
    const minute = normalizedValue.getMinutes()
    const monthDayTime = `${pad2(month)}-${pad2(day)} ${pad2(hour)}:${pad2(minute)}`
    return {
      display: monthDayTime,
      displayDate: `${year}-${pad2(month)}-${pad2(day)}`,
      download: `${year}${pad2(month)}${pad2(day)}_${pad2(hour)}${pad2(minute)}`,
      downloadDate: `${year}${pad2(month)}${pad2(day)}`
    }
  }
  const match = String(value).match(
    /^(\d{4})[-/](\d{1,2})[-/](\d{1,2})(?:[ T](\d{1,2}):(\d{1,2}))?/
  )
  if (!match) return undefined
  const [, year, month, day, hour = '00', minute = '00'] = match
  const monthDayTime = `${pad2(month)}-${pad2(day)} ${pad2(hour)}:${pad2(minute)}`
  return {
    display: monthDayTime,
    displayDate: `${year}-${pad2(month)}-${pad2(day)}`,
    download: `${year}${pad2(month)}${pad2(day)}_${pad2(hour)}${pad2(minute)}`,
    downloadDate: `${year}${pad2(month)}${pad2(day)}`
  }
}

const taskIdPart = (task: TkGenerationOutputNameTask) => `${TASK_LABEL}${task.id || 'output'}`

const videoIndexPart = (task: TkGenerationOutputNameTask) =>
  Number(task.videoIndex || 0) > 0
    ? `${VIDEO_INDEX_PREFIX}${task.videoIndex}${VIDEO_INDEX_SUFFIX}`
    : undefined

const normalizeCreatorName = (task: TkGenerationOutputNameTask) => {
  const creatorName = task.creatorName == null ? '' : String(task.creatorName).trim()
  if (creatorName) return creatorName
  const creator = task.creator == null ? '' : String(task.creator).trim()
  return creator ? `\u7528\u6237${creator}` : '\u7528\u6237'
}

const dailyUserVideoNoPart = (task: TkGenerationOutputNameTask) => {
  const no = Number(task.dailyUserVideoNo || 0)
  return no > 0 ? pad3(no) : undefined
}

const sanitizeDownloadPart = (value: string) =>
  value
    .trim()
    .replace(/[\\/:*?"<>|]+/g, '-')
    .replace(/\s+/g, '_')
    .replace(/_+/g, '_')
    .replace(/-+/g, '-')
    .replace(/^[_-]+|[_-]+$/g, '')

const extractContentDispositionFileName = (contentDisposition?: string | null) => {
  if (!contentDisposition) return undefined
  const utf8Match = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i)
  if (utf8Match?.[1]) {
    try {
      return decodeURIComponent(utf8Match[1]).trim()
    } catch {
      return utf8Match[1].trim()
    }
  }
  const quotedMatch = contentDisposition.match(/filename="([^"]+)"/i)
  if (quotedMatch?.[1]) return quotedMatch[1].trim()
  const plainMatch = contentDisposition.match(/filename=([^;]+)/i)
  return plainMatch?.[1]?.trim()
}

const outputUrlDownloadFileName = (task: TkGenerationOutputNameTask) => {
  const outputUrl = task.outputUrl == null ? '' : String(task.outputUrl).trim()
  if (!outputUrl) return undefined
  try {
    const url = new URL(outputUrl)
    const fileName = extractContentDispositionFileName(
      url.searchParams.get('response-content-disposition')
    )
    return fileName || undefined
  } catch {
    return undefined
  }
}

const businessOutputName = (task: TkGenerationOutputNameTask) => {
  const dateParts = normalizeDateParts(task.createTime)
  const dailyNo = dailyUserVideoNoPart(task)
  return dateParts?.displayDate && dailyNo
    ? [dateParts.displayDate, normalizeCreatorName(task), dailyNo].join('-')
    : undefined
}

export const buildGenerationOutputDisplayName = (task: TkGenerationOutputNameTask) => {
  const businessName = businessOutputName(task)
  if (businessName) return businessName
  const ossDownloadFileName = outputUrlDownloadFileName(task)
  if (ossDownloadFileName) return ossDownloadFileName
  const dateParts = normalizeDateParts(task.createTime)
  const parts = [dateParts?.display, taskIdPart(task), videoIndexPart(task)]
  return parts.filter(Boolean).join(' \u00b7 ')
}

export const buildGenerationOutputDownloadName = (task: TkGenerationOutputNameTask) => {
  const businessName = businessOutputName(task)
  if (businessName) return `${businessName}.mp4`
  const ossDownloadFileName = outputUrlDownloadFileName(task)
  if (ossDownloadFileName) return ossDownloadFileName
  const dateParts = normalizeDateParts(task.createTime)
  const parts = [DEFAULT_DOWNLOAD_TITLE, dateParts?.download, taskIdPart(task), videoIndexPart(task)]
    .filter(Boolean)
    .map((item) => sanitizeDownloadPart(String(item)))
  return `${parts.join('_')}.mp4`
}

export const buildGenerationCreatorLabel = (task: TkGenerationOutputNameTask) => {
  const creator = task.creator == null ? '' : String(task.creator).trim()
  const creatorName = task.creatorName == null ? '' : String(task.creatorName).trim()
  if (creatorName) {
    return `${CREATOR_LABEL}\uff1a${creatorName}`
  }
  return `${CREATOR_LABEL}\uff1a${creator ? `${USER_ID_LABEL} ${creator}` : '-'}`
}
