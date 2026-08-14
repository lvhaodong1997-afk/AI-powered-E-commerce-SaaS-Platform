const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const source = fs.readFileSync(
  path.join(__dirname, '../src/views/tk/dashboard/index.vue'),
  'utf8'
)

assert.match(
  source,
  /const getBgmDisplayName =/,
  'BGM options must use a dedicated display-name helper'
)

assert.match(
  source,
  /item\.sourceType === 'USER'/,
  'user-uploaded BGM names must be derived from the uploaded file name'
)

assert.match(
  source,
  /decodeURIComponent\(/,
  'BGM display names must decode file URLs before showing them'
)

assert.match(
  source,
  /TkBgmAssetApi\.upload\(rawFile\.name,/,
  'BGM upload must preserve the original file name'
)

