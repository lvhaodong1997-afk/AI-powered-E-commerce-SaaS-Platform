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
