const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const root = path.resolve(__dirname, '..')
const read = (relativePath) => fs.readFileSync(path.join(root, relativePath), 'utf8')

const apiSource = read('src/api/tk/videoPublishCenter/index.ts')
const pageSource = read('src/views/tk/video-publish-center/index.vue')

assert.match(apiSource, /scheduledAt\?: string/)
assert.match(apiSource, /url:\s*['"]\/tk\/tiktok-publish\/reschedule['"]/)
assert.match(apiSource, /url:\s*['"]\/tk\/tiktok-publish\/cancel['"]/)

assert.match(pageSource, /publishForm\.scheduleMode/)
assert.match(pageSource, /publishForm\.scheduledAt/)
assert.match(pageSource, /type=['"]datetime['"]/)
assert.match(pageSource, /publishForm\.accountIds\.length\s*!==\s*1/)
assert.match(pageSource, /publishForm\.postMode\s*!==\s*['"]DIRECT_POST['"]/)
assert.match(pageSource, /scope\.row\.status\s*===\s*['"]SCHEDULED['"]/)
assert.match(pageSource, /scope\.row\.status\s*!==\s*['"]SCHEDULED['"].*syncTask/s)
assert.match(pageSource, /rescheduleTask/)
assert.match(pageSource, /cancelScheduledTask/)

console.log('TikTok app scheduled publish frontend contract passed')
