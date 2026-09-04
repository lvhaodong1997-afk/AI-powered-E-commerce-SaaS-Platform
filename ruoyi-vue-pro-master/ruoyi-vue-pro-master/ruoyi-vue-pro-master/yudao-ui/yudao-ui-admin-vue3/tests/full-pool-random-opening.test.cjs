const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const sourcePath = path.resolve(__dirname, '../src/views/tk/dashboard/index.vue')
const source = fs.readFileSync(sourcePath, 'utf8')

assert.match(
  source,
  /if \(openingVideoFile\.value\) \{\s*payload\.openingVideoName = openingVideoFile\.value\.name\s*\}/,
  'local opening file must be represented in the JSON precheck payload'
)
assert.match(source, /openingFullPoolRandomHint:/)
assert.match(source, /v-if="createForm\.openingVideoUrl \|\| openingVideoFile"/)
assert.match(
  source,
  /createForm\.openingProcessMode === 'NATIVE'[\s\S]*copy\.openingNativeDurationHint[\s\S]*isFullPoolRandomMode[\s\S]*copy\.openingFullPoolRandomHint[\s\S]*copy\.openingFullVideoHint/
)
assert.match(
  source,
  /全素材随机拼接时，该视频固定放在最前面，后续从全部素材中随机拼接。/
)
assert.match(
  source,
  /In Random pool mode, this video is placed first; later clips are selected randomly from the full material pool\./
)

console.log('full pool random opening tests passed')
