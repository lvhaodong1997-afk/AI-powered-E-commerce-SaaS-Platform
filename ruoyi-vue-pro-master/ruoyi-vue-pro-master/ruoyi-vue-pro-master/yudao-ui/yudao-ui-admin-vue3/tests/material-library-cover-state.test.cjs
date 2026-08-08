const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const source = fs.readFileSync(
  path.join(__dirname, '../src/views/tk/material-library/index.vue'),
  'utf8'
)

assert.match(
  source,
  /const coverErrorKey = \(video: TkMaterialVideoVO\) =>/,
  'cover load failures must be tracked by video id plus cover URL'
)

assert.match(
  source,
  /!brokenVideoCovers\[coverErrorKey\(scope\.row\)\]/,
  'the thumbnail must retry when a refreshed signed cover URL changes'
)

assert.match(
  source,
  /@error="markVideoCoverBroken\(scope\.row\)"/,
  'cover error handling must receive the whole row so it can include the current URL'
)

assert.match(
  source,
  /if \(current\) \{[\s\S]*?selectedLibrary\.value = current[\s\S]*?videoQuery\.libraryId = current\.id as any[\s\S]*?await getVideoList\(\)/,
  'refreshing an existing selected library must also refresh its video list'
)
