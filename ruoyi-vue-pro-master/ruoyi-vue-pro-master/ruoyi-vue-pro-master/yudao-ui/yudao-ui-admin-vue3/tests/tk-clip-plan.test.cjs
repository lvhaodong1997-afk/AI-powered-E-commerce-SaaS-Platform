const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const ts = require('typescript')

const sourcePath = path.resolve(__dirname, '../src/views/tk/generation/clipPlan.ts')
const source = fs.readFileSync(sourcePath, 'utf8')
const compiled = ts.transpileModule(source, {
  compilerOptions: {
    module: ts.ModuleKind.CommonJS,
    target: ts.ScriptTarget.ES2020
  }
}).outputText

const moduleExports = {}
const moduleObject = { exports: moduleExports }
const runner = new Function('exports', 'module', 'require', compiled)
runner(moduleExports, moduleObject, require)

const {
  buildClipPlanDetails,
  buildClipSectionSummary,
  formatClipRange,
  parseClipPlan
} = moduleObject.exports

const clipPlan = JSON.stringify([
  {
    orderNo: 1,
    sourceType: 'OPENING',
    fileName: 'hook.mp4',
    section: 'S1_HOOK',
    sectionName: '黄金3秒',
    startSecond: 0,
    durationSecond: 3,
    reason: '开头视频片段'
  },
  {
    orderNo: 2,
    sourceType: 'MATERIAL',
    materialVideoId: 18,
    fileName: 'demo-01.mp4',
    section: 'S4_DEMO',
    startSecond: 4,
    durationSecond: 3,
    matchScore: 2,
    reason: '使用演示，标签或画面方向命中 2 个，优先排序，裁剪 4-7 秒'
  },
  {
    orderNo: 3,
    sourceType: 'MATERIAL',
    materialVideoId: 19,
    fileName: 'demo-02.mp4',
    section: 'S4_DEMO',
    startSecond: 7,
    durationSecond: 4,
    matchScore: 1
  }
])

assert.equal(formatClipRange(4, 3), '4-7s')
assert.deepEqual(parseClipPlan('bad json'), [])

const summary = buildClipSectionSummary(clipPlan)
assert.deepEqual(
  summary.map((item) => [item.section, item.label, item.duration]),
  [
    ['S1_HOOK', 'S1 黄金3秒', 3],
    ['S4_DEMO', 'S4 使用演示', 7]
  ]
)

const details = buildClipPlanDetails(clipPlan)
assert.equal(details.length, 3)
assert.deepEqual(details[1], {
  orderNo: 2,
  sourceType: 'MATERIAL',
  sourceTypeLabel: '素材库',
  materialVideoId: 18,
  materialVideoIdText: '18',
  fileName: 'demo-01.mp4',
  section: 'S4_DEMO',
  sectionLabel: 'S4 使用演示',
  clipRange: '4-7s',
  durationSecond: 3,
  matchScoreText: '2',
  reason: '使用演示，标签或画面方向命中 2 个，优先排序，裁剪 4-7 秒'
})

console.log('tk clip plan tests passed')
