const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const source = fs.readFileSync(
  path.join(__dirname, '../src/views/tk/dashboard/index.vue'),
  'utf8'
)

const createGenerationBlock = source.slice(
  source.indexOf('const handleCreateGeneration = async () =>'),
  source.indexOf('const handleRetryGeneration = async () =>')
)

assert.match(
  createGenerationBlock,
  /if \(generationSubmittingCount\.value > 0\) \{\s*return\s*\}/,
  'a repeated click must not start a second submission'
)
assert.doesNotMatch(
  createGenerationBlock,
  /const startFreshGenerationSession = !hasActiveGenerationTasks\.value/,
  'an active task must not lock a new generation submission'
)
assert.match(
  source,
  /interface GenerationSubmissionSnapshot \{/,
  'generation values must have an explicit immutable submission snapshot'
)
assert.match(
  createGenerationBlock,
  /const submissionSnapshot = createGenerationSubmissionSnapshot\(\)/,
  'the snapshot must be captured before asynchronous generation work'
)
assert.match(
  source,
  /precheckGenerationScripts\(scripts, submissionSnapshot\)/,
  'precheck and creation must use the submission snapshot'
)
assert.match(source, /createGenerationPayload\(script, snapshot\)/)
assert.match(source, /createGeneration\(createGenerationPayload\(scripts\[0\], snapshot\)\)/)
assert.match(
  source,
  /batchGenerationTasks\.value = mergeGenerationTasks\(/,
  'a new submission must retain previously tracked tasks'
)

console.log('dashboard generation concurrency contract tests passed')
