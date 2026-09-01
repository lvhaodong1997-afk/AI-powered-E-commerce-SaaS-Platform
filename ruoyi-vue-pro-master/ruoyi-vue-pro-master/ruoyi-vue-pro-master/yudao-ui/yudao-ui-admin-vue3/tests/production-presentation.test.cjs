const assert = require('node:assert/strict')
const { spawnSync } = require('node:child_process')
const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')
const { test } = require('node:test')

const root = path.resolve(__dirname, '..')
const read = (relativePath) => {
  const filePath = path.resolve(root, relativePath)
  return fs.existsSync(filePath) ? fs.readFileSync(filePath, 'utf8') : ''
}

const indexHtml = read('index.html')
const packageJson = read('package.json')
const brandingGuardPath = path.resolve(root, 'scripts/check-prod-branding.mjs')
const useTitle = read('src/hooks/web/useTitle.ts')
const login = read('src/views/Login/Login.vue')
const materialLibrary = read('src/views/tk/material-library/index.vue')
const publishCenter = read('src/views/tk/video-publish-center/index.vue')
const creativeWorkshop = read('src/views/tk/creative-workshop/index.vue')
const zhLocale = read('src/locales/tk/zh-CN.ts')
const enLocale = read('src/locales/tk/en.ts')
const hardcodedI18nCheck = read('scripts/check-tk-hardcoded-i18n.mjs')
const errorCodes = read(
  '../../yudao-module-tk/src/main/java/cn/iocoder/yudao/module/tk/enums/ErrorCodeConstants.java'
)

const productionHtml = ({ title = '', loadingTitle = '', extraHead = '', extraBody = '' }) => `
<!doctype html>
<html>
  <head>
    ${extraHead}
    <title>${title}</title>
  </head>
  <body>
    <div class="app-loading-name">${loadingTitle}</div>
    ${extraBody}
  </body>
</html>
`

const runBrandingGuard = (html) => {
  const tempRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'clipforge-branding-'))
  const distDir = path.join(tempRoot, 'dist-prod')
  fs.mkdirSync(distDir)
  fs.writeFileSync(path.join(distDir, 'index.html'), html, 'utf8')
  try {
    return spawnSync(process.execPath, [brandingGuardPath], {
      cwd: tempRoot,
      encoding: 'utf8'
    })
  } finally {
    fs.rmSync(tempRoot, { recursive: true, force: true })
  }
}

const extractVueTemplate = (source) => {
  const start = source.indexOf('<template>')
  const end = source.indexOf('<script', start)
  return start >= 0 ? source.slice(start, end >= 0 ? end : source.length) : ''
}

const hasUnsafeUrlReference = (expression) => {
  const withoutSafeLabels = expression.replace(
    /displayVideoFileName\([^)]*\b(?:outputUrl|videoUrl)\b[^)]*\)/g,
    ''
  )
  return /\b(?:outputUrl|videoUrl)\b/.test(withoutSafeLabels)
}

