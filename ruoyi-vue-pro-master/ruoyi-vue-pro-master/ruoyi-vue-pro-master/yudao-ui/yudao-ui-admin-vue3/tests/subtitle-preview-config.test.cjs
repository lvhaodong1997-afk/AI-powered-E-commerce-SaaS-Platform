const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
;(async () => {
  const { subtitlePreviewWords, karaokeResultMessage, restoreSubtitleReplay, subtitlePreviewFontSize } =
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
  const approvedSizes = { small: 58, medium: 68, large: 86, extra_large: 96 }
  assert.equal(typeof subtitlePreviewFontSize, 'function', 'preview must scale approved render font sizes')
  for (const [size, renderSize] of Object.entries(approvedSizes)) {
    assert.equal(subtitlePreviewFontSize(size), `calc(100cqw * ${renderSize} / 1080)`)
    assert.match(source, new RegExp(`value: '${size}'`))
    const replayForm = {}
    restoreSubtitleReplay(replayForm, { subtitleFontSize: size })
    assert.equal(replayForm.subtitleFontSize, size)
    const sizePayload = capture({ subtitleFontSize: size }, { value: true })
    assert.equal(sizePayload.subtitleFontSize, size)
  }
  assert.equal(subtitlePreviewFontSize(), subtitlePreviewFontSize('medium'))
  assert.equal(subtitlePreviewFontSize('unknown'), subtitlePreviewFontSize('medium'))
  assert.match(source, /label: isEn.value \? 'Small' : '小', value: 'small'/)
  assert.match(source, /label: isEn.value \? 'Medium' : '中', value: 'medium'/)
  assert.match(source, /label: isEn.value \? 'Large' : '大', value: 'large'/)
  assert.match(source, /label: isEn.value \? 'Extra large' : '特大', value: 'extra_large'/)
  assert.match(source, /subtitleFontSize: 'medium'/)
  assert.match(source, /:style="\{ '--subtitle-preview-font-size': subtitlePreviewFontSize\(createForm.subtitleFontSize\) \}"/)
  const previewCss = source.slice(source.indexOf('.subtitle-style-preview {'), source.indexOf('.library-select :deep'))
  assert.match(previewCss, /\.subtitle-preview-screen \{[^}]*container-type: inline-size;/)
  assert.match(previewCss, /\.subtitle-preview-screen \{[^}]*padding: 0;/)
  assert.match(previewCss, /\.subtitle-preview-caption \{[^}]*width: max-content;/)
  assert.match(previewCss, /\.subtitle-preview-caption \{[^}]*box-sizing: border-box;/)
  assert.match(previewCss, /max-width: calc\(100% - 24px\)/)
  assert.match(previewCss, /overflow-wrap: anywhere/)
  assert.match(previewCss, /font-size: var\(--subtitle-preview-font-size\)/)
  assert.doesNotMatch(previewCss, /--subtitle-preview-font-size:\s*\d+px/)
  const styleConfig = source.slice(source.indexOf('const subtitleStyleOptions = ['), source.indexOf('const subtitlePositionOptions = ['))
  const styles = [...styleConfig.matchAll(/value: '([^']+)'/g)].map((match) => match[1])
  assert.equal(styles.length, 11)
  for (const style of styles) {
    for (const rule of previewCss.matchAll(/([^{}]+)\{([^{}]*)\}/g)) {
      if (rule[1].includes(`.is-${style}`) && rule[1].includes('.subtitle-preview-caption')) {
        assert.doesNotMatch(rule[2], /(?:^|;)\s*(?:font-size|--subtitle-preview-font-size):/, `${style} must inherit the shared size`)
      }
    }
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
