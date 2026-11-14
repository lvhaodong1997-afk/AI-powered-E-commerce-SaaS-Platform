import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const web = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const repo = path.resolve(web, '../../../../..')
const source = path.join(repo, 'docs/api')
const output = path.join(web, 'public/api-reference')
const check = process.argv.includes('--check')
const guides = [
  {
    id: 'quickstart',
    title: '快速开始',
    hint: '从准备账号到下载第一条成片',
    file: 'quickstart.md'
  },
  {
    id: 'calling-rules',
    title: '鉴权与调用规则',
    hint: 'Token、积分、轮询、重试与错误处理',
    file: 'calling-rules.md'
  },
  {
    id: 'tiktok-integration',
    title: 'TikTok 发布接入',
    hint: 'HMAC 签名、授权、上传与发布',
    file: 'tiktok-integration.md'
  }
]
const downloads = [
  {
    file: 'tk-automation-v1.md',
    title: '自动出片参数参考',
    detail: '完整请求、响应与错误处理说明'
  },
  { file: 'tk-platform-api.md', title: '平台接口总览', detail: '对外接口范围与能力边界' },
  {
    file: 'tk-platform-openapi.json',
    title: 'OpenAPI 3.0',
    detail: '导入 API 工具，查看全部接口与字段'
  },
  {
    file: 'tk-automation.postman_collection.json',
    title: 'Postman Collection',
    detail: '导入后配置自己的账号与资源'
  },
  {
    file: 'tk-automation.postman_environment.json',
    title: 'Postman 环境模板',
    detail: '与 Collection 一起导入'
  },
  {
    file: 'examples/automation.py',
    title: 'Python 完整示例',
    detail: '资源查询 → 提交 → 续查 → 下载'
  },
  {
    file: 'examples/automation.mjs',
    title: 'Node.js 完整示例',
    detail: '使用环境变量和原生 fetch'
  },
  { file: 'examples/README.md', title: '示例运行说明', detail: '配置项、命令和失败恢复方法' },
  { file: 'examples/.env.example', title: '环境变量模板', detail: '仅占位符，不包含真实凭证' },
  {
    file: 'examples/request.example.json',
    title: '生成请求模板',
    detail: '资源 ID 需替换为自己的查询结果'
  }
]
const files = new Map()
for (const { file } of [...guides, ...downloads])
  files.set(
    file,
    Buffer.from(fs.readFileSync(path.join(source, file), 'utf8').replaceAll('\r\n', '\n'))
  )
const spec = JSON.parse(files.get('tk-platform-openapi.json').toString())
if (!spec.paths?.['/admin-api/tk/automation/v1/tasks']?.post)
  throw Error('自动出片契约缺失，拒绝生成不完整接入包')
const catalog = {
  version: '2026-11-14',
  spec,
  guides: guides.map((item) => ({ ...item, content: files.get(item.file).toString() })),
  downloads
}
files.set('catalog.json', Buffer.from(JSON.stringify(catalog, null, 2) + '\n'))

// Store-only ZIP: deterministic timestamps and CRCs keep downloads reproducible without dependencies.
function crc32(buffer) {
  let crc = 0xffffffff
  for (const byte of buffer) {
    crc ^= byte
    for (let i = 0; i < 8; i++) crc = (crc >>> 1) ^ (crc & 1 ? 0xedb88320 : 0)
  }
  return (crc ^ 0xffffffff) >>> 0
}
function zip(entries) {
  const parts = [],
    directory = []
  let offset = 0,
    centralSize = 0
  for (const [filename, content] of entries) {
    const name = Buffer.from(filename),
      local = Buffer.alloc(30),
      central = Buffer.alloc(46)
    const crc = crc32(content)
    local.writeUInt32LE(0x04034b50)
    local.writeUInt16LE(20, 4)
    local.writeUInt16LE(0x800, 6)
    local.writeUInt16LE(33, 12)
    local.writeUInt32LE(crc, 14)
    local.writeUInt32LE(content.length, 18)
    local.writeUInt32LE(content.length, 22)
    local.writeUInt16LE(name.length, 26)
    central.writeUInt32LE(0x02014b50)
    central.writeUInt16LE(20, 4)
    central.writeUInt16LE(20, 6)
    central.writeUInt16LE(0x800, 8)
    central.writeUInt16LE(33, 14)
    central.writeUInt32LE(crc, 16)
    central.writeUInt32LE(content.length, 20)
    central.writeUInt32LE(content.length, 24)
    central.writeUInt16LE(name.length, 28)
    central.writeUInt32LE(offset, 42)
    parts.push(local, name, content)
    directory.push(central, name)
    offset += local.length + name.length + content.length
    centralSize += central.length + name.length
  }
  const end = Buffer.alloc(22)
  end.writeUInt32LE(0x06054b50)
  end.writeUInt16LE(entries.size, 8)
  end.writeUInt16LE(entries.size, 10)
  end.writeUInt32LE(centralSize, 12)
  end.writeUInt32LE(offset, 16)
  return Buffer.concat([...parts, ...directory, end])
}
files.set(
  'clipforge-api-kit.zip',
  zip(new Map([...files].filter(([name]) => name !== 'catalog.json')))
)
const stale = []
for (const [file, content] of files) {
  const target = path.join(output, file)
  if (check) {
    if (!fs.existsSync(target) || !fs.readFileSync(target).equals(content)) stale.push(file)
  } else {
    fs.mkdirSync(path.dirname(target), { recursive: true })
    fs.writeFileSync(target, content)
  }
}
if (stale.length)
  throw Error(`文档下载文件已过期，请运行 node scripts/build-api-docs.mjs：${stale.join(', ')}`)
console.log(
  `API docs ${check ? 'verified' : 'generated'}: ${files.size} files, ${Object.keys(spec.paths).length} paths`
)
