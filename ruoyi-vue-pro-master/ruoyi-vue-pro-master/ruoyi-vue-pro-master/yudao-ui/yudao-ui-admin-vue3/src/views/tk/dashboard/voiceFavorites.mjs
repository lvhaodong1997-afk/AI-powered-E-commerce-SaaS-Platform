/** Apply every active filter, then promote favorites without changing the provider order within each group. */
export function filterVoiceOptions(options, filter) {
  const keyword = (filter.name || '').trim().toLowerCase()
  return options
    .filter(
      (option) =>
        (!keyword || option.label.toLowerCase().includes(keyword)) &&
        (!filter.language || option.language === filter.language) &&
        (!filter.country || option.country === filter.country) &&
        (!filter.gender || option.gender === filter.gender) &&
        (!filter.favoritesOnly || option.favorite)
    )
    .sort((a, b) => Number(Boolean(b.favorite)) - Number(Boolean(a.favorite)))
}

export function getVoiceEmptyState(options, filter) {
  if (!filter.favoritesOnly) return 'voice.noMatches'
  return options.some((option) => option.favorite) ? 'voice.noFavoriteMatches' : 'voice.noFavorites'
}

/** Keep a single write in flight. Only the server-confirmed result may change UI state. */
export async function persistVoiceFavorite(state, option, write, confirmed) {
  if (state.pending) return
  state.pending = option.voiceId
  const favorite = !option.favorite
  try {
    await write(option.voiceId, favorite)
    confirmed(option.voiceId, favorite)
  } finally {
    state.pending = ''
  }
}

export function buildDashScopeVoiceOptions(presets, profiles, favorites, historical) {
  const keys = new Set(favorites)
  const option = (voiceId, label, extra = {}) => ({
    value: voiceId,
    voiceId,
    label,
    model: '',
    language: '',
    country: '',
    gender: '',
    description: '',
    previewUrl: '',
    isDefault: false,
    favorite: keys.has(voiceId),
    ...extra
  })
  const options = presets.map((item) => option(item.value, item.label))
  profiles
    .filter(
      (item) =>
        String(item.provider || 'DASHSCOPE').toUpperCase() !== 'MIMO' &&
        item.status === 'READY' &&
        item.enabled
    )
    .forEach((item) =>
      options.push(
        option(`custom:${item.id}`, item.name, {
          language: item.language || '',
          previewUrl: item.previewFileUrl || ''
        })
      )
    )
  historical.forEach((item) => {
    if (!options.some((existing) => existing.voiceId === item.value)) {
      options.push(option(item.value, item.label, { favoriteDisabled: true }))
    }
  })
  favorites.forEach((key) => {
    if (!options.some((item) => item.voiceId === key))
      options.push(option(key, key, { unavailable: true }))
  })
  return options
}
