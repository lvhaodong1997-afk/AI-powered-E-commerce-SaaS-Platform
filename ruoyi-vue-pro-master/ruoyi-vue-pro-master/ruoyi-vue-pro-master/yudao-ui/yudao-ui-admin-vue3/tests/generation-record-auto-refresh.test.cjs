const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const source = fs.readFileSync(
  path.join(__dirname, '../src/views/tk/generation/index.vue'),
  'utf8'
)

assert.match(
  source,
  /const refreshAnalysisStatuses = async \(\) =>/,
  'AI analysis records must refresh running rows without a full page reload'
)

assert.match(
  source,
  /const mergeAnalysisStatus = \(items: TkReferenceAnalysisStatusVO\[\]\) =>/,
  'AI analysis polling must merge lightweight status responses into existing rows'
)

assert.match(
  source,
  /document\.hidden/,
  'record polling must pause while the browser tab is hidden'
)

assert.match(
  source,
  /document\.addEventListener\('visibilitychange', handleRecordVisibilityChange\)/,
  'record pages must resume sync when the browser tab becomes visible again'
)

assert.match(
  source,
  /onBeforeUnmount\(\(\) => \{[\s\S]*clearAnalysisPolling\(\)[\s\S]*clearGenerationPolling\(\)[\s\S]*removeEventListener\('visibilitychange', handleRecordVisibilityChange\)/,
  'record pages must clear all polling timers and visibility listeners on unmount'
)
