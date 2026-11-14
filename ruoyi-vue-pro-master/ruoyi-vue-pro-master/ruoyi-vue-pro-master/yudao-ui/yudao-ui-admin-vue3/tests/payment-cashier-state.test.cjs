const fs = require('node:fs')
const path = require('node:path')
const assert = require('node:assert/strict')
const test = require('node:test')
const ts = require('typescript')
const moduleObject = { exports: {} }
new Function(
  'module',
  'exports',
  ts.transpileModule(
    fs.readFileSync(path.join(__dirname, '../src/utils/tenantOnboarding.ts'), 'utf8'),
    { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } }
  ).outputText
)(moduleObject, moduleObject.exports)
const { safeProviderUrl, paymentEntryState, mergePaymentOrder } = moduleObject.exports
const pending = {
  orderNo: 'TKP_a',
  status: 'payment_pending',
  creditStatus: 'PENDING',
  paymentMethod: 'wechat_pay',
  expiresAt: 1,
  version: 1
}

test('Alipay provider redirect supports the current exact pm-redirects host', () => {
  assert.equal(
    safeProviderUrl('https://pm-redirects.stripe.com/alipay/authorize/fixture', 'redirect'),
    'https://pm-redirects.stripe.com/alipay/authorize/fixture'
  )
  for (const url of [
    'https://pm-redirects.stripe.com.evil.test/x',
    'http://pm-redirects.stripe.com/x',
    'https://u:p@pm-redirects.stripe.com/x',
    'https://pm-redirects.stripe.com:444/x',
    'https://pm-redirects.stripe.com/x?client_secret=x'
  ])
    assert.equal(safeProviderUrl(url, 'redirect'), '')
})

test('pending provider entry survives elapsed quote time and repeated authoritative reads', () => {
  const order = { ...pending, qrImageUrl: 'https://qr.stripe.com/fixture' }
  assert.equal(paymentEntryState(order), 'qr')
  const refreshed = mergePaymentOrder(order, { ...order, version: 2 })
  assert.equal(paymentEntryState(refreshed, { refreshing: true }), 'qr')
  const alipay = {
    ...pending,
    paymentMethod: 'alipay',
    redirectUrl: 'https://pm-redirects.stripe.com/alipay/fixture'
  }
  assert.equal(paymentEntryState(alipay), 'redirect')
})

test('missing entry, loading, and QR image failure have recoverable distinct states', () => {
  assert.equal(paymentEntryState(pending), 'entry_missing')
  assert.equal(paymentEntryState(pending, { refreshing: true }), 'loading')
  assert.equal(
    paymentEntryState(
      { ...pending, qrImageUrl: 'https://qr.stripe.com/fixture' },
      { imageFailed: true }
    ),
    'image_failed'
  )
  assert.equal(
    paymentEntryState({ ...pending, qrImageUrl: 'https://evil.test/x' }),
    'entry_missing'
  )
})

test('server terminal states remove payment entries and distinguish credits awaiting allocation', () => {
  const previous = {
    ...pending,
    qrImageUrl: 'https://qr.stripe.com/fixture',
    redirectUrl: 'https://hooks.stripe.com/fixture'
  }
  for (const status of ['canceled', 'refund_pending', 'refunded', 'review']) {
    const current = mergePaymentOrder(previous, { ...previous, status, version: 2 })
    assert.equal(paymentEntryState(current), status)
  }
  assert.equal(paymentEntryState({ ...previous, status: 'paid' }), 'credit_pending')
  assert.equal(
    paymentEntryState({ ...previous, status: 'paid', creditStatus: 'CREDITED' }),
    'complete'
  )
  assert.equal(paymentEntryState({ ...previous, status: 'quoted' }), 'quoted')
})
