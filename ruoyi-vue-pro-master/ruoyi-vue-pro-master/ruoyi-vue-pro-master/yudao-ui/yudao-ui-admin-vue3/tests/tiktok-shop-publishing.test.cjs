const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const ts = require('typescript')

function load(relative, imports = {}) {
  const filename = path.join(__dirname, '..', relative)
  assert.ok(fs.existsSync(filename), `Shop behavior helper missing: ${relative}`)
  const output = ts.transpileModule(fs.readFileSync(filename, 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 }
  }).outputText
  const module = { exports: {} }
  new Function('require', 'module', 'exports', output)((name) => imports[name] || require(name), module, module.exports)
  return module.exports
}

test('OAuth message guard accepts only the exact popup origin source and session', () => {
  const { acceptShopOAuthMessage } = load('src/views/tk/video-publish-center/components/tiktokShopBehavior.ts')
  const popup = {}
  const expected = { callbackOrigin: 'https://shop.example', sessionId: 42 }
  assert.equal(acceptShopOAuthMessage({ origin: 'https://shop.example', source: popup, data: { type: 'tiktok-shop-auth', sessionId: 42, status: 'SUCCESS' } }, expected, popup), true)
  assert.equal(acceptShopOAuthMessage({ origin: 'https://evil.example', source: popup, data: { type: 'tiktok-shop-auth', sessionId: 42, status: 'SUCCESS' } }, expected, popup), false)
  assert.equal(acceptShopOAuthMessage({ origin: 'https://shop.example', source: {}, data: { type: 'tiktok-shop-auth', sessionId: 42, status: 'SUCCESS' } }, expected, popup), false)
  assert.equal(acceptShopOAuthMessage({ origin: 'https://shop.example', source: popup, data: { type: 'tiktok-shop-auth', sessionId: 41, status: 'SUCCESS' } }, expected, popup), false)
  assert.equal(acceptShopOAuthMessage({ origin: 'https://shop.example', source: popup, data: { type: 'other', sessionId: 42, status: 'SUCCESS' } }, expected, popup), false)
})

test('changing immutable form inputs invalidates confirmation and idempotency', () => {
  const { createShopFormGuard } = load('src/views/tk/video-publish-center/components/tiktokShopBehavior.ts')
  const guard = createShopFormGuard()
  const first = { accountId: 1, generationTaskId: 10, title: 'hello', productId: 'p1', productLinkTitle: 'green mug', isAiGenerated: true }
  const key = guard.keyFor(first)
  guard.confirm(first)
  assert.equal(guard.canConfirm(first), true)
  assert.equal(guard.keyFor(first), key)
  assert.equal(guard.canConfirm({ ...first, title: 'changed' }), false)
  assert.notEqual(guard.keyFor({ ...first, title: 'changed' }), key)
})

test('double click submits one immutable task and unknown timeout only refreshes', async () => {
  const { createShopSubmitter } = load('src/views/tk/video-publish-center/components/tiktokShopBehavior.ts')
  let creates = 0
  let confirms = 0
  const submitter = createShopSubmitter({
    create: async () => { creates++; return { id: 7, stage: 'AWAITING_CONFIRMATION', version: 1, requestHash: 'hash' } },
    confirm: async () => { confirms++; throw new Error('timeout') },
    get: async () => ({ id: 7, stage: 'RECOVERY_REQUIRED', requestId: 'req-1' })
  })
  const form = { accountId: 1, generationTaskId: 10, title: 'hello', productId: 'p1', productLinkTitle: 'green mug', isAiGenerated: true }
  const task = await submitter.create(form)
  await Promise.all([submitter.confirm(task, form), submitter.confirm(task, form)])
  assert.equal(creates, 1)
  assert.equal(confirms, 1)
  assert.equal(submitter.task.stage, 'RECOVERY_REQUIRED')
  assert.equal(submitter.task.requestId, 'req-1')
})

