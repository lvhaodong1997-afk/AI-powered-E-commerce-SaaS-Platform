const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const ts = require('typescript')

function load(relative, imports = {}) {
  const file = path.join(__dirname, '..', relative)
  assert.ok(fs.existsSync(file), `Meta P0 implementation missing: ${relative}`)
  const output = ts.transpileModule(fs.readFileSync(file, 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 }
  }).outputText
  const module = { exports: {} }
  new Function('require', 'module', 'exports', output)(
    (name) => imports[name] || (name === './metaStats' ? load('src/views/tk/video-publish-center/components/metaStats.ts')
      : name === '@/locales/tk/metaPublishMessages' ? load('src/locales/tk/metaPublishMessages.ts') : require(name)), module, module.exports
  )
  return module.exports
}

const deferred = () => {
  let resolve, reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
const accounts = [
  { id: 1, platform: 'INSTAGRAM', status: 'ACTIVE', accountName: 'IG' },
  { id: 2, platform: 'FACEBOOK_PAGE', status: 'ACTIVE', accountName: 'Page A' },
  { id: 3, platform: 'FACEBOOK_PAGE', status: 'ACTIVE', accountName: 'Page B' }
]

test('Meta publishing copy follows the selected locale for static and dynamic text', () => {
  const { metaText, translateMetaText } = load('src/locales/tk/metaPublishMessages.ts')
  assert.equal(metaText('meta.title', 'zh-CN'), 'Instagram / Facebook 发布')
  assert.equal(metaText('meta.title', 'en'), 'Instagram / Facebook Publishing')
  assert.equal(translateMetaText('发布成功', 'zh-CN'), '发布成功')
  assert.equal(translateMetaText('发布成功', 'en'), 'Published successfully')
  assert.equal(translateMetaText('指标', 'en'), 'Metric')
  assert.equal(translateMetaText('指标', 'zh-CN'), '指标')
  assert.equal(translateMetaText('授权未完成，请检查账号权限或绑定归属后重试', 'en'), 'Authorization is incomplete. Check the account permissions and Page ownership, then try again')
  assert.equal(translateMetaText('OSS 上传失败（HTTP 413）', 'en'), 'OSS upload failed (HTTP 413)')
})

test('Meta statistics format numbers and timestamps with the selected locale', () => {
  const { formatMetricValue, formatSocialDate } = load('src/views/tk/video-publish-center/components/metaStats.ts')
  assert.equal(formatMetricValue({ value: 1234.5 }, 'en-US'), '1,234.5')
  assert.notEqual(formatSocialDate(1700000000000, 'zh-CN'), formatSocialDate(1700000000000, 'en-US'))
})

const stats = (id, value = 0, syncStatus = 'SUCCESS') => ({ objectId: id, platform: 'INSTAGRAM', syncStatus,
  lastSuccessTime: 1700000000000, lastAttemptTime: 1700000000000, metrics: [
    { key: 'followers', value, unit: 'count', sourceMetric: 'followers_count', scope: 'account', period: 'lifetime', availability: 'AVAILABLE', fetchedAt: 1700000000000 }
  ] })

test('stats metric formatting distinguishes null, zero and reactions from likes', () => {
  const { formatMetricValue, metricLabel, mergeStats } = load('src/views/tk/video-publish-center/components/metaStats.ts')
  assert.equal(formatMetricValue({ value: null }), '—')
  assert.equal(formatMetricValue({ value: 0 }), '0')
  assert.match(metricLabel({ key: 'likes', sourceMetric: 'post_reactions_by_type_total' }), /反应/)
  assert.equal(metricLabel({ key: 'likes', sourceMetric: 'likes' }), '点赞')
  const prior = stats(1, 17)
  const failed = stats(1, null, 'FAILED'); failed.metrics[0].availability = 'ERROR'; failed.errorMessage = 'permission revoked'
  const merged = mergeStats(prior, failed)
  assert.equal(merged.metrics[0].value, 17); assert.equal(merged.metrics[0].availability, 'ERROR')
  assert.equal(merged.metrics[0].stale, true); assert.equal(merged.errorMessage, 'permission revoked')
})

test('all availability states have meaningful Chinese labels and failed old values retain their own timestamp', () => {
  const { availabilityLabel, metricLabel, mergeStats } = load('src/views/tk/video-publish-center/components/metaStats.ts')
  for (const status of ['AVAILABLE', 'UNKNOWN', 'UNSUPPORTED', 'PERMISSION_REQUIRED', 'AUTH_REQUIRED', 'OBJECT_UNAVAILABLE', 'ERROR', 'PENDING']) {
    assert.match(availabilityLabel(status), /[\u4e00-\u9fff]/, status)
  }
  assert.match(metricLabel({ key: 'views', sourceMetric: 'blue_reels_play_count' }), /播放/)
  const prior = stats(1, 0)
  const next = stats(1, null, 'FAILED')
  next.metrics[0].availability = 'OBJECT_UNAVAILABLE'; next.metrics[0].fetchedAt = 1800000000000
  const merged = mergeStats(prior, next)
  assert.equal(merged.metrics[0].value, 0)
  assert.equal(merged.metrics[0].fetchedAt, 1700000000000)
  assert.equal(merged.metrics[0].availability, 'OBJECT_UNAVAILABLE')
  assert.equal(merged.metrics[0].stale, true)
})

test('disabled and inactive statistics snapshots have Chinese labels', () => {
  const { statsStatusLabel } = load('src/views/tk/video-publish-center/components/metaStats.ts')
  assert.equal(statsStatusLabel('DISABLED'), '已禁用')
  assert.equal(statsStatusLabel('INACTIVE'), '未启用')
})

test('new API methods send exact IDs and methods', async () => {
  const requests = []
  const request = Object.fromEntries(['get', 'post'].map(method => [method, async args => requests.push({ method, ...args })]))
  const { SocialPublishApi: api } = load('src/api/tk/socialPublish/index.ts', { '@/config/axios': { __esModule: true, default: request } })
  await api.accountStats(1); await api.accountStatsSync(1); await api.detailStats(9); await api.detailStatsSync(9); await api.mediaGet(43)
  assert.deepEqual(requests, [
    { method: 'get', url: '/tk/social-account/stats', params: { id: 1 } },
    { method: 'post', url: '/tk/social-account/stats/sync', params: { id: 1 } },
    { method: 'get', url: '/tk/social-publish/detail/stats', params: { detailId: 9 } },
    { method: 'post', url: '/tk/social-publish/detail/stats/sync', params: { detailId: 9 } },
    { method: 'get', url: '/tk/social-publish/media/get', params: { id: 43 } }
  ])
})

test('axios response handling avoids native request invocation and duplicate error toasts', () => {
  const source = fs.readFileSync(path.resolve(__dirname, '../src/config/axios/service.ts'), 'utf8')
  assert.match(source, /response\.config\.responseType/)
  assert.doesNotMatch(source, /response\.request\.responseType/)
  assert.match(source, /error\.displayedByAxios\s*=\s*true/)
})

test('INSPECTING continues polling, UNVERIFIED cannot publish, and stopping invalidates in-flight media reads', async () => {
  const slow = deferred()
  let reads = 0
  const h = setup({ videoUploadComplete: async () => ({ id: 43, mediaType: 'VIDEO', status: 'PROCESSING', metadataStatus: 'PENDING' }),
    mediaGet: async () => ++reads === 1 ? { id: 43, mediaType: 'VIDEO', status: 'PROCESSING', metadataStatus: 'INSPECTING' } : slow.promise })
  await h.controller.start(); Object.assign(h.state.draft, { title: 'Video', accountIds: [1] })
  await h.controller.upload({ name: 'clip.mp4', type: 'video/mp4', size: 100 }); await h.tick()
  assert.equal(h.state.media.metadataStatus, 'INSPECTING'); assert.equal(h.timers.size, 2)
  const pending = h.tick(); await new Promise(setImmediate); h.controller.stop()
  slow.resolve({ id: 43, mediaType: 'VIDEO', status: 'READY', metadataStatus: 'VERIFIED' }); await pending
  assert.equal(h.state.media.metadataStatus, 'INSPECTING'); assert.equal(h.timers.size, 0)
  await h.controller.start()
  h.state.media = { id: 43, mediaType: 'VIDEO', status: 'READY', metadataStatus: 'UNVERIFIED' }
  await assert.rejects(h.controller.submit(), /检测/)
  h.controller.stop()
})

test('stats stopped during read cannot resurrect timers or overwrite a restarted panel', async () => {
  const slow = deferred(); let reads = 0
  const h = setup({ accountStats: () => ++reads === 1 ? slow.promise : Promise.resolve(stats(1, 2)) })
  await h.controller.start(); const old = h.controller.refreshStats('account', 1)
  h.controller.stop(); await h.controller.start(); await h.controller.refreshStats('account', 1)
  slow.resolve(stats(1, 99, 'PROCESSING')); await old
  assert.equal(h.state.stats['account:1'].metrics[0].value, 2); assert.equal(h.timers.size, 1)
  h.controller.stop()
})

test('statistics polling is bounded and does not continue after disposal', async () => {
  const h = setup({ detailStats: async id => stats(id, null, 'PROCESSING') })
  await h.controller.start(); await h.controller.openStats('detail', 9)
  for (let i = 0; i < 60; i++) await h.tick()
  assert.equal(h.timers.size, 1)
  assert.match(h.state.statsErrors['detail:9'], /暂停/)
  h.controller.stop(); assert.equal(h.timers.size, 0)
})

test('stats sync uses existing account update permission and publish query permission', async () => {
  let writes = 0
  const h = setup({ accountStatsSync: async () => { writes++; return { accepted: true, syncStatus: 'PROCESSING' } } },
    permission => permission !== 'tk:social-account:update')
  await h.controller.start()
  await assert.rejects(h.controller.syncStats('account', 1), /权限/)
  assert.equal(writes, 0); h.controller.stop()
})

test('missing metrics are not labeled as old values and account refresh cannot erase a newer failure', async () => {
  const { mergeStats } = load('src/views/tk/video-publish-center/components/metaStats.ts')
  const missing = stats(1, null, 'FAILED'); missing.metrics[0].availability = 'OBJECT_UNAVAILABLE'
  assert.equal(mergeStats(undefined, missing).metrics[0].stale, false)
  const h = setup({ accountPage: async () => ({ list: [{ ...accounts[0], stats: stats(1, 4) }], total: 1 }),
    accountStats: async () => { throw new Error('offline') } })
  await h.controller.start(); await h.controller.refreshStats('account', 1); await h.controller.refreshAccounts()
  assert.equal(h.state.stats['account:1'].metrics[0].stale, true)
  assert.equal(h.state.stats['account:1'].syncStatus, 'FAILED')
  h.controller.stop()
})

test('account stats failure preserves prior value and failed status', async () => {
  let fail = false
  const h = setup({ accountStats: async () => { if (fail) throw new Error('offline'); return stats(1, 8) } })
  await h.controller.start(); await h.controller.refreshStats('account', 1)
  fail = true; await h.controller.refreshStats('account', 1)
  assert.equal(h.state.stats['account:1'].metrics[0].value, 8)
  assert.match(h.state.statsErrors['account:1'], /offline/)
  assert.equal(h.state.stats['account:1'].metrics[0].stale, true)
  h.controller.stop()
})

test('accepted sync remains pending, prevents duplicates and observes server cooldown', async () => {
  let syncs = 0
  const h = setup({ accountStatsSync: async () => { syncs++; return { accepted: true, syncStatus: 'PROCESSING', nextPollAfterSeconds: 2 } },
    accountStats: async () => ({ ...stats(1), lastAttemptTime: 60000, nextSyncTime: 6 * 60 * 60 * 1000 }) })
  await h.controller.start()
  await Promise.all([h.controller.syncStats('account', 1), h.controller.syncStats('account', 1)])
  assert.equal(syncs, 1); assert.equal(h.state.stats['account:1'].syncStatus, 'PROCESSING')
  await h.tick(); assert.equal(h.state.stats['account:1'].syncStatus, 'SUCCESS')
  h.advance(61000); await h.controller.syncStats('account', 1); assert.equal(syncs, 1)
  h.advance(60000); await h.controller.syncStats('account', 1); assert.equal(syncs, 2)
  h.controller.stop(); assert.equal(h.timers.size, 0)
})

for (const kind of ['account', 'detail']) {
  test(`${kind} manual sync ignores future automatic schedule and retains local POST cooldown`, async () => {
    const now = 1700000000000
    const snapshot = { ...stats(1), lastAttemptTime: now - 61000,
      nextSyncTime: now + (kind === 'account' ? 6 * 60 : 30) * 60000 }
    let syncs = 0
    const h = setup({
      [kind + 'Stats']: async () => snapshot,
      [kind + 'StatsSync']: async () => { syncs++; return { accepted: true, syncStatus: 'PROCESSING', nextPollAfterSeconds: 1 } }
    })
    h.advance(now)
    await h.controller.start(); await h.controller.refreshStats(kind, 1)
    assert.equal(h.controller.canSyncStats(kind, 1), true, 'automatic schedule must not block manual sync')
    await h.controller.syncStats(kind, 1); assert.equal(syncs, 1)
    await h.tick()
    h.advance(59999); await h.controller.syncStats(kind, 1)
    assert.equal(syncs, 1, 'local POST cooldown survives a read returning an older lastAttemptTime')
    h.advance(1); await h.controller.syncStats(kind, 1)
    assert.equal(syncs, 2)
    h.controller.stop()
  })
}

for (const format of ['millis', 'ISO']) {
  test(`lastAttemptTime enforces the 60 second manual cooldown for ${format} timestamps`, async () => {
    const now = 1700000000000, attempted = now - 30000
    let syncs = 0
    const h = setup({
      accountPage: async () => ({ list: [{ ...accounts[0], stats: { ...stats(1),
        lastAttemptTime: format === 'ISO' ? new Date(attempted).toISOString() : attempted,
        nextSyncTime: now - 1 } }], total: 1 }),
      accountStatsSync: async () => { syncs++; return { accepted: true, syncStatus: 'PROCESSING' } }
    })
    h.advance(now); await h.controller.start()
    assert.equal(h.controller.canSyncStats('account', 1), false)
    h.advance(29999); await h.controller.syncStats('account', 1); assert.equal(syncs, 0)
    h.advance(1); await h.controller.syncStats('account', 1); assert.equal(syncs, 1)
    h.controller.stop()
  })
}

test('opening account drawer during sync cannot strand busy state or discard accepted sync', async () => {
  const slow = deferred()
  const h = setup({ accountStatsSync: () => slow.promise, accountStats: async () => stats(1, 8) })
  await h.controller.start()
  const syncing = h.controller.syncStats('account', 1)
  await h.controller.openStats('account', 1)
  slow.resolve({ accepted: true, syncStatus: 'PROCESSING', nextPollAfterSeconds: 1 }); await syncing
  assert.equal(h.state.statsSyncing['account:1'], false)
  assert.equal(h.state.stats['account:1'].syncStatus, 'PROCESSING')
  await h.tick(); assert.equal(h.state.stats['account:1'].metrics[0].value, 8)
  h.controller.stop()
})

test('initial account snapshot PENDING polls automatically without opening its drawer', async () => {
  const h = setup({ accountPage: async () => ({ list: [{ ...accounts[0], stats: stats(1, null, 'PENDING') }], total: 1 }),
    accountStats: async () => stats(1, 12) })
  await h.controller.start()
  assert.equal(h.timers.size, 2)
  await h.tick()
  assert.equal(h.state.stats['account:1'].metrics[0].value, 12)
  assert.equal(h.state.stats['account:1'].syncStatus, 'SUCCESS')
  assert.equal(h.timers.size, 1); h.controller.stop()
})

for (const optionalSnapshot of ['unchanged', 'absent']) {
  for (const status of ['PENDING', 'PROCESSING']) {
    test(`restart resumes cached ${status} account statistics with ${optionalSnapshot} snapshot`, async () => {
      const snapshot = stats(1, 4, status)
      let pages = 0, reads = 0
      const h = setup({
        accountPage: async () => ({ list: [{ ...accounts[0], ...(++pages === 1 || optionalSnapshot === 'unchanged' ? { stats: snapshot } : {}) }], total: 1 }),
        accountStats: async () => { reads++; return stats(1, 12) }
      })
      await h.controller.start(); assert.equal(h.timers.size, 2)
      h.controller.stop(); assert.equal(h.timers.size, 0)
      await h.controller.start()
      assert.equal(h.timers.size, 2, 'restart must restore stats timer even without a newer embedded snapshot')
      await h.controller.refreshAccounts()
      assert.equal(h.timers.size, 2, 'account refresh must not duplicate the restored timer')
      await h.tick(); assert.equal(reads, 1)
      assert.equal(h.state.stats['account:1'].syncStatus, 'SUCCESS')
      assert.equal(h.state.stats['account:1'].metrics[0].value, 12)
      assert.equal(h.timers.size, 1)
      h.controller.stop(); assert.equal(h.timers.size, 0)
    })
  }
}

for (const completion of ['SUCCESS', 'PARTIAL_SUCCESS', 'FAILED']) {
  test(`account page accepts same-attempt ${completion} after PROCESSING and clears its timer`, async () => {
    let snapshot = stats(1, 4, 'PROCESSING'), reads = 0
    const h = setup({
      accountPage: async () => ({ list: [{ ...accounts[0], stats: snapshot }], total: 1 }),
      accountStats: async () => { reads++; return stats(1, 99, 'PROCESSING') }
    })
    await h.controller.start(); assert.equal(h.timers.size, 2)
    snapshot = stats(1, completion === 'FAILED' ? null : 12, completion)
    if (completion === 'FAILED') { snapshot.metrics[0].availability = 'ERROR'; snapshot.errorMessage = '平台读取失败' }
    await h.controller.refreshAccounts()
    assert.equal(h.state.stats['account:1'].syncStatus, completion)
    assert.equal(h.state.stats['account:1'].metrics[0].value, completion === 'FAILED' ? 4 : 12)
    assert.equal(h.timers.size, 1)
    await h.tick(); assert.equal(reads, 0, 'completed snapshots must cancel the old stats timer')
    h.controller.stop()
  })
}

test('delayed same-attempt completion cannot overwrite a newer statistics read failure', async () => {
  const slow = deferred(); let pages = 0
  const h = setup({
    accountPage: async () => ++pages === 1 ? { list: [{ ...accounts[0], stats: stats(1, 4, 'PROCESSING') }], total: 1 } : slow.promise,
    accountStats: async () => { throw new Error('newer read failure') }
  })
  await h.controller.start()
  const page = h.controller.refreshAccounts()
  await h.controller.refreshStats('account', 1)
  slow.resolve({ list: [{ ...accounts[0], stats: stats(1, 12) }], total: 1 }); await page
  assert.equal(h.state.stats['account:1'].syncStatus, 'FAILED')
  assert.equal(h.state.stats['account:1'].metrics[0].value, 4)
  assert.equal(h.state.stats['account:1'].metrics[0].stale, true)
  assert.equal(h.state.statsErrors['account:1'], 'newer read failure')
  assert.equal(h.timers.size, 1); h.controller.stop()
})

test('completed authorization refreshes statistics even for an existing cached account row', async () => {
  let reads = 0
  const h = setup({ session: async () => ({ status: 'SUCCESS' }),
    accountStats: async id => { reads++; return stats(id, 23) } })
  await h.controller.start()
  h.state.stats['account:1'] = stats(1, 7, 'FAILED')
  await h.controller.connect('INSTAGRAM'); await h.tick()
  assert.equal(h.state.stats['account:1'].metrics[0].value, 23)
  assert.equal(h.state.stats['account:1'].syncStatus, 'SUCCESS')
  assert.equal(reads, 3); h.controller.stop()
})

test('drawer selection rejects stale reads and closing disposes its polling', async () => {
  const slow = deferred()
  const h = setup({ detailStats: id => id === 1 ? slow.promise : Promise.resolve(stats(id, 9, 'PROCESSING')) })
  await h.controller.start()
  const old = h.controller.openStats('detail', 1)
  await h.controller.openStats('detail', 2)
  slow.resolve(stats(1, 99)); await old
  assert.equal(h.state.statsTarget.id, 2); assert.equal(h.state.stats['detail:2'].metrics[0].value, 9)
  assert.equal(h.state.stats['detail:1'], undefined)
  h.controller.closeStats(); assert.equal(h.timers.size, 1)
  h.controller.stop(); assert.equal(h.timers.size, 0)
})

test('video upload skips browser decoding, omits fps, waits for READY, permits 1GiB', async () => {
  let body
  const h = setup({ capabilities: async () => ({ enabled: true, mediaTypes: ['VIDEO'], platforms: ['INSTAGRAM'], maxVideoBytes: 1024 ** 3 }),
    videoUploadComplete: async data => { body = data; return { id: 43, mediaType: 'VIDEO', status: 'PROCESSING', metadataStatus: 'PENDING' } } })
  await h.controller.start(); Object.assign(h.state.draft, { title: 'Video', accountIds: [1] })
  await h.controller.upload({ name: 'clip.mp4', type: 'video/mp4', size: 1024 ** 3 })
  assert.equal(Object.hasOwn(body, 'frameRate'), false)
  await assert.rejects(h.controller.submit(), /检测|READY/)
  await h.tick(); await h.controller.submit()
  assert.equal(h.calls.filter(([name]) => name === 'create').length, 1)
  h.controller.stop(); assert.equal(h.timers.size, 0)
})

test('failed detector blocks publish and exposes error', async () => {
  const h = setup({ videoUploadComplete: async () => ({ id: 43, mediaType: 'VIDEO', status: 'PROCESSING' }),
    mediaGet: async () => ({ id: 43, mediaType: 'VIDEO', status: 'FAILED', metadataStatus: 'FAILED', metadataError: 'No video stream' }) })
  await h.controller.start(); Object.assign(h.state.draft, { title: 'Video', accountIds: [1] })
  await h.controller.upload({ name: 'bad.mp4', type: 'video/mp4', size: 100 }); await h.tick()
  assert.equal(h.state.media.metadataError, 'No video stream')
  await assert.rejects(h.controller.submit(), /No video stream|检测/)
  assert.equal(h.timers.size, 1); h.controller.stop()
})

test('media selection change and stop invalidate upload completion and poll results', async () => {
  for (const phase of ['upload', 'poll']) {
    const slow = deferred()
    const h = setup({ videoUploadComplete: () => phase === 'upload' ? slow.promise : Promise.resolve({ id: 43, mediaType: 'VIDEO', status: 'PROCESSING' }), mediaGet: () => slow.promise })
    await h.controller.start()
    const upload = h.controller.upload({ name: 'clip.mp4', type: 'video/mp4', size: 100 })
    await new Promise(setImmediate)
    const pending = phase === 'upload' ? upload : h.tick()
    await new Promise(setImmediate)
    h.controller.selectGenerated(71)
    slow.resolve({ id: 43, mediaType: 'VIDEO', status: 'READY', metadataStatus: 'READY' }); await pending
    assert.equal(h.state.media, undefined); assert.equal(h.state.draft.generationTaskId, 71)
    h.controller.stop(); assert.equal(h.timers.size, 0)
  }
})

function setup(overrides = {}, permitted = () => true) {
  const { createMetaPublishController } = load('src/views/tk/video-publish-center/components/metaPublishController.ts', {
    '@/api/tk/socialPublish': { uploadSocialVideoToOss: async (_session, _file, progress) => progress?.(100) }
  })
  const calls = [], timers = new Map(), notices = []
  let timerId = 0, now = 0
  const popup = { closed: false, location: { href: '' }, close() { this.closed = true }, opener: {} }
  const api = {
    capabilities: async () => ({ enabled: true, mediaTypes: ['IMAGE', 'VIDEO'], platforms: ['INSTAGRAM', 'FACEBOOK_PAGE'] }),
    accountPage: async () => ({ list: accounts, total: 3 }),
    accountStats: async id => stats(id),
    taskPage: async () => ({ list: [], total: 0 }),
    detailPage: async () => ({ list: [], total: 0 }),
    authorize: async (platform) => { calls.push(['authorize', platform]); return { sessionId: 'session-A', authorizeUrl: 'https://example.com/oauth' } },
    session: async (id) => { calls.push(['session', id]); return { status: 'PENDING' } },
    facebookPages: async () => [{ id: 'page-A', name: 'A', tasks: ['CREATE_CONTENT'] }],
    bindPages: async (data) => { calls.push(['bind', data]); return true },
    upload: async () => ({ id: 42, fileName: 'photo.jpg', publicUrl: 'https://example.com/image.jpg', mediaType: 'IMAGE', fileSize: 100, status: 'READY' }),
    videoUploadSession: async () => ({ uploadId: 'upload-1', objectKey: 'video/1.mp4' }),
    videoUploadComplete: async () => ({ id: 43, mediaType: 'VIDEO', fileName: 'clip.mp4', status: 'READY', metadataStatus: 'VERIFIED' }),
    mediaGet: async () => ({ id: 43, mediaType: 'VIDEO', status: 'READY', metadataStatus: 'VERIFIED' }),
    create: async (data) => { calls.push(['create', data]); return 91 },
    retry: async (id) => { calls.push(['retry', id]); return true },
    sync: async (id) => { calls.push(['sync', id]); return true },
    validate: async (id) => { calls.push(['validate', id]); return true },
    unbind: async (id) => { calls.push(['unbind', id]); return true },
    delete: async (id) => { calls.push(['delete', id]); return true },
    ...overrides
  }
  const controller = createMetaPublishController(api, permitted, {
    openPopup: () => { calls.push(['popup']); return popup },
    setTimeout: (fn) => { timers.set(++timerId, fn); return timerId },
    clearTimeout: (id) => timers.delete(id), now: () => now,
    idempotencyKey: () => `key-${++timerId}`,
    notify: (text) => notices.push(text)
  })
  return { controller, state: controller.state, calls, popup, timers, notices,
    advance: (ms) => { now += ms },
    tick: async () => { const current = [...timers.values()]; timers.clear(); for (const fn of current) await fn() }
  }
}

test('API uses unwrapped payloads, exact routes and browser multipart boundary', async () => {
  const requests = []
  const request = Object.fromEntries(['get', 'post', 'delete'].map((method) => [method, async (args) => {
    requests.push({ method, ...args }); return args.url.endsWith('/create') ? 91 : { list: [], total: 0 }
  }]))
  const { SocialPublishApi: api } = load('src/api/tk/socialPublish/index.ts', { '@/config/axios': { __esModule: true, default: request } })
  await api.authorize('INSTAGRAM'); await api.authorize('FACEBOOK_PAGE')
  await api.session('session-A'); await api.facebookPages('session-A')
  await api.bindPages({ sessionId: 'session-A', pageIds: ['page-A'] })
  await api.accountPage({ pageNo: 1, pageSize: 100 })
  await api.validate(1); await api.unbind(1); await api.delete(1)
  await api.upload(new Blob(['jpeg'], { type: 'image/jpeg' }))
  assert.equal(await api.create({ accountIds: [2], title: 'Text', facebookMessage: 'Hi', idempotencyKey: 'key' }), 91)
  await api.taskPage({ pageNo: 1, pageSize: 10 })
  await api.detailPage({ pageNo: 1, pageSize: 10, taskId: 91 })
  await api.retry(9); await api.sync(91)
  assert.deepEqual(requests.map(({ method, url }) => `${method} ${url}`), [
    'post /tk/social-auth/instagram/redirect-url', 'post /tk/social-auth/facebook/redirect-url',
    'get /tk/social-auth/session', 'get /tk/social-account/facebook-pages',
    'post /tk/social-account/facebook-pages/bind', 'get /tk/social-account/page',
    'post /tk/social-account/validate', 'delete /tk/social-account/unbind', 'delete /tk/social-account/delete',
    'post /tk/social-publish/media/upload', 'post /tk/social-publish/create',
    'get /tk/social-publish/task-page', 'get /tk/social-publish/detail-page',
    'post /tk/social-publish/retry', 'post /tk/social-publish/status/sync'
  ])
  assert.deepEqual(requests[4].data, { sessionId: 'session-A', pageIds: ['page-A'] })
  assert.ok(requests[9].data instanceof FormData)
  assert.ok(requests[9].data.get('file'))
  assert.equal(requests[9].headersType, undefined)
  assert.equal(requests[10].timeout, 180000, 'snapshotting a generated video can take as long as upload')
  assert.deepEqual(requests[12].params, { pageNo: 1, pageSize: 10, taskId: 91 })
  assert.deepEqual(requests[13].params, { detailId: 9 })
  await api.capabilities()
  assert.equal(requests.at(-1).url, '/tk/social-publish/capabilities')
})

test('capabilities gates every table query and action while configuration is disabled', async () => {
  const order = []
  const h = setup({
    capabilities: async () => { order.push('capabilities'); return { enabled: false, mediaTypes: ['IMAGE'], platforms: ['INSTAGRAM', 'FACEBOOK_PAGE'] } },
    accountPage: async () => { order.push('accounts'); return { list: [], total: 0 } },
    taskPage: async () => { order.push('tasks'); return { list: [], total: 0 } }
  })
  await h.controller.start()
  assert.deepEqual(order, ['capabilities'])
  assert.equal(h.state.enabled, false)
  assert.equal(h.timers.size, 0)
  await assert.rejects(h.controller.connect('INSTAGRAM'), /配置/)
  await assert.rejects(h.controller.submit(), /配置/)
  h.controller.stop()
})

test('mixed targets keep independent copy and create asynchronously using mediaId', async () => {
  const h = setup(); await h.controller.start()
  Object.assign(h.state.draft, { accountIds: [1, 2, 3], title: 'Campaign', instagramCaption: 'IG caption', facebookMessage: 'FB message' })
  await assert.rejects(h.controller.submit(), /媒体|视频/)
  await h.controller.upload({ type: 'image/jpeg', size: 100 })
  await h.controller.submit()
  const payload = h.calls.find(([name]) => name === 'create')[1]
  assert.deepEqual(payload.accountIds, [1, 2, 3])
  assert.equal(payload.instagramCaption, 'IG caption'); assert.equal(payload.facebookMessage, 'FB message')
  assert.equal(payload.mediaId, 42); assert.equal(payload.mediaUrl, undefined)
  assert.equal(h.state.lastTaskId, 91); assert.equal(h.state.detailTaskId, 91)
  assert.equal(h.state.submitting, false)
  h.controller.stop(); assert.equal(h.timers.size, 0)
})

test('Facebook text-only requires text; invalid JPEG and oversize upload never reach server', async () => {
  let uploads = 0
  const h = setup({ upload: async () => { uploads++; return { id: 42 } } }); await h.controller.start()
  Object.assign(h.state.draft, { accountIds: [2], title: 'FB', facebookMessage: ' ' })
  await assert.rejects(h.controller.submit(), /正文/)
  h.state.draft.facebookMessage = 'Hello'
  await h.controller.submit()
  for (const file of [{ type: 'video/webm', size: 100 }, { type: 'image/png', size: 100 }, { type: 'image/jpeg', size: 8 * 1024 * 1024 + 1 }]) {
    await assert.rejects(h.controller.upload(file), /JPEG|8 MB/)
  }
  assert.equal(uploads, 0); h.controller.stop()
})

test('MP4 video and generated video are exclusive sources; capabilities rejects unsupported types', async () => {
  const h = setup({ upload: async () => ({ id: 43, mediaType: 'VIDEO', fileName: 'clip.mp4' }) })
  await h.controller.start(); Object.assign(h.state.draft, { accountIds: [1, 2], title: 'Video' })
  await h.controller.upload({ name: 'clip.mp4', type: 'video/mp4', size: 100 })
  await h.controller.submit()
  assert.equal(h.calls.find(([name]) => name === 'create')[1].mediaId, 43)
  h.controller.selectGenerated(71)
  await h.controller.submit()
  const payload = h.calls.filter(([name]) => name === 'create').at(-1)[1]
  assert.equal(payload.generationTaskId, 71); assert.equal(payload.mediaId, undefined)
  await assert.rejects(h.controller.upload({ type: 'video/mp4', size: 100 * 1024 * 1024 + 1 }), /100 MB/)
  h.state.mediaTypes = ['IMAGE']
  await assert.rejects(h.controller.submit(), /支持/)
  await assert.rejects(h.controller.upload({ type: 'video/mp4', size: 100 }), /支持/)
  h.controller.stop()
})

test('lost create response reuses idempotency key and blocks overlapping submissions', async () => {
  const result = deferred(), payloads = []
  const h = setup({ create: (data) => { payloads.push(data); return payloads.length === 1 ? result.promise : Promise.resolve(91) } })
  await h.controller.start(); Object.assign(h.state.draft, { accountIds: [2], title: 'Post', facebookMessage: 'Hi' })
  const first = h.controller.submit()
  await h.controller.submit(); assert.equal(payloads.length, 1)
  result.reject(new Error('network')); await assert.rejects(first, /network/)
  await h.controller.submit()
  assert.equal(payloads[0].idempotencyKey, payloads[1].idempotencyKey)
  h.controller.stop()
})

test('popup opens synchronously; closing it does not discard server session completion', async () => {
  const auth = deferred()
  const h = setup({ authorize: () => auth.promise, session: async () => ({ status: 'SUCCESS' }) })
  await h.controller.start(); const pending = h.controller.connect('INSTAGRAM')
  assert.equal(h.calls.at(-1)[0], 'popup')
  h.popup.closed = true
  auth.resolve({ sessionId: 'session-A', authorizeUrl: 'https://example.com/oauth' }); await pending
  await h.tick(); assert.equal(h.state.auth.status, 'SUCCESS')
  h.controller.stop(); assert.equal(h.timers.size, 0)
})

test('Page picker only binds server candidates from the current session', async () => {
  const h = setup({ session: async () => ({ status: 'PAGES_READY' }) })
  await h.controller.start(); await h.controller.connect('FACEBOOK_PAGE'); await h.tick()
  assert.equal(h.state.pages[0].id, 'page-A')
  h.state.pageIds = ['forged-page']; await assert.rejects(h.controller.bindPages(), /主页/)
  h.state.pageIds = ['page-A']; await h.controller.bindPages()
  assert.deepEqual(h.calls.find(([name]) => name === 'bind')[1], { sessionId: 'session-A', pageIds: ['page-A'] })
  h.controller.stop()
})

test('stop invalidates an in-flight auth response and prevents timer resurrection', async () => {
  const result = deferred()
  const h = setup({ session: () => result.promise }); await h.controller.start()
  await h.controller.connect('INSTAGRAM'); const tick = h.tick()
  h.controller.stop(); result.resolve({ status: 'PAGES_READY' }); await tick
  assert.equal(h.state.pages.length, 0); assert.equal(h.timers.size, 0)
  assert.equal(h.popup.closed, true)
})

test('auth polling expires locally instead of leaking indefinitely', async () => {
  const h = setup(); await h.controller.start(); await h.controller.connect('INSTAGRAM')
  h.advance(11 * 60 * 1000); await h.tick()
  assert.equal(h.state.auth.status, 'EXPIRED')
  h.controller.stop(); assert.equal(h.timers.size, 0)
})

test('only FAILED and REAUTH_REQUIRED details can retry; UNKNOWN requires manual verification', async () => {
  const h = setup(); await h.controller.start()
  for (const status of ['UNKNOWN', 'SUCCESS', 'PROCESSING', 'PENDING']) {
    await assert.rejects(h.controller.retry({ id: 7, status }), /重试|核验/)
  }
  assert.equal(h.calls.filter(([name]) => name === 'retry').length, 0)
  await h.controller.retry({ id: 8, status: 'FAILED' })
  await h.controller.retry({ id: 9, status: 'REAUTH_REQUIRED' })
  assert.deepEqual(h.calls.filter(([name]) => name === 'retry'), [['retry', 8], ['retry', 9]])
  h.controller.stop()
})

test('permissions guard requests as well as controls', async () => {
  let queries = 0
  const h = setup({ accountPage: async () => { queries++; return { list: [], total: 0 } }, taskPage: async () => { queries++; return { list: [], total: 0 } } }, () => false)
  await h.controller.start(); assert.equal(queries, 0)
  await assert.rejects(h.controller.connect('INSTAGRAM'), /权限/)
  await assert.rejects(h.controller.submit(), /权限/)
  await assert.rejects(h.controller.upload({ type: 'image/jpeg', size: 100 }), /权限/)
  await assert.rejects(h.controller.retry({ id: 1, status: 'FAILED' }), /权限/)
  await assert.rejects(h.controller.accountAction('unbind', 1), /权限/)
  assert.equal(h.calls.length, 0); h.controller.stop()
})

test('server title, caption and target limits are validated before create', async () => {
  const h = setup(); await h.controller.start()
  Object.assign(h.state.draft, { accountIds: [2], title: 'x'.repeat(256), facebookMessage: 'Hi' })
  await assert.rejects(h.controller.submit(), /255/)
  h.state.draft.title = 'Post'; h.state.draft.instagramCaption = 'x'.repeat(2201)
  await assert.rejects(h.controller.submit(), /2200/)
  h.state.draft.instagramCaption = ''; h.state.draft.facebookMessage = 'x'.repeat(5001)
  await assert.rejects(h.controller.submit(), /5000/)
  h.state.draft.facebookMessage = 'Hi'; h.state.draft.accountIds = Array.from({ length: 21 }, (_, i) => i + 1)
  await assert.rejects(h.controller.submit(), /20/)
  assert.equal(h.calls.filter(([name]) => name === 'create').length, 0)
  h.controller.stop()
})

test('a delayed detail response cannot replace the newly selected task', async () => {
  const old = deferred()
  const h = setup({ detailPage: ({ taskId }) => taskId === 1 ? old.promise : Promise.resolve({ list: [{ id: 22, publishTaskId: 2, status: 'PROCESSING' }], total: 1 }) })
  await h.controller.start()
  const first = h.controller.openDetails(1)
  await h.controller.openDetails(2)
  old.resolve({ list: [{ id: 11, publishTaskId: 1, status: 'SUCCESS' }], total: 1 })
  await first
  assert.equal(h.state.details[0].publishTaskId, 2)
  h.controller.stop()
})

test('a created task remains accepted when its first status query fails', async () => {
  let failRead = false, creates = 0
  const h = setup({
    create: async () => { failRead = true; creates++; return 91 },
    detailPage: async () => { if (failRead) throw new Error('offline'); return { list: [], total: 0 } }
  })
  await h.controller.start(); Object.assign(h.state.draft, { accountIds: [2], title: 'Post', facebookMessage: 'Hi' })
  await h.controller.submit()
  assert.equal(creates, 1); assert.equal(h.state.lastTaskId, 91)
  assert.match(h.state.pollError, /已创建/)
  h.controller.stop()
})

test('AUTHORIZED accounts are selectable while expired or unbound accounts reject create', async () => {
  const h = setup({ accountPage: async () => ({ list: [{ ...accounts[1], status: 'AUTHORIZED' }], total: 1 }) })
  await h.controller.start(); Object.assign(h.state.draft, { accountIds: [2], title: 'Post', facebookMessage: 'Hi' })
  await h.controller.submit()
  assert.equal(h.state.lastTaskId, 91)
  h.state.accounts[0].status = 'UNBOUND'
  await assert.rejects(h.controller.submit(), /不可用/)
  h.controller.stop()
})

test('polling waits for both requests after one rejects, preventing overlapping status reads', async () => {
  const slow = deferred()
  let taskReads = 0
  const h = setup({
    taskPage: async () => { if (++taskReads > 1) throw new Error('offline'); return { list: [], total: 0 } },
    detailPage: () => slow.promise
  })
  await h.controller.start(); h.state.detailTaskId = 91
  const pending = h.tick()
  await new Promise(setImmediate)
  assert.equal(h.timers.size, 0, 'do not schedule next poll while a detail read is in flight')
  slow.resolve({ list: [], total: 0 }); await pending
  assert.equal(h.timers.size, 1)
  h.controller.stop()
})

test('capabilities maxVideoBytes takes precedence over legacy MB configuration', async () => {
  let uploads = 0
  const h = setup({
    capabilities: async () => ({ enabled: true, mediaTypes: ['IMAGE', 'VIDEO'], platforms: ['INSTAGRAM', 'FACEBOOK_PAGE'], maxVideoBytes: 10 * 1024 * 1024, maxVideoSizeMb: 100 }),
    videoUploadComplete: async () => { uploads++; return { id: 43, mediaType: 'VIDEO', status: 'READY', metadataStatus: 'VERIFIED', width: 1080, height: 1920, durationSeconds: 30, frameRate: 30 } }
  })
  await h.controller.start()
  await assert.rejects(h.controller.upload({ type: 'video/mp4', size: 10 * 1024 * 1024 + 1 }), /10 MB/)
  assert.equal(uploads, 0)
  await h.controller.upload({ type: 'video/mp4', size: 100 })
  assert.equal(h.state.media.width, 1080); assert.equal(h.state.media.frameRate, 30)
  h.controller.stop()
})

for (const failedRead of ['accountPage', 'taskPage']) {
  test(`initial ${failedRead} failure reconnects and manual refresh keeps one timer`, async () => {
    let reads = 0
    const h = setup({ [failedRead]: async () => {
      if (++reads === 1) throw new Error('temporary initial read failure')
      return failedRead === 'accountPage' ? { list: accounts, total: 3 } : { list: [{ id: 91, status: 'PROCESSING' }], total: 1 }
    } })
    await h.controller.start()
    assert.equal(h.state.enabled, true)
    assert.equal(h.timers.size, 1, 'enabled panel must reconnect after its initial read fails')
    assert.equal(h.state.loadError, '', 'a data read failure must not be reported as a capabilities failure')
    assert.match(h.state.pollError, /重连|刷新/)
    await h.tick()
    assert.equal(reads, 2)
    assert.equal(h.state.pollError, '')
    assert.equal(h.timers.size, 1)
    await h.controller.refreshAccounts(); await h.controller.refreshTasks()
    assert.equal(h.timers.size, 1, 'manual refresh must not duplicate or remove the reconnect timer')
    await h.tick()
    assert.equal(h.timers.size, 1)
    h.controller.stop(); assert.equal(h.timers.size, 0)
  })
}

test('initial rejected read waits for the other read before scheduling reconnect', async () => {
  const slow = deferred()
  const h = setup({ accountPage: async () => { throw new Error('offline') }, taskPage: () => slow.promise })
  const start = h.controller.start()
  await new Promise(setImmediate)
  assert.equal(h.timers.size, 0)
  slow.resolve({ list: [], total: 0 }); await start
  assert.equal(h.timers.size, 1)
  h.controller.stop()
})

test('stopping during failed initialization cannot resurrect reconnect timers', async () => {
  const slow = deferred()
  const h = setup({ accountPage: async () => { throw new Error('offline') }, taskPage: () => slow.promise })
  const start = h.controller.start()
  await new Promise(setImmediate)
  h.controller.stop(); slow.resolve({ list: [], total: 0 }); await start
  assert.equal(h.timers.size, 0)
})

test('disabled, unauthorized and failed capabilities never schedule table reconnects even on manual refresh', async () => {
  for (const mode of ['disabled', 'unauthorized', 'capabilities-failed']) {
    let tableReads = 0, capabilityReads = 0
    const h = setup({
      capabilities: async () => { capabilityReads++; if (mode === 'capabilities-failed') throw new Error('config unavailable'); return { enabled: false, mediaTypes: [], platforms: [] } },
      accountPage: async () => { tableReads++; return { list: [], total: 0 } },
      taskPage: async () => { tableReads++; return { list: [], total: 0 } }
    }, () => mode !== 'unauthorized')
    await h.controller.start(); await h.controller.refreshAccounts(); await h.controller.refreshTasks()
    assert.equal(tableReads, 0); assert.equal(h.timers.size, 0)
    assert.equal(capabilityReads, mode === 'unauthorized' ? 0 : 1)
    h.controller.stop()
  }
})
