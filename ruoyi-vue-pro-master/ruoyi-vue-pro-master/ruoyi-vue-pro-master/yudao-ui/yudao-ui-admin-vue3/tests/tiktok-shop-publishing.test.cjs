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
  const { acceptShopOAuthMessage, shopAuthErrorKey } = load('src/views/tk/video-publish-center/components/tiktokShopBehavior.ts')
  const popup = {}
  const expected = { callbackOrigin: 'https://shop.example', sessionId: 42 }
  assert.equal(acceptShopOAuthMessage({ origin: 'https://shop.example', source: popup, data: { type: 'tiktok-shop-auth', sessionId: 42, status: 'SUCCESS' } }, expected, popup), true)
  assert.equal(acceptShopOAuthMessage({ origin: 'https://shop.example', source: popup, data: { type: 'tiktok-shop-auth', sessionId: 42, status: 'SUCCESS', authType: 'SELLER' } }, { ...expected, authType: 'CREATOR' }, popup), false)
  assert.equal(acceptShopOAuthMessage({ origin: 'https://shop.example', source: popup, data: { type: 'tiktok-shop-auth', sessionId: 42, status: 'SUCCESS', authType: 'SELLER' } }, { ...expected, authType: 'SELLER' }, popup), true)
  assert.equal(acceptShopOAuthMessage({ origin: 'https://evil.example', source: popup, data: { type: 'tiktok-shop-auth', sessionId: 42, status: 'SUCCESS' } }, expected, popup), false)
  assert.equal(acceptShopOAuthMessage({ origin: 'https://shop.example', source: {}, data: { type: 'tiktok-shop-auth', sessionId: 42, status: 'SUCCESS' } }, expected, popup), false)
  assert.equal(acceptShopOAuthMessage({ origin: 'https://shop.example', source: popup, data: { type: 'tiktok-shop-auth', sessionId: 41, status: 'SUCCESS' } }, expected, popup), false)
  assert.equal(acceptShopOAuthMessage({ origin: 'https://shop.example', source: popup, data: { type: 'other', sessionId: 42, status: 'SUCCESS' } }, expected, popup), false)
assert.equal(shopAuthErrorKey('INVALID_STATE'), 'shop.authorizationInvalid')
assert.equal(shopAuthErrorKey('PROVIDER_ERROR'), 'shop.authorizationProviderError')
assert.equal(shopAuthErrorKey('CONFIGURATION_CHANGED'), 'shop.authorizationConfigChanged')
assert.equal(shopAuthErrorKey('WRONG_ACCOUNT_TYPE'), 'shop.authorizationWrongAccount')
assert.equal(shopAuthErrorKey('INSUFFICIENT_SCOPE'), 'shop.authorizationInsufficientScope')
assert.equal(shopAuthErrorKey('OTHER'), 'shop.authorizationFailed')
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

function panelHarness(overrides = {}, grants = ['*'], props = {}) {
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
    'load', 'loadAccounts', 'loadHistory', 'connect', 'onOAuth', 'resetDependent', 'resetMedia', 'resetProduct', 'invalidate',
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
  }, { window, defineProps: () => props, defineEmits: () => () => {} })
  if (!overrides.capabilities) exposed.capabilities.value = configured
  return { ...exposed, window, timers, listeners, calls, api, configured, stop: () => cleanups.forEach(fn => fn()) }
}

test('workspace handoff locks the selected product to its original creator account', async () => {
  const panel = panelHarness({ accounts: async () => [{ id: 1, authStatus: 'AUTHORIZED' }, { id: 2, authStatus: 'AUTHORIZED' }] }, ['*'], {
    initialSelection: { accountId: 2, product: { id: 'p', title: 'Tea', selectionProof: 'proof' } }
  })
  await panel.loadAccounts()
  assert.equal(panel.accountId.value, 2)
  assert.equal(panel.form.productId, 'p')
  panel.mediaMode.value = 'upload'
  panel.resetMedia()
  assert.equal(panel.form.selectionProof, 'proof')
  panel.accountId.value = 1
  panel.resetDependent()
  assert.equal(panel.form.selectionProof, undefined)
  panel.stop()
})

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
  panel.accounts.value = [{ id: 1, authStatus: 'AUTHORIZED' }]
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