test('precheck quality failure is advisory while violation failure blocks', () => {
  const { normalizeShopPrecheck } = load('src/views/tk/video-publish-center/components/tiktokShopBehavior.ts')
  const advisory = normalizeShopPrecheck({ violationStatus: 'SUCCESS', qualityStatus: 'FAIL', qualityPresent: true, qualityIssues: [{ code: 'LOW', risk: 'low', suggestions: 'fix' }] })
  assert.equal(advisory.passed, true)
  assert.equal(advisory.qualityAdvisory, true)
  const blocked = normalizeShopPrecheck({ violationStatus: 'FAIL', qualityStatus: 'SUCCESS', violationIssues: [{ code: 'BAD', risk: 'high', suggestions: 'remove' }] })
  assert.equal(blocked.passed, false)
  assert.equal(blocked.failed, true)
})

test('products reject an earlier account response after account source or filter changes', async () => {
  const { createShopRevision } = load('src/views/tk/video-publish-center/components/tiktokShopBehavior.ts')
  const revision = createShopRevision()
  const first = revision.next()
  const second = revision.next()
  assert.equal(revision.current(first), false)
  assert.equal(revision.current(second), true)
  revision.invalidate()
  assert.equal(revision.current(second), false)
})

test('draft and upload revisions reject late results after invalidation', () => {
  const { createShopRevision } = load('src/views/tk/video-publish-center/components/tiktokShopBehavior.ts')
  const gate = createShopRevision()
  const first = gate.begin()
  gate.invalidate()
  assert.equal(gate.current(first), false)
  const second = gate.begin()
  assert.equal(gate.current(second), true)
})

test('poller is nonoverlapping finite and cannot resurrect after unmount', async () => {
  const { createShopPoller } = load('src/views/tk/video-publish-center/components/tiktokShopBehavior.ts')
  const timers = new Map()
  let counter = 0, reads = 0, errors = 0, resolve
  const poller = createShopPoller(() => { reads++; return new Promise(yes => { resolve = yes }) }, () => errors++, {
    set: fn => { timers.set(++counter, fn); return counter }, clear: id => timers.delete(id)
  }, 2)
  poller.start()
  const callback = [...timers.values()][0]; timers.clear()
  const pending = callback()
  assert.equal(reads, 1); assert.equal(timers.size, 0)
  poller.stop(); resolve(true); await pending
  assert.equal(timers.size, 0)
  poller.start()
  const one = [...timers.values()][0]; timers.clear()
  const p1 = one(); resolve(true); await p1
  const two = [...timers.values()][0]; timers.clear()
  const p2 = two(); resolve(true); await p2
  assert.equal(errors, 1); assert.equal(timers.size, 0)
})

test('only SUCCESS is a successful outcome; malformed precheck cannot enable confirmation', () => {
  const { isShopTerminal, canConfirmShopTask } = load('src/views/tk/video-publish-center/components/tiktokShopBehavior.ts')
  assert.equal(isShopTerminal('PROCESSING'), false)
  assert.equal(isShopTerminal('SUCCESS'), true)
  assert.equal(canConfirmShopTask({ stage: 'AWAITING_CONFIRMATION', precheckResult: '{broken' }), false)
  assert.equal(canConfirmShopTask({ stage: 'AWAITING_CONFIRMATION', precheckResult: JSON.stringify({ violationStatus: 'SUCCESS', qualityPresent: true, qualityStatus: 'PROCESSING' }) }), false)
})

test('Shop-only startup uses narrow Shop grants and preserves existing ordinary startup', () => {
  const { shopStartup } = load('src/views/tk/video-publish-center/components/tiktokShopBehavior.ts')
  const has = grants => requested => requested.some(p => grants.includes(p))
  assert.deepEqual(shopStartup(has(['tk:video-publish-center:query', 'tk:tiktok-shop-account:query'])), { shopOnly: true, initialTab: 'shop' })
  assert.deepEqual(shopStartup(has(['tk:video-publish-center:query', 'tk:generation:query', 'tk:tiktok-shop-account:query'])), { shopOnly: false, initialTab: 'videos' })
})

