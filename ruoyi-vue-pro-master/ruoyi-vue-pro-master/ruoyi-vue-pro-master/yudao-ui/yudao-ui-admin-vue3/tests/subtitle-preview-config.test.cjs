const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
;(async () => {
  const { subtitlePreviewWords, karaokeResultMessage, restoreSubtitleReplay } =
    await import('../src/views/tk/dashboard/subtitlePreview.mjs')
  const words = subtitlePreviewWords(['Feel ', 'fresh fruit ', 'today'], true)
  assert.deepEqual(
    words.map((word) => word.text),
    ['Feel ', 'fresh ', 'fruit ', 'today']
  )
  assert.deepEqual(
    words.map((word) => word.keyword),
    [false, true, true, false]
  )
  assert.equal(subtitlePreviewWords(['新品', '芒果', '🤩'], false).at(-1).text, '🤩')
  const resultCopy = { partial: 'partly applied', disabled: 'disabled', unknown: 'older task' }
  const requested = { subtitleEnabled: true, subtitleKaraokeEnabled: true }
  assert.match(
    karaokeResultMessage({ ...requested, subtitleKaraokeStatus: 'partial' }, resultCopy),
    /partly applied/
  )
  assert.match(
    karaokeResultMessage({ ...requested, subtitleKaraokeStatus: 'disabled' }, resultCopy),
    /disabled/
  )
  assert.match(karaokeResultMessage(requested, resultCopy), /older task/)
  assert.equal(karaokeResultMessage({ ...requested, subtitleKaraokeStatus: 'enabled' }, resultCopy), '')
  assert.equal(karaokeResultMessage({ subtitleKaraokeEnabled: false }, resultCopy), '')
  const form = { subtitleFontSize: 'small', subtitleStyle: 'classic_white', subtitleKaraokeEnabled: false }
  const replay = { subtitleFontSize: 'extra_large', subtitleStyle: 'brand_minimal', subtitleKaraokeEnabled: true }
  restoreSubtitleReplay(form, replay)
  assert.equal(form.subtitleFontSize, 'extra_large')
  assert.equal(form.subtitleStyle, 'brand_minimal')
  assert.equal(form.subtitleKaraokeEnabled, true)
  replay.subtitleFontSize = 'small'
  assert.equal(form.subtitleFontSize, 'extra_large')
  restoreSubtitleReplay(form, {})
  assert.equal(form.subtitleFontSize, 'medium')
  const source = fs.readFileSync(
    path.join(__dirname, '../src/views/tk/dashboard/index.vue'),
    'utf8'
  )
  const payloadStart = source.indexOf('function getSubtitlePayload() {')
  const payloadEnd = source.indexOf('\nasync function ensureBgmReadyForGeneration', payloadStart)
  const payloadForm = { subtitleEnabled: true, subtitleFontSize: 'extra_large' }
  const capture = new Function('createForm', 'isVoiceoverEnabled',
    source.slice(payloadStart, payloadEnd) + '\nreturn getSubtitlePayload()')
  const payload = capture(payloadForm, { value: true })
  payloadForm.subtitleFontSize = 'small'
  assert.equal(payload.subtitleFontSize, 'extra_large')
  assert.match(source, /subtitlePayload: \{ \.\.\.getSubtitlePayload\(\) \}/)
  assert.match(source, /restoreSubtitleReplay\(createForm, task\)/)
  for (const size of ['small', 'medium', 'large', 'extra_large']) {
    assert.match(source, new RegExp(`value: '${size}'`))
    assert.match(source, new RegExp(`is-size-${size} \\.subtitle-preview-caption`))
  }
  assert.match(source, /v-for="\(word, index\) in subtitlePreviewWordList"/)
  assert.match(source, /@keyframes subtitle-word-highlight/)
  assert.match(source, /Noto Serif CJK SC/)
  assert.match(source, /:title="subtitleKaraokeResultNotice"/)
  assert.doesNotMatch(source, /has-karaoke-preview \.subtitle-preview-caption::after/)
  console.log('subtitle preview and result behavior tests passed')
})().catch((error) => {
  console.error(error)
  process.exit(1)
})
