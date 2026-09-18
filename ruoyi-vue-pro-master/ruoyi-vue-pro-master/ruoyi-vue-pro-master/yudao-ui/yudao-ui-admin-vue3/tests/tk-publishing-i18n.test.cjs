const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')

const root = path.resolve(__dirname, '..')
const read = (relative) => fs.readFileSync(path.join(root, relative), 'utf8')

const contentDisplay = read('src/views/tk/tiktok-content-display/index.vue')
const accountStats = read('src/views/tk/tiktok-account-stats/index.vue')
const openApi = read('src/views/tk/open-api/index.vue')

test('TikTok content display uses locale keys for visible controls and messages', () => {
  assert.match(contentDisplay, /tt\('contentDisplay\.account'\)/)
  assert.match(contentDisplay, /tt\('contentDisplay\.syncSuccess'/)
  assert.doesNotMatch(contentDisplay, /label="账号"/)
  assert.doesNotMatch(contentDisplay, /同步账号公开视频/)
  assert.doesNotMatch(contentDisplay, /message\.success\('视频详情已刷新'\)/)
})

test('TikTok account stats translates dynamic sync failures', () => {
  assert.match(accountStats, /tt\('accountStats\.syncFailed'/)
  assert.doesNotMatch(accountStats, /个账号同步失败/)
})

test('Open API management uses locale keys for visible controls and dynamic labels', () => {
  assert.match(openApi, /tt\('openApi\.title'\)/)
  assert.match(openApi, /tt\('openApi\.permission\.auth'\)/)
  assert.match(openApi, /tt\('openApi\.eventStatus\.PENDING'\)/)
  assert.doesNotMatch(openApi, /<h1>开放 API 管理<\/h1>/)
  assert.doesNotMatch(openApi, /label="调用方"/)
  assert.doesNotMatch(openApi, /const labels: Record<string, string> = \{ auth: '授权'/)
})

test('new publishing locale keys exist in both language catalogs', () => {
  const zh = read('src/locales/tk/zh-CN.ts')
  const en = read('src/locales/tk/en.ts')
  for (const key of [
    'contentDisplay.account',
    'contentDisplay.syncSuccess',
    'accountStats.syncFailed',
    'openApi.title',
    'openApi.permission.auth',
    'openApi.eventStatus.PENDING'
  ]) {
    assert.match(zh, new RegExp(`['"]${key.replace('.', '\\.')}`))
    assert.match(en, new RegExp(`['"]${key.replace('.', '\\.')}`))
  }
})
