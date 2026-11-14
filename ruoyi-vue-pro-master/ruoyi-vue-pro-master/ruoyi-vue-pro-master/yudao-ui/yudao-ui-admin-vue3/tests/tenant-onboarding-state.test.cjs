const fs = require('node:fs')
const path = require('node:path')
const assert = require('node:assert/strict')
const test = require('node:test')
const ts = require('typescript')
const source = fs.readFileSync(path.join(__dirname, '../src/utils/tenantOnboarding.ts'), 'utf8')
const moduleObject = { exports: {} }
new Function('module', 'exports', ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 }
}).outputText)(moduleObject, moduleObject.exports)
const { registrationAttempt, changePhone, applySmsChallenge, prepareSmsSend, safeProviderUrl,
  mergePaymentOrder, shouldPollOrder, isPaidAndCredited, quoteAttempt, parsePaymentDraft } = moduleObject.exports
test('uncertain registration retries keep the same request and ignore refreshed captcha', () => {
  const payload = { mobile: '13800000001', tenantName: 'A', username: 'owner', captchaVerification: 'captcha1' }
  const first = registrationAttempt(undefined, payload, () => 'request1')
  assert.equal(registrationAttempt(first, {...payload, captchaVerification:'captcha2'}, () => 'request2').requestId, 'request1')
  assert.equal(registrationAttempt(first, {...payload, tenantName:'B'}, () => 'request2').requestId, 'request2')
})
test('phone changes clear the challenge, OTP and cooldown and invalidate delayed responses', () => {
  const state = { mobile:'13800000001', requestId:'request1', smsRequestId:'sms1', code:'123456', resendAt:123, deliveryStatus:'SENT' }
  const changed = changePhone(state, '13800000002', () => 'request2')
  assert.equal(changed.smsRequestId, '')
  assert.equal(changed.code, '')
  assert.equal(changed.resendAt, 0)
  assert.equal(applySmsChallenge(changed, state.mobile, {smsRequestId:'stale'}, 10), changed)
})
test('network uncertainty and pending delivery retry original SMS request but explicit later resend gets a new one', () => {
  const state = changePhone(undefined, '13800000001', () => 'request1')
  assert.equal(prepareSmsSend(state, 500, () => 'request2').requestId, 'request1')
  const pending = applySmsChallenge(state, state.mobile, {smsRequestId:'sms1',deliveryStatus:'PENDING',resendAfterSeconds:0}, 500)
  assert.equal(prepareSmsSend(pending, 600, () => 'request2').requestId, 'request1')
  const sent = applySmsChallenge(state, state.mobile, {smsRequestId:'sms1',deliveryStatus:'SENT',resendAfterSeconds:60}, 500)
  assert.equal(prepareSmsSend(sent, 600, () => 'request2').requestId, 'request1')
  assert.equal(prepareSmsSend(sent, 60501, () => 'request2').requestId, 'request2')
})
test('quote recovery freezes original selection and idempotency key', () => {
  const first = quoteAttempt(undefined,{packageCode:'P1000',paymentMethod:'alipay'},()=> 'key1')
  assert.deepEqual(quoteAttempt(first,{packageCode:'P5000',paymentMethod:'wechat_pay'},()=> 'key2'), first)
  assert.equal(first.idempotencyKey,'key1')
})
test('only HTTPS exact Stripe hosts and safe URL parameters are accepted', () => {
  assert.equal(safeProviderUrl('https://checkout.stripe.com/c/pay/cs_test_1','redirect'),'https://checkout.stripe.com/c/pay/cs_test_1')
  assert.equal(safeProviderUrl('https://qr.stripe.com/test.png','qr'),'https://qr.stripe.com/test.png')
  for (const url of ['javascript:alert(1)','http://checkout.stripe.com/x','https://checkout.stripe.com.evil.test/x',
    'https://user:pass@checkout.stripe.com/x','https://checkout.stripe.com:444/x','https://checkout.stripe.com/x?client_secret=s',
    'https://evil.test/qr.png','https://qr.stripe.com/x?%63lient_secret=s']) assert.equal(safeProviderUrl(url,'redirect'),'')
})
test('stale payment response cannot regress order or credit state', () => {
  const refunded={orderNo:'TKP_a',status:'refunded',creditStatus:'REFUNDED',version:5,amountCents:1234,currency:'HKD'}
  assert.equal(mergePaymentOrder(refunded,{...refunded,status:'paid',version:4}),refunded)
  assert.equal(mergePaymentOrder(refunded,{...refunded,status:'paid',version:6}),refunded)
  assert.equal(mergePaymentOrder(refunded,{...refunded,orderNo:'TKP_b',version:6}),refunded)
})
test('browser return or paid payment alone never means credits arrived', () => {
  assert.equal(isPaidAndCredited({status:'payment_pending',creditStatus:'PENDING'}),false)
  assert.equal(isPaidAndCredited({status:'paid',creditStatus:'PENDING'}),false)
  assert.equal(isPaidAndCredited({status:'paid',creditStatus:'CREDITED'}),true)
  assert.equal(shouldPollOrder({status:'paid',creditStatus:'PENDING'}),true)
  assert.equal(shouldPollOrder({status:'refunded',creditStatus:'REFUNDED'}),false)
  assert.equal(shouldPollOrder({status:'created',creditStatus:'PENDING'}),false)
})
test('local recovery draft excludes secrets and provider URLs and rejects hostile shape', () => {
  const raw=JSON.stringify({idempotencyKey:'12345678-1234-1234-1234-123456789012',packageCode:'P1000',paymentMethod:'alipay',client_secret:'must-drop',redirectUrl:'must-drop'})
  assert.deepEqual(parsePaymentDraft(raw),{idempotencyKey:'12345678-1234-1234-1234-123456789012',packageCode:'P1000',paymentMethod:'alipay'})
  for(const invalid of ['null','{','{}',JSON.stringify({idempotencyKey:'x',packageCode:'P1000',paymentMethod:'alipay'})]) assert.equal(parsePaymentDraft(invalid),undefined)
})

test('tenant administrator predicate preserves legacy alias and excludes member/platform identities', () => {
  for (const level of ['TENANT_ADMIN', 'COMPANY_ADMIN']) assert.equal(moduleObject.exports.isTenantAdministrator(level), true)
  for (const level of ['MEMBER', 'COMPANY_MEMBER', 'PLATFORM_ADMIN', '', undefined, null]) assert.equal(moduleObject.exports.isTenantAdministrator(level), false)
})
test('zero-second recovered and expired challenges permit a fresh explicit send', () => {
  const original = changePhone(undefined, '13800000001', () => 'original')
  for (const expiresInSeconds of [240, 0]) {
    const recovered = applySmsChallenge(original, original.mobile, {smsRequestId:'sms1',deliveryStatus:'SENT',resendAfterSeconds:0,expiresInSeconds}, 1000)
    assert.equal(recovered.resendAt, 1000)
    assert.equal(recovered.expiresAt, 1000 + expiresInSeconds * 1000)
    assert.equal(prepareSmsSend(recovered, 1000, () => 'fresh').requestId, 'fresh')
  }
})
