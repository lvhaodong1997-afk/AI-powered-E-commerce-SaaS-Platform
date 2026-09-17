import type { SocialMetric, SocialStats, SocialTime } from '@/api/tk/socialPublish'

export const statsKey = (kind: 'account' | 'detail', id: number) => `${kind}:${id}`
export const timeMillis = (value?: SocialTime | null) => value == null ? 0 : typeof value === 'number' ? value : new Date(value).getTime() || 0
export const formatMetricValue = (metric?: Pick<SocialMetric, 'value'>) =>
  metric?.value == null || !Number.isFinite(metric.value) ? '—' : metric.value.toLocaleString('zh-CN')
export function metricLabel(metric: Pick<SocialMetric, 'key' | 'sourceMetric'>) {
  if (metric.key === 'likes') return /reaction/i.test(metric.sourceMetric || '') ? '反应数（Reactions）' : '点赞'
  if (metric.key === 'views' && /play/i.test(metric.sourceMetric || '')) return '播放次数'
  return ({ followers: '粉丝', following: '关注', mediaCount: '作品数', views: '观看', reach: '触达',
    comments: '评论', shares: '分享', saves: '收藏' } as Record<string, string>)[metric.key] || metric.key
}
export const availabilityLabel = (value: string) => ({
  AVAILABLE: '可用', PENDING: '待同步', UNKNOWN: '暂未获取', UNSUPPORTED: '平台不支持',
  PERMISSION_REQUIRED: '需要权限', AUTH_REQUIRED: '需要重新授权', OBJECT_UNAVAILABLE: '平台作品或对象不可用', ERROR: '读取失败'
}[value] || value)
export const statsStatusLabel = (value?: string) => ({
  SUCCESS: '同步成功', PROCESSING: '同步中', PENDING: '等待同步', FAILED: '同步失败',
  PARTIAL_SUCCESS: '部分成功', NEVER: '尚未同步', COOLDOWN: '冷却中', DISABLED: '已禁用', INACTIVE: '未启用'
}[value || ''] || value || '尚未同步')

// Preserve provenance and the original fetch time whenever displaying an older value.
export function mergeStats(previous: SocialStats | undefined, next: SocialStats): SocialStats {
  const failed = ['FAILED', 'ERROR'].includes(next.syncStatus)
  const metrics = next.metrics.map(metric => {
    const old = previous?.metrics.find(item => item.key === metric.key)
    if (metric.value == null && old?.value != null && (failed || metric.availability !== 'AVAILABLE')) {
      return { ...old, availability: metric.availability, errorCode: metric.errorCode,
        errorMessage: metric.errorMessage, stale: true }
    }
    return { ...metric, stale: metric.value != null && (failed || metric.availability !== 'AVAILABLE') }
  })
  if (failed) for (const old of previous?.metrics || []) {
    if (!metrics.some(metric => metric.key === old.key)) metrics.push({ ...old, availability: 'ERROR', stale: true })
  }
  return { ...next, lastSuccessTime: next.lastSuccessTime ?? previous?.lastSuccessTime, metrics }
}
