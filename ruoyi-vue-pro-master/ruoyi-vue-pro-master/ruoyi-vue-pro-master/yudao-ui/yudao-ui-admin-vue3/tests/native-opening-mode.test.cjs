const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const sourcePath = path.resolve(__dirname, '../src/views/tk/dashboard/index.vue')
const source = fs.readFileSync(sourcePath, 'utf8')

assert.match(source, /type OpeningProcessMode = 'NATIVE' \| 'STANDARD'/)
assert.match(source, /openingProcessMode: 'NATIVE'/)
assert.match(
  source,
  /v-if="createForm\.openingVideoUrl \|\| openingVideoFile" class="opening-process-mode"/
)
assert.match(source, /v-model="createForm\.openingProcessMode"/)
assert.match(source, /:options="openingProcessModeOptions"/)
assert.match(source, /openingModeNative: '保留原生（推荐）'/)
assert.match(source, /openingModeStandard: '按普通素材处理'/)
assert.match(source, /payload\.openingProcessMode = createForm\.openingProcessMode/)
assert.match(source, /formData\.append\('openingProcessMode', createForm\.openingProcessMode\)/)
assert.match(
  source,
  /createForm\.openingProcessMode = task\.openingProcessMode \|\| 'STANDARD'/
)
assert.match(source, /开头视频时长不限于 3 秒/)
assert.match(source, /Opening videos do not need to be exactly 3 seconds long\./)
assert.doesNotMatch(source, /固定作为前3秒片头/)

const openingModeItemStyle = source.match(
  /\.opening-process-mode-control :deep\(\.el-segmented__item\) \{([\s\S]*?)\n\}/
)
assert.ok(openingModeItemStyle, 'opening mode segmented item style should exist')
assert.match(openingModeItemStyle[1], /padding-inline:\s*5px/)

const openingModeLabelStyle = source.match(
  /\.opening-process-mode-control :deep\(\.el-segmented__item-label\) \{([\s\S]*?)\n\}/
)
assert.ok(openingModeLabelStyle, 'opening mode label style should exist')
assert.match(openingModeLabelStyle[1], /overflow-wrap:\s*normal/)
assert.match(openingModeLabelStyle[1], /word-break:\s*normal/)
assert.doesNotMatch(openingModeLabelStyle[1], /overflow-wrap:\s*anywhere/)

console.log('native opening mode tests passed')
