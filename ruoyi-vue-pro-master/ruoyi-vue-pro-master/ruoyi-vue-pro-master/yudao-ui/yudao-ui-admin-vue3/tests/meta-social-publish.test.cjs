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
    (name) => imports[name] || require(name), module, module.exports
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

function setup(overrides = {}, permitted = () => true) {
  const { createMetaPublishController } = load('src/views/tk/video-publish-center/components/metaPublishController.ts')
  const calls = [], timers = new Map(), notices = []
  let timerId = 0, now = 0
  const popup = { closed: false, location: { href: '' }, close() { this.closed = true }, opener: {} }
  const api = {
    capabilities: async () => ({ enabled: true, mediaTypes: ['IMAGE', 'VIDEO'], platforms: ['INSTAGRAM', 'FACEBOOK_PAGE'] }),
    accountPage: async () => ({ list: accounts, total: 3 }),
    taskPage: async () => ({ list: [], total: 0 }),
    detailPage: async () => ({ list: [], total: 0 }),
    authorize: async (platform) => { calls.push(['authorize', platform]); return { sessionId: 'session-A', authorizeUrl: 'https://example.com/oauth' } },
    session: async (id) => { calls.push(['session', id]); return { status: 'PENDING' } },
    facebookPages: async () => [{ id: 'page-A', name: 'A', tasks: ['CREATE_CONTENT'] }],
    bindPages: async (data) => { calls.push(['bind', data]); return true },
    upload: async () => ({ id: 42, fileName: 'photo.jpg', publicUrl: 'https://example.com/image.jpg', mediaType: 'IMAGE', fileSize: 100, status: 'READY' }),
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
    upload: async () => { uploads++; return { id: 43, mediaType: 'VIDEO', width: 1080, height: 1920, durationSeconds: 30, frameRate: 30 } }
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
