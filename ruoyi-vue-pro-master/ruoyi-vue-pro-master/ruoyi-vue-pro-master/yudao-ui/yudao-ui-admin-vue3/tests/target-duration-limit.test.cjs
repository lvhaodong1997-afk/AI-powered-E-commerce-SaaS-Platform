const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const dashboardPath = path.resolve(__dirname, '../src/views/tk/dashboard/index.vue')
const zhLocalePath = path.resolve(__dirname, '../src/locales/tk/zh-CN.ts')
const enLocalePath = path.resolve(__dirname, '../src/locales/tk/en.ts')

const dashboard = fs.readFileSync(dashboardPath, 'utf8')
const zhLocale = fs.readFileSync(zhLocalePath, 'utf8')
const enLocale = fs.readFileSync(enLocalePath, 'utf8')

assert.match(dashboard, /const MAX_TARGET_DURATION = 180/)
assert.match(dashboard, /支持 8-180 秒/)
assert.match(dashboard, /Supported range: 8-180 seconds\./)
assert.match(zhLocale, /支持 8-180 秒/)
assert.match(enLocale, /supports 8-180 seconds/)

console.log('target duration limit tests passed')
