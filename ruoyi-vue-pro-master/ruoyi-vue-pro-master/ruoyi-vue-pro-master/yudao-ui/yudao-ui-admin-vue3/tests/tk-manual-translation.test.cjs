const assert = require('node:assert/strict')
const { test } = require('node:test')

const api = import('../src/views/tk/dashboard/manualTranslation.mjs')
const context = { flow: 'ECOMMERCE', sourceText: '  原始文案  ', targetLanguage: 'en' }
const readyState = async () => {
  const m = await api
  const state = m.createManualTranslationState()
  m.updateManualTranslationContext(state, context)
  m.setManualTranslationEnabled(state, true)
  const request = m.beginManualTranslation(state)
  m.completeManualTranslation(state, request, {
    sourceText: '原始文案', targetLanguage: 'en', translatedText: 'Translated copy'
  })
  return { m, state }
}

test('original copy is selected by default and empty lead copy remains a visual-only mix', async () => {
  const m = await api
  const state = m.createManualTranslationState()
  m.updateManualTranslationContext(state, context)
  assert.deepEqual(m.selectManualCopy(state), {
    ready: true, promptText: '原始文案', manualOriginalText: '原始文案', manualTranslationEnabled: false
  })
  m.updateManualTranslationContext(state, { ...context, flow: 'LEAD_GENERATION', sourceText: ' ' })
  m.setManualTranslationEnabled(state, true)
  assert.equal(state.enabled, false)
  assert.equal(m.selectManualCopy(state).promptText, '')
  assert.equal(m.selectManualCopy(state).ready, true)
})

test('enabled translation blocks submission until ready and switching off restores original copy', async () => {
  const { m, state } = await readyState()
  assert.equal(m.selectManualCopy(state).promptText, 'Translated copy')
  m.beginManualTranslation(state)
  assert.equal(m.selectManualCopy(state).ready, false)
  assert.equal(m.selectManualCopy(state).promptText, '')
  m.setManualTranslationEnabled(state, false)
  assert.equal(m.selectManualCopy(state).promptText, '原始文案')
  assert.equal(m.selectManualCopy(state).ready, true)
})

test('source and language changes invalidate results and pending responses', async () => {
  const { m, state } = await readyState()
  const oldRequest = m.beginManualTranslation(state)
  m.updateManualTranslationContext(state, { ...context, sourceText: '新文案' })
  assert.equal(m.selectManualCopy(state).ready, false)
  assert.equal(m.completeManualTranslation(state, oldRequest, {
    sourceText: '原始文案', targetLanguage: 'en', translatedText: 'Stale'
  }), false)
  const currentRequest = m.beginManualTranslation(state)
  m.updateManualTranslationContext(state, { ...context, sourceText: '新文案', targetLanguage: 'de' })
  assert.equal(m.completeManualTranslation(state, currentRequest, {
    sourceText: '新文案', targetLanguage: 'en', translatedText: 'Stale again'
  }), false)
  assert.equal(state.translatedText, '')
})

test('new requests win over older completions and errors', async () => {
  const { m, state } = await readyState()
  const oldRequest = m.beginManualTranslation(state)
  const latestRequest = m.beginManualTranslation(state)
  assert.equal(m.completeManualTranslation(state, latestRequest, {
    sourceText: '原始文案', targetLanguage: 'en', translatedText: 'Latest'
  }), true)
  assert.equal(m.failManualTranslation(state, oldRequest), false)
  assert.equal(m.completeManualTranslation(state, oldRequest, {
    sourceText: '原始文案', targetLanguage: 'en', translatedText: 'Older'
  }), false)
  assert.equal(m.selectManualCopy(state).promptText, 'Latest')
})