test('all immutable fields rotate idempotency keys and duplicate creation shares one request', async () => {
  const { createShopFormGuard, createShopSubmitter } = load('src/views/tk/video-publish-center/components/tiktokShopBehavior.ts')
  const form = { accountId: 1, generationTaskId: 10, title: 'hello', productId: 'p1', productSource: 'SHOWCASE', productLinkTitle: 'green mug', selectionProof: 'proof', isAiGenerated: true }
  for (const changed of [{ generationTaskId: 11 }, { mediaId: 12, generationTaskId: undefined }, { productId: 'p2' }, { accountId: 2 }, { productLinkTitle: 'blue mug' }, { isAiGenerated: false }]) {
    const guard = createShopFormGuard(), key = guard.keyFor(form)
    guard.confirm(form)
    assert.equal(guard.canConfirm({ ...form, ...changed }), false)
    assert.notEqual(guard.keyFor({ ...form, ...changed }), key)
  }
  let count = 0, resolve
  const submitter = createShopSubmitter({ create: () => { count++; return new Promise(yes => { resolve = yes }) } })
  const first = submitter.create(form), second = submitter.create(form)
  assert.equal(count, 1)
  resolve({ id: 8 }); await Promise.all([first, second])
})

test('caption uses UTF16 limit and anchor uses Unicode codepoints without emoji or punctuation', () => {
  const { validateShopForm } = load('src/views/tk/video-publish-center/components/tiktokShopBehavior.ts')
  const form = { accountId: 1, mediaId: 4, title: 'a'.repeat(2200), productId: 'p', selectionProof: 'proof', productLinkTitle: '绿茶 123', isAiGenerated: false }
  assert.equal(validateShopForm(form), undefined)
  assert.equal(validateShopForm({ ...form, title: '😀'.repeat(1101) }), 'shop.captionInvalid')
  for (const anchor of ['a'.repeat(30), 'green-mug', 'mug😀', '   ']) assert.equal(validateShopForm({ ...form, productLinkTitle: anchor }), 'shop.anchorInvalid')
})

test('Shop API uses only frozen Shop endpoints and multipart field file', async () => {
  const calls = []
  const request = Object.fromEntries(['get', 'post', 'delete'].map(method => [method, async args => { calls.push({ method, ...args }); return {} }]))
  const { TiktokShopApi: api } = load('src/api/tk/tiktokShop/index.ts', { '@/config/axios': { __esModule: true, default: request } })
  await api.capabilities(); await api.products(3, { source: 'SHOWCASE', pageSize: 20 })
  await api.confirm(7, { confirm: true, version: 1, requestHash: 'h' })
  await api.uploadChunk('u', 0, new Blob(['abc']), new AbortController().signal)
  assert.equal(calls[0].url, '/tk/tiktok-shop/capabilities')
  assert.equal(calls[1].url, '/tk/tiktok-shop/accounts/3/products')
  assert.deepEqual(calls[2].data, { confirm: true, version: 1, requestHash: 'h' })
  assert.equal(calls[2].skipAuthReplay, true)
  assert.equal(calls[3].data.get('file').size, 3)
  assert.equal(calls[3].data.get('chunk'), null)
  assert.equal(calls[3].params.chunkIndex, 0)
})

test('canceling Shop upload aborts transport and rejects late completion media id', async () => {
  let completeResolve, canceled = 0, lastSignal
  const api = {
    createMediaSession: async () => ({ uploadId: 'u' }),
    completeMedia: async (_id, signal) => { lastSignal = signal; return new Promise(yes => { completeResolve = yes }) },
    cancelMedia: async () => { canceled++ }
  }
  const { createShopUpload } = load('src/utils/tiktokShopUpload.ts', {
    '@/api/tk/tiktokShop': { TiktokShopApi: api },
    '@/utils/tkChunkUpload': { uploadFileInChunks: async (_file, options) => {
      const session = await options.createSession()
      return { result: await options.complete(session.uploadId) }
    } },
    axios: { __esModule: true, default: {} }
  })
  const upload = createShopUpload({ name: 'a.mp4', size: 3, type: 'video/mp4' })
  await new Promise(setImmediate)
  upload.cancel()
  assert.equal(lastSignal.aborted, true)
  completeResolve({ id: 99, status: 'READY' })
  await assert.rejects(upload.result, /CANCELLED/)
  assert.equal(canceled, 1)
})

