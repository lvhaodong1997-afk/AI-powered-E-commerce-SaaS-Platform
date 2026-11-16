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


test('material guide and smoke runner ship in an isolated reproducible documentation bundle', () => {
  const os = require('node:os')
  const output = fs.mkdtempSync(path.join(os.tmpdir(), 'material-api-docs-'))
  try {
    execFileSync(process.execPath, ['scripts/build-api-docs.mjs', '--output-dir', output], { cwd: root, stdio: 'pipe' })
    const catalog = JSON.parse(fs.readFileSync(path.join(output, 'catalog.json')))
    assert.equal(catalog.version, '2026-11-16')
    assert.ok(catalog.guides.find((guide) => guide.id === 'material-library'))
    for (const file of ['material-library.md', 'examples/material-library-smoke.mjs']) {
      assert.ok(catalog.downloads.some((item) => item.file === file), file + ' must be downloadable')
      assert.ok(fs.readFileSync(path.join(output, 'clipforge-api-kit.zip')).includes(Buffer.from(file)))
      assert.equal(fs.readFileSync(path.join(output, file), 'utf8'), fs.readFileSync(path.resolve(root, '../../../../../docs/api', file), 'utf8').replaceAll('\r\n', '\n'))
    }
    assert.ok(catalog.guides.some((guide) => guide.id === 'tiktok-integration'))
    execFileSync(process.execPath, ['scripts/build-api-docs.mjs', '--output-dir', output, '--check'], { cwd: root, stdio: 'pipe' })
  } finally { fs.rmSync(output, { recursive: true, force: true }) }
})

test('Postman material writes are one-shot guarded and capture returned resource IDs', () => {
  const collection = JSON.parse(fs.readFileSync(path.resolve(root, '../../../../../docs/api/tk-automation.postman_collection.json')))
  const folder = collection.item.find((item) => item.name === '素材库管理与视频上传')
  assert.ok(folder, 'material management requests must be present')
  const requests = folder.item
  const required = ['/tk/material-library/create', '/tk/material-library/update', '/tk/material-library/delete', '/tk/material-video/upload', '/tk/material-video/usage-phase', '/tk/material-video/segment-type', '/tk/material-video/delete-batch', '/tk/upload/material-video/session/create', '/tk/upload/material-video/chunk', '/tk/upload/material-video/session/complete']
  for (const suffix of required) assert.ok(requests.some((item) => item.request.url.raw.includes(suffix)), suffix)
  const vm = require('node:vm')
  for (const item of requests.filter((item) => item.request.method !== 'GET')) {
    const script = item.event.find((event) => event.listen === 'prerequest').script.exec.join('\n')
    const variables = new Map([['allowMaterialWrite', 'false']])
    let skipped = false
    const pm = { environment: {get: (key) => variables.get(key), set: (key, value) => variables.set(key, value)}, execution: {skipRequest: () => {skipped = true}} }
    assert.throws(() => vm.runInNewContext(script, {pm}), /allowMaterialWrite/)
    assert.ok(skipped, item.name + ' must skip request by default')
    variables.set('allowMaterialWrite', 'true')
    try { vm.runInNewContext(script, {pm}) } catch (_) {}
    assert.equal(variables.get('allowMaterialWrite'), 'false', item.name + ' must consume guard before request')
  }
  for (const [suffix, variable] of [['/tk/material-library/create', 'libraryId'], ['/tk/material-video/upload', 'videoId'], ['/tk/upload/material-video/session/create', 'uploadId'], ['/tk/upload/material-video/session/complete', 'videoId']]) {
    const item = requests.find((item) => item.request.url.raw.endsWith(suffix))
    const script = item.event.find((event) => event.listen === 'test').script.exec.join('\n')
    assert.ok(script.includes('pm.environment.set("' + variable + '"'), suffix + ' must capture returned ID')
  }
  const oss = requests.find((item) => item.request.url.raw === '{{materialUploadUrl}}')
  assert.equal(oss.request.auth.type, 'noauth')
  assert.equal(oss.request.header.length, 0)
})


test('Postman material JSON payloads preserve int64 IDs and choose the upload mode contract', () => {
  const vm = require('node:vm')
  const collection = JSON.parse(fs.readFileSync(path.resolve(root, '../../../../../docs/api/tk-automation.postman_collection.json')))
  const requests = collection.item.find((item) => item.name === '素材库管理与视频上传').item
  const int64 = '9007199254740993'
  const base = {allowMaterialWrite:'true',accessToken:'token',tenantId:'1',libraryId:int64,videoId:int64,materialLibraryName:'name "quoted"',materialFileSize:'987',materialFileName:'sample.mp4',materialContentType:'video/mp4',uploadId:'session',materialUploadMode:'local',materialObjectKey:'object-key',materialPublicUrl:'https://example.invalid/sample.mp4',materialTags:'tag',usagePhase:'GENERAL',segmentType:'GENERAL'}
  for (const prefix of ['M03','M04','M08','M12','M13','M14','M17']) {
    const request = requests.find((item) => item.name.startsWith(prefix))
    for (const mode of prefix === 'M12' ? ['local', 'oss'] : ['local']) {
      const values = new Map(Object.entries({...base, materialUploadMode: mode}))
      if (prefix === 'M03') values.delete('libraryId')
      if (prefix === 'M08') values.delete('uploadId')
      if (prefix === 'M12') values.delete('videoId')
      let raw
      const pm = {environment:{get:(key)=>values.get(key),set:(key,value)=>values.set(key,value)},execution:{skipRequest:()=>assert.fail('valid request was skipped')},request:{body:{update:(body)=>{raw=body}}}}
      vm.runInNewContext(request.event.find((event)=>event.listen==='prerequest').script.exec.join('\n'), {pm})
      const payload = JSON.parse(raw)
      assert.equal(values.get('allowMaterialWrite'), 'false')
      if (['M04','M08','M13','M14','M17'].includes(prefix) || (prefix === 'M12' && mode === 'oss')) assert.ok(raw.includes(int64), prefix + ' preserves original ID digits')
      if (prefix === 'M12') {
        assert.equal(payload.uploadId, 'session')
        assert.equal(payload.objectKey, mode === 'oss' ? 'object-key' : undefined)
        assert.equal(payload.fileName, mode === 'oss' ? 'sample.mp4' : undefined)
      }
    }
  }
  for (const [prefix,key] of [['M03','libraryId'],['M07','videoId'],['M12','videoId']]) {
    const values = new Map()
    const pm = {test:()=>{},response:{json:()=>({code:0,data:Number(int64)}),text:()=>'{"code":0,"data":'+int64+',"msg":""}'},environment:{set:(key,value)=>values.set(key,value)}}
    const item = requests.find((item)=>item.name.startsWith(prefix))
    vm.runInNewContext(item.event.find((event)=>event.listen==='test').script.exec.join('\n'), {pm})
    assert.equal(values.get(key), int64, prefix + ' captures returned ID without rounding')
  }
})
