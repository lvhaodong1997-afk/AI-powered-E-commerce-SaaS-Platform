import { useLocaleStore } from '@/store/modules/locale'
import { translateTkKey, translateTkTextByLocale } from '@/locales/tk'

export const useTkI18n = () => {
  const localeStore = useLocaleStore()
  const locale = computed(() => localeStore.getCurrentLocale.lang)
  const isEn = computed(() => locale.value === 'en')

  const tt = (key: string) => translateTkKey(key, locale.value)
  const tText = (text?: string) => translateTkTextByLocale(text, locale.value)

  return {
    isEn,
    locale,
    tt,
    tText
  }
}