const findUnsafeVisibleUrlBindings = (source) => {
  const template = extractVueTemplate(source)
  const violations = []
  for (const match of template.matchAll(/\{\{([\s\S]*?)\}\}/g)) {
    if (hasUnsafeUrlReference(match[1])) violations.push(`interpolation: ${match[1].trim()}`)
  }
  const visibleAttributes = new Set([
    'alt',
    'aria-description',
    'aria-label',
    'aria-valuetext',
    'content',
    'data-tooltip',
    'label',
    'placeholder',
    'title',
    'tooltip',
    'v-bind',
    'v-text'
  ])
  const attributePattern = /(?:^|\s)(v-text|v-bind(?::[\w-]+)?|:[\w-]+)\s*=\s*(["'])([\s\S]*?)\2/g
  for (const match of template.matchAll(attributePattern)) {
    const rawName = match[1]
    const name = rawName.startsWith(':')
      ? rawName.slice(1)
      : rawName.startsWith('v-bind:')
        ? rawName.slice('v-bind:'.length)
        : rawName
    const isVisible = visibleAttributes.has(name) || name.startsWith('aria-') || name.includes('tooltip')
    if (isVisible && hasUnsafeUrlReference(match[3])) {
      violations.push(`${rawName}: ${match[3].trim()}`)
    }
  }
  return violations
}

test('uses a stable ClipForge Studio title without empty separators', () => {
  assert.match(useTitle, /const DEFAULT_APP_TITLE = 'ClipForge Studio'/)
  assert.match(useTitle, /sanitizeEnglishTitleText\(value\)\.trim\(\)/)
  assert.match(useTitle, /normalizeTitleSegment\(appStore\.getTitle\) \|\| DEFAULT_APP_TITLE/)
  assert.match(useTitle, /const routeTitle = newTitle\?\.trim\(\)/)
  assert.match(useTitle, /normalizeTitleSegment\(pageTitle\)/)
  assert.match(useTitle, /\[appTitle, sanitizedPageTitle\]\.filter\(Boolean\)\.join\(' - '\)/)
})

test('keeps Vite title substitution with a runtime fallback', () => {
  assert.match(indexHtml, /<title>%VITE_APP_TITLE%<\/title>/)
  assert.match(indexHtml, /data-app-title-fallback="ClipForge Studio"/)
  assert.match(indexHtml, /\^%\[\^%\]\+%\$/)
})

test('runs a production branding guard after the production build', () => {
  assert.match(packageJson, /"build:prod"[^\n]+check-prod-branding\.mjs/)
})

test('production branding guard accepts exact title and loading text', () => {
  const result = runBrandingGuard(
    productionHtml({ title: 'ClipForge Studio', loadingTitle: 'ClipForge Studio' })
  )
  assert.equal(result.status, 0, result.stderr)
})

test('production branding guard rejects unresolved placeholders', () => {
  const result = runBrandingGuard(
    productionHtml({ title: '%VITE_APP_TITLE%', loadingTitle: '%VITE_APP_TITLE%' })
  )
  assert.equal(result.status, 1, result.stdout)
})

test('production branding guard rejects missing brand text', () => {
  const result = runBrandingGuard(productionHtml({ title: '', loadingTitle: '' }))
  assert.equal(result.status, 1, result.stdout)
})

test('production branding guard rejects a wrong title despite other brand mentions', () => {
  const result = runBrandingGuard(
    productionHtml({
      title: 'Other Studio',
      loadingTitle: 'ClipForge Studio',
      extraHead: '<meta property="og:title" content="ClipForge Studio" />'
    })
  )
  assert.equal(result.status, 1, result.stdout)
})

test('production branding guard rejects wrong loading text despite fallback brand mentions', () => {
  const result = runBrandingGuard(
    productionHtml({
      title: 'ClipForge Studio',
      loadingTitle: 'Other Studio',
      extraBody: '<section class="public-fallback">ClipForge Studio</section>'
    })
  )
  assert.equal(result.status, 1, result.stdout)
})

test('uses the product name and qualitative facts on the login hero', () => {
  assert.match(login, /heroTitle: 'ClipForge Studio'/)
  assert.doesNotMatch(login, /24K\+|8ms/)
  assert.match(login, /Workflow/)
  assert.match(login, /Access/)
  assert.match(login, /User confirmed/)
})

test('uses the configured 1 GB material upload limit in validation and copy', () => {
  assert.match(materialLibrary, /const MAX_UPLOAD_FILE_SIZE = 1_000_000_000/)
  assert.match(materialLibrary, /单文件最大 1GB/)
  assert.match(materialLibrary, /超过 1GB/)
  assert.doesNotMatch(materialLibrary, /100MB/)
  assert.match(zhLocale, /单文件最大 1GB/)
  assert.match(enLocale, /Max 1GB per file/)
})

test('keeps the backend upload-limit error configuration-neutral', () => {
  assert.match(
    errorCodes,
    /TK_UPLOAD_FILE_TOO_LARGE\s*=\s*new ErrorCode\([^\n]+"视频文件超过当前上传大小限制"\)/
  )
})

test('renders safe video filenames without OSS query parameters', () => {
  assert.match(publishCenter, /const displayVideoFileName = \(url\?: string\)/)
  assert.match(publishCenter, /split\(\/\[\?#\]\//)
  assert.deepEqual(findUnsafeVisibleUrlBindings(publishCenter), [])
  assert.deepEqual(
    findUnsafeVisibleUrlBindings(`
      <template><span>{{ displayVideoFileName(row.outputUrl) }}</span></template>
      <script>const originalUrl = row.outputUrl</script>
    `),
    []
  )
  for (const unsafeTemplate of [
    '<template><span>{{ row.outputUrl }}</span></template>',
    '<template><span v-text="row.videoUrl"></span></template>',
    '<template><span :title="row.outputUrl">Video</span></template>',
    '<template><el-tooltip :content="row.videoUrl">Video</el-tooltip></template>',
    '<template><span :aria-label="row.outputUrl">Video</span></template>'
  ]) {
    assert.notEqual(findUnsafeVisibleUrlBindings(unsafeTemplate).length, 0, unsafeTemplate)
  }
})

test('formats string and numeric timestamps through formatDate', () => {
  assert.match(publishCenter, /import \{ formatDate \} from '@\/utils\/formatTime'/)
  assert.match(publishCenter, /const formatTimestamp = \(value\?: string \| number\)/)
  for (const field of ['createTime', 'accessTokenExpireTime', 'lastSyncTime']) {
    assert.match(publishCenter, new RegExp(`formatTimestamp\\(scope\\.row\\.${field}\\)`))
  }
  assert.doesNotMatch(publishCenter, /<el-table-column[^>]+prop="(?:createTime|lastSyncTime)"/)
})

test('localizes all four publishing overview labels', () => {
  const keys = [
    'publish.overview.authorizedAccounts',
    'publish.overview.pendingPublishes',
    'publish.overview.failedTasks',
    'publish.overview.tokenIssues'
  ]
  for (const key of keys) {
    assert.match(zhLocale, new RegExp(`'${key}'\\s*:`))
    assert.match(enLocale, new RegExp(`'${key}'\\s*:`))
    assert.match(publishCenter, new RegExp(`tt\\('${key}'\\)`))
  }
})

test('removes developer placeholders and exempts self-localized TK pages', () => {
  assert.doesNotMatch(creativeWorkshop, /已预留|占位状态|后续补充真实接口/)
  assert.match(creativeWorkshop, /<button class="upload-entry" type="button" disabled>/)
  assert.match(creativeWorkshop, /<button class="floating-help" type="button" disabled>/)
  assert.match(creativeWorkshop, /为一款跨境电商商品生成 TikTok 创意短视频/)
  assert.match(hardcodedI18nCheck, /src\/views\/tk\/data-dashboard\/index\.vue/)
  assert.match(hardcodedI18nCheck, /src\/views\/tk\/generation-batch\/index\.vue/)
  assert.match(hardcodedI18nCheck, /src\/views\/tk\/generation-route\/index\.vue/)
})
