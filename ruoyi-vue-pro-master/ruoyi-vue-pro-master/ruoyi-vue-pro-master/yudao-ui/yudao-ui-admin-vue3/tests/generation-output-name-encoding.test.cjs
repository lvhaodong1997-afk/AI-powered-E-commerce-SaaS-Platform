const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const sourcePath = path.resolve(__dirname, '../src/utils/tkGenerationOutputName.ts')
const source = fs.readFileSync(sourcePath, 'utf8')

assert.match(source, /const decodeContentDispositionFileName = \(value: string\)/)
assert.ok(source.includes('/%[0-9a-f]{2}/i'))
assert.match(source, /decodeContentDispositionFileName\(utf8Match\[1\]\)/)
assert.doesNotMatch(source, /return decodeURIComponent\(utf8Match\[1\]\)\.trim\(\)/)

console.log('generation output name encoding tests passed')
