const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const dashboardPath = path.resolve(__dirname, '../src/views/tk/dashboard/index.vue')
const zhLocalePath = path.resolve(__dirname, '../src/locales/tk/zh-CN.ts')
const enLocalePath = path.resolve(__dirname, '../src/locales/tk/en.ts')
const generationPropertiesPath = path.resolve(
  __dirname,
  '../../../yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/framework/config/TkGenerationProperties.java'
)

const dashboard = fs.readFileSync(dashboardPath, 'utf8')
const zhLocale = fs.readFileSync(zhLocalePath, 'utf8')
const enLocale = fs.readFileSync(enLocalePath, 'utf8')
const generationProperties = fs.readFileSync(generationPropertiesPath, 'utf8')

assert.match(dashboard, /const MAX_TARGET_DURATION = 500/)
assert.match(dashboard, /支持 8-500 秒/)
assert.match(dashboard, /Supported range: 8-500 seconds\./)
assert.match(zhLocale, /支持 8-500 秒/)
assert.match(enLocale, /supports 8-500 seconds/)
assert.match(generationProperties, /private Integer maxTargetDuration = 500/)
assert.match(
  dashboard,
  /<el-button\s+plain\s+tag="a"\s+:href="audioExportResult\.audioUrl"\s+:download="audioExportDownloadName"\s+target="_blank"\s*>/
)

console.log('target duration limit tests passed')
