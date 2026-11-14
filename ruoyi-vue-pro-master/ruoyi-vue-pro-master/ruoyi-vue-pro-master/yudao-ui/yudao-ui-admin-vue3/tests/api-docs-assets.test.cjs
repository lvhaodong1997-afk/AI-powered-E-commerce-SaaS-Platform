const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const { execFileSync } = require('node:child_process')
const root = path.resolve(__dirname, '..')

test('download assets are reproducible from current sources', () => {
  execFileSync(process.execPath, ['scripts/build-api-docs.mjs', '--check'], {
    cwd: root,
    stdio: 'pipe'
  })
})

test('documentation bundle contains the contract, guides and complete examples', () => {
  const publicDir = path.join(root, 'public/api-reference')
  const index = JSON.parse(fs.readFileSync(path.join(publicDir, 'catalog.json')))
  const contract = index.spec
  assert.ok(contract.paths['/admin-api/tk/automation/v1/tasks'].post)
  assert.ok(index.guides.find((g) => g.id === 'quickstart'))
  const zip = fs.readFileSync(path.join(publicDir, 'clipforge-api-kit.zip'))
  assert.equal(zip.readUInt32LE(), 0x04034b50)
  for (const name of [
    'automation.py',
    'automation.mjs',
    'quickstart.md',
    'tk-platform-openapi.json'
  ]) {
    assert.ok(zip.includes(Buffer.from(name)), `missing ${name}`)
  }
  for (const file of index.downloads) {
    assert.ok(fs.statSync(path.join(publicDir, file.file)).isFile())
  }
})
