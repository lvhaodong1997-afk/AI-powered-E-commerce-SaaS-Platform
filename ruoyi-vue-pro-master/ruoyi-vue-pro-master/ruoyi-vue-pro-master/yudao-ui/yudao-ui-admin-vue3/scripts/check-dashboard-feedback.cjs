const fs = require('fs')
const path = require('path')

const dashboardPath = path.resolve(__dirname, '../src/views/tk/dashboard/index.vue')
const source = fs.readFileSync(dashboardPath, 'utf8')
const useMessagePath = path.resolve(__dirname, '../src/hooks/web/useMessage.ts')
const useMessageSource = fs.readFileSync(useMessagePath, 'utf8')

const checks = [
  {
    name: 'dashboard uses inline field feedback',
    pass: source.includes('analysis-field-message') && source.includes('analysisValidation.sourceUrl')
  },
  {
    name: 'sample format is shown inline instead of top message',
    pass: source.includes('sampleInfoVisible') && !source.includes('message.info(copy.value.sampleInfo)')
  },
  {
    name: 'required analysis fields do not use global warning toasts',
    pass:
      !source.includes('message.warning(copy.value.inputLinkWarning)') &&
      !source.includes('message.warning(copy.value.selectLibraryWarning)')
  },
  {
    name: 'reanalyze confirmation is contextual',
    pass: source.includes('<el-popconfirm') && source.includes('handleForceReanalyze')
  },
  {
    name: 'system messages avoid the top navigation',
    pass: useMessageSource.includes('offset: MESSAGE_OFFSET') && useMessageSource.includes('showClose: true')
  }
]

const failed = checks.filter((check) => !check.pass)

if (failed.length) {
  console.error('Dashboard feedback checks failed:')
  failed.forEach((check) => console.error(`- ${check.name}`))
  process.exit(1)
}

console.log(`Dashboard feedback checks passed: ${checks.length}/${checks.length}`)
