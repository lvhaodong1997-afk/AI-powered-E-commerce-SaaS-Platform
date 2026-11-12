const assert = require('node:assert/strict')
const { test } = require('node:test')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const ts = require('typescript')
const helperPath = path.join(__dirname, '../src/utils/tiktokPhotoPublish.ts')
function loadHelper(globals = {}) {
  const context = { exports: {}, ...globals }
  vm.runInNewContext(ts.transpileModule(fs.readFileSync(helperPath, 'utf8'), {
    compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS }
  }).outputText, context)
  return context.exports
}
const context = { exports: loadHelper() }
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

test('creator query timeouts explain that uploaded photos can be kept and permissions retried', () => {
  for (const error of [{ response: { status: 524 }, message: 'Request failed with status code 524' },
    { code: 'ECONNABORTED', message: 'timeout of 55000ms exceeded' }]) {
    const message = context.exports.photoCreatorErrorMessage(error)
    assert.match(message, /账号发布权限.*超时/)
    assert.match(message, /重试/)
    assert.match(message, /图片无需重新上传/)
    assert.doesNotMatch(message, /Request failed/)
  }
  assert.equal(context.exports.photoCreatorErrorMessage(new Error('所选 TikTok 账号未授权')), '所选 TikTok 账号未授权')
})

function browserHarness({ bitmap, imageFails = false, encodeFails = false } = {}) {
  const revoked = []
  const canvas = {
    getContext: () => ({ fillRect() {}, drawImage() {} }),
    toBlob: (callback) => callback(encodeFails ? null : { size: 100, type: 'image/jpeg' })
  }
  const helper = loadHelper({
    createImageBitmap: bitmap,
    Image: class {
      naturalWidth = 2400
      naturalHeight = 1600
      set src(value) { queueMicrotask(() => imageFails ? this.onerror() : this.onload()) }
    },
    URL: { createObjectURL: () => 'blob:photo-test', revokeObjectURL: (url) => revoked.push(url) },
    document: { createElement: () => canvas },
    File: class {
      constructor(parts, name, options) { this.name = name; this.type = options.type; this.size = parts[0].size }
    }
  })
  return { helper, canvas, revoked }
}

for (const mode of ['unavailable', 'rejected']) {
  test(`a readable JPEG still converts when the bitmap decoder is ${mode}`, async () => {
    const bitmap = mode === 'rejected' ? async () => { throw new Error('The source image could not be decoded.') } : undefined
    const { helper, canvas, revoked } = browserHarness({ bitmap })
    const result = await helper.prepareTikTokPhoto({ name: '商品.jpeg', size: 120 })
    assert.equal(result.name, '商品.jpg')
    assert.equal(result.type, 'image/jpeg')
    assert.equal(canvas.width, 1620)
    assert.equal(canvas.height, 1080)
    assert.deepEqual(revoked, ['blob:photo-test'])
  })
}

test('an unreadable JPEG reports its filename and a recovery action, releasing its URL', async () => {
  const { helper, revoked } = browserHarness({
    bitmap: async () => { throw new Error('The source image could not be decoded.') },
    imageFails: true
  })
  await assert.rejects(helper.prepareTikTokPhoto({ name: '坏图.jpeg', size: 120 }), (error) => {
    assert.match(error.message, /坏图\.jpeg/)
    assert.match(error.message, /无法读取图片/)
    assert.match(error.message, /重新导出/)
    return true
  })
  assert.deepEqual(revoked, ['blob:photo-test'])
})

test('the fallback releases its object URL even when JPEG encoding fails', async () => {
  const { helper, revoked } = browserHarness({ encodeFails: true })
  await assert.rejects(helper.prepareTikTokPhoto({ name: '商品.jpeg', size: 120 }), /图片转换失败/)
  assert.deepEqual(revoked, ['blob:photo-test'])
})

test('a decoded bitmap is closed even when JPEG encoding fails', async () => {
  let closed = 0
  const { helper } = browserHarness({
    bitmap: async () => ({ width: 2400, height: 1600, close: () => { closed++ } }),
    encodeFails: true
  })
  await assert.rejects(helper.prepareTikTokPhoto({ name: '商品.jpeg', size: 120 }), /图片转换失败/)
  assert.equal(closed, 1)
})
