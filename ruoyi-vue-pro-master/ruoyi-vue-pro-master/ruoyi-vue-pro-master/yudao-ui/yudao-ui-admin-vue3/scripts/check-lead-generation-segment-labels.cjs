const fs = require('fs')
const path = require('path')

const materialLibraryPath = path.resolve(__dirname, '../src/views/tk/material-library/index.vue')
const i18nPath = path.resolve(__dirname, '../src/utils/tkI18n.ts')

const materialSource = fs.readFileSync(materialLibraryPath, 'utf8')
const i18nSource = fs.readFileSync(i18nPath, 'utf8')

const expectedLeadSegments = [
  ['S1_HOOK', 'S1 黄金开场', 'S1'],
  ['S2_PAIN', 'S2 背景交代', 'S2'],
  ['S3_REVEAL', 'S3 场景进入', 'S3'],
  ['S4_DEMO', 'S4 过程展示', 'S4'],
  ['S5_PROOF', 'S5 结果证明', 'S5'],
  ['S6_DETAIL', 'S6 案例验证', 'S6'],
  ['S7_LIFESTYLE', 'S7 工具展示', 'S7'],
  ['GENERAL', 'S8 转化引导', 'S8']
]

const expectedTranslations = [
  ['S1 黄金开场', 'S1 Hook Opening'],
  ['S2 背景交代', 'S2 Background Context'],
  ['S3 场景进入', 'S3 Scene Entry'],
  ['S4 过程展示', 'S4 Process Demo'],
  ['S5 结果证明', 'S5 Results Proof'],
  ['S6 案例验证', 'S6 Case Validation'],
  ['S7 工具展示', 'S7 Tool Showcase'],
  ['S8 转化引导', 'S8 Conversion CTA']
]

let failed = false

for (const [value, label, shortLabel] of expectedLeadSegments) {
  const pattern = new RegExp(`${value}: \\{ label: '${label}', shortLabel: '${shortLabel}' \\}`)
  if (!pattern.test(materialSource)) {
    console.error(`FAIL missing lead segment label ${value} -> ${label}`)
    failed = true
  } else {
    console.log(`PASS lead segment label ${value}`)
  }
}

if (!/引流视频要求 S1-S8/.test(materialSource)) {
  console.error('FAIL lead key segment rule should mention S1-S8')
  failed = true
} else {
  console.log('PASS lead key segment rule mentions S1-S8')
}

if (/通用引流素材/.test(materialSource)) {
  console.error('FAIL legacy lead general label should not remain')
  failed = true
} else {
  console.log('PASS legacy lead general label removed')
}

if (!/\{\{ getSegmentOptionShortLabel\(item\) \}\}/.test(materialSource)) {
  console.error('FAIL segment summary should use lead-generation short labels')
  failed = true
} else {
  console.log('PASS segment summary uses lead-generation short labels')
}

if (/\{\{ localizeUiText\(item\.shortLabel\) \}\}/.test(materialSource)) {
  console.error('FAIL segment summary still reads raw item.shortLabel')
  failed = true
} else {
  console.log('PASS segment summary no longer reads raw item.shortLabel')
}

for (const [zh, en] of expectedTranslations) {
  const pattern = new RegExp(`'${zh}': '${en}'`)
  if (!pattern.test(i18nSource)) {
    console.error(`FAIL missing English fallback ${zh} -> ${en}`)
    failed = true
  } else {
    console.log(`PASS English fallback ${zh}`)
  }
}

if (failed) {
  process.exit(1)
}
