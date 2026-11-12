interface PublishDiagnostic {
  status?: string
  phase?: string
  tiktokStatus?: string
  transferPolicy?: string
  uploadSource?: string
  callbackStatus?: string
  callbackHttpStatus?: number
}

export const publishTransferLabel = (row: PublishDiagnostic): string => {
  if (row.uploadSource === 'PULL_FROM_URL') return '平台直接拉取'
  if (row.uploadSource === 'FILE_UPLOAD') {
    return row.transferPolicy?.startsWith('OSS_PULL') ? '服务器上传（已回退）' : '服务器上传'
  }
  return row.transferPolicy?.startsWith('OSS_PULL') ? '等待直拉校验' : '等待素材准备'
}

export const publishQueueStageLabel = (row: PublishDiagnostic): string => {
  if (row.status === 'SUCCESS') return '发布成功'
  if (row.status === 'FAILED') return '发布失败'
  if (row.status === 'CANCELLED') return '已取消'
  if (row.status === 'SCHEDULED') return '等待定时发布'
  if (row.tiktokStatus === 'PULL_PREFLIGHT_RETRY') return '链接校验等待重试'
  if (row.tiktokStatus === 'RECOVERY_REQUIRED') return '等待后台核验'
  if (row.tiktokStatus === 'PROCESSING_DOWNLOAD') return '平台下载中'
  const phases: Record<string, string> = {
    MATERIAL_PREPARATION: '校验发布素材', READY_TO_INIT: '准备提交平台',
    INIT_SENT: '确认平台受理结果', UPLOADING: '视频上传中',
    UPLOAD_URL_EXPIRED: '上传链接过期，等待核验', PUBLISH_ID_SAVED: '准备上传视频',
    PLATFORM_PROCESSING: '平台处理中', RECOVERY_REQUIRED: '等待后台核验'
  }
  return phases[row.phase || ''] || (row.status === 'PENDING' ? '等待执行' : '平台处理中')
}

export const publishCallbackLabel = (row: PublishDiagnostic): string => {
  const labels: Record<string, string> = {
    DELIVERED: '通知已送达', RETRYING: '通知重试中', PENDING: '通知待发送',
    DELIVERING: '通知发送中', SKIPPED: '通知已跳过', FAILED: '通知投递失败'
  }
  const label = labels[row.callbackStatus || ''] || '—'
  return row.callbackStatus === 'RETRYING' && row.callbackHttpStatus
    ? `${label}（HTTP ${row.callbackHttpStatus}）` : label
}
