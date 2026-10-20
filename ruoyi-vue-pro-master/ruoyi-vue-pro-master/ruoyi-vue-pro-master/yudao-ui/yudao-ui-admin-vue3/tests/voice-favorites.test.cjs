const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const { pathToFileURL } = require('node:url')

async function main() {
  const helper = path.resolve(__dirname, '../src/views/tk/dashboard/voiceFavorites.mjs')
  assert.ok(fs.existsSync(helper), 'Shared voice favorite behavior must be implemented')
  const {
    filterVoiceOptions,
    getVoiceEmptyState,
    persistVoiceFavorite,
    buildDashScopeVoiceOptions
  } = await import(pathToFileURL(helper))
  const options = [
    {
      voiceId: 'a',
      label: 'Alice',
      language: 'en',
      country: 'US',
      gender: 'female',
      favorite: false
    },
    {
      voiceId: 'b',
      label: 'Betty',
      language: 'en',
      country: 'US',
      gender: 'female',
      favorite: true
    },
    {
      voiceId: 'c',
      label: 'Cathy',
      language: 'en',
      country: 'US',
      gender: 'female',
      favorite: true
    },
    { voiceId: 'd', label: 'Danny', language: 'zh', country: 'CN', gender: 'male', favorite: false }
  ]
  assert.deepEqual(
    filterVoiceOptions(options, {}).map((x) => x.voiceId),
    ['b', 'c', 'a', 'd']
  )
  assert.deepEqual(
    options.map((x) => x.voiceId),
    ['a', 'b', 'c', 'd'],
    'Sorting must not mutate provider order'
  )
  assert.deepEqual(
    filterVoiceOptions(options, {
      name: '  T ',
      language: 'en',
      country: 'US',
      gender: 'female',
      favoritesOnly: true
    }).map((x) => x.voiceId),
    ['b', 'c']
  )
  assert.deepEqual(filterVoiceOptions(options, { language: 'zh', favoritesOnly: true }), [])
  assert.equal(
    getVoiceEmptyState(options, { favoritesOnly: true, country: 'CN' }),
    'voice.noFavoriteMatches'
  )
  assert.equal(getVoiceEmptyState([], { favoritesOnly: true }), 'voice.noFavorites')
  assert.equal(getVoiceEmptyState([], {}), 'voice.noMatches')
  const built = buildDashScopeVoiceOptions(
    [{ value: 'Preset', label: 'Preset voice' }],
    [
      {
        id: 7,
        name: 'Custom',
        status: 'READY',
        enabled: true,
        provider: 'DASHSCOPE',
        previewFileUrl: 'preview'
      },
      { id: 8, name: 'Not ready', status: 'FAILED', enabled: true, provider: 'DASHSCOPE' },
      { id: 9, name: 'Hidden', status: 'READY', enabled: true, provider: 'MIMO' }
    ],
    ['Preset', 'custom:7', 'custom:8', 'gone'],
    []
  )
  assert.deepEqual(
    built.map((x) => x.voiceId),
    ['Preset', 'custom:7', 'custom:8', 'gone']
  )
  assert.equal(built[1].favorite, true)
  assert.equal(built[1].previewUrl, 'preview')
  assert.equal(built[2].unavailable, true)
  assert.equal(built[3].unavailable, true)
  assert.ok(!built.some((x) => x.voiceId === 'custom:9'))
  const state = { pending: '' }
  let requests = 0,
    resolveWrite,
    changes = []
  const write = () => {
    requests++
    return new Promise((resolve) => {
      resolveWrite = resolve
    })
  }
  const first = persistVoiceFavorite(state, options[1], write, (id, value) =>
    changes.push([id, value])
  )
  await persistVoiceFavorite(state, options[1], write, () => assert.fail('Duplicate write'))
  assert.equal(requests, 1)
  assert.equal(state.pending, 'b')
  assert.deepEqual(changes, [], 'Only a confirmed write changes the favorite')
  resolveWrite()
  await first
  assert.deepEqual(changes, [['b', false]])
  assert.equal(state.pending, '')
  await assert.rejects(
    persistVoiceFavorite(
      state,
      options[0],
      async () => {
        throw Error('offline')
      },
      () => assert.fail('Failed writes cannot appear successful')
    ),
    /offline/
  )
  assert.equal(state.pending, '')
  assert.equal(options[0].favorite, false)
  console.log(
    'Voice favorites filter, empty state, stale choices, persistence failures and race tests passed'
  )
}
main().catch((error) => {
  console.error(error)
  process.exitCode = 1
})
