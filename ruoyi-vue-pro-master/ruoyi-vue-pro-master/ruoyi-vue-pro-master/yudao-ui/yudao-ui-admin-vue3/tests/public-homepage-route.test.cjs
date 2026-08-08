const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const routerSource = fs.readFileSync(
  path.join(__dirname, '../src/router/modules/remaining.ts'),
  'utf8'
)
const permissionSource = fs.readFileSync(path.join(__dirname, '../src/permission.ts'), 'utf8')
const homepageSource = fs.readFileSync(
  path.join(__dirname, '../src/views/Public/TkReview.vue'),
  'utf8'
)
const privacyPolicySource = fs.readFileSync(
  path.join(__dirname, '../src/views/Public/PrivacyPolicy.vue'),
  'utf8'
)
const termsOfServiceSource = fs.readFileSync(
  path.join(__dirname, '../src/views/Public/TermsOfService.vue'),
  'utf8'
)
const indexHtmlSource = fs.readFileSync(path.join(__dirname, '../index.html'), 'utf8')
const publicDir = path.join(__dirname, '../public')
const staticPrivacyPolicySource = fs.readFileSync(
  path.join(publicDir, 'privacy-policy.html'),
  'utf8'
)
const staticTermsOfServiceSource = fs.readFileSync(
  path.join(publicDir, 'terms-of-service.html'),
  'utf8'
)
const staticDataDeletionSource = fs.readFileSync(path.join(publicDir, 'data-deletion.html'), 'utf8')
const robotsPath = path.join(publicDir, 'robots.txt')
const sitemapPath = path.join(publicDir, 'sitemap.xml')

const rootRoute = routerSource.match(/  \{\s*path:\s*'\/'[\s\S]*?\n  \},/)?.[0] || ''
const indexRoute = routerSource.match(/  \{\s*path:\s*'\/index'[\s\S]*?\n  \},/)?.[0] || ''
const dataDeletionRoute =
  routerSource.match(/  \{\s*path:\s*'\/data-deletion'[\s\S]*?\n  \},/)?.[0] || ''

assert.match(
  rootRoute,
  /component:\s*\(\)\s*=>\s*import\('@\/views\/Public\/TkReview\.vue'\)/,
  'root path must render the public ClipForge Studio homepage'
)

assert.doesNotMatch(
  rootRoute,
  /redirect:\s*'\/tk\/dashboard'/,
  'root path must not redirect unauthenticated visitors to the private dashboard'
)

assert.match(
  indexRoute,
  /redirect:\s*'\/tk\/dashboard'/,
  '/index must remain available as a private dashboard shortcut'
)

