const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const helperPath = path.join(
  __dirname,
  '../src/utils/tiktokMediaUpload.ts'
)
const apiPath = path.join(
  __dirname,
  '../src/api/tk/videoPublishCenter/index.ts'
)
const pagePath = path.join(
  __dirname,
  '../src/views/tk/video-publish-center/index.vue'
)

assert.equal(fs.existsSync(helperPath), true, 'TikTok chunk upload helper must exist')
const helperSource = fs.readFileSync(helperPath, 'utf8')
const apiSource = fs.readFileSync(apiPath, 'utf8')
const pageSource = fs.readFileSync(pagePath, 'utf8')
const controllerSource = fs.readFileSync(
  path.join(
    __dirname,
    '../../../yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/controller/admin/tiktok/TkTiktokPublishController.java'
  ),
  'utf8'
)

assert.match(helperSource, /createTikTokMediaUploadSession/)
assert.match(helperSource, /uploadTikTokMediaInChunks/)
assert.match(helperSource, /completeTikTokMediaUpload/)
assert.match(helperSource, /UPLOAD_CHUNK_RETRY_COUNT/)
assert.match(helperSource, /uploadOss/)
assert.match(
  helperSource,
  /DEFAULT_TIKTOK_UPLOAD_CHUNK_SIZE\s*=\s*1\s*\*\s*1024\s*\*\s*1024/,
  'TikTok uploads must use 1MB fallback chunks behind Cloudflare'
)
assert.match(
  helperSource,
  /uploadMediaChunk\(formData,\s*uploadId,\s*chunkIndex/,
  'Chunk requests must carry upload identity outside the multipart body as well'
)
assert.match(apiSource, /createMediaUploadSession/)
assert.match(apiSource, /uploadMediaChunk/)
assert.match(apiSource, /completeMediaUpload/)
assert.match(apiSource, /uploadMode/)
assert.match(apiSource, /objectKey/)
assert.match(
  apiSource,
  /params:\s*\{\s*uploadId,\s*chunkIndex\s*\}/,
  'Chunk API must include uploadId and chunkIndex as query parameters'
)
assert.match(
  controllerSource,
  /@RequestParam\(value\s*=\s*"uploadId",\s*required\s*=\s*false\)/,
  'Chunk endpoint must handle missing multipart fields explicitly'
)
assert.match(pageSource, /uploadTikTokMediaInChunks/)

console.log('TikTok media chunk upload tests passed')
