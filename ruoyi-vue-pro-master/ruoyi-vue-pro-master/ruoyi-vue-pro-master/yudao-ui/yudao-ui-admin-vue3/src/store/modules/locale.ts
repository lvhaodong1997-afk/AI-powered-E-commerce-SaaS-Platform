import { defineStore } from 'pinia'
import { store } from '../index'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import en from 'element-plus/es/locale/lang/en'
import { CACHE_KEY, useCache } from '@/hooks/web/useCache'
import { LocaleDropdownType } from '@/types/localeDropdown'

const { wsCache } = useCache()

const DEFAULT_LOCALE: LocaleType = 'en'
const FORCE_EN_LOCALE_MARK = 'TK_FORCE_EN_LOCALE_V1'

const elLocaleMap = {
  'zh-CN': zhCn,
  en: en
}

const normalizeLocale = (lang?: LocaleType): LocaleType => {
  return lang && elLocaleMap[lang] ? lang : DEFAULT_LOCALE
}

const resolveInitialLocale = (): LocaleType => {
  if (!wsCache.get(FORCE_EN_LOCALE_MARK)) {
    wsCache.set(CACHE_KEY.LANG, DEFAULT_LOCALE)
    wsCache.set(FORCE_EN_LOCALE_MARK, 'done')
    return DEFAULT_LOCALE
  }

  return normalizeLocale(wsCache.get(CACHE_KEY.LANG))
}

interface LocaleState {
  currentLocale: LocaleDropdownType
  localeMap: LocaleDropdownType[]
}

export const useLocaleStore = defineStore('locales', {
  state: (): LocaleState => {
    const initialLocale = resolveInitialLocale()

    return {
      currentLocale: {
        lang: initialLocale,
        elLocale: elLocaleMap[initialLocale]
      },
      // 多语言
      localeMap: [
        {
          lang: 'zh-CN',
          name: '简体中文'
        },
        {
          lang: 'en',
          name: 'English'
        }
      ]
    }
  },
  getters: {
    getCurrentLocale(): LocaleDropdownType {
      return this.currentLocale
    },
    getLocaleMap(): LocaleDropdownType[] {
      return this.localeMap
    }
  },
  actions: {
    setCurrentLocale(localeMap: LocaleDropdownType) {
      // this.locale = Object.assign(this.locale, localeMap)
      const lang = normalizeLocale(localeMap?.lang)
      this.currentLocale.lang = lang
      this.currentLocale.elLocale = elLocaleMap[lang]
      wsCache.set(CACHE_KEY.LANG, lang)
    }
  }
})

export const useLocaleStoreWithOut = () => {
  return useLocaleStore(store)
}
