const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const axiosSource = fs.readFileSync(
  path.join(__dirname, '../src/config/axios/index.ts'),
  'utf8'
)
const axiosServiceSource = fs.readFileSync(
  path.join(__dirname, '../src/config/axios/service.ts'),
  'utf8'
)
const publishApiSource = fs.readFileSync(
  path.join(__dirname, '../src/api/tk/videoPublishCenter/index.ts'),
  'utf8'
)

assert.match(
  axiosSource,
  /const isFormData =\s+typeof FormData !== 'undefined' && otherOption\.data instanceof FormData/,
  'FormData requests must let the browser generate the multipart boundary'
)
assert.match(
  publishApiSource,
  /const res = await request\.upload<\{ data: TkTiktokPublishMediaVO \}>/,
  'TikTok media upload must unwrap the upload response explicitly'
)
assert.match(
  publishApiSource,
  /return res\.data as TkTiktokPublishMediaVO/,
  'TikTok media upload must expose the uploaded media object'
)
assert.match(
  axiosServiceSource,
  /Promise\.reject\(new Error\(msg\)\)/,
  'business errors must preserve the backend message for upload feedback'
)

console.log('TikTok upload request tests passed')
