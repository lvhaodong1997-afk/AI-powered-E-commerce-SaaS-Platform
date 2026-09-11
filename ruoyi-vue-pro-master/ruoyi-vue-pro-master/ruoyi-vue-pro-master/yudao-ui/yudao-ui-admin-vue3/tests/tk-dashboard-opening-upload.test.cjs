const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const read = (relativePath) => fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')

const dashboard = read('../src/views/tk/dashboard/index.vue')
const generationApi = read('../src/api/tk/generation/index.ts')
const generationPayloadBlock = dashboard.slice(
  dashboard.indexOf('const createGenerationPayload'),
  dashboard.indexOf('const createAudioExportRequestId')
)

assert.match(dashboard, /uploadFileInChunks/, 'dashboard must use the shared chunk uploader')
assert.match(generationPayloadBlock, /payload\.openingUploadId/, 'generation must bind the completed opening upload session')
assert.doesNotMatch(
  generationPayloadBlock,
  /TkGenerationApi\.createGenerationWithOpening\(/,
  'dashboard must send JSON generation requests after opening upload completes'
)
assert.doesNotMatch(
  generationPayloadBlock,
  /formData\.append\(['"]openingVideoFile['"]|new FormData\(\)/,
  'dashboard must not put the opening video into the generation request'
)
assert.match(generationApi, /openingUploadId\?: string/)
assert.match(generationApi, /opening\/session\/create/)
assert.match(generationApi, /opening\/chunk/)
assert.match(generationApi, /opening\/session\/complete/)
assert.match(generationApi, /opening\/session\/\$\{uploadId\}/)

console.log('dashboard opening upload contract tests passed')