function deferred() {
  let resolve, reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}

function componentScript(name, names, imports = {}, globals = {}) {
  const source = fs.readFileSync(path.join(__dirname, '../src/views/tk/video-publish-center/components', name), 'utf8')
  const script = source.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1]
  const output = ts.transpileModule(`${script}\nexport { ${names.join(', ')} }`, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 }
  }).outputText
  const module = { exports: {} }
  new Function('require', 'module', 'exports', ...Object.keys(globals), output)(
    name => imports[name] || (name.endsWith('.vue') ? {} : require(name)), module, module.exports, ...Object.values(globals))
  return module.exports
}

function panelHarness(overrides = {}, grants = ['*']) {
  const vue = require('vue'), timers = new Map(), listeners = new Set(), cleanups = [], calls = []
  let timerId = 0
  const window = {
    setTimeout: fn => { timers.set(++timerId, fn); return timerId },
    clearTimeout: id => timers.delete(id),
    addEventListener: (_type, fn) => listeners.add(fn),
    removeEventListener: (_type, fn) => listeners.delete(fn),
    open: () => ({ location: { href: 'about:blank' }, closed: false, close() { this.closed = true } })
  }
  const configured = { enabled: true, configured: true, publishingConfigured: true, callbackOrigin: 'https://shop.example', maxVideoBytes: 500 * 1024 ** 2, supportedVideoFormats: ['MP4', 'MOV', 'WEBM'] }
  const api = {
    capabilities: async () => configured,
    accounts: async () => [], tasks: async () => ({ list: [], total: 0 }),
    create: async () => { calls.push('create'); return { id: 7, stage: 'CREATED' } },
    get: async id => ({ id, stage: 'PROCESSING' }), confirm: async () => ({ id: 7, stage: 'PROCESSING' }),
    authorize: async () => ({ sessionId: 42, authorizationUrl: 'https://services.tiktokshop.com/open/authorize' }),
    disconnect: async () => {}, ...overrides
  }
  const helper = load('src/views/tk/video-publish-center/components/tiktokShopBehavior.ts')
  const exposed = componentScript('TiktokShopPublishPanel.vue', [
    'capabilities', 'accountId', 'accounts', 'activeTask', 'form', 'mediaMode', 'history', 'submitted', 'error',
    'load', 'loadAccounts', 'loadHistory', 'connect', 'onOAuth', 'resetDependent', 'resetProduct', 'invalidate',
    'createTask', 'confirmTask', 'selectHistory', 'upload', 'cancelUpload', 'poller', 'selectedAccount'
  ], {
    vue: { ...vue, onMounted: () => {}, onBeforeUnmount: fn => cleanups.push(fn) },
    'element-plus': { ElMessageBox: { confirm: async () => {} } },
    '@/api/tk/tiktokShop': { TiktokShopApi: api },
    '@/api/tk/generation': { TkGenerationApi: { getGenerationPage: async () => { calls.push('generation'); return { list: [] } } } },
    './tiktokShopBehavior': helper,
    '@/utils/tiktokShopUpload': { createShopUpload: overrides.createShopUpload || (() => { throw new Error('unexpected upload') }) },
    '@/hooks/web/useTkI18n': { useTkI18n: () => ({ tt: key => key }) },
    '@/directives/permission/hasPermi': { hasPermission: requested => grants.includes('*') || requested.some(p => grants.includes(p)) }
  }, { window })
  if (!overrides.capabilities) exposed.capabilities.value = configured
  return { ...exposed, window, timers, listeners, calls, api, configured, stop: () => cleanups.forEach(fn => fn()) }
}

