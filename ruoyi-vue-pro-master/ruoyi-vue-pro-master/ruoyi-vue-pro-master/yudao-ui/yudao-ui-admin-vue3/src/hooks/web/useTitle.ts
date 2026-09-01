import { computed, watch } from 'vue'
import { isString } from '@/utils/is'
import { useAppStoreWithOut } from '@/store/modules/app'
import { useLocaleStoreWithOut } from '@/store/modules/locale'
import { sanitizeEnglishTitleText } from '@/utils/tkTextSanitizer'

const appStore = useAppStoreWithOut()
const localeStore = useLocaleStoreWithOut()
const DEFAULT_APP_TITLE = 'ClipForge Studio'
const normalizeTitleSegment = (value?: string) => sanitizeEnglishTitleText(value).trim()

export const useTitle = (newTitle?: string) => {
  const { t } = useI18n()
  const title = computed(() => {
    const currentLang = localeStore.getCurrentLocale.lang
    const appTitle = normalizeTitleSegment(appStore.getTitle) || DEFAULT_APP_TITLE
    const routeTitle = newTitle?.trim()
    let sanitizedPageTitle = ''

    if (routeTitle) {
      const pageTitle = routeTitle.includes('.') ? t(routeTitle, currentLang) : routeTitle
      sanitizedPageTitle = normalizeTitleSegment(pageTitle)
    }
    return [appTitle, sanitizedPageTitle].filter(Boolean).join(' - ')
  })

  watch(
    title,
    (n, o) => {
      if (isString(n) && n !== o && document) {
        document.title = n
      }
    },
    { immediate: true }
  )

  return title
}
