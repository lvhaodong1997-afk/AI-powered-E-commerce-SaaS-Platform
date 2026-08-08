const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const source = fs.readFileSync(
  path.join(__dirname, '../src/views/tk/dashboard/index.vue'),
  'utf8'
)

assert.match(
  source,
  /:class="subtitlePreviewClasses"/,
  'subtitle preview must be driven by computed style, font size, and position classes'
)

assert.match(
  source,
  /const subtitlePreviewClasses = computed\(\(\) => \[/,
  'subtitle preview class list must be computed from current form state'
)

assert.match(
  source,
  /`is-position-\$\{createForm\.subtitlePositionMode\}`/,
  'subtitle preview must expose selected position mode as a CSS class'
)

assert.match(
  source,
  /`is-size-\$\{createForm\.subtitleFontSize\}`/,
  'subtitle preview must expose selected font size as a CSS class'
)

assert.match(
  source,
  /const subtitlePreviewUsesEmphasis = computed/,
  'subtitle preview must decide whether the middle phrase is emphasized'
)

assert.match(
  source,
  /v-if="subtitlePreviewUsesEmphasis"/,
  'standard white preview should be able to render the middle phrase without a colored strong tag'
)

assert.match(
  source,
  /\.subtitle-style-preview\.is-classic_white \.subtitle-preview-caption strong[\s\S]*?color:\s*#fff/,
  'classic white preview must keep emphasized text white when emphasis is rendered'
)

for (const size of ['small', 'medium', 'large']) {
  assert.match(
    source,
    new RegExp(`\\.subtitle-style-preview\\.is-size-${size} \\.subtitle-preview-caption`),
    `subtitle preview must include a visible ${size} font-size class`
  )
}

for (const position of [
  'smart_safe',
  'fixed_bottom',
  'fixed_middle',
  'alternate',
  'sentence_rotate',
  'random_safe'
]) {
  assert.match(
    source,
    new RegExp(`\\.subtitle-style-preview\\.is-position-${position}`),
    `subtitle preview must include position treatment for ${position}`
  )
}

console.log('subtitle preview config tests passed')
