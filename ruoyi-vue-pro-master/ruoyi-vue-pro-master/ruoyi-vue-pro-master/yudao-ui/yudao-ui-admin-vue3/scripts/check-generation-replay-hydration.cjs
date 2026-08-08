const fs = require('fs')
const path = require('path')

const dashboardPath = path.resolve(__dirname, '../src/views/tk/dashboard/index.vue')
const source = fs.readFileSync(dashboardPath, 'utf8')

const checks = [
  {
    name: 'manual lead generation internal source is recognized',
    pattern: /MANUAL_LEAD_GENERATION_SOURCE_PREFIX\s*=\s*['"]manual-lead-generation:\/\//,
  },
  {
    name: 'generation replay no longer requires sourceUrl for manual lead generation records',
    pattern: /if\s*\(!task\.libraryId\)\s*{[\s\S]*?missingReplayGeneration/,
  },
  {
    name: 'manual lead generation replay clears internal sourceUrl instead of showing it',
    pattern: /createForm\.sourceUrl\s*=\s*isManualLeadGenerationSource\(task\.sourceUrl\)\s*\?\s*''\s*:\s*\(task\.sourceUrl\s*\|\|\s*''\)/,
  },
  {
    name: 'manual lead generation replay restores prompt or script text',
    pattern: /manualLeadScriptText\.value\s*=\s*\(task\.promptText\s*\|\|\s*task\.scriptText\s*\|\|\s*''\)\.trim\(\)/,
  },
  {
    name: 'custom voice replay restores voiceProfileId selection',
    pattern: /createForm\.voiceCode\s*=\s*`custom:\$\{task\.voiceProfileId\}`/,
  },
  {
    name: 'custom voices are loaded before matching replay voice profile',
    pattern: /await\s+loadCustomVoices\(\)/,
  },
  {
    name: 'unknown replay voice codes are added as historical voice options',
    pattern: /historicalVoiceOptions[\s\S]*?ensureHistoricalVoiceOption[\s\S]*?copy\.value\.historicalVoice/,
  },
  {
    name: 'voice select renders historical voice option group',
    pattern: /<el-option-group v-if="historicalVoiceOptions\.length" :label="copy\.historicalVoiceGroup">/,
  },
  {
    name: 'analysis result drawer is hidden until analysis starts or has a result',
    pattern:
      /v-if="shouldShowAnalysisResultDrawer"[\s\S]*?class="analysis-result-drawer"[\s\S]*?const shouldShowAnalysisResultDrawer = computed\(\s*\(\) =>[\s\S]*?analysisResultRunning\.value[\s\S]*?analysisProgress\.failed[\s\S]*?Boolean\(referenceAnalysis\.value\?\.id\)/,
  },
  {
    name: 'analysis preview body is hidden until analysis starts or has a result',
    pattern:
      /v-if="shouldShowAnalysisBody"[\s\S]*?class="analysis-body"[\s\S]*?const shouldShowAnalysisBody = computed\(\s*\(\) => shouldShowAnalysisResultDrawer\.value\)/,
  },
  {
    name: 'reference preview card only renders real preview media',
    pattern:
      /v-if="shouldShowReferenceCard"[\s\S]*?class="reference-card"[\s\S]*?const shouldShowReferenceCard = computed\(\s*\(\) => Boolean\(referencePreviewUrl\.value \|\| referenceCoverUrl\.value\)\)/,
  },
  {
    name: 'analysis body uses a single-column layout without preview media',
    pattern:
      /:class="\{ single: !shouldShowReferenceCard \}"[\s\S]*?class="analysis-result-drawer"[\s\S]*?:class="\{ expanded: analysisResultExpanded, full: !shouldShowReferenceCard \}"[\s\S]*?\.analysis-body\.single\s*{[\s\S]*?grid-template-columns: minmax\(0, 1fr\)/,
  },
  {
    name: 'workflow steps expose completed current and pending states',
    pattern:
      /:class="\[`status-\$\{flowStepStatus\(index\)\}`[\s\S]*?flowStepStatus\(index\) === 'current'[\s\S]*?const flowStepStatus = \(index: number\): FlowStepStatus =>[\s\S]*?return 'completed'[\s\S]*?return 'current'[\s\S]*?return 'pending'/,
  },
  {
    name: 'workflow steps render number and status labels',
    pattern:
      /class="flow-step-number"[\s\S]*?formatFlowStepNumber\(index\)[\s\S]*?class="flow-status-label"[\s\S]*?flowStepStatusLabel\(index\)[\s\S]*?const formatFlowStepNumber = \(index: number\) => String\(index \+ 1\)\.padStart\(2, '0'\)/,
  },
  {
    name: 'workflow connector lines indicate completed progress',
    pattern:
      /class="flow-connector"[\s\S]*?:class="\{ done: index < activeStep \}"[\s\S]*?\.flow-connector\.done\s*{[\s\S]*?background: linear-gradient\(90deg, #22c55e, #3b82f6\)/,
  },
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
