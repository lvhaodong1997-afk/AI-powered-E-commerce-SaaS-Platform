const assert = require('node:assert/strict')
const { test } = require('node:test')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')
const ts = require('typescript')

const source = fs.readFileSync(path.join(__dirname, '../src/utils/tiktokPublishDiagnostics.ts'), 'utf8')
const context = { exports: {} }
vm.runInNewContext(ts.transpileModule(source, { compilerOptions: {
  target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS
} }).outputText, context)
const { publishTransferLabel, publishQueueStageLabel, publishCallbackLabel } = context.exports

test('planned pull does not conceal an actual file upload', () => {
  assert.equal(publishTransferLabel({ transferPolicy: 'OSS_PULL_V1', uploadSource: 'FILE_UPLOAD' }), '服务器上传（已回退）')
  assert.equal(publishTransferLabel({ transferPolicy: 'OSS_PULL_V2_STRICT', uploadSource: 'PULL_FROM_URL' }), '平台直接拉取')
  assert.equal(publishTransferLabel({ transferPolicy: 'OSS_PULL_V2_STRICT' }), '等待直拉校验')
})
test('published result and callback retry have separate labels', () => {
  assert.equal(publishQueueStageLabel({ status: 'SUCCESS', phase: 'CONFIRMED_TERMINAL' }), '发布成功')
  assert.equal(publishCallbackLabel({ callbackStatus: 'RETRYING', callbackHttpStatus: 503 }), '通知重试中（HTTP 503）')
  assert.equal(publishCallbackLabel({ callbackStatus: 'DELIVERED' }), '通知已送达')
})
test('durable preflight and platform download have distinct stages', () => {
  assert.equal(publishQueueStageLabel({ status: 'PENDING', tiktokStatus: 'PULL_PREFLIGHT_RETRY' }), '链接校验等待重试')
  assert.equal(publishQueueStageLabel({ status: 'PROCESSING', tiktokStatus: 'PROCESSING_DOWNLOAD' }), '平台下载中')
  assert.equal(publishQueueStageLabel({ status: 'PROCESSING', tiktokStatus: 'RECOVERY_REQUIRED' }), '等待后台核验')
})
