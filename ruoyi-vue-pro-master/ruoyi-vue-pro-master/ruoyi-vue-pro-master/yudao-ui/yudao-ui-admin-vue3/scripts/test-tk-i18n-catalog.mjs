import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'

const root = process.cwd()

const requiredFiles = [
  'src/locales/tk/zh-CN.ts',
  'src/locales/tk/en.ts',
  'src/locales/tk/index.ts',
  'src/hooks/web/useTkI18n.ts',
  'scripts/check-tk-i18n-coverage.mjs'
]

for (const file of requiredFiles) {
  assert.equal(fs.existsSync(path.join(root, file)), true, `${file} should exist`)
}

const zhSource = fs.readFileSync(path.join(root, 'src/locales/tk/zh-CN.ts'), 'utf8')
const enSource = fs.readFileSync(path.join(root, 'src/locales/tk/en.ts'), 'utf8')
const indexSource = fs.readFileSync(path.join(root, 'src/locales/tk/index.ts'), 'utf8')
const hookSource = fs.readFileSync(path.join(root, 'src/hooks/web/useTkI18n.ts'), 'utf8')
const checkSource = fs.readFileSync(path.join(root, 'scripts/check-tk-i18n-coverage.mjs'), 'utf8')

const requiredKeys = [
  'common.search',
  'common.reset',
  'layout.systemManagement',
  'layout.tkMaterialFactory',
  'generation.clipDetails',
  'generation.generationDetails',
  'dashboard.generateVideo',
  'material.libraryCount',
  'material.currentVideoCount',
  'material.videoCount',
  'material.capacity',
  'material.uploadVideos',
  'material.newLibrary',
  'material.uploadToCurrentLibrary',
  'material.purpose.s1Hook',
  'material.purpose.s2Pain',
  'material.missingKeyUses',
  'publish.accountMatrix',
  'creative.heroTitle',
  'company.companyName',
  'businessLog.businessTraceId'
]

for (const key of requiredKeys) {
  assert.match(zhSource, new RegExp(`['"]${key}['"]`), `zh-CN should include ${key}`)
  assert.match(enSource, new RegExp(`['"]${key}['"]`), `en should include ${key}`)
}

assert.match(indexSource, /translateTkKey/)
assert.match(indexSource, /translateTkTextByLocale/)
assert.match(hookSource, /useTkI18n/)
assert.match(hookSource, /tt/)
assert.match(checkSource, /Translated content/)
assert.equal(enSource.includes('Translated content'), false)
assert.equal(indexSource.includes('Translated content'), false)

console.log('tk i18n catalog checks passed')
