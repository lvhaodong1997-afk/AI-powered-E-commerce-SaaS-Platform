const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const ts = require('typescript')
const assert = require('node:assert/strict')
const test = require('node:test')

function dialogHarness() {
  const sfc = fs.readFileSync(path.join(__dirname, '../src/views/tk/recharge/PaymentDialog.vue'), 'utf8')
  const source = sfc.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1].replace(/^import[\s\S]*?from ['"][^'"]+['"]\s*$/gm, '')
  const pending = []
  const timers = []
  const emitted = []
  const props = {modelValue:true,order:{orderNo:'TKP_a',status:'payment_pending',creditStatus:'PENDING',version:1}}
  let watcher
  const context = {
    defineProps:()=>props, defineEmits:()=> (...args)=>emitted.push(args),
    useLocaleStore:()=>({getCurrentLocale:{lang:'en'}}), ref:value=>({value}),
    computed:read=>({get value(){return read()}}),
    watch:(_read, callback)=>{watcher=callback;callback()}, onBeforeUnmount:()=>{},
    setTimeout:(callback,delay)=>{timers.push({callback,delay});return timers.length},
    clearTimeout:()=>{}, PaymentApi:{get:orderNo=>new Promise(resolve=>pending.push({orderNo,resolve}))},
    shouldPollOrder:()=>true,mergePaymentOrder:(_old,current)=>current,
    safeProviderUrl:()=>'',isPaidAndCredited:()=>false
  }
  vm.runInNewContext(ts.transpileModule(source,{compilerOptions:{target:ts.ScriptTarget.ES2020,module:ts.ModuleKind.None}}).outputText,context)
  return {props,pending,timers,emitted,changed:()=>watcher()}
}
const flush = () => new Promise(resolve=>setImmediate(resolve))
test('reopening while the original GET is pending starts latest polling and ignores the old result', async () => {
  const h=dialogHarness()
  assert.equal(h.pending.length,1)
  h.props.modelValue=false;h.changed()
  h.props.modelValue=true;h.changed()
  assert.equal(h.pending.length,2,'reopened generation must not be blocked by old in-flight refresh')
  h.pending[0].resolve({...h.props.order,status:'paid'});await flush()
  assert.equal(h.emitted.length,0,'old generation cannot publish stale result')
  assert.equal(h.timers.length,0,'old generation cannot schedule successor')
  h.pending[1].resolve(h.props.order);await flush()
  assert.equal(h.emitted.length,1)
  assert.equal(h.timers.length,1,'latest visible generation continues automatic polling')
})
test('switching orders during GET keeps the new order refresh owner and schedules its successor', async () => {
  const h=dialogHarness()
  h.props.order={...h.props.order,orderNo:'TKP_b'};h.changed()
  assert.equal(h.pending.length,2)
  h.pending[0].resolve({orderNo:'TKP_a',status:'paid'});await flush()
  assert.equal(h.emitted.length,0)
  h.pending[1].resolve(h.props.order);await flush()
  assert.equal(h.emitted[0][1].orderNo,'TKP_b')
  assert.equal(h.timers.length,1)
})
