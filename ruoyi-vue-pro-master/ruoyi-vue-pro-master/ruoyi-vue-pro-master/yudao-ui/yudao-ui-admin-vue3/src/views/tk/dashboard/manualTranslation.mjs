export const targetCountryOptions = Object.freeze([
  { country: 'CN', value: 'zh-cn' },
  { country: 'US', value: 'en-us' },
  { country: 'GB', value: 'en-gb' },
  { country: 'JP', value: 'ja-jp' },
  { country: 'KR', value: 'ko-kr' },
  { country: 'DE', value: 'de-de' },
  { country: 'FR', value: 'fr-fr' },
  { country: 'ES', value: 'es-es' },
  { country: 'IT', value: 'it-it' },
  { country: 'BR', value: 'pt-br' },
  { country: 'MX', value: 'es-mx' },
  { country: 'NL', value: 'nl-nl' },
  { country: 'TR', value: 'tr-tr' },
  { country: 'PT', value: 'pt-pt' },
  { country: 'VN', value: 'vi-vn' },
  { country: 'TH', value: 'th-th' }
].map((item) => Object.freeze(item)))

const legacyLanguages = { en: 'en-us', de: 'de-de', es: 'es-es', fr: 'fr-fr', nl: 'nl-nl' }
export const resolveTargetCountry = (language) => {
  const code = typeof language === 'string' ? language.trim().toLowerCase() : ''
  return targetCountryOptions.find((item) => item.value === (legacyLanguages[code] || code))
}

const MAX_SCRIPT_LENGTH = 3000
const normalizeText = (text) => (typeof text === 'string' ? text.trim() : '')

export const createManualTranslationState = () => ({
  enabled: false,
  flow: '',
  sourceText: '',
  targetLanguage: '',
  translatedText: '',
  status: 'idle',
  error: '',
  version: 0
})

const invalidateTranslation = (state) => {
  state.version += 1
  state.translatedText = ''
  state.status = 'idle'
  state.error = ''
}

export const updateManualTranslationContext = (state, context) => {
  const sourceText = normalizeText(context.sourceText)
  const flow = context.flow || ''
  const targetLanguage = context.targetLanguage || ''
  if (state.flow === flow && state.sourceText === sourceText && state.targetLanguage === targetLanguage) {
    return
  }
  if (state.flow !== flow || !sourceText) state.enabled = false
  invalidateTranslation(state)
  Object.assign(state, { flow, sourceText, targetLanguage })
}

export const setManualTranslationEnabled = (state, enabled) => {
  state.enabled = Boolean(enabled && state.flow && state.sourceText)
  invalidateTranslation(state)
}

export const beginManualTranslation = (state) => {
  if (!state.enabled || !resolveTargetCountry(state.targetLanguage) || !state.sourceText || state.sourceText.length > MAX_SCRIPT_LENGTH) {
    return undefined
  }
  invalidateTranslation(state)
  state.status = 'loading'
  return Object.freeze({
    version: state.version,
    flow: state.flow,
    sourceText: state.sourceText,
    targetLanguage: state.targetLanguage
  })
}

const isCurrentRequest = (state, request) => Boolean(
  request && state.enabled && state.status === 'loading' &&
  request.version === state.version && request.flow === state.flow &&
  request.sourceText === state.sourceText && request.targetLanguage === state.targetLanguage
)

export const failManualTranslation = (state, request, error = 'REQUEST_FAILED') => {
  if (!isCurrentRequest(state, request)) return false
  state.status = 'error'
  state.error = error
  state.translatedText = ''
  return true
}

export const completeManualTranslation = (state, request, response) => {
  if (!isCurrentRequest(state, request)) return false
  const translatedText = normalizeText(response?.translatedText)
  if (!translatedText || translatedText.length > MAX_SCRIPT_LENGTH ||
      normalizeText(response?.sourceText) !== request.sourceText ||
      response?.targetLanguage !== request.targetLanguage) {
    failManualTranslation(state, request, 'INVALID_RESULT')
    return false
  }
  state.translatedText = translatedText
  state.status = 'ready'
  state.error = ''
  return true
}

export const selectManualCopy = (state) => {
  const validSource = state.sourceText.length <= MAX_SCRIPT_LENGTH
  const ready = validSource && Boolean(resolveTargetCountry(state.targetLanguage)) && (!state.enabled || (
    state.status === 'ready' && Boolean(state.translatedText) &&
    state.translatedText.length <= MAX_SCRIPT_LENGTH
  ))
  return {
    ready,
    promptText: ready ? (state.enabled ? state.translatedText : state.sourceText) : '',
    manualOriginalText: state.sourceText,
    manualTranslationEnabled: state.enabled
  }
}

export const captureManualCopy = (state) => {
  const copy = selectManualCopy(state)
  if (!copy.ready) throw new Error('MANUAL_COPY_NOT_READY')
  return Object.freeze({ ...copy, targetLanguage: state.targetLanguage })
}

export const readManualTranslationReplay = (task, defaultLanguage) => {
  let metadata
  try {
    metadata = JSON.parse(task.generationRouteConfig || '{}') || {}
  } catch {
    metadata = {}
  }
  const enabled = metadata?.manualTranslationEnabled === true
  const finalText = normalizeText(task.promptText || task.scriptText)
  const savedLanguage = metadata.manualTranslationTargetLanguage || task.targetLanguage || defaultLanguage
  const country = resolveTargetCountry(savedLanguage)
  const consistentCountry = !metadata.manualTranslationTargetCountry || metadata.manualTranslationTargetCountry === country?.country
  const validCountry = country && consistentCountry
  return {
    hasManualTranslationMetadata: typeof metadata?.manualTranslationEnabled === 'boolean' &&
      typeof metadata?.manualOriginalText === 'string',
    enabled,
    sourceText: enabled ? normalizeText(metadata.manualOriginalText) : finalText,
    translatedText: enabled ? finalText : '',
    targetLanguage: validCountry ? country.value : '',
    targetCountry: validCountry ? country.country : ''
  }
}

export const restoreManualTranslation = (state, replay) => {
  updateManualTranslationContext(state, replay)
  setManualTranslationEnabled(state, replay.enabled)
  // Corrupt translated tasks must remain blocked, never substitute the saved final text as the original.
  if (replay.enabled && (!state.sourceText || !resolveTargetCountry(state.targetLanguage))) {
    state.enabled = true
    state.status = 'error'
    state.error = 'INVALID_RESULT'
    return
  }
  if (state.enabled) {
    const request = beginManualTranslation(state)
    completeManualTranslation(state, request, {
      sourceText: replay.sourceText,
      targetLanguage: replay.targetLanguage,
      translatedText: replay.translatedText
    })
  }
}
