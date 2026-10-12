const assert = require('node:assert/strict')
const { test } = require('node:test')
const fs = require('node:fs')
const path = require('node:path')
const api = import('../src/views/tk/dashboard/manualTranslation.mjs')

test('country selector has exactly the approved flat 16 options and canonical regional languages', async () => {
  const { targetCountryOptions } = await api
  assert.deepEqual(targetCountryOptions.map(({ country, value }) => [country, value]), [
    ['CN', 'zh-cn'], ['US', 'en-us'], ['GB', 'en-gb'], ['JP', 'ja-jp'], ['KR', 'ko-kr'],
    ['DE', 'de-de'], ['FR', 'fr-fr'], ['ES', 'es-es'], ['IT', 'it-it'], ['BR', 'pt-br'],
    ['MX', 'es-mx'], ['NL', 'nl-nl'], ['TR', 'tr-tr'], ['PT', 'pt-pt'], ['VN', 'vi-vn'], ['TH', 'th-th']
  ])
})

test('legacy replay languages select canonical countries while preserving both saved copies', async () => {
  const m = await api
  for (const [legacy, canonical, country] of [
    ['en', 'en-us', 'US'], ['en-us', 'en-us', 'US'], ['de', 'de-de', 'DE'],
    ['es', 'es-es', 'ES'], ['fr', 'fr-fr', 'FR'], ['nl', 'nl-nl', 'NL'], ['zh-cn', 'zh-cn', 'CN']
  ]) {
    const replay = m.readManualTranslationReplay({ targetLanguage: legacy, promptText: 'Final',
      generationRouteConfig: JSON.stringify({ manualTranslationEnabled: true, manualOriginalText: 'Original', manualTranslationTargetLanguage: legacy })
    }, 'zh-cn')
    assert.equal(replay.targetLanguage, canonical)
    assert.equal(replay.targetCountry, country)
    const state = m.createManualTranslationState()
    m.restoreManualTranslation(state, { ...replay, flow: 'ECOMMERCE' })
    assert.equal(state.sourceText, 'Original')
    assert.equal(state.translatedText, 'Final')
    assert.equal(m.selectManualCopy(state).ready, true)
  }
})

test('unsupported and inconsistent replay metadata stay blocked without silently selecting China', async () => {
  const m = await api
  for (const metadata of [
    { manualTranslationTargetLanguage: 'xx' },
    { manualTranslationTargetLanguage: 'en-gb', manualTranslationTargetCountry: 'US' },
    { manualTranslationTargetLanguage: 'en-us', manualTranslationTargetCountry: 'XX' }
  ]) {
    const replay = m.readManualTranslationReplay({ targetLanguage: 'en-us', promptText: 'Final',
      generationRouteConfig: JSON.stringify({ manualTranslationEnabled: true, manualOriginalText: 'Original', ...metadata })
    }, 'zh-cn')
    assert.equal(replay.targetLanguage, '')
    const state = m.createManualTranslationState()
    m.restoreManualTranslation(state, { ...replay, flow: 'ECOMMERCE' })
    assert.equal(state.sourceText, 'Original')
    assert.equal(m.selectManualCopy(state).ready, false)
    assert.equal(m.beginManualTranslation(state), undefined)
    m.updateManualTranslationContext(state, { flow: 'ECOMMERCE', sourceText: 'Original', targetLanguage: 'en-gb' })
    assert.equal(m.selectManualCopy(state).ready, false)
    const request = m.beginManualTranslation(state)
    assert.equal(m.completeManualTranslation(state, request, { sourceText: 'Original', targetLanguage: 'en-gb', translatedText: 'New final' }), true)
  }
})

test('translation and top selector share a flat country model with provider-neutral bilingual copy', () => {
  const source = fs.readFileSync(path.join(__dirname, '../src/views/tk/dashboard/index.vue'), 'utf8')
  const panel = source.slice(source.indexOf('<div v-if="manualTranslationFlow"'), source.indexOf('<p>{{ copy.manualTranslationHint }}'))
  assert.match(panel, /<el-select[\s\S]*v-model="createForm.targetLanguage"/)
  assert.match(panel, /v-for="item in languageOptions"/)
  assert.doesNotMatch(panel, /el-option-group/)
  assert.doesNotMatch(source, /DeepSeek/i)
  assert.match(source, /manualTranslationToggle: 'Enable translation'/)
  assert.match(source, /manualTranslationToggle: '启用翻译'/)
  assert.match(source, /manualTranslationTarget: 'Target country'/)
  assert.match(source, /manualTranslationTarget: '翻译目标国家'/)
})
test('JSON null replay config remains compatible with an old original task', async () => {
  const m = await api
  const replay = m.readManualTranslationReplay({ generationRouteConfig: 'null', promptText: 'Old original', targetLanguage: 'en' }, 'zh-cn')
  assert.equal(replay.sourceText, 'Old original')
  assert.equal(replay.targetLanguage, 'en-us')
})

test('new country metadata restores its canonical target and immutable saved copy', async () => {
  const m = await api
  const replay = m.readManualTranslationReplay({ targetLanguage: 'en-gb', promptText: 'Saved UK translation', generationRouteConfig: JSON.stringify({
    manualTranslationEnabled: true, manualOriginalText: '原文', manualTranslationTargetLanguage: 'en-gb', manualTranslationTargetCountry: 'GB'
  }) }, 'zh-cn')
  const state = m.createManualTranslationState()
  m.restoreManualTranslation(state, { ...replay, flow: 'LEAD_GENERATION' })
  assert.equal(replay.targetCountry, 'GB')
  assert.equal(m.captureManualCopy(state).promptText, 'Saved UK translation')
  assert.equal(m.captureManualCopy(state).targetLanguage, 'en-gb')
})
