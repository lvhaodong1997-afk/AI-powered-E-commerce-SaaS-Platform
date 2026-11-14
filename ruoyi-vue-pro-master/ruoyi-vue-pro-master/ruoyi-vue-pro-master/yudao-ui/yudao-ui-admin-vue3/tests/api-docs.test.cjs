const { test } = require('node:test')
const assert = require('node:assert/strict')

const lib = () => import('../src/views/Public/apiDocs.mjs')

test('conditional voice choices retain outer request fields and composed response examples', async () => {
  const { schemaRows, sampleValue } = await lib()
  assert.equal(sampleValue({}, { oneOf: [{ type: 'integer' }, { type: 'string' }], example: 0 }), 0)
  const spec = {
    components: { schemas: { Base: { properties: { code: { type: 'integer', example: 0 } } } } }
  }
  const request = {
    properties: { narration: { type: 'string' } },
    required: ['narration'],
    oneOf: [{ required: ['voiceCode'] }, { required: ['voiceProfileId'] }]
  }
  assert.equal(schemaRows(spec, request)[0].name, 'narration')
  assert.deepEqual(
    sampleValue(spec, {
      allOf: [{ $ref: '#/components/schemas/Base' }, { properties: { data: { type: 'boolean' } } }]
    }),
    { code: 0, data: false }
  )
})

test('operation examples keep all HMAC headers together and preserve empty request bodies', async () => {
  const { operationExample } = await lib()
  const spec = {
    servers: [{ url: 'https://api.example.test' }],
    security: [{ TkClientId: [], TkTimestamp: [], TkNonce: [], TkSignature: [] }],
    components: {
      securitySchemes: {
        TkClientId: { type: 'apiKey', in: 'header', name: 'X-TK-Client-Id' },
        TkTimestamp: { type: 'apiKey', in: 'header', name: 'X-TK-Timestamp' },
        TkNonce: { type: 'apiKey', in: 'header', name: 'X-TK-Nonce' },
        TkSignature: { type: 'apiKey', in: 'header', name: 'X-TK-Signature' }
      }
    }
  }
  const example = operationExample(spec, '/admin-api/tk/open/v1/tiktok/connections', 'get', {})
  assert.equal(Object.keys(example.headers).length, 4)
  assert.equal(example.body, undefined)
  assert.match(example.curl, /X-TK-Signature/)
  assert.doesNotMatch(example.curl, /--data/)
})

test('public endpoints do not inherit authentication and JSON bodies retain booleans', async () => {
  const { operationExample } = await lib()
  const spec = {
    servers: [{ url: 'https://example.test' }],
    security: [{ Bearer: [] }],
    components: {}
  }
  const example = operationExample(spec, '/login', 'post', {
    security: [],
    requestBody: { content: { 'application/json': { example: { enabled: false, volume: 0.4 } } } }
  })
  assert.equal(example.headers.Authorization, undefined)
  assert.deepEqual(JSON.parse(example.body), { enabled: false, volume: 0.4 })
  assert.match(example.python, /import json/)
  assert.match(example.javascript, /JSON\.stringify/)
})

test('schema fields include nested refs without infinite recursion', async () => {
  const { schemaRows } = await lib()
  const spec = {
    components: {
      schemas: {
        Item: {
          type: 'object',
          required: ['id'],
          properties: {
            id: { type: 'integer', minimum: 1 },
            next: { $ref: '#/components/schemas/Item' }
          }
        }
      }
    }
  }
  const rows = schemaRows(spec, { $ref: '#/components/schemas/Item' })
  assert.equal(rows[0].name, 'id')
  assert.equal(rows[0].required, true)
  assert.equal(rows[0].constraint, '最小 1')
  assert.ok(rows.length < 8)
})

test('markdown parsing preserves code, tables and literal HTML as text', async () => {
  const { markdownBlocks } = await lib()
  const blocks = markdownBlocks(
    '# 接入\n\n<script>alert(1)</script>\n\n| 字段 | 类型 |\n| --- | --- |\n| id | number |\n\n```json\n{"code":0}\n```'
  )
  assert.equal(blocks[0].type, 'heading')
  assert.equal(blocks[1].text, '<script>alert(1)</script>')
  assert.deepEqual(blocks.find((b) => b.type === 'table').rows, [['id', 'number']])
  assert.equal(blocks.find((b) => b.type === 'code').text, '{"code":0}')
})

test('query and path examples encode parameters and filter search by method and summary', async () => {
  const { operationExample, searchOperations } = await lib()
  const example = operationExample(
    { servers: [{ url: 'https://example.test' }] },
    '/tasks/{taskId}',
    'get',
    {
      parameters: [
        { name: 'taskId', in: 'path', example: 789 },
        { name: 'fileName', in: 'query', example: '芒果 干.mp4' }
      ]
    }
  )
  assert.match(example.url, /\/tasks\/789\?fileName=/)
  assert.ok(!example.url.includes('芒果'))
  assert.equal(
    searchOperations([{ method: 'post', path: '/tasks', summary: '生成任务' }], 'POST 生成').length,
    1
  )
  assert.equal(
    searchOperations([{ method: 'post', path: '/tasks', summary: '生成任务' }], '下载').length,
    0
  )
})

test('file upload examples send real binary or multipart bodies', async () => {
  const { operationExample, schemaRows } = await lib()
  const spec = { servers: [{ url: 'https://example.test' }] }
  const binary = operationExample(spec, '/chunks/0', 'put', {
    requestBody: {
      content: { 'application/octet-stream': { schema: { type: 'string', format: 'binary' } } }
    }
  })
  assert.match(binary.curl, /--data-binary.*@chunk.bin/)
  assert.match(binary.python, /read_bytes/)
  assert.match(binary.javascript, /readFile/)
  const multipart = operationExample(spec, '/ocr', 'post', {
    requestBody: {
      content: {
        'multipart/form-data': {
          schema: { properties: { file: { type: 'string', format: 'binary' } } }
        }
      }
    }
  })
  assert.match(multipart.curl, /--form.*file=@image.png/)
  assert.match(multipart.python, /multipart\/form-data; boundary=/)
  assert.match(multipart.javascript, /FormData/)
  assert.equal(multipart.headers['Content-Type'], undefined)
  assert.equal(
    schemaRows(spec, {
      oneOf: [
        { properties: { code: { type: 'integer' } } },
        { properties: { error: { type: 'string' } } }
      ]
    })[0].name,
    'code'
  )
})
