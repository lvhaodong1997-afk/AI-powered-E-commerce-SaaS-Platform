const fs = require('fs')
const path = require('path')

const sourcePath = path.resolve(__dirname, '../src/views/tk/material-library/index.vue')
const source = fs.readFileSync(sourcePath, 'utf8')

const checks = [
  ['workbench shell', /class="material-workbench"/],
  ['library sidebar', /class="library-panel"/],
  ['library card list', /class="library-card-list"/],
  ['selected library detail panel', /class="library-detail"/],
  ['operation status metrics', /class="ops-metrics"/],
  ['segment completeness rail', /class="segment-health"/],
  ['compact empty state', /class="[^"]*empty-workbench[^"]*"/],
  ['preview drawer', /<el-drawer[\s\S]*class="material-video-preview-drawer"/],
  ['drawer segment editor', /handlePreviewSegmentTypeChange/],
  ['upload progress summary panel', /class="upload-progress-panel"/],
  ['selected batch bar', /class="selected-action-bar"/]
]

let failed = false
for (const [name, pattern] of checks) {
  if (pattern.test(source)) {
    console.log(`PASS ${name}`)
  } else {
    console.error(`FAIL ${name}`)
    failed = true
  }
}

if (/<ContentWrap>\s*<el-table v-loading="loading" :data="list"/.test(source)) {
  console.error('FAIL library table should not remain as the primary layout')
  failed = true
} else {
  console.log('PASS library table is no longer the primary layout')
}

if (failed) {
  process.exit(1)
}
