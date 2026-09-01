import fs from 'node:fs'
import path from 'node:path'

const outputPath = path.resolve(process.cwd(), 'dist-prod/index.html')

if (!fs.existsSync(outputPath)) {
  console.error(`Production branding check failed: missing ${outputPath}`)
  process.exit(1)
}

const html = fs.readFileSync(outputPath, 'utf8')
const failures = []
const expectedTitle = 'ClipForge Studio'

const normalizeVisibleText = (markup = '') =>
  markup
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/<[^>]+>/g, ' ')
    .replace(/&nbsp;|&#160;|&#x0*a0;/gi, ' ')
    .replace(/&amp;/gi, '&')
    .replace(/&lt;/gi, '<')
    .replace(/&gt;/gi, '>')
    .replace(/&quot;/gi, '"')
    .replace(/&#39;|&apos;/gi, "'")
    .replace(/\s+/g, ' ')
    .trim()

const titleMatches = [...html.matchAll(/<title\b[^>]*>([\s\S]*?)<\/title>/gi)]
const loadingTitleMatches = [
  ...html.matchAll(
    /<([a-z][\w:-]*)\b(?=[^>]*\bclass\s*=\s*(?:"[^"]*\bapp-loading-name\b[^"]*"|'[^']*\bapp-loading-name\b[^']*'))[^>]*>([\s\S]*?)<\/\1>/gi
  )
]

if (html.includes('%VITE_APP_TITLE%')) {
  failures.push('VITE_APP_TITLE placeholder remains in the production HTML')
}
if (titleMatches.length !== 1) {
  failures.push(`expected one title element, found ${titleMatches.length}`)
} else {
  const titleText = normalizeVisibleText(titleMatches[0][1])
  if (titleText !== expectedTitle) {
    failures.push(`title text must be "${expectedTitle}", received "${titleText}"`)
  }
}
if (loadingTitleMatches.length !== 1) {
  failures.push(`expected one .app-loading-name element, found ${loadingTitleMatches.length}`)
} else {
  const loadingTitleText = normalizeVisibleText(loadingTitleMatches[0][2])
  if (loadingTitleText !== expectedTitle) {
    failures.push(`loading title text must be "${expectedTitle}", received "${loadingTitleText}"`)
  }
}

if (failures.length) {
  console.error('Production branding check failed:')
  failures.forEach((failure) => console.error(`- ${failure}`))
  process.exit(1)
}

console.log('production branding check passed')
