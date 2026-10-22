const subtitleRenderFontSizes = Object.freeze({
  small: 58,
  medium: 68,
  large: 86,
  extra_large: 96
})

/** Map the 1080px ASS canvas to the preview screen's responsive inline size. */
export function subtitlePreviewFontSize(size = 'medium') {
  const fontSize = Object.hasOwn(subtitleRenderFontSizes, size)
    ? subtitleRenderFontSizes[size]
    : subtitleRenderFontSizes.medium
  return `calc(100cqw * ${fontSize} / 1080)`
}

// libass uses REAL_DIM (ascent + descent), Canvas/CSS use em. Metrics are measured at 100px.
export function subtitlePreviewFontPixels(size, screenWidth, metrics) {
  const nominal = subtitleRenderFontSizes[size] || subtitleRenderFontSizes.medium
  const height = (metrics.fontBoundingBoxAscent + metrics.fontBoundingBoxDescent) ||
    (metrics.actualBoundingBoxAscent + metrics.actualBoundingBoxDescent) || 100
  return nominal * screenWidth / 1080 * 100 / height
}

const decorations = { classic_white: 5, yellow_keyword: 5, tiktok_large: 8, promo_bold: 3,
  clean_product: 2, neon_pop: 7, yellow_story: 5, price_flash: 4, step_card: 2,
  brand_minimal: 2, comment_bubble: 3 }

/** Centered preview positions share server x=540, margins=80; CSS padding is deducted separately. */
export function subtitlePreviewSafeWidth(screenWidth, style = 'classic_white', x = 540) {
  return Math.max(1, (Math.min(900, 2 * Math.min(x - 80, 1000 - x)) -
    2 * (decorations[style] ?? 5) - 8) * screenWidth / 1080)
}

const closing = '，,、。！？!?；;：:.”’」』）)]}》〉'
const opening = '“‘「『（([{《〈'
const protectedPhrases = ['系统能力', '执行按钮', '评论提效', '个人经验', '标准流程', '业务逻辑',
  '低水平', '重复劳动', '中等质量', '多维表格', '自动化输出', '低质视频', '高质量视频',
  '30条高质量视频', '多维表格提效', '提效', '底层逻辑', '整理了一份', '98块',
  '配料表', '芒果', '任何', '其他', '自然', '高温', '烘干', '厚切', '干净', '添加', '成熟', '口感', '嚼劲']
