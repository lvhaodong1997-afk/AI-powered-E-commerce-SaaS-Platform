import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'

const root = process.cwd()
const forbidden = ['Translated content', 'fallbackText', 'cjkChunkPattern', 'collapseFallback']
const sourceDirs = ['src/views/tk', 'src/components/TkGlobalI18nBridge', 'src/utils/tkI18n.ts', 'src/utils/tkTextSanitizer.ts']
const cjkPattern = /[\u3400-\u9fff]/

const files = []
const walk = (entry) => {
  if (!fs.existsSync(entry)) return
  const stat = fs.statSync(entry)
  if (stat.isDirectory()) {
    for (const child of fs.readdirSync(entry)) walk(path.join(entry, child))
    return
  }
  if (/\.(vue|ts)$/.test(entry)) files.push(entry)
}

sourceDirs.forEach((item) => walk(path.join(root, item)))

for (const file of files) {
  const source = fs.readFileSync(file, 'utf8')
  for (const token of forbidden) {
    assert.equal(
      source.includes(token),
      false,
      `${path.relative(root, file)} contains forbidden i18n fallback token: ${token}`
    )
  }
}

const zhSource = fs.readFileSync(path.join(root, 'src/locales/tk/zh-CN.ts'), 'utf8')
const enSource = fs.readFileSync(path.join(root, 'src/locales/tk/en.ts'), 'utf8')
const keyPattern = /['"]([a-z][a-zA-Z0-9]*(?:\.[a-zA-Z0-9]+)+)['"]\s*:/g
const readKeys = (source) => [...source.matchAll(keyPattern)].map((match) => match[1]).sort()
const zhKeys = readKeys(zhSource)
const enKeys = readKeys(enSource)

assert.deepEqual(enKeys, zhKeys, 'TK zh-CN and en language files must expose the same keys')
assert.equal(cjkPattern.test(enSource), false, 'TK English language file must not contain Chinese UI text')

console.log(`tk i18n coverage checks passed: ${enKeys.length} keys, ${files.length} source files scanned`)
