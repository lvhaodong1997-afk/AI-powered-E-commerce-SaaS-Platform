const assert = require('node:assert/strict')
const { test } = require('node:test')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const ts = require('typescript')
const { ref } = require('vue')
const source = fs.readFileSync(path.join(__dirname, '../src/views/tk/dashboard/components/BgmManagerDialog.vue'), 'utf8')
const removeSource = source.slice(source.indexOf('const remove = async'), source.indexOf('const upload = async'))
function manager({ references = [], totalReferences = references.length, confirm = async () => {}, deleteTrack = async () => {} } = {}) {
  const calls = [], pending = ref(false)
  const context = vm.createContext({ pending,
    props: { isEn: false }, text: (zh) => zh,
    TkBgmAssetApi: { referenceCount: async () => totalReferences, references: async (id) => { calls.push(['references', id]); return references }, delete: async (...args) => { calls.push(['delete', ...args]); await deleteTrack() } },
    audienceCategories: [{ code: 'SILVER', zh: '银发经济' }],
    message: { confirm: async (text) => { calls.push(['confirm', text]); await confirm() } },
    stop: () => calls.push(['stop']), emit: (...args) => calls.push(['emit', ...args])
  })
  vm.runInContext(ts.transpileModule(removeSource, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText + '\nglobalThis.remove = remove', context)
  return { remove: context.remove, calls, pending }
}
test('BGM deletion requires server canDelete permission', async () => {
  const h = manager(); await h.remove({ id: 1, sourceType: 'USER', canDelete: false })
  assert.deepEqual(h.calls, [])
})
test('referenced BGM requires explicit confirmation listing preset names before confirmed delete', async () => {
  const h = manager({ references: [{ id: 3, audienceCode: 'SILVER' }] })
  await h.remove({ id: 1, name: 'song', canDelete: true })
  assert.equal(h.calls[0][0], 'references'); assert.match(h.calls[1][1], /银发经济/)
  assert.deepEqual(h.calls.find(call => call[0] === 'delete'), ['delete', 1, true])
  assert.ok(h.calls.find(call => call[0] === 'emit' && call[1] === 'deleted'))
})
test('cancelling reference confirmation leaves BGM and current selection untouched', async () => {
  const h = manager({ references: [{ audienceCode: 'SILVER' }], confirm: async () => { throw Error('cancel') } })
  await h.remove({ id: 1, name: 'song', canDelete: true })
  assert.ok(!h.calls.some(call => ['delete', 'emit'].includes(call[0]))); assert.equal(h.pending.value, false)
})
test('delete failure preserves selection and releases pending guard', async () => {
  const h = manager({ deleteTrack: async () => { throw Error('changed references') } })
  await assert.rejects(h.remove({ id: 1, name: 'song', canDelete: true }), /changed references/)
  assert.ok(!h.calls.some(call => call[0] === 'emit')); assert.equal(h.pending.value, false)
})

 test('colleague-only references require explicit cross-user unbind confirmation without preset details', async () => {
  const h = manager({ totalReferences: 1 }); await h.remove({id: 1, name: 'song', canDelete: true})
  assert.match(h.calls.find(c => c[0] === 'confirm')[1], /其他用户.*重新配置/)
  assert.deepEqual(h.calls.find(c => c[0] === 'delete'), ['delete', 1, true])
 })
 test('cancelling colleague-only confirmation makes no delete or selection change', async () => {
  const h = manager({totalReferences: 1, confirm: async () => { throw Error('cancel') }});
  await h.remove({id: 1, name: 'song', canDelete: true});
  assert.ok(!h.calls.some(c => ['delete','emit','stop'].includes(c[0]))); assert.equal(h.pending.value, false)
 })

test('failed colleague-reference delete preserves selection and preview', async () => {
  const h = manager({ totalReferences: 1, deleteTrack: async () => { throw Error('delete rejected') } })
  await assert.rejects(h.remove({ id: 1, name: 'song', canDelete: true }), /delete rejected/)
  assert.ok(!h.calls.some(c => ['emit', 'stop'].includes(c[0])))
  assert.equal(h.pending.value, false)
})

test('own-reference confirmation explicitly covers other users and newly added references', async () => {
  const h = manager({ references: [{ audienceCode: 'SILVER' }] })
  await h.remove({ id: 1, name: 'song', canDelete: true })
  assert.match(h.calls.find(c => c[0] === 'confirm')[1], /其他用户及后续新增引用/)
})
