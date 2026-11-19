type Tone = 'success' | 'info' | 'warning' | 'danger' | 'primary'

const accountTones: Record<string, Tone> = {
  AUTHORIZED: 'success', REAUTH_REQUIRED: 'warning', DISCONNECTED: 'info'
}

const taskTones: Record<string, Tone> = {
  CREATED: 'info', UPLOADING: 'primary', PRECHECKING: 'primary', AWAITING_CONFIRMATION: 'warning',
  SUBMITTING: 'primary', SENDING: 'primary', PROCESSING: 'primary', SUCCESS: 'success',
  FAILED: 'danger', RECOVERY_REQUIRED: 'warning'
}

function stateLabel(prefix: string, tones: Record<string, Tone>, value?: string) {
  return value && Object.prototype.hasOwnProperty.call(tones, value)
    ? { label: `shop.${prefix}.${value}`, type: tones[value] }
    : { label: 'shop.stateUnknown', type: 'info' as const }
}

export const shopAccountState = (value?: string) => stateLabel('accountState', accountTones, value)
export const shopTaskState = (value?: string) => stateLabel('taskState', taskTones, value)

export function formatShopDate(value: string | number | undefined, locale: string) {
  if (!value) return '-'
  const input = String(value).trim()
  const date = /^\d{10}$/.test(input) ? new Date(Number(input) * 1000)
    : /^\d{13}$/.test(input) ? new Date(Number(input)) : new Date(input)
  if (!Number.isFinite(date.getTime())) return '-'
  return date.toLocaleString(locale, { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}
