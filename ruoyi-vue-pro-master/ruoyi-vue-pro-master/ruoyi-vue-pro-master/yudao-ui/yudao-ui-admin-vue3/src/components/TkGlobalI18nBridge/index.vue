<script setup lang="ts">
import { useLocaleStore } from '@/store/modules/locale'
import { translateUiTextWithWhitespace } from '@/utils/tkI18n'
import {
  sanitizeEnglishAttributeText,
  sanitizeEnglishVisibleText
} from '@/utils/tkTextSanitizer'

defineOptions({ name: 'TkGlobalI18nBridge' })

const localeStore = useLocaleStore()
const currentLocale = computed(() => localeStore.getCurrentLocale.lang)
const translatedPlaceholderAttr = 'data-tk-i18n-placeholder'
const translatedValueAttr = 'data-tk-i18n-value'
const translatableAttrs = ['placeholder', 'title', 'aria-label'] as const
const translatedTextNodes = new WeakMap<Text, string>()
let observer: MutationObserver | undefined
let pending = false
let settleTimers: number[] = []

const translateTextNode = (node: Text) => {
  const original = translatedTextNodes.get(node) || node.data

  if (currentLocale.value !== 'en') {
    if (translatedTextNodes.has(node) && node.data !== original) {
      node.data = original
    }
    translatedTextNodes.delete(node)
    return
  }

  const translated = sanitizeEnglishVisibleText(translateUiTextWithWhitespace(original))
  if (translated !== original) {
    translatedTextNodes.set(node, original)
    if (node.data !== translated) {
      node.data = translated
    }
  }
}

const restoreAttribute = (element: Element, attr: string, originalAttr: string) => {
  if (!element.hasAttribute(originalAttr)) {
    return
  }
  const original = element.getAttribute(originalAttr) || ''
  if (element.getAttribute(attr) !== original) {
    element.setAttribute(attr, original)
  }
  element.removeAttribute(originalAttr)
}

const translateAttribute = (element: Element, attr: string, originalAttr: string) => {
  const original = element.getAttribute(originalAttr) || element.getAttribute(attr) || ''
  const translated = sanitizeEnglishAttributeText(translateUiTextWithWhitespace(original))
  if (translated !== original) {
    element.setAttribute(originalAttr, original)
    if (element.getAttribute(attr) !== translated) {
      element.setAttribute(attr, translated)
    }
  }
}

const isReadonlyDisplayInput = (element: Element): element is HTMLInputElement => {
  if (!(element instanceof HTMLInputElement)) {
    return false
  }
  return element.readOnly || Boolean(element.closest('.el-select, .el-cascader, .el-date-editor'))
}

const translateReadonlyValue = (element: Element) => {
  if (!isReadonlyDisplayInput(element)) {
    return
  }
  const original = element.getAttribute(translatedValueAttr) || element.value || ''
  const translated = sanitizeEnglishAttributeText(translateUiTextWithWhitespace(original))
  if (translated !== original) {
    element.setAttribute(translatedValueAttr, original)
    if (element.value !== translated) {
      element.value = translated
    }
  }
}

const restoreReadonlyValue = (element: Element) => {
  if (!isReadonlyDisplayInput(element) || !element.hasAttribute(translatedValueAttr)) {
    return
  }
  const original = element.getAttribute(translatedValueAttr) || ''
  if (element.value !== original) {
    element.value = original
  }
  element.removeAttribute(translatedValueAttr)
}

const translateElement = (element: Element) => {
  if (currentLocale.value !== 'en') {
    restoreAttribute(element, 'placeholder', translatedPlaceholderAttr)
    translatableAttrs
      .filter((attr) => attr !== 'placeholder')
      .forEach((attr) => restoreAttribute(element, attr, `data-tk-i18n-${attr}`))
    restoreReadonlyValue(element)
    return
  }

  translateAttribute(element, 'placeholder', translatedPlaceholderAttr)
  translatableAttrs
    .filter((attr) => attr !== 'placeholder')
    .forEach((attr) => translateAttribute(element, attr, `data-tk-i18n-${attr}`))
  translateReadonlyValue(element)
}

const walk = (root: ParentNode) => {
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT | NodeFilter.SHOW_ELEMENT, {
    acceptNode(node) {
      const parent = node.nodeType === Node.TEXT_NODE ? node.parentElement : (node as Element)
      if (!parent || ['SCRIPT', 'STYLE', 'TEXTAREA'].includes(parent.tagName)) {
        return NodeFilter.FILTER_REJECT
      }
      if (parent.closest('.tox, .w-e-text-container, [contenteditable="true"]')) {
        return NodeFilter.FILTER_REJECT
      }
      return NodeFilter.FILTER_ACCEPT
    }
  })
  while (walker.nextNode()) {
    const node = walker.currentNode
    if (node.nodeType === Node.TEXT_NODE) {
      translateTextNode(node as Text)
    } else {
      translateElement(node as Element)
    }
  }
}

const scheduleTranslate = () => {
  if (pending) {
    return
  }
  pending = true
  requestAnimationFrame(() => {
    pending = false
    walk(document.body)
  })
}

const clearSettlingWalks = () => {
  settleTimers.forEach((timer) => window.clearTimeout(timer))
  settleTimers = []
}

const scheduleSettlingWalks = () => {
  clearSettlingWalks()
  ;[120, 500, 1200, 3000].forEach((delay) => {
    settleTimers.push(
      window.setTimeout(() => {
        walk(document.body)
      }, delay)
    )
  })
}

onMounted(() => {
  walk(document.body)
  scheduleSettlingWalks()
  observer = new MutationObserver(scheduleTranslate)
  observer.observe(document.body, {
    childList: true,
    subtree: true,
    characterData: true,
    attributes: true,
    attributeFilter: ['placeholder', 'title', 'aria-label', 'value']
  })
})

onBeforeUnmount(() => {
  observer?.disconnect()
  clearSettlingWalks()
})

watch(currentLocale, () => {
  scheduleTranslate()
  scheduleSettlingWalks()
})
</script>

<template>
  <span class="hidden" aria-hidden="true"></span>
</template>
