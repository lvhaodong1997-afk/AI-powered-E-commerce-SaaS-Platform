import tkEn from './en'
import tkZhCN from './zh-CN'

export type TkLocale = 'zh-CN' | 'en'

export const tkMessages: Record<TkLocale, Record<string, string>> = {
  'zh-CN': tkZhCN,
  en: tkEn
}

export const tkTextKeyMap: Record<string, string> = Object.entries(tkZhCN).reduce(
  (map, [key, value]) => {
    map[value] = key
    return map
  },
  {} as Record<string, string>
)

export const translateTkKey = (key: string, locale: string = 'zh-CN') => {
  const normalizedLocale: TkLocale = locale === 'en' ? 'en' : 'zh-CN'
  return tkMessages[normalizedLocale][key] || key
}

export const translateTkTextByLocale = (text?: string, locale: string = 'zh-CN') => {
  if (!text || locale !== 'en') {
    return text || ''
  }
  const key = tkTextKeyMap[text]
  return key ? translateTkKey(key, locale) : text
}

export const tkEnglishTextMap: Record<string, string> = Object.entries(tkTextKeyMap).reduce(
  (map, [zhText, key]) => {
    map[zhText] = tkEn[key] || zhText
    return map
  },
  {} as Record<string, string>
)
