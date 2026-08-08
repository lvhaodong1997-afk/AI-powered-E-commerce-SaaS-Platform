const fs = require('node:fs')
const path = require('node:path')
const crypto = require('node:crypto')

const root = process.cwd()
const sourceIcon = 'C:\\Users\\lhd\\Desktop\\clipforge-studio-icon-1024.png'
const pngTargets = [
  'public/logo.png',
  'public/clipforge-app-icon.png',
  'src/assets/imgs/logo.png'
]
const icoTarget = 'public/favicon.ico'
const gifTarget = 'public/logo.gif'

const read = (file) => fs.readFileSync(file)
const sha256 = (file) => crypto.createHash('sha256').update(read(file)).digest('hex')

const readPngSize = (file) => {
  const buffer = read(file)
  if (buffer.toString('ascii', 1, 4) !== 'PNG') {
    throw new Error(`${file}: not a PNG file`)
  }
  return {
    width: buffer.readUInt32BE(16),
    height: buffer.readUInt32BE(20)
  }
}

const failures = []

if (!fs.existsSync(sourceIcon)) {
  failures.push(`${sourceIcon}: missing source icon`)
} else {
  const sourceHash = sha256(sourceIcon)
  const sourceSize = readPngSize(sourceIcon)
  if (sourceSize.width !== 1024 || sourceSize.height !== 1024) {
    failures.push(`${sourceIcon}: expected 1024x1024 PNG, got ${sourceSize.width}x${sourceSize.height}`)
  }

  for (const target of pngTargets) {
    const absolute = path.join(root, target)
    if (!fs.existsSync(absolute)) {
      failures.push(`${target}: missing`)
      continue
    }
    if (sha256(absolute) !== sourceHash) {
      failures.push(`${target}: does not match source icon`)
    }
  }
}

if (!fs.existsSync(path.join(root, icoTarget))) {
  failures.push(`${icoTarget}: missing`)
}

if (!fs.existsSync(path.join(root, gifTarget))) {
  failures.push(`${gifTarget}: missing`)
}

if (failures.length > 0) {
  console.error(failures.join('\n'))
  process.exit(1)
}

console.log('Product icon checks passed')
