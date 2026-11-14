const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const test = require('node:test')
const ts = require('typescript')

const utils = { exports: {} }
new Function(
  'module',
  'exports',
  ts.transpileModule(
    fs.readFileSync(path.join(__dirname, '../src/utils/tenantOnboarding.ts'), 'utf8'),
    {
      compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 }
    }
  ).outputText
)(utils, utils.exports)
const source = fs.readFileSync(path.join(__dirname, '../src/views/tk/recharge/index.vue'), 'utf8')

function harness(error) {
  const attempted = [],
    saved = new Map()
  let sequence = 0
  const context = {
    Error,
    PAYMENT_QUOTE_REJECTED_CODE: 1060000053,
    quoting: { value: false },
    tenantAdmin: { value: true },
    draft: { value: undefined },
    packageCode: { value: 'VERIFY_1' },
    paymentMethod: { value: 'alipay' },
    purchaseQuantity: { value: 1 },
    selected: { value: undefined },
    dialogVisible: { value: false },
    loadError: { value: '' },
    storage: (key, value) => {
      saved.set(key, value)
    },
    rememberOrder: () => {},
    loadOrders: async () => {},
    text: (zh) => zh,
    quoteAttempt: (previous, selection) =>
      utils.exports.quoteAttempt(previous, selection, () => `request-${++sequence}`),
    PaymentApi: {
      quote: async (input) => {
        attempted.push(input)
        if (attempted.length === 1) throw error
        return { ...input, status: 'quoted', currency: 'CNY', amountCents: input.quantity * 100 }
      }
    }
  }
  const methods = ['createQuote', 'restoreOrder']
    .map((name) => source.match(new RegExp(`const ${name} = async \\([^]*?\\n\\}`))[0])
    .join('\n')
  vm.runInNewContext(
    ts.transpileModule(
      methods + '\nglobalThis.invoke=createQuote;globalThis.restore=restoreOrder',
      { compilerOptions: { target: ts.ScriptTarget.ES2020 } }
    ).outputText,
    context
  )
  return { context, attempted, saved }
}
test('known no-quote rejection unlocks selection and uses a new key for five units', async () => {
  const h = harness(
    Object.assign(new Error('该渠道最低付款金额为5.00 CNY，请重新选择购买数量'), {
      businessCode: 1060000053
    })
  )
  await h.context.invoke()
  assert.equal(h.context.draft.value, undefined)
  assert.equal(h.saved.get(':draft'), '')
  assert.match(h.context.loadError.value, /最低付款金额/)
  h.context.purchaseQuantity.value = 5
  await h.context.invoke()
  assert.deepEqual(
    h.attempted.map((x) => x.quantity),
    [1, 5]
  )
  assert.notEqual(h.attempted[0].idempotencyKey, h.attempted[1].idempotencyKey)
  assert.equal(h.context.loadError.value, '')
})
test('unknown outcome still retries the exact original quantity and request', async () => {
  const h = harness(new Error('network result unknown'))
  await h.context.invoke()
  assert.ok(h.context.draft.value)
  h.context.purchaseQuantity.value = 5
  await h.context.invoke()
  assert.deepEqual(
    h.attempted.map((x) => x.quantity),
    [1, 1]
  )
  assert.equal(h.attempted[0].idempotencyKey, h.attempted[1].idempotencyKey)
})
test('rejected history has a readable notice and never opens an unpriced payment dialog', async () => {
  const h = harness(new Error('unused'))
  h.context.PaymentApi.get = async () => ({
    orderNo: 'TKP_rejected',
    status: 'canceled',
    quoteRejected: true
  })
  h.context.selected.value = { currency: 'CNY', amountCents: 500 }
  await h.context.restore({ orderNo: 'TKP_rejected' })
  assert.equal(h.context.selected.value, undefined)
  assert.equal(h.context.dialogVisible.value, false)
  assert.match(h.context.loadError.value, /拒绝/)
})
