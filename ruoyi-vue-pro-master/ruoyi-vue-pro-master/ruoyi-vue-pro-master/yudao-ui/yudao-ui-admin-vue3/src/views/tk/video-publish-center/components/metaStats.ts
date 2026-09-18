import type { SocialMetric, SocialStats, SocialTime } from '@/api/tk/socialPublish'
import { metaText } from '@/locales/tk/metaPublishMessages'

export const statsKey = (kind: 'account' | 'detail', id: number) => `${kind}:${id}`
export const timeMillis = (value?: SocialTime | null) => value == null ? 0 : typeof value === 'number' ? value : new Date(value).getTime() || 0
export const formatMetricValue = (metric?: Pick<SocialMetric, 'value'>, locale = 'zh-CN') =>
  metric?.value == null || !Number.isFinite(metric.value) ? '—' : metric.value.toLocaleString(locale.startsWith('en') ? 'en-US' : 'zh-CN')
export const formatSocialDate = (value?: SocialTime | null, locale = 'zh-CN') => {
  const timestamp = timeMillis(value)
  if (!timestamp) return '—'
  return new Intl.DateTimeFormat(locale.startsWith('en') ? 'en-US' : 'zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit'
  }).format(new Date(timestamp))
}
export function metricLabel(metric: Pick<SocialMetric, 'key' | 'sourceMetric'>, locale = 'zh-CN') {
  const key = metric.key === 'likes'
    ? /reaction/i.test(metric.sourceMetric || '') ? 'meta.reactions' : 'meta.likes'
    : metric.key === 'views' && /play/i.test(metric.sourceMetric || '') ? 'meta.playCount'
      : ({ followers: 'meta.followers', following: 'meta.following', mediaCount: 'meta.mediaCount', views: 'meta.views', reach: 'meta.reach',
        comments: 'meta.comments', shares: 'meta.shares', saves: 'meta.saves' } as Record<string, string>)[metric.key]
  return key ? metaText(key, locale) : metric.key
}
export const availabilityLabel = (value: string, locale = 'zh-CN') => {
  const key = ({ AVAILABLE: 'meta.availableState', PENDING: 'meta.pendingState', UNKNOWN: 'meta.unknownState', UNSUPPORTED: 'meta.unsupportedState',
    PERMISSION_REQUIRED: 'meta.permissionRequired', AUTH_REQUIRED: 'meta.authRequired', OBJECT_UNAVAILABLE: 'meta.objectUnavailable', ERROR: 'meta.readFailed' } as Record<string, string>)[value]
  return key ? metaText(key, locale) : value
}
export const statsStatusLabel = (value?: string, locale = 'zh-CN') => {
  const key = ({ SUCCESS: 'meta.syncSuccess', PROCESSING: 'meta.syncing', PENDING: 'meta.waitingSync', FAILED: 'meta.syncFailed',
    PARTIAL_SUCCESS: 'meta.partialSync', NEVER: 'meta.neverSynced', COOLDOWN: 'meta.cooldown', DISABLED: 'meta.disabled', INACTIVE: 'meta.inactive' } as Record<string, string>)[value || '']
  return key ? metaText(key, locale) : value || metaText('meta.neverSynced', locale)
}
export const scopeLabel = (value?: string, locale = 'zh-CN') => ({ account: metaText('meta.scopeAccount', locale), detail: metaText('meta.scopeDetail', locale), media: metaText('meta.scopeDetail', locale) }[value || ''] || value || '—')
export const periodLabel = (value?: string, locale = 'zh-CN') => value === 'day' ? metaText('meta.periodDay', locale) : value || '—'
export const unitLabel = (value?: string, locale = 'zh-CN') => value === 'count' ? metaText('meta.unitCount', locale) : value || '—'

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
