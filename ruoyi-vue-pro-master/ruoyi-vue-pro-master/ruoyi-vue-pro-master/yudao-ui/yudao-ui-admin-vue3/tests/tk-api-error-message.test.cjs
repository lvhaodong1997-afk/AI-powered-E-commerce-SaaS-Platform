const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')

const servicePath = path.join(__dirname, '..', 'src', 'config', 'axios', 'service.ts')
const serviceSource = fs.readFileSync(servicePath, 'utf8')

test('maps generic TK 500 errors to actionable messages by endpoint', () => {
  assert.match(
    serviceSource,
    /getTkApiErrorMessage\(config\.url, msg, t\('sys\.api\.errMsg500'\)\)/
  )
  assert.match(serviceSource, /TikTok 公开视频同步失败，请检查账号授权和 video\.list 权限后重试/)
  assert.match(serviceSource, /TikTok 发布状态同步失败，请稍后重试/)
})