test('actual panel cancels old upload on media mode change and cannot restore its media', async () => {
  const pending = deferred(); let canceled = 0
  const panel = panelHarness({ createShopUpload: () => ({ result: pending.promise, cancel() { canceled++ } }) })
  panel.mediaMode.value = 'upload'
  const uploading = panel.upload({ target: { files: [{ name: 'a.mp4', size: 3, type: 'video/mp4' }] } })
  panel.resetMedia()
  assert.equal(canceled, 1)
  pending.resolve({ id: 99, status: 'READY' }); await uploading
  assert.equal(panel.form.mediaId, undefined)
  panel.stop()
})

test('actual panel keeps an in-flight upload on account change but clears the product and task', async () => {
  const pending = deferred()
  const panel = panelHarness({ createShopUpload: () => ({ result: pending.promise, cancel() { assert.fail('upload canceled') } }) })
  panel.mediaMode.value = 'upload'
  const uploading = panel.upload({ target: { files: [{ name: 'a.mp4', size: 3 }] } })
  panel.form.title = 'Prepared title'
  panel.form.selectionProof = 'old proof'
  panel.activeTask.value = { id: 7, stage: 'AWAITING_CONFIRMATION' }
  panel.resetDependent()
  pending.resolve({ id: 99 }); await uploading
  assert.equal(panel.form.mediaId, 99)
  assert.equal(panel.form.title, 'Prepared title')
  assert.equal(panel.form.selectionProof, undefined)
  assert.equal(panel.activeTask.value, undefined)
  panel.stop()
})

test('actual panel cannot precheck with an expired account or without create permission', async () => {
  for (const [authStatus, grants] of [['EXPIRED', ['*']], ['AUTHORIZED', ['tk:tiktok-shop-account:query']]]) {
    const panel = panelHarness({}, grants)
    panel.accounts.value = [{ id: 1, authStatus }]
    panel.accountId.value = 1
    Object.assign(panel.form, { mediaId: 4, title: 'hi', productId: 'p', selectionProof: 'proof', productLinkTitle: 'Mug', isAiGenerated: false })
    await panel.createTask()
    assert.equal(panel.calls.includes('create'), false)
    panel.stop()
  }
})