const clauseStarts = ['加上', '而且', '并且', '但是', '同时', '然后', '所以', '因为', '只要', '也不', '不仅']
const asciiWord = (char) => !!char && /^[a-z\d'%\-]$/i.test(char)

/** Display-only layout; UTF-16 offsets match backend boundaries. Token metadata is preserved. */
export function layoutSubtitlePreview(tokens, safeWidth, measureText, options = {}) {
  const text = tokens.map((token) => token.text).join('')
  const width = Math.max(1, safeWidth)
  const emSize = options.emSize || 1
  const graphemes = new Intl.Segmenter(undefined, { granularity: 'grapheme' }).segment(text)
  const offsets = Array.from(graphemes, (entry) => entry.index).filter((offset) => offset > 0)
  const phrases = options.phrases || protectedPhrases
  const safe = (offset) => {
    const left = text.slice(0, offset).trimEnd()
    const right = text.slice(offset).trimStart()
    const previous = text[offset - 1]
    const next = text[offset]
    return left && right && !opening.includes(Array.from(left).at(-1)) && !closing.includes(Array.from(right)[0]) &&
      !(asciiWord(previous) && asciiWord(next)) &&
      !(previous === '.' && /\d/.test(text[offset - 2] || '') && /\d/.test(next)) &&
      !(options.wordBoundary && !options.wordBoundary(offset)) &&
      !phrases.some((phrase) => {
        for (let start = text.indexOf(phrase); start >= 0; start = text.indexOf(phrase, start + 1)) {
          if (offset > start && offset < start + phrase.length) return true
        }
        return false
      })
  }
  const priority = (offset) => closing.includes(text[offset - 1]) ? 3 :
    /\s/u.test(text[offset - 1] + text[offset]) ? 2 :
      clauseStarts.some((clause) => text.startsWith(clause, offset)) ? 1 : 0
  let split = text.length
  if (measureText(text.trim()) > width) {
    const valid = offsets.filter(safe)
    const fitting = valid.filter((offset) => measureText(text.slice(0, offset).trimEnd()) <= width)
    const fullest = fitting.at(-1)
    split = fullest ?? valid[0] ?? text.length
    if (fullest !== undefined) {
      const fullWidth = measureText(text.slice(0, fullest).trimEnd())
      for (const offset of fitting.slice().reverse()) {
        if (fullWidth - measureText(text.slice(0, offset).trimEnd()) > emSize * 2) break
        if (priority(offset) > priority(split)) split = offset
      }
    }
  }
  const ranges = split < text.length ? [[0, split], [split, text.length]] : [[0, text.length]]
  const lines = ranges.map(([start, end]) => {
    // Apply exactly the same edge trimming to displayed pieces and boundary safety.
    start += text.slice(start, end).length - text.slice(start, end).trimStart().length
    end = start + text.slice(start, end).trimEnd().length
    let offset = 0
    return tokens.flatMap((token, tokenIndex) => {
      const tokenStart = offset
      offset += token.text.length
      const piece = token.text.slice(Math.max(0, start - tokenStart), Math.max(0, Math.min(token.text.length, end - tokenStart)))
      return piece ? [{ ...token, text: piece, tokenIndex }] : []
    })
  })
  const widest = Math.max(1, ...lines.map((line) => measureText(line.map((token) => token.text).join(''))))
  return { lines, scale: Math.min(1, width / widest), safeWidth: width }
}

/** Shared by the Vue preview and browser QA. Caller waits for document.fonts.ready. */
export function measureSubtitlePreview(caption, screen, size, style, tokens) {
  const computed = getComputedStyle(caption)
  const canvas = document.createElement('canvas')
  const context = canvas.getContext('2d')
  if (!context) return null
  const font = (px) => `${computed.fontStyle} ${computed.fontWeight} ${px}px ${computed.fontFamily}`
  context.font = font(100)
  // Include visible scripts so fallback faces contribute their actual browser metrics.
  const sample = tokens.map((token) => token.text).join('') || 'Hg国'
  const baseFontSize = subtitlePreviewFontPixels(size, screen.clientWidth, context.measureText(sample))
  context.font = font(baseFontSize)
  const measureText = (text) => {
    const metrics = context.measureText(computed.textTransform === 'uppercase' ? text.toUpperCase() : text)
    return (Math.max(metrics.width, metrics.actualBoundingBoxRight || 0) +
      Math.max(0, metrics.actualBoundingBoxLeft || 0)) * 1.01
  }
  const cssWidth = ['paddingLeft', 'paddingRight', 'borderLeftWidth', 'borderRightWidth']
    .reduce((sum, key) => sum + (parseFloat(computed[key]) || 0), 0)
  const safeWidth = Math.max(1, subtitlePreviewSafeWidth(screen.clientWidth, style) - cssWidth)
  const result = layoutSubtitlePreview(tokens, safeWidth, measureText, { emSize: baseFontSize })
  return { ...result, fontSize: baseFontSize * result.scale, baseFontSize }
}

/** @param {string[]} parts @param {boolean} english @returns {{text: string, keyword: boolean}[]} */
export function subtitlePreviewWords(parts, english) {
  return parts.flatMap((text, partIndex) => {
    const tokens = english ? text.match(/\S+\s*/g) || [] : Array.from(text)
    return tokens.map((text) => ({ text, keyword: partIndex === 1 }))
  })
}

export function karaokeResultMessage(task, copy) {
  if (!task?.subtitleKaraokeEnabled || task.subtitleEnabled === false) return ''
  if (task.subtitleKaraokeStatus === 'partial') return copy.partial
  if (task.subtitleKaraokeStatus === 'disabled') return copy.disabled
  if (!task.subtitleKaraokeStatus) return copy.unknown
  return ''
}

export function restoreSubtitleReplay(form, task) {
  const defaults = {
    subtitleEnabled: true,
    subtitleStyle: 'classic_white',
    subtitlePositionMode: 'smart_safe',
    subtitleKeywordEnabled: true,
    subtitleKeywords: '',
    subtitleKeywordMode: 'auto_manual',
    subtitleKaraokeEnabled: true,
    subtitleActiveColor: '#35F27A',
    subtitleKeywordColor: '#FFD84D',
    subtitleFontSize: 'medium'
  }
  for (const [key, fallback] of Object.entries(defaults)) {
    form[key] = task[key] ?? fallback
  }
}
