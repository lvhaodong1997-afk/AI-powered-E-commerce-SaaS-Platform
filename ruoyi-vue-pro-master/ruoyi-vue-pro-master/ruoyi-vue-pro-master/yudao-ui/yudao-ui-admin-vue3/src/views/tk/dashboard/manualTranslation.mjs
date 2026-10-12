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
  if (!state.enabled || !state.sourceText || state.sourceText.length > MAX_SCRIPT_LENGTH) {
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
  const ready = validSource && (!state.enabled || (
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
    metadata = JSON.parse(task.generationRouteConfig || '{}')
  } catch {
    metadata = {}
  }
  const enabled = metadata?.manualTranslationEnabled === true
  const finalText = normalizeText(task.promptText || task.scriptText)
  return {
    hasManualTranslationMetadata: typeof metadata?.manualTranslationEnabled === 'boolean' &&
      typeof metadata?.manualOriginalText === 'string',
    enabled,
    sourceText: enabled ? normalizeText(metadata.manualOriginalText) : finalText,
    translatedText: enabled ? finalText : '',
    targetLanguage: (enabled && metadata.manualTranslationTargetLanguage) || task.targetLanguage || defaultLanguage
  }
}

export const restoreManualTranslation = (state, replay) => {
  updateManualTranslationContext(state, replay)
  setManualTranslationEnabled(state, replay.enabled)
  // Corrupt translated tasks must remain blocked, never substitute the saved final text as the original.
  if (replay.enabled && !state.sourceText) {
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
