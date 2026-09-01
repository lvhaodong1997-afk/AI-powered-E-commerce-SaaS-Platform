import fs from 'node:fs'
import path from 'node:path'

const root = process.cwd()
const scanRoots = [
  'src/views/tk',
  'src/hooks/web/useTkI18n.ts',
  'src/api/tk'
]

const ignoredFiles = new Set([
  // These pages already carry internal zh/en copy tables covered by tk i18n coverage checks.
  'src/views/tk/dashboard/index.vue',
  'src/views/tk/data-dashboard/index.vue',
  'src/views/tk/generation-batch/index.vue',
  'src/views/tk/generation-route/index.vue'
])

const legacyAllowedFiles = new Set([
  // Existing TK pages still being migrated. The check blocks new files from adding hardcoded system copy.
  'src/api/tk/creativeWorkshop/index.ts',
  'src/views/tk/business-log/index.vue',
  'src/views/tk/company/index.vue',
  'src/views/tk/creative-workshop/index.vue',
  'src/views/tk/generation/clipPlan.ts',
  'src/views/tk/generation/index.vue',
  'src/views/tk/material-library/index.vue',
  'src/views/tk/video-publish-center/index.vue',
  'src/views/tk/voice/components/VoiceProfileDialog.vue'
])

const allowedChineseSnippets = [
  // User/business data examples and input defaults are intentionally not translated in this phase.
  'TikTok',
  'AIGC',
  'MP3',
  'WAV',
  'M4A',
  'MP4',
  'MOV',
  'WebM'
]

const cjkPattern = /[\u3400-\u9fff]/
const textFilePattern = /\.(vue|ts|tsx|js|jsx|mjs)$/

const toPosix = (filePath) => filePath.split(path.sep).join('/')

const walk = (entry) => {
  const abs = path.join(root, entry)
  if (!fs.existsSync(abs)) return []
  const stat = fs.statSync(abs)
  if (stat.isFile()) return [abs]
  const out = []
  for (const child of fs.readdirSync(abs)) {
    const childAbs = path.join(abs, child)
    const childStat = fs.statSync(childAbs)
    if (childStat.isDirectory()) {
      if (['node_modules', 'dist', 'dist-prod', 'build'].includes(child)) continue
      out.push(...walk(path.relative(root, childAbs)))
    } else {
      out.push(childAbs)
    }
  }
  return out
}

const stripComments = (line) =>
  line
    .replace(/<!--.*?-->/g, '')
    .replace(/\/\*.*?\*\//g, '')
    .replace(/^\s*\/\/.*$/, '')

const isAllowedLine = (line) => {
  const trimmed = line.trim()
  if (!trimmed || !cjkPattern.test(trimmed)) return true
  if (trimmed.startsWith('*') || trimmed.startsWith('//')) return true
  if (allowedChineseSnippets.some((snippet) => trimmed.includes(snippet)) && !/[\u3400-\u9fff]{2,}/.test(trimmed)) {
    return true
  }
  return false
}

const files = scanRoots.flatMap(walk)
const violations = []

for (const abs of files) {
  const rel = toPosix(path.relative(root, abs))
  if (!textFilePattern.test(rel) || ignoredFiles.has(rel) || legacyAllowedFiles.has(rel)) continue
  const source = fs.readFileSync(abs, 'utf8')
  source.split(/\r?\n/).forEach((rawLine, index) => {
    const line = stripComments(rawLine)
    if (cjkPattern.test(line) && !isAllowedLine(line)) {
      violations.push(`${rel}:${index + 1}: ${rawLine.trim()}`)
    }
  })
}

if (violations.length) {
  console.error('TK hardcoded i18n check failed. Move system copy to src/locales/tk/* and use tt().')
  console.error(violations.slice(0, 120).join('\n'))
  if (violations.length > 120) {
    console.error(`... ${violations.length - 120} more`)
  }
  process.exit(1)
}

console.log('tk hardcoded i18n checks passed')
