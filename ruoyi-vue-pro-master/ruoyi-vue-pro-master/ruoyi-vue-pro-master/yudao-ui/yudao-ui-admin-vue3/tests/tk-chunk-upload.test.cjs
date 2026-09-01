const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const utilityPath = path.join(__dirname, '../src/utils/tkChunkUpload.ts')
const materialPagePath = path.join(__dirname, '../src/views/tk/material-library/index.vue')
const tiktokHelperPath = path.join(__dirname, '../src/utils/tiktokMediaUpload.ts')

assert.equal(fs.existsSync(utilityPath), true, 'shared chunk upload utility must exist')
const utilitySource = fs.readFileSync(utilityPath, 'utf8')
const materialSource = fs.readFileSync(materialPagePath, 'utf8')
const tiktokSource = fs.readFileSync(tiktokHelperPath, 'utf8')

assert.match(utilitySource, /createSession/)
assert.match(utilitySource, /uploadChunk/)
assert.match(utilitySource, /complete/)
assert.match(utilitySource, /cancel/)
assert.match(utilitySource, /retry/i)
assert.match(utilitySource, /uploadedChunks/)
assert.match(utilitySource, /error\?\.(?:msg|message)/)
assert.match(materialSource, /tkChunkUpload|uploadFileInChunks/)
assert.match(tiktokSource, /tkChunkUpload|uploadFileInChunks/)

console.log('Shared chunk upload tests passed')