assert.match(permissionSource, /const publicPageList = \[[^\]]*'\/'/, 'root path must bypass auth')
assert.match(permissionSource, /const whiteList = \[[\s\S]*'\/'/, 'root path must be whitelisted')
assert.match(
  dataDeletionRoute,
  /component:\s*\(\)\s*=>\s*import\('@\/views\/Public\/DataDeletion\.vue'\)/,
  'data deletion route must render a dedicated public page'
)
assert.match(
  permissionSource,
  /const publicPageList = \[[^\]]*'\/data-deletion'/,
  'data deletion path must bypass auth'
)
assert.match(
  permissionSource,
  /const whiteList = \[[\s\S]*'\/data-deletion'/,
  'data deletion path must be whitelisted'
)
assert.match(
  permissionSource,
  /if \(to\.path === '\/login'\) \{[\s\S]*?next\(\{ path: '\/tk\/dashboard' \}\)/,
  'logged-in login visits must still land on the private dashboard'
)

assert.match(homepageSource, /Official site/, 'homepage must present as the official site')
assert.doesNotMatch(
  homepageSource,
  /For TikTok Developer App Review/,
  'homepage must not look like an internal-only review page'
)
assert.match(homepageSource, /How ClipForge Studio works/, 'homepage must explain the product')
assert.match(
  homepageSource,
  /Platform and data controls/,
  'homepage must expose compliance controls'
)
assert.match(homepageSource, /Support and legal/, 'homepage must expose support and legal links')
assert.match(
  homepageSource,
  /shangan@zswo\.net/,
  'homepage must provide the updated public support contact'
)
assert.equal(
  (homepageSource.match(/mailto:shangan@zswo\.net/g) || []).length,
  1,
  'homepage must keep one primary support email link to avoid repeated contact blocks'
)
assert.match(homepageSource, /Data Deletion/, 'homepage must link to data deletion instructions')
assert.match(
  homepageSource,
  /not a login page/i,
  'homepage must make clear the website URL is not a login page'
)
assert.doesNotMatch(
  homepageSource,
  /@creator_demo|User confirms upload|Video Publishing Center|Ready for user review/,
  'homepage must not include a mock publishing form that can look like a private app screen'
)
assert.doesNotMatch(
  homepageSource,
  /<textarea|<select|type="checkbox"/,
  'homepage hero must not include form controls from a mock publishing UI'
)
assert.doesNotMatch(homepageSource, /review-page/, 'homepage CSS naming must not look review-only')
assert.doesNotMatch(homepageSource, /review-footer/, 'footer CSS naming must not look review-only')

assert.match(
  indexHtmlSource,
  /content="ClipForge Studio helps teams create, review, and prepare short-form videos/,
  'static HTML meta description must describe the public website'
)
assert.doesNotMatch(
  indexHtmlSource,
  /admin console/,
  'static HTML meta description must not present the root URL as an admin console'
)
assert.match(
  indexHtmlSource,
  /<link rel="canonical" href="https:\/\/clipforgestudio\.fnn\.net\.cn\/" \/>/,
  'public website must declare its canonical root URL'
)
assert.match(indexHtmlSource, /property="og:type" content="website"/, 'Open Graph type is required')
assert.match(
  indexHtmlSource,
  /property="og:url" content="https:\/\/clipforgestudio\.fnn\.net\.cn\/"/,
  'Open Graph URL must point to the public root URL'
)
assert.match(
  indexHtmlSource,
  /name="twitter:card" content="summary_large_image"/,
  'Twitter card is required'
)
assert.match(
  indexHtmlSource,
  /<script type="application\/ld\+json">[\s\S]*"@type": "SoftwareApplication"[\s\S]*"name": "ClipForge Studio"/,
  'structured data must describe ClipForge Studio as a software application'
)
assert.match(
  indexHtmlSource,
  /<noscript>[\s\S]*ClipForge Studio works best with JavaScript enabled/,
  'noscript fallback is required'
)
assert.match(
  indexHtmlSource,
  /<section class="public-fallback" aria-label="ClipForge Studio public website fallback">/,
  'static app fallback must be visible before the Vue bundle loads'
)
assert.match(
  indexHtmlSource,
  /Create, review, and prepare short-form videos/,
  'static fallback must explain the public website'
)
assert.match(
  indexHtmlSource,
  /shangan@zswo\.net/,
  'static fallback must include the public support email'
)
assert.match(
  indexHtmlSource,
  /email_off/,
  'static fallback must keep the support email visible through Cloudflare'
)
assert.match(
  indexHtmlSource,
  /Data Deletion/,
  'static fallback must link to data deletion instructions'
)
assert.doesNotMatch(
  indexHtmlSource,
  /AI material engine starting/,
  'static root must not look like an app loading screen'
)
assert.doesNotMatch(
  indexHtmlSource,
  /Permission module loading/,
  'static root must not look like an auth loading screen'
)

for (const [name, source] of [
  ['privacy policy', privacyPolicySource],
  ['terms of service', termsOfServiceSource]
]) {
  assert.match(source, /shangan@zswo\.net/, `${name} must provide the public support email`)
  assert.match(source, /data deletion/i, `${name} must mention data deletion requests`)
  assert.doesNotMatch(
    source,
    /service owner associated with this deployment/,
    `${name} must not use vague deployment-owner contact copy`
  )
}

for (const [name, source] of [
  ['static privacy policy', staticPrivacyPolicySource],
  ['static terms of service', staticTermsOfServiceSource],
  ['static data deletion', staticDataDeletionSource]
]) {
  assert.match(source, /shangan@zswo\.net/, `${name} must include the public support email`)
  assert.match(
    source,
    /email_off/,
    `${name} must keep the support email visible through Cloudflare`
  )
  assert.doesNotMatch(
    source,
    /service owner associated with this deployment/,
    `${name} must not use vague deployment-owner contact copy`
  )
}

assert.equal(fs.existsSync(robotsPath), true, 'robots.txt must exist')
const robotsSource = fs.readFileSync(robotsPath, 'utf8')
assert.match(robotsSource, /User-agent: \*/, 'robots.txt must address crawlers')
assert.match(robotsSource, /^Allow: \/$/m, 'robots.txt must allow the public root')
assert.match(robotsSource, /Disallow: \/tk\//, 'robots.txt must avoid indexing private app routes')
assert.match(
  robotsSource,
  /Sitemap: https:\/\/clipforgestudio\.fnn\.net\.cn\/sitemap\.xml/,
  'robots.txt must point to the sitemap'
)

assert.equal(fs.existsSync(sitemapPath), true, 'sitemap.xml must exist')
const sitemapSource = fs.readFileSync(sitemapPath, 'utf8')
for (const publicUrl of [
  'https://clipforgestudio.fnn.net.cn/',
  'https://clipforgestudio.fnn.net.cn/review',
  'https://clipforgestudio.fnn.net.cn/privacy-policy',
  'https://clipforgestudio.fnn.net.cn/terms-of-service',
  'https://clipforgestudio.fnn.net.cn/data-deletion'
]) {
  assert.match(
    sitemapSource,
    new RegExp(`<loc>${publicUrl}</loc>`),
    `sitemap must include ${publicUrl}`
  )
}

console.log('public homepage route tests passed')