test('actual panel authorizes twice, rejects unknown callback statuses and disposes pending OAuth', async () => {
  let id = 0
  const panel = panelHarness({ authorize: async () => ({ sessionId: ++id, authorizationUrl: 'https://services.tiktokshop.com/open/authorize' }) })
  const popups = []; panel.window.open = () => { const popup = { location: { href: '' }, close() {} }; popups.push(popup); return popup }
  for (let session = 1; session <= 2; session++) {
    await panel.connect()
    assert.equal(panel.listeners.size, 1)
    panel.onOAuth({ origin: 'https://shop.example', source: popups.at(-1), data: { type: 'tiktok-shop-auth', sessionId: session, status: 'PROCESSING', authType: 'CREATOR' } })
    assert.equal(panel.listeners.size, 1)
    panel.onOAuth({ origin: 'https://shop.example', source: popups.at(-1), data: { type: 'tiktok-shop-auth', sessionId: session, status: 'SUCCESS', authType: 'CREATOR' } })
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

test('creator selection keeps input without an authorized account and drops an obsolete response', async () => {
  const vue = require('vue'), calls = [], pending = deferred()
  let accounts = []
  const creator = componentScript('../../tiktok-shop/TiktokShopCreatorPanel.vue', [
    'initialize', 'resetPage', 'search', 'publish', 'view', 'keyword', 'rows', 'nextCursor'
  ], {
    vue: { ...vue, onMounted: () => {}, onBeforeUnmount: () => {} },
    'element-plus': {},
    '../video-publish-center/components/tiktokShopBehavior': load('src/views/tk/video-publish-center/components/tiktokShopBehavior.ts'),
    '@/api/tk/tiktokShop': { TiktokShopApi: { accounts: async () => accounts, creatorPage: (...args) => { calls.push(args); return pending.promise } } },
    '@/hooks/web/useTkI18n': { useTkI18n: () => ({ tt: key => key }) },
    '@/directives/permission/hasPermi': { hasPermission: () => true }
  }, { defineEmits: () => () => assert.fail('published without authorization') })
  creator.keyword.value = 'tea'
  creator.view.value = 'samples'
  await creator.initialize()
  await creator.search()
  creator.publish({ available: true, selectionProof: 'old' })
  assert.deepEqual(calls, [])
  accounts = [{ id: 1, authStatus: 'AUTHORIZED' }]
  const querying = creator.initialize()
  await Promise.resolve()
  assert.deepEqual(calls[0].slice(0, 2), [1, 'samples'])
  accounts = [{ id: 1, authStatus: 'EXPIRED' }]
  await creator.initialize()
  pending.resolve({ rows: [{ id: 'stale' }], nextPageToken: 'stale' })
  await querying
  assert.deepEqual(creator.rows.value, [])
  assert.equal(creator.nextCursor.value, undefined)
  assert.equal(creator.keyword.value, 'tea')
})

test('photo draft keeps caption and images across accounts and cannot review without authorization', async () => {
  const vue = require('vue')
  const photo = componentScript('../../tiktok-shop/TiktokShopPhotoPanel.vue', [
    'accounts', 'accountId', 'capabilities', 'title', 'linkTitle', 'images', 'product', 'result', 'review'
  ], {
    vue: { ...vue, onMounted: () => {}, onUnmounted: () => {} },
    'element-plus': { ElMessageBox: { confirm: () => assert.fail('opened review without authorization') } },
    '@/api/tk/tiktokShop': { TiktokShopApi: { photoHistory: async () => ({ list: [], total: 0 }) } },
    '@/hooks/web/useTkI18n': { useTkI18n: () => ({ tt: key => key }) },
    '@/directives/permission/hasPermi': { hasPermission: () => true }
  })
  photo.capabilities.value = { publishingConfigured: true }
  photo.title.value = 'Prepared caption'
  photo.linkTitle.value = 'Mug'
  photo.images.value = [{ uid: 1 }]
  photo.product.value = { selectionProof: 'old' }
  await photo.review()
  photo.accounts.value = [{ id: 1, authStatus: 'AUTHORIZED' }]
  photo.accountId.value = 1
  await vue.nextTick()
  assert.equal(photo.product.value, undefined)
  assert.equal(photo.title.value, 'Prepared caption')
  assert.equal(photo.images.value[0].uid, 1)
  photo.result.value = { id: 3 }
  photo.accountId.value = undefined
  await vue.nextTick()
  assert.equal(photo.result.value, undefined)
})

test('OAuth timeout uses returned session lifetime instead of a fixed five minutes', () => {
  const { shopOAuthTimeoutMs } = load('src/views/tk/video-publish-center/components/tiktokShopBehavior.ts')
  assert.equal(shopOAuthTimeoutMs({ expiresInSeconds: 180 }), 180_000)
assert.equal(shopOAuthTimeoutMs({ expiresInSeconds: 1800 }), 1_800_000)
  assert.equal(shopOAuthTimeoutMs({ expireTime: '2026-11-17T14:20:00.000Z' }, Date.parse('2026-11-17T14:10:00.000Z')), 600_000)
  assert.equal(shopOAuthTimeoutMs({}, 0), 1_800_000)
})

test('catalog cursor keeps a previous-page stack and resets independently', () => {
  const { createCatalogCursor } = load('src/views/tk/video-publish-center/components/tiktokShopBehavior.ts')
  const cursor = createCatalogCursor()
  assert.equal(cursor.token(), undefined)
  assert.equal(cursor.canBack(), false)
  cursor.next('p2')
  cursor.next('p3')
  assert.equal(cursor.token(), 'p3')
  cursor.back()
  assert.equal(cursor.token(), 'p2')
  cursor.reset()
  assert.equal(cursor.token(), undefined)
  assert.equal(cursor.canBack(), false)
})

test('Shop API seller catalog uses official seller paths only', async () => {
  const calls = []
  const request = Object.fromEntries(['get', 'post', 'delete'].map(method => [method, async args => { calls.push({ method, ...args }); return {} }]))
  const { TiktokShopApi: api } = load('src/api/tk/tiktokShop/index.ts', { '@/config/axios': { __esModule: true, default: request } })
  await api.authorize({ authType: 'SELLER', companyId: 9 })
  await api.sellerAccounts({ companyId: 9 })
  await api.sellerProducts(8, { companyId: 9, pageSize: 20, pageToken: 'n1' })
  await api.sellerOrders(8, { companyId: 9, pageSize: 20 })
  await api.disconnectSeller(3)
  assert.deepEqual(calls[0], { method: 'post', url: '/tk/tiktok-shop/auth/sessions', data: { authType: 'SELLER', companyId: 9 } })
  assert.equal(calls[1].url, '/tk/tiktok-shop/seller/accounts')
  assert.equal(calls[2].url, '/tk/tiktok-shop/seller/shops/8/products')
  assert.deepEqual(calls[2].params, { companyId: 9, pageSize: 20, pageToken: 'n1' })
  assert.equal(calls[3].url, '/tk/tiktok-shop/seller/shops/8/orders')
  assert.deepEqual(calls[4], { method: 'delete', url: '/tk/tiktok-shop/seller/accounts/3' })
})

function sellerHarness(overrides = {}, grants = ['*']) {
  const vue = require('vue'), timers = new Map(), listeners = new Set(), cleanups = [], calls = []
  let timerId = 0
  const window = {
    setTimeout: fn => { timers.set(++timerId, fn); return timerId },
    clearTimeout: id => timers.delete(id),
    addEventListener: (_type, fn) => listeners.add(fn),
    removeEventListener: (_type, fn) => listeners.delete(fn),
    open: () => ({ location: { href: 'about:blank' }, closed: false, close() { this.closed = true } })
  }
  const configured = { enabled: true, configured: true, publishingConfigured: true, sellerEnabled: true, sellerConfigured: true, sellerEnvironment: 'PRODUCTION', callbackOrigin: 'https://shop.example' }
  const api = {
    capabilities: async () => configured,
    sellerAccounts: async () => { calls.push('sellerAccounts'); return [] },
    sellerShops: async () => { calls.push('sellerShops'); return [] },
    sellerProducts: async () => { calls.push('sellerProducts'); return { products: [], total: 0 } },
    sellerOrders: async () => { calls.push('sellerOrders'); return { orders: [], total: 0 } },
    authorize: async data => { calls.push(['authorize', data]); return { sessionId: 42, authorizationUrl: 'https://auth.tiktok-shops.com/open/authorize', expiresInSeconds: 120 } },
    disconnectSeller: async () => { calls.push('disconnectSeller') },
    ...overrides
  }
  const helper = load('src/views/tk/video-publish-center/components/tiktokShopBehavior.ts')
  const exposed = componentScript('TiktokShopSellerPanel.vue', [
    'capabilities', 'accounts', 'shops', 'products', 'orders', 'accountId', 'shopId', 'error', 'productHint', 'orderHint',
    'load', 'refreshLists', 'selectAccount', 'selectShop', 'connect', 'onOAuth', 'pageProducts', 'pageOrders', 'resetSelection',
    'selectedAccount', 'selectedShop', 'productCursor', 'orderCursor'
  ], {
    vue: { ...vue, onMounted: () => {}, onBeforeUnmount: fn => cleanups.push(fn) },
    'element-plus': { ElMessageBox: { confirm: async () => {} } },
    '@/api/tk/tiktokShop': { TiktokShopApi: api },
    './tiktokShopBehavior': helper,
    '@/hooks/web/useTkI18n': { useTkI18n: () => ({ tt: key => key }) },
    '@/directives/permission/hasPermi': { hasPermission: requested => grants.includes('*') || requested.some(p => grants.includes(p)) }
  }, { window })
  if (!overrides.capabilities) exposed.capabilities.value = configured
  return { ...exposed, window, timers, listeners, calls, api, configured, stop: () => cleanups.forEach(fn => fn()) }
}

test('actual seller panel does not fetch shops when seller configuration is missing', async () => {
  const panel = sellerHarness({ capabilities: async () => ({ sellerConfigured: false, sellerConfigurationMessage: 'seller missing', callbackOrigin: 'https://shop.example' }) }, ['tk:tiktok-shop-account:query'])
  await panel.load()
  assert.equal(panel.calls.includes('sellerAccounts'), false)
  assert.equal(panel.calls.includes('sellerShops'), false)
  await panel.connect()
  assert.equal(panel.listeners.size, 0)
  assert.equal(panel.calls.some(item => Array.isArray(item) && item[0] === 'authorize'), false)
  panel.stop()
})

test('actual seller panel drops a stale catalog page after the shop changes', async () => {
  const pending = deferred()
  const panel = sellerHarness({
    sellerAccounts: async () => [{ id: 1, companyId: 9, displayName: 'A', authStatus: 'AUTHORIZED', grantedScopes: 'seller.product.basic,seller.order.info', canReadProducts: true, canReadOrders: true }],
    sellerShops: async () => [{ id: 8, accountId: 1, companyId: 9, shopId: 'S8' }, { id: 9, accountId: 1, companyId: 9, shopId: 'S9' }],
    sellerProducts: async (id, params) => {
      if (id === 8) return pending.promise
      return { products: [{ id: 'p-new', title: 'fresh', status: 'ACTIVATE' }], nextPageToken: 'n2' }
    },
    sellerOrders: async () => ({ orders: [] })
  })
  panel.accounts.value = await panel.api.sellerAccounts()
  panel.shops.value = await panel.api.sellerShops()
  panel.accountId.value = 1
  panel.selectShop({ id: 8, accountId: 1, companyId: 9, shopId: 'S8' })
  panel.selectShop({ id: 9, accountId: 1, companyId: 9, shopId: 'S9' })
  pending.resolve({ products: [{ id: 'stale', title: 'old', status: 'ACTIVATE' }] })
  await new Promise(setImmediate)
  await new Promise(setImmediate)
  assert.equal(panel.shopId.value, 9)
  assert.equal(panel.products.value[0]?.id, 'p-new')
  assert.equal(panel.products.value.some(item => item.id === 'stale'), false)
  panel.stop()
})

test('actual seller panel authorizes with seller type, ignores creator callbacks and disposes OAuth', async () => {
  const panel = sellerHarness()
  const popups = []; panel.window.open = () => { const popup = { location: { href: '' }, close() {} }; popups.push(popup); return popup }
  await panel.connect()
  assert.deepEqual(panel.calls.find(item => Array.isArray(item) && item[0] === 'authorize')[1], { authType: 'SELLER' })
  assert.equal(panel.timers.size, 1)
  panel.onOAuth({ origin: 'https://shop.example', source: popups[0], data: { type: 'tiktok-shop-auth', sessionId: 42, status: 'SUCCESS', authType: 'CREATOR' } })
  assert.equal(panel.listeners.size, 1)
  panel.onOAuth({ origin: 'https://shop.example', source: popups[0], data: { type: 'tiktok-shop-auth', sessionId: 42, status: 'SUCCESS', authType: 'SELLER' } })
  assert.equal(panel.listeners.size, 0)
  panel.stop()
  assert.equal(panel.timers.size, 0)
})

test('actual seller panel skips catalog reads without product or order scopes', async () => {
  const panel = sellerHarness({
    sellerAccounts: async () => [{ id: 1, companyId: 9, displayName: 'A', authStatus: 'AUTHORIZED', grantedScopes: 'seller.authorization.info', canReadProducts: false, canReadOrders: false }],
    sellerShops: async () => [{ id: 8, accountId: 1, companyId: 9, shopId: 'S8' }]
  })
  panel.accounts.value = await panel.api.sellerAccounts()
  panel.shops.value = await panel.api.sellerShops()
  panel.accountId.value = 1
  panel.calls.length = 0
  panel.selectShop({ id: 8, accountId: 1, companyId: 9, shopId: 'S8' })
  await new Promise(setImmediate)
  assert.equal(panel.productHint.value, 'shop.sellerMissingProductScope')
  assert.equal(panel.orderHint.value, 'shop.sellerMissingOrderScope')
  assert.equal(panel.calls.includes('sellerProducts'), false)
  assert.equal(panel.calls.includes('sellerOrders'), false)
  panel.stop()
})

test('publishing workspace keeps creator as the default entry', () => {
  const source = fs.readFileSync(path.join(__dirname, '../src/views/tk/video-publish-center/components/TiktokShopWorkspace.vue'), 'utf8')
  assert.match(source, /value="creator"/)
  assert.match(source, /value="seller"/)
  assert.match(source, /mode = ref<'creator' \| 'seller'>\('creator'\)/)
  const index = fs.readFileSync(path.join(__dirname, '../src/views/tk/video-publish-center/index.vue'), 'utf8')
  assert.match(index, /TiktokShopWorkspace/)
  assert.doesNotMatch(index, /<TiktokShopPublishPanel/)
})
