const fs = require('node:fs')
const path = require('node:path')

const root = process.cwd()
const brand = 'ClipForge Studio'
const files = [
  '.env',
  'package.json',
  'index.html',
  'public/privacy-policy.html',
  'public/terms-of-service.html',
  'src/views/Public/TkReview.vue',
  'src/views/Public/PrivacyPolicy.vue',
  'src/views/Public/TermsOfService.vue',
  'src/router/modules/remaining.ts',
  'src/utils/tkI18n.ts',
  'src/utils/tkTextSanitizer.ts'
]

const forbidden = [
  ['TK', ' Auto Mix'].join(''),
  ['TK', ' Material Factory'].join(''),
  ['TK', '素材工厂'].join(''),
  ['TK', '自动混剪'].join('')
]
const failures = []

for (const file of files) {
  const absolute = path.join(root, file)
  if (!fs.existsSync(absolute)) {
    failures.push(`${file}: missing`)
    continue
  }
  const text = fs.readFileSync(absolute, 'utf8')
  if (!text.includes(brand)) {
    failures.push(`${file}: missing ${brand}`)
  }
  for (const value of forbidden) {
    if (text.includes(value)) {
      failures.push(`${file}: still contains ${value}`)
    }
  }
}

if (failures.length > 0) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('ClipForge brand checks passed')
