const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const read = (relativePath) => fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8')

const dashboard = read('../src/views/tk/dashboard/index.vue')
const generationApi = read('../src/api/tk/generation/index.ts')
const publishCenter = read('../src/views/tk/video-publish-center/index.vue')

assert.match(dashboard, /const HOME_MATERIAL_LIMIT = 36/)
assert.match(
  dashboard,
  /libraries\.value\.slice\(0, HOME_MATERIAL_LIMIT\)/,
  'homepage material cards must be limited without truncating the source library list'
)
assert.match(dashboard, /const showBatchGenerationControls = false/)
assert.match(generationApi, /scriptTitle\?: string/)
assert.match(
  publishCenter,
  /publishForm\.title = row\.scriptTitle \|\| row\.title/,
  'generated publish title must prefer the selected script title'
)
assert.match(publishCenter, /scope\.row\.scriptTitle/, 'generation records must show the selected script title')

console.log('P0 dashboard title and materials tests passed')
