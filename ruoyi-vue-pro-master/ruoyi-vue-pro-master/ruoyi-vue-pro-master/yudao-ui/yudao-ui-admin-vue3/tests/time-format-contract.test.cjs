const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const root = path.resolve(__dirname, '..')
const read = (relativePath) => fs.readFileSync(path.join(root, relativePath), 'utf8')

const formatTime = read('src/utils/formatTime.ts')
assert.match(formatTime, /export function formatTimestamp\(/)
assert.match(formatTime, /1_000_000_000_000/)

const pageExpectations = [
  ['src/views/tk/generation/index.vue', /formatTimestamp\([^)]*createTime/],
  ['src/views/tk/generation-batch/index.vue', /formatTimestamp\([^)]*createTime/],
  ['src/views/tk/data-dashboard/index.vue', /formatTimestamp\([^)]*createTime/],
  ['src/views/tk/company/index.vue', /formatTimestamp\([^)]*createTime/],
  ['src/views/tk/business-log/index.vue', /formatTimestamp\([^)]*createTime/],
  ['src/views/tk/generation-route/index.vue', /formatTimestamp\([^)]*(createTime|updateTime)/],
  ['src/views/tk/open-api/index.vue', /formatTimestamp\([^)]*(createTime|updateTime|nextRetryTime|deliveredTime)/],
  ['src/views/tk/video-publish-center/index.vue', /formatTimestamp\([^)]*createTime/],
  ['src/views/tk/tiktok-account-stats/index.vue', /formatTimestamp\([^)]*(dataUpdatedAt|statsUpdatedAt)/],
  ['src/views/tk/video-publish-center/components/MetaPublishPanel.vue', /formatTimestamp\([^)]*createTime/]
]

for (const [relativePath, pattern] of pageExpectations) {
  const source = read(relativePath)
  assert.match(source, pattern, `${relativePath} should format visible date fields`)
}

const forbiddenRawTimeOutput = [
  ['src/views/tk/generation/index.vue', /<el-table-column[^>]+prop="createTime"[^>]*\/>/],
  ['src/views/tk/generation-batch/index.vue', /row\.createTime \|\| '-'/],
  ['src/views/tk/data-dashboard/index.vue', /<el-table-column[^>]+prop="createTime"[^>]*\/>/],
  ['src/views/tk/company/index.vue', /<el-table-column[^>]+prop="createTime"[^>]*\/>/],
  ['src/views/tk/business-log/index.vue', /<el-table-column[^>]+prop="createTime"[^>]*\/>/],
  ['src/views/tk/generation-route/index.vue', /<el-table-column[^>]+prop="(createTime|updateTime)"[^>]*\/>/],
  ['src/views/tk/open-api/index.vue', /valueOrDash\((row|eventDetail)\.(createTime|updateTime|nextRetryTime|deliveredTime)\)/],
  ['src/views/tk/video-publish-center/components/MetaPublishPanel.vue', /<el-table-column[^>]+prop="createTime"[^>]*\/>/]
]

for (const [relativePath, pattern] of forbiddenRawTimeOutput) {
  assert.doesNotMatch(read(relativePath), pattern, `${relativePath} still renders a raw date field`)
}

console.log('time-format-contract: passed')
