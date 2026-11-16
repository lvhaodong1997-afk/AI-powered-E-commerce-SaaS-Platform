const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const product = path.resolve(__dirname, '../../..')
const workspace = path.resolve(product, '../../..')
const spec = JSON.parse(fs.readFileSync(path.join(workspace, 'docs/api/tk-platform-openapi.json'), 'utf8'))
const controllerRoot = path.join(product, 'yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/controller/admin')
const schemas = spec.components.schemas
const op = (suffix, method = 'post') => spec.paths[`/admin-api/tk/${suffix}`]?.[method]

test('every current material controller mapping and permission is documented, including aliases', () => {
  let count = 0
  for (const file of ['material/TkMaterialLibraryController.java', 'material/TkMaterialVideoController.java', 'upload/TkUploadController.java']) {
    const source = fs.readFileSync(path.join(controllerRoot, file), 'utf8')
    const prefix = source.match(/@RequestMapping\("([^"]+)"\)/)[1]
    for (const match of source.matchAll(/@(Get|Post|Put|Delete)Mapping\(([^\n]+)\)[\s\S]*?@PreAuthorize\("@ss.hasPermission\('([^']+)'\)"\)/g)) {
      for (const [, suffix] of match[2].matchAll(/"([^"]+)"/g)) {
        const url = `/admin-api${prefix}${suffix}`
        const operation = spec.paths[url]?.[match[1].toLowerCase()]
        assert.ok(operation, `missing ${match[1]} ${url}`)
        assert.ok(operation.description.includes(match[3]), `wrong permission ${url}`)
        assert.deepEqual(operation.security, [{ AdminBearer: [], TenantId: [] }])
        assert.ok(operation.responses['200'].content['application/json'].examples.businessError)
        assert.ok(operation.responses['401'])
        assert.ok(operation.responses['403'])
        count++
      }
    }
  }
  assert.equal(count, 19, 'review added or removed backend endpoints')
})

test('multipart upload and chunk bodies expose real binary fields and zero based indexes', () => {
  const direct = op('material-video/upload').requestBody.content['multipart/form-data'].schema
  assert.deepEqual(direct.required, ['libraryId', 'file'])
  assert.equal(direct.properties.file.format, 'binary')
  const chunk = op('upload/material-video/chunk').requestBody.content['multipart/form-data'].schema
  assert.deepEqual(chunk.required, ['uploadId', 'chunkIndex', 'chunk'])
  assert.equal(chunk.properties.chunkIndex.minimum, 0)
  assert.equal(chunk.properties.chunk.format, 'binary')
  assert.match(op('material-video/upload').description, /104857600/)
  assert.match(op('upload/material-video/chunk').description, /local/)
})

test('upload modes and terminal session restrictions match material services', () => {
  assert.deepEqual(schemas.MaterialUploadSession.properties.uploadMode.enum, ['local', 'oss'])
  assert.match(schemas.MaterialUploadSession.description, /OSSAccessKeyId/)
  assert.match(schemas.MaterialUploadSessionStatus.description, /UPLOADING/)
  assert.match(schemas.MaterialUploadSessionStatus.description, /COMPLETED/)
  assert.match(schemas.MaterialUploadSessionStatus.description, /CANCELLED/)
  assert.match(schemas.MaterialUploadSessionStatus.description, /EXPIRED/)
  assert.match(op('upload/material-video/session/complete').description, /幂等/)
  assert.match(op('upload/material-video/session/complete').description, /HEAD/)
  assert.deepEqual(schemas.MaterialUploadCompleteRequest.required, ['uploadId'])
  assert.ok(schemas.MaterialUploadCompleteRequest['x-upload-mode-requirements'].oss.includes('objectKey'))
})

test('async readiness and segmentation changes preserve actual backend casing and side effects', () => {
  assert.deepEqual(schemas.MaterialVideo.properties.status.enum, ['UPLOADING', 'PARSING', 'AVAILABLE', 'FAILED'])
  assert.match(schemas.MaterialVideo.properties.status.description, /READY/)
  assert.equal(schemas.MaterialVideo.properties.duration.description.includes('秒'), true)
  const desc = op('material-video/segment-type', 'put').description
  for (const phase of ['ATTENTION', 'RESULT_EFFECT', 'PRODUCT_SHOW']) assert.ok(desc.includes(phase))
  assert.match(op('material-video/segment-summary', 'get').description, /AVAILABLE/)
  assert.equal(schemas.MaterialUsagePhaseInput.enum, undefined, 'normalization accepts unknown values')
  assert.equal(schemas.MaterialSegmentTypeInput.enum, undefined, 'normalization accepts unknown values')
  assert.match(schemas.MaterialSegmentTypeInput.description, /GENERAL/)
})

test('paging and request validation do not invent constraints absent from backend VOs', () => {
  for (const suffix of ['material-library/page', 'material-video/page']) {
    const params = op(suffix, 'get').parameters
    assert.equal(params.find((p) => p.name === 'pageNo').schema.minimum, 1)
    assert.equal(params.find((p) => p.name === 'pageSize').schema.maximum, 200)
  }
  assert.deepEqual(schemas.MaterialLibrarySaveRequest.required, ['name'])
  assert.equal(schemas.MaterialLibrarySaveRequest.properties.name.maxLength, undefined)
  assert.equal(schemas.MaterialLibrarySaveRequest.properties.status.enum, undefined)
  assert.equal(schemas.MaterialVideoIdsRequest.properties.ids.minItems, 1)
  assert.equal(schemas.MaterialVideoIdsRequest.properties.ids.maxItems, undefined)
})

test('material timestamps use the backend Unix millisecond serialization in schemas and examples', () => {
  for (const name of ['MaterialLibrary', 'MaterialVideo']) {
    const field = schemas[name].properties.createTime
    assert.equal(field.type, 'integer', name)
    assert.equal(field.format, 'int64', name)
    assert.ok(Number.isSafeInteger(field.example), name)
  }
  for (const suffix of ['material-library/get', 'material-library/page', 'material-video/get', 'material-video/page']) {
    const data = op(suffix, 'get').responses['200'].content['application/json'].examples.success.value.data
    const item = data.list ? data.list[0] : data
    assert.ok(Number.isSafeInteger(item.createTime), suffix)
  }
})

test('all local schema references and operation IDs in the combined contract remain valid', () => {
  assert.deepEqual(schemas.MaterialApiSuccess.properties.code.enum, [0])
  assert.deepEqual(schemas.MaterialApiError.properties.code.not, { enum: [0] }, 'success must not match both oneOf branches')
  const ids = new Set()
  for (const item of Object.values(spec.paths)) {
    for (const [method, operation] of Object.entries(item)) {
      if (!['get', 'post', 'put', 'delete', 'patch'].includes(method)) continue
      assert.ok(!ids.has(operation.operationId), `duplicate ${operation.operationId}`)
      ids.add(operation.operationId)
    }
  }
  function visit(value) {
    if (!value || typeof value !== 'object') return
    if (value.$ref?.startsWith('#/')) {
      const target = value.$ref.slice(2).split('/').reduce((v, k) => v?.[k.replace(/~1/g, '/').replace(/~0/g, '~')], spec)
      assert.ok(target, `unresolved ${value.$ref}`)
    }
    for (const child of Object.values(value)) visit(child)
  }
  visit(spec)
})