test('actual panel does not query disabled capabilities or send unauthorized mutations', async () => {
  let reads = 0
  const panel = panelHarness({ capabilities: async () => ({ enabled: false, configured: false }), accounts: async () => { reads++; return [] }, tasks: async () => { reads++; return { list: [] } } }, ['tk:tiktok-shop-account:query'])
  await panel.load()
  assert.equal(reads, 0)
  Object.assign(panel.form, { accountId: 1, mediaId: 4, title: 'hi', productId: 'p', productSource: 'SHOP', selectionProof: 'proof', productLinkTitle: 'Mug', isAiGenerated: false })
  panel.accountId.value = 1
  await panel.createTask()
  assert.equal(panel.calls.includes('create'), false)
  await panel.connect()
  assert.equal(panel.listeners.size, 0)
  panel.stop()
})

test('actual panel clears parent product proof and ignores create after a draft edit', async () => {
  const pending = deferred(), panel = panelHarness({ create: () => pending.promise })
  panel.accountId.value = 1
  Object.assign(panel.form, { mediaId: 4, title: 'hi', productId: 'p', selectionProof: 'proof', productLinkTitle: 'Mug', isAiGenerated: false })
  const creating = panel.createTask()
  panel.resetProduct()
  assert.equal(panel.form.productId, undefined)
  assert.equal(panel.form.selectionProof, undefined)
  pending.resolve({ id: 7, stage: 'AWAITING_CONFIRMATION' })
  await creating
  assert.equal(panel.activeTask.value, undefined)
  assert.equal(panel.timers.size, 0)
  panel.stop()
})

test('actual panel stops and fences a pending poll when the draft is invalidated', async () => {
  const pending = deferred(), panel = panelHarness({ get: () => pending.promise })
  panel.activeTask.value = { id: 7, stage: 'PROCESSING' }
  panel.poller.start()
  const callback = [...panel.timers.values()][0]; panel.timers.clear()
  const polling = callback()
  panel.invalidate()
  pending.resolve({ id: 7, stage: 'SUCCESS' })
  await polling
  assert.equal(panel.activeTask.value, undefined)
  assert.equal(panel.timers.size, 0)
  panel.stop()
})

test('actual panel cancels old upload on account/media change and cannot restore its media', async () => {
  const pending = deferred(); let canceled = 0
  const panel = panelHarness({ createShopUpload: () => ({ result: pending.promise, cancel() { canceled++ } }) })
  panel.mediaMode.value = 'upload'
  const uploading = panel.upload({ target: { files: [{ name: 'a.mp4', size: 3, type: 'video/mp4' }] } })
  panel.resetDependent()
  assert.equal(canceled, 1)
  pending.resolve({ id: 99, status: 'READY' }); await uploading
  assert.equal(panel.form.mediaId, undefined)
  panel.stop()
})

test('actual panel authorizes twice, rejects unknown callback statuses and disposes pending OAuth', async () => {
  let id = 0
  const panel = panelHarness({ authorize: async () => ({ sessionId: ++id, authorizationUrl: 'https://services.tiktokshop.com/open/authorize' }) })
  const popups = []; panel.window.open = () => { const popup = { location: { href: '' }, close() {} }; popups.push(popup); return popup }
  for (let session = 1; session <= 2; session++) {
    await panel.connect()
    assert.equal(panel.listeners.size, 1)
    panel.onOAuth({ origin: 'https://shop.example', source: popups.at(-1), data: { type: 'tiktok-shop-auth', sessionId: session, status: 'PROCESSING' } })
    assert.equal(panel.listeners.size, 1)
    panel.onOAuth({ origin: 'https://shop.example', source: popups.at(-1), data: { type: 'tiktok-shop-auth', sessionId: session, status: 'SUCCESS' } })
    assert.equal(panel.listeners.size, 0)
  }
  panel.stop()
  assert.equal(panel.timers.size, 0)
})

test('actual panel history resumes polling and uses the immutable task account', async () => {
  const panel = panelHarness()
  panel.accounts.value = [{ id: 1, displayName: 'Draft' }, { id: 2, displayName: 'History' }]
  panel.accountId.value = 1
  panel.api.get = async () => ({ id: 8, accountId: 2, stage: 'PROCESSING' })
  await panel.selectHistory({ id: 8 })
  assert.ok(panel.timers.size > 0)
  assert.equal(panel.selectedAccount.value.id, 1) // Editing account remains independent.
  panel.stop()
})
