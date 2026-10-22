const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
;(async () => {
  const { subtitlePreviewWords, karaokeResultMessage, restoreSubtitleReplay, subtitlePreviewFontSize,
    layoutSubtitlePreview, subtitlePreviewFontPixels, subtitlePreviewSafeWidth, measureSubtitlePreview } =
    await import('../src/views/tk/dashboard/subtitlePreview.mjs')
  assert.equal(typeof layoutSubtitlePreview, 'function', 'single-line-first measured layout must exist')
  const tokens = (text) => Array.from(text).map((text, index) => ({ text, keyword: index % 2 === 0, delay: index * .3 }))
  const measure = (text) => Array.from(text).length * 10
  const layout = (text, width = 100, options = {}) => layoutSubtitlePreview(tokens(text), width, measure, { emSize: 10, ...options })
  const visible = (result) => result.lines.map((line) => line.map((token) => token.text).join(''))
  assert.deepEqual(visible(layout('试试这个厚切芒果干。', 110)), ['试试这个厚切芒果干。'])
  assert.deepEqual(visible(layout('甲乙丙丁戊己庚辛壬癸甲乙丙丁', 100)), ['甲乙丙丁戊己庚辛壬癸', '甲乙丙丁'])
  assert.deepEqual(visible(layout('甲乙丙丁戊己，庚辛壬癸甲乙丙', 90)), ['甲乙丙丁戊己，', '庚辛壬癸甲乙丙'])
  for (const text of ['甲乙丙丁戊己庚辛壬癸甲乙 。丙丁戊己庚辛', '甲乙丙丁戊己庚辛壬癸，甲乙']) {
    assert.ok(!/^[。，]/u.test(visible(layout(text, 120))[1]), 'trimmed second line cannot start with closing punctuation')
  }
  assert.deepEqual(visible(layout('Fresh mango tastes great', 120)), ['Fresh mango', 'tastes great'])
  for (const atom of ['39.8', '100%', '👩🏽‍💻', 'e\u0301', '芒果']) {
    const lines = visible(layout(`甲乙丙${atom}丁戊己庚辛`, 50))
    assert.ok(lines.some((line) => line.includes(atom)), `${atom} must stay intact`)
  }
  const original = tokens('甲乙丙丁戊己庚辛壬癸甲乙')
  const result = layoutSubtitlePreview(original, 70, measure, { emSize: 10 })
  assert.deepEqual(result.lines.flat().map(({ text, keyword, delay }) => ({ text, keyword, delay })), original)
  assert.equal(layout('甲乙丙丁戊己庚辛壬癸甲乙', 150).lines.length, 1)
  assert.equal(result.lines.length, 2)
  assert.equal(layout('甲乙丙丁戊己庚辛壬癸甲乙', 150, {}).scale, 1)
  const huge = layout('甲乙丙丁戊己庚辛壬癸'.repeat(12), 100)
  assert.equal(huge.lines.length, 2)
  assert.ok(Math.max(...visible(huge).map(measure)) * huge.scale <= 100)
  assert.equal(subtitlePreviewFontPixels('large', 1080, { fontBoundingBoxAscent: 110, fontBoundingBoxDescent: 15 }), 68.8)
  assert.equal(subtitlePreviewFontPixels('large', 540, { fontBoundingBoxAscent: 110, fontBoundingBoxDescent: 15 }), 34.4)
  assert.equal(subtitlePreviewFontPixels('small', 1080, { fontBoundingBoxAscent: 80, fontBoundingBoxDescent: 20 }), 58)
  assert.equal(subtitlePreviewSafeWidth(1080, 'classic_white'), 882)
  assert.equal(subtitlePreviewSafeWidth(540, 'tiktok_large'), 438)
  assert.deepEqual(visible(layout('甲乙丙丁戊己庚辛', 50, { wordBoundary: (offset) => offset % 3 === 0 })), ['甲乙丙', '丁戊己庚辛'])
  assert.deepEqual(visible(layout('')), [''])
  // DOM adapter must measure the selected logical size afresh, never the prior safe-fit font size.
  const originalDocument = global.document
  const originalComputedStyle = global.getComputedStyle
  const computedStyle = { fontStyle: 'normal', fontWeight: '700', fontFamily: 'test-font', fontSize: '1px',
    paddingLeft: '12px', paddingRight: '12px', borderLeftWidth: '2px', borderRightWidth: '2px' }
  const context = { font: '', measureText(text) {
    const px = Number(this.font.match(/([\d.]+)px/)[1])
    return { width: Array.from(text).length * px, fontBoundingBoxAscent: px, fontBoundingBoxDescent: px * .25 }
  } }
  try {
    global.getComputedStyle = () => computedStyle
    global.document = { createElement: () => ({ getContext: () => context }) }
    const screen = { clientWidth: 320 }
    const first = measureSubtitlePreview({}, screen, 'large', 'promo_bold', tokens('甲乙丙丁戊己庚辛壬癸'.repeat(8)))
    assert.ok(first.scale < 1)
    computedStyle.fontSize = `${first.fontSize}px`
    const again = measureSubtitlePreview({}, screen, 'large', 'promo_bold', tokens('甲乙丙丁戊己庚辛壬癸'.repeat(8)))
    assert.equal(again.baseFontSize, first.baseFontSize)
    assert.equal(again.fontSize, first.fontSize)
    assert.equal(first.safeWidth, subtitlePreviewSafeWidth(320, 'promo_bold') - 28)
    screen.clientWidth = 640
    const resized = measureSubtitlePreview({}, screen, 'large', 'promo_bold', tokens('试试这个厚切芒果干。'))
    assert.equal(resized.baseFontSize, first.baseFontSize * 2)
    assert.equal(resized.lines.length, 1)
    assert.ok(measureSubtitlePreview({}, screen, 'extra_large', 'promo_bold', tokens('甲乙')).baseFontSize > resized.baseFontSize)
  } finally {
    global.document = originalDocument
    global.getComputedStyle = originalComputedStyle
  }
  console.log('measured single-line layout behavior tests passed')
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
  assert.match(source, /measureSubtitlePreview\(subtitlePreviewCaption.value, subtitlePreviewScreen.value/)
  assert.match(source, /await document.fonts.ready/)
  assert.match(source, /new ResizeObserver\(scheduleSubtitlePreviewLayout\)/)
  assert.match(source, /subtitlePreviewObserver\?\.disconnect\(\)/)
  const previewCss = source.slice(source.indexOf('.subtitle-style-preview {'), source.indexOf('.library-select :deep'))
  assert.match(previewCss, /\.subtitle-preview-screen \{[^}]*container-type: inline-size;/)
  assert.match(previewCss, /\.subtitle-preview-screen \{[^}]*padding: 0;/)
  const screenCss = previewCss.match(/\.subtitle-preview-screen \{([^}]+)\}/)[1]
  assert.match(screenCss, /aspect-ratio: 2 \/ 1;/, 'preview height must grow with canvas width for padded two-line captions')
  assert.match(screenCss, /min-height: 158px;/, 'narrow preview must retain its minimum safe height')
  assert.doesNotMatch(screenCss, /(?:^|;)\s*(?:height|max-height):/, 'preview must not cap its responsive height')
  assert.match(previewCss, /\.subtitle-preview-caption \{[^}]*width: max-content;/)
  assert.match(previewCss, /\.subtitle-preview-caption \{[^}]*box-sizing: border-box;/)
  assert.doesNotMatch(previewCss, /overflow-wrap: anywhere|max-width: calc\(100% - 24px\)/)
  assert.match(previewCss, /\.subtitle-preview-line \{[^}]*white-space: pre;/)
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
  assert.match(source, /v-for="\(line, lineIndex\) in subtitlePreviewLines"/)
  assert.match(source, /word.tokenIndex \* 0.3/)
  assert.match(source, /@keyframes subtitle-word-highlight/)
  assert.match(source, /Noto Serif CJK SC/)
  assert.match(source, /:title="subtitleKaraokeResultNotice"/)
  assert.doesNotMatch(source, /has-karaoke-preview \.subtitle-preview-caption::after/)
  console.log('subtitle preview and result behavior tests passed')
})().catch((error) => {
  console.error(error)
  process.exit(1)
})
