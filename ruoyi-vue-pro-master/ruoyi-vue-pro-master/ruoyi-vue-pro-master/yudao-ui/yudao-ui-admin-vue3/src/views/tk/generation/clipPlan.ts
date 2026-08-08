import { tkText } from '@/utils/tkI18n'

export type ClipTagType = 'success' | 'warning' | 'danger' | 'info'

export interface RawClipPlanItem {
  orderNo?: number
  sourceType?: string
  materialVideoId?: number
  fileName?: string
  startSecond?: number
  durationSecond?: number
  reason?: string
  section?: string
  sectionName?: string
  matchScore?: number
}

export interface ClipSectionSummaryItem {
  section: string
  label: string
  duration: number
  type: ClipTagType
}

export interface ClipPlanDetailItem {
  orderNo: number
  sourceType: string
  sourceTypeLabel: string
  materialVideoId?: number
  materialVideoIdText: string
  fileName: string
  section: string
  sectionLabel: string
  usageMode: string
  durationSecond: number
  reason: string
}

export const clipSectionMetas: Record<string, { label: string; type: ClipTagType }> = {
  S1_HOOK: { label: 'S1 黄金3秒', type: 'warning' },
  S2_PAIN: { label: 'S2 痛点场景', type: 'danger' },
  S3_REVEAL: { label: 'S3 产品亮相', type: 'success' },
  S4_DEMO: { label: 'S4 使用演示', type: 'success' },
  S5_PROOF: { label: 'S5 效果证明', type: 'danger' },
  S6_DETAIL: { label: 'S6 细节特写', type: 'info' },
  S7_LIFESTYLE: { label: 'S7 场景融入', type: 'warning' },
  ATTENTION: { label: '吸引注意', type: 'warning' },
  PRODUCT_SHOW: { label: '产品展示', type: 'success' },
  RESULT_EFFECT: { label: '使用效果', type: 'danger' },
  GENERAL: { label: '通用素材', type: 'info' }
}

export const parseClipPlan = (value?: string): RawClipPlanItem[] => {
  if (!value) return []
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed : []
  } catch (e) {
    return []
  }
}

const normalizeSection = (section?: string) => section || 'GENERAL'

export const sectionLabel = (section?: string, sectionName?: string) => {
  const normalized = normalizeSection(section)
  return sectionName || clipSectionMetas[normalized]?.label || normalized
}

export const formatUsageMode = (startSecond?: number, durationSecond?: number) => {
  const start = Number(startSecond || 0)
  const duration = Number(durationSecond || 0)
  return start > 0
    ? tkText(`完整使用（起点 ${start}s）`, `Use full clip (start at ${start}s)`)
    : tkText(`完整使用 ${duration}s`, `Use full clip ${duration}s`)
}

const sourceTypeLabel = (sourceType?: string) => {
  if (sourceType === 'OPENING') return tkText('开头视频', 'Opening Video')
  if (sourceType === 'MATERIAL') return tkText('素材库', 'Material Library')
  return sourceType || '-'
}

export const buildClipSectionSummary = (clipPlan?: string): ClipSectionSummaryItem[] => {
  const summary = new Map<string, ClipSectionSummaryItem>()
  parseClipPlan(clipPlan).forEach((item) => {
    const section = normalizeSection(item.section)
    const meta = clipSectionMetas[section] || clipSectionMetas.GENERAL
    const current = summary.get(section) || {
      section,
      label: meta.label,
      duration: 0,
      type: meta.type
    }
    current.duration += Number(item.durationSecond || 0)
    summary.set(section, current)
  })
  return Array.from(summary.values())
}

export const buildClipPlanDetails = (clipPlan?: string): ClipPlanDetailItem[] =>
  parseClipPlan(clipPlan).map((item, index) => {
    const section = normalizeSection(item.section)
    return {
      orderNo: Number(item.orderNo || index + 1),
      sourceType: item.sourceType || '',
      sourceTypeLabel: sourceTypeLabel(item.sourceType),
      materialVideoId: item.materialVideoId,
      materialVideoIdText: item.materialVideoId == null ? '-' : String(item.materialVideoId),
      fileName: item.fileName || '-',
      section,
      sectionLabel: sectionLabel(section, item.sectionName),
      usageMode: formatUsageMode(item.startSecond, item.durationSecond),
      durationSecond: Number(item.durationSecond || 0),
      reason: item.reason || '-'
    }
  })