test('switching off or changing manual flow cancels pending translation', async () => {
  const { m, state } = await readyState()
  const request = m.beginManualTranslation(state)
  m.setManualTranslationEnabled(state, false)
  assert.equal(m.completeManualTranslation(state, request, {
    sourceText: '原始文案', targetLanguage: 'en', translatedText: 'Ignored'
  }), false)
  m.setManualTranslationEnabled(state, true)
  m.updateManualTranslationContext(state, { ...context, flow: 'LEAD_GENERATION' })
  assert.equal(state.enabled, false)
  assert.equal(state.translatedText, '')
})

test('blank oversized and mismatched translations never fall back to original copy', async () => {
  for (const response of [
    { sourceText: '原始文案', targetLanguage: 'en', translatedText: ' ' },
    { sourceText: '原始文案', targetLanguage: 'en', translatedText: 'a'.repeat(3001) },
    { sourceText: '其他文案', targetLanguage: 'en', translatedText: 'Wrong' },
    { sourceText: '原始文案', targetLanguage: 'de', translatedText: 'Wrong language' }
  ]) {
    const { m, state } = await readyState()
    const request = m.beginManualTranslation(state)
    assert.equal(m.completeManualTranslation(state, request, response), false)
    assert.equal(state.status, 'error')
    assert.equal(state.error, 'INVALID_RESULT')
    assert.equal(m.selectManualCopy(state).ready, false)
    assert.equal(m.selectManualCopy(state).promptText, '')
  }
})

test('oversized original text cannot translate or submit', async () => {
  const m = await api
  const state = m.createManualTranslationState()
  m.updateManualTranslationContext(state, { ...context, sourceText: 'a'.repeat(3001) })
  assert.equal(m.selectManualCopy(state).ready, false)
  m.setManualTranslationEnabled(state, true)
  assert.equal(m.beginManualTranslation(state), undefined)
})

test('replay restores original separately from final copy and binds saved language', async () => {
  const m = await api
  const replay = m.readManualTranslationReplay({
    targetLanguage: 'en', promptText: 'Saved translation', scriptText: 'Pipeline text',
    generationRouteConfig: JSON.stringify({
      scriptMode: 'MANUAL', clipPlanMode: 'FULL_POOL_RANDOM', manualTranslationEnabled: true,
      manualOriginalText: 'Saved original', manualTranslationTargetLanguage: 'de'
    })
  }, 'zh-cn')
  const state = m.createManualTranslationState()
  m.restoreManualTranslation(state, { ...replay, flow: 'ECOMMERCE' })
  assert.equal(state.sourceText, 'Saved original')
  assert.equal(state.targetLanguage, 'de-de')
  assert.equal(m.selectManualCopy(state).promptText, 'Saved translation')
  m.setManualTranslationEnabled(state, false)
  assert.equal(m.selectManualCopy(state).promptText, 'Saved original')
})

test('old and malformed replay metadata defaults translation off with promptText fallback', async () => {
  const m = await api
  for (const generationRouteConfig of [undefined, '{broken', '{"scriptMode":"MANUAL"}']) {
    const replay = m.readManualTranslationReplay({
      promptText: 'Old original', scriptText: 'Other', targetLanguage: 'en', generationRouteConfig
    }, 'zh-cn')
    assert.equal(replay.enabled, false)
    assert.equal(replay.sourceText, 'Old original')
    assert.equal(replay.translatedText, '')
  }
})

test('a submitted snapshot stays immutable through pending precheck and later edits', async () => {
  const { m, state } = await readyState()
  const snapshot = m.captureManualCopy(state)
  m.updateManualTranslationContext(state, { ...context, sourceText: 'Edited after precheck' })
  m.setManualTranslationEnabled(state, false)
  assert.equal(Object.isFrozen(snapshot), true)
  assert.equal(snapshot.promptText, 'Translated copy')
  assert.equal(snapshot.manualOriginalText, '原始文案')
  assert.equal(snapshot.manualTranslationEnabled, true)
  assert.equal(snapshot.targetLanguage, 'en')
  m.setManualTranslationEnabled(state, true)
  assert.throws(() => m.captureManualCopy(state), /MANUAL_COPY_NOT_READY/)
})
