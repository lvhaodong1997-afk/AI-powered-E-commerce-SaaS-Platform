const assert = require('node:assert/strict')
const { test } = require('node:test')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const ts = require('typescript')
const helperPath = path.join(__dirname, '../src/utils/tiktokPhotoPublish.ts')
const context = { exports: {} }
if (fs.existsSync(helperPath)) vm.runInNewContext(ts.transpileModule(fs.readFileSync(helperPath, 'utf8'), {
  compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS }
}).outputText, context)
const photos = [{ id: 1 }, { id: 2 }, { id: 3 }]
test('moving and removing images preserves the chosen cover by identity', () => {
  const moved = context.exports.movePhoto(photos, 1, 1, 0)
  assert.equal(moved.coverIndex, 0)
  assert.equal(moved.photos.map(p => p.id).join(','), '2,1,3')
  const removed = context.exports.removePhoto(moved.photos, moved.coverIndex, 1)
  assert.equal(removed.coverIndex, 0)
  assert.equal(removed.photos.map(p => p.id).join(','), '2,3')
  const coverRemoved = context.exports.removePhoto(removed.photos, 0, 0)
  assert.equal(coverRemoved.coverIndex, 0)
})
test('photo source never submits stale video ids and preserves image order', () => {
  const source = context.exports.photoPublishSource(photos, 2)
  assert.equal(source.uploadedPhotoIds.join(','), '1,2,3')
  assert.equal(source.mediaType, 'PHOTO')
  assert.equal(source.photoCoverIndex, 2)
  assert.equal(source.uploadedVideoId, undefined)
  assert.equal(source.generationTaskId, undefined)
  assert.throws(() => context.exports.photoPublishSource([], 0))
  assert.throws(() => context.exports.photoPublishSource(photos, 3))
})
test('selection enforces 35 photos, supported formats and 20MB before upload', () => {
  assert.throws(() => context.exports.validatePhotoSelection([{ name: 'a.jpg', size: 1 }], 35))
  assert.throws(() => context.exports.validatePhotoSelection([{ name: 'a.jpg', size: 20 * 1024 * 1024 + 1 }], 0))
  assert.throws(() => context.exports.validatePhotoSelection([{ name: 'a.svg', size: 1 }], 0))
  assert.doesNotThrow(() => context.exports.validatePhotoSelection([{ name: 'a.webp', size: 10 }], 0))
})
