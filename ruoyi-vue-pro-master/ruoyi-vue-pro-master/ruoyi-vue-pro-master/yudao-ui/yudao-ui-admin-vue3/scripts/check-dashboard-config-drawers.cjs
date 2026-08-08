const fs = require('fs')
const path = require('path')

const dashboardPath = path.resolve(__dirname, '../src/views/tk/dashboard/index.vue')
const source = fs.readFileSync(dashboardPath, 'utf8')

const checks = [
  {
    name: 'opening hook settings are wrapped in a compact drawer',
    pattern: /class="config-drawer opening-config-drawer"[\s\S]*?openingConfigExpanded/
  },
  {
    name: 'subtitle settings are wrapped in a compact drawer',
    pattern: /class="config-drawer subtitle-config-drawer"[\s\S]*?subtitleConfigExpanded/
  },
  {
    name: 'voice settings are wrapped in a compact drawer',
    pattern: /class="config-drawer voice-config-drawer"[\s\S]*?voiceConfigExpanded/
  },
  {
    name: 'analysis result is wrapped in a compact drawer',
    pattern: /class="analysis-result-drawer"[\s\S]*?analysisResultExpanded/
  },
  {
    name: 'drawers expose summary status text',
    pattern: /voiceConfigSummary[\s\S]*?openingConfigSummary[\s\S]*?subtitleConfigSummary[\s\S]*?analysisResultSummary/
  },
  {
    name: 'drawers use chevron icons for expand and collapse',
    pattern: /config-drawer-chevron[\s\S]*?ep:arrow-down/
  },
  {
    name: 'old always-expanded opening label is not directly followed by upload widget',
    pattern: /<div v-show="openingConfigExpanded" class="config-drawer-body">[\s\S]*?<el-upload/
  },
  {
    name: 'old always-expanded subtitle body is gated by subtitle drawer state',
    pattern: /<template v-if="createForm\.subtitleEnabled && subtitleConfigExpanded">/
  },
  {
    name: 'voice select body is gated by voice drawer state',
    pattern: /<div v-show="voiceConfigExpanded" class="config-drawer-body voice-config-body">/
  },
  {
    name: 'material overview actions navigate to real material page',
    pattern: /@click="goMaterialLibrary\(\)"[\s\S]*?@click="goMaterialLibraryUpload"/
  }
]

let failed = false
for (const check of checks) {
  if (!check.pattern.test(source)) {
    console.error(`FAIL ${check.name}`)
    failed = true
  } else {
    console.log(`PASS ${check.name}`)
  }
}

if (failed) {
  process.exit(1)
}
