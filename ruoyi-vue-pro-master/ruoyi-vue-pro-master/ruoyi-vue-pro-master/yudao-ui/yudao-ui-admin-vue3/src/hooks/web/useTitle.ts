import { computed, watch } from 'vue'
import { isString } from '@/utils/is'
import { useAppStoreWithOut } from '@/store/modules/app'
import { useLocaleStoreWithOut } from '@/store/modules/locale'
import { sanitizeEnglishTitleText } from '@/utils/tkTextSanitizer'

const appStore = useAppStoreWithOut()
const localeStore = useLocaleStoreWithOut()

export const useTitle = (newTitle?: string) => {
  const { t } = useI18n()
  const title = computed(() => {
    const currentLang = localeStore.getCurrentLocale.lang
    const appTitle = sanitizeEnglishTitleText(appStore.getTitle)

    if (!newTitle) {
      return appTitle
    }
    const pageTitle = newTitle.includes('.') ? t(newTitle, currentLang) : newTitle
    const sanitizedPageTitle = sanitizeEnglishTitleText(pageTitle)
    return `${appTitle} - ${sanitizedPageTitle}`
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
