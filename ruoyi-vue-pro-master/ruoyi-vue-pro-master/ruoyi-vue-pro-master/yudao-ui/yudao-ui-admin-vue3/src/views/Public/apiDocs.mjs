export function resolveSchema(spec, schema = {}) {
  if (!schema?.$ref) return schema || {}
  return (
    schema.$ref
      .split('/')
      .slice(1)
      .reduce((node, key) => node?.[key], spec) || {}
  )
}

function combinedSchema(spec, schema, depth = 0) {
  const source = resolveSchema(spec, schema)
  if (depth > 10) return source
  const selected =
    !source.properties && source.oneOf?.length
      ? combinedSchema(spec, source.oneOf[0], depth + 1)
      : source
  return (selected.allOf || []).reduce((result, entry) => {
    const part = combinedSchema(spec, entry, depth + 1)
    return {
      ...result,
      ...part,
      properties: { ...result.properties, ...part.properties },
      required: [...(result.required || []), ...(part.required || [])]
    }
  }, selected)
}

export function schemaRows(spec, schema, prefix = '', seen = new Set()) {
  if (!schema || seen.has(schema.$ref || schema)) return []
  const nextSeen = new Set(seen).add(schema.$ref || schema)
  const combined = combinedSchema(spec, schema)
  return Object.entries(combined.properties || {}).flatMap(([key, original]) => {
    const item = resolveSchema(spec, original)
    const name = prefix ? `${prefix}.${key}` : key
    const constraints = []
    if (item.enum) constraints.push(item.enum.join(' / '))
    if (item.minimum !== undefined) constraints.push(`最小 ${item.minimum}`)
    if (item.maximum !== undefined) constraints.push(`最大 ${item.maximum}`)
    if (item.minLength !== undefined) constraints.push(`至少 ${item.minLength} 字符`)
    if (item.maxLength !== undefined) constraints.push(`最多 ${item.maxLength} 字符`)
    if (item.pattern) constraints.push(`格式 ${item.pattern}`)
    if (item.default !== undefined) constraints.push(`默认 ${JSON.stringify(item.default)}`)
    if (item.nullable) constraints.push('可为 null')
    const type =
      item.type || (item.properties || item.allOf ? 'object' : item.oneOf ? 'oneOf' : '—')
    const row = {
      name,
      type: type === 'array' ? `array<${resolveSchema(spec, item.items).type || 'object'}>` : type,
      required: (combined.required || []).includes(key),
      description: item.description || '',
      constraint: constraints.join('；')
    }
    const nested = item.type === 'array' ? item.items : original
    return [row, ...schemaRows(spec, nested, item.type === 'array' ? `${name}[]` : name, nextSeen)]
  })
}

export function sampleValue(spec, original, depth = 0) {
  if (depth > 7) return null
  const source = resolveSchema(spec, original)
  if (source.example !== undefined) return source.example
  if (source.default !== undefined) return source.default
  const schema = combinedSchema(spec, original)
  if (schema.example !== undefined) return schema.example
  if (schema.default !== undefined) return schema.default
  if (schema.enum?.length) return schema.enum[0]
  if (schema.type === 'array') return [sampleValue(spec, schema.items, depth + 1)]
  if (schema.properties)
    return Object.fromEntries(
      Object.entries(schema.properties)
        .filter(([key]) => key !== 'voiceProfileId')
        .map(([key, value]) => [key, sampleValue(spec, value, depth + 1)])
    )
  if (schema.type === 'boolean') return false
  if (['integer', 'number'].includes(schema.type)) return schema.minimum ?? 1
  return 'REPLACE_ME'
}

const shellQuote = (value) => `'${String(value).replaceAll("'", "'\\''")}'`

export function operationExample(spec, path, method, operation) {
  const headers = {}
  const schemes = spec.components?.securitySchemes || {}
  const security = operation.security ?? spec.security ?? []
  for (const key of Object.keys(security[0] || {})) {
    const scheme = schemes[key]
    if (scheme?.type === 'http' && scheme.scheme === 'bearer')
      headers.Authorization = 'Bearer YOUR_ACCESS_TOKEN'
    if (scheme?.type === 'apiKey' && scheme.in === 'header') {
      headers[scheme.name] =
        scheme.name === 'tenant-id'
          ? 'YOUR_TENANT_ID'
          : `YOUR_${scheme.name.replaceAll('-', '_').toUpperCase()}`
    }
  }
  const params = [...(spec.paths?.[path]?.parameters || []), ...(operation.parameters || [])]
  const query = new URLSearchParams()
  for (const original of params) {
    const p = resolveSchema(spec, original)
    const value =
      p.example ??
      p.schema?.example ??
      p.schema?.default ??
      (p.in === 'path' ? '789' : 'REPLACE_ME')
    if (p.in === 'path') path = path.replace(`{${p.name}}`, encodeURIComponent(value))
    if (
      p.in === 'query' &&
      (p.required || p.example !== undefined || p.schema?.default !== undefined)
    )
      query.set(p.name, value)
    if (p.in === 'header' && !headers[p.name] && p.required) headers[p.name] = value
  }
  const base = (operation.servers ||
    spec.servers || [{ url: 'https://clipforgestudio.fnn.net.cn' }])[0].url
  const url = `${base.replace(/\/$/, '')}${path}${query.size ? `?${query}` : ''}`
  const content = resolveSchema(spec, operation.requestBody).content || {}
  const media = content['application/json']
  const multipart = !!content['multipart/form-data']
  const binary = !!content['application/octet-stream']
  const body = media
    ? JSON.stringify(
        media.example ??
          Object.values(media.examples || {})[0]?.value ??
          sampleValue(spec, media.schema),
        null,
        2
      )
    : undefined
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (binary) headers['Content-Type'] = 'application/octet-stream'
  const uploadArgs = binary
    ? [`  --data-binary '@chunk.bin'`]
    : multipart
      ? [`  --form 'file=@image.png'`]
      : []
  const curl = [
    `curl --request ${method.toUpperCase()} ${shellQuote(url)}`,
    ...Object.entries(headers).map(
      ([key, value]) => `  --header ${shellQuote(`${key}: ${value}`)}`
    ),
    ...uploadArgs,
    ...(body === undefined ? [] : [`  --data-raw ${shellQuote(body)}`])
  ].join(' \\\n')
  const pythonBody = binary
    ? 'from pathlib import Path\nbody = Path("chunk.bin").read_bytes()'
    : multipart
      ? 'from pathlib import Path\nimport uuid\nboundary = "----clipforge" + uuid.uuid4().hex\nbody = (f\'--{boundary}\\r\\nContent-Disposition: form-data; name="file"; filename="image.png"\\r\\nContent-Type: image/png\\r\\n\\r\\n\'.encode()\n        + Path("image.png").read_bytes() + f"\\r\\n--{boundary}--\\r\\n".encode())\nheaders["Content-Type"] = "multipart/form-data; boundary=" + boundary'
      : body === undefined
        ? 'body = None'
        : `body = ${JSON.stringify(body)}.encode("utf-8")`
  const python = `import json\nimport urllib.request\n\nurl = ${JSON.stringify(url)}\nheaders = json.loads(${JSON.stringify(JSON.stringify(headers))})\n${pythonBody}\nrequest = urllib.request.Request(url, data=body, headers=headers, method="${method.toUpperCase()}")\nwith urllib.request.urlopen(request, timeout=30) as response:\n    print(response.read().decode("utf-8"))`
  const jsUpload = binary
    ? 'import { readFile } from "node:fs/promises";\nconst body = await readFile("chunk.bin");\n'
    : multipart
      ? 'import { readFile } from "node:fs/promises";\nconst body = new FormData();\nbody.append("file", new Blob([await readFile("image.png")], { type: "image/png" }), "image.png");\n'
      : ''
  const javascript = `${jsUpload}const url = ${JSON.stringify(url)};\nconst response = await fetch(url, {\n  method: "${method.toUpperCase()}",\n  headers: ${JSON.stringify(headers, null, 2).replaceAll('\n', '\n  ')},${jsUpload ? '\n  body,' : body === undefined ? '' : `\n  body: JSON.stringify(${body.replaceAll('\n', '\n  ')}),`}\n  signal: AbortSignal.timeout(30000),\n});\nconsole.log(await response.json());`
  return {
    url,
    headers,
    body,
    curl,
    python,
    javascript,
    signed: Object.values(headers).some((v) => String(v).includes('X_TK_SIGNATURE')),
    multipart,
    binary
  }
}

export function searchOperations(operations, query) {
  const words = query.toLowerCase().trim().split(/\s+/).filter(Boolean)
  return operations.filter((op) =>
    words.every((word) =>
      `${op.method} ${op.path} ${op.summary} ${(op.tags || []).join(' ')}`
        .toLowerCase()
        .includes(word)
    )
  )
}

export function markdownBlocks(source = '') {
  const lines = source.replaceAll('\r\n', '\n').split('\n')
  const blocks = []
  const cells = (line) =>
    line
      .trim()
      .replace(/^\||\|$/g, '')
      .split('|')
      .map((c) => c.trim())
  let index = 0
  while (index < lines.length) {
    const line = lines[index]
    if (!line.trim() || /^\s*---+\s*$/.test(line)) {
      index++
      continue
    }
    if (/^```/.test(line)) {
      const language = line.slice(3).trim()
      const text = []
      while (++index < lines.length && !/^```/.test(lines[index])) text.push(lines[index])
      blocks.push({ type: 'code', language, text: text.join('\n') })
      index++
      continue
    }
    if (/^#{1,6}\s/.test(line)) {
      blocks.push({
        type: 'heading',
        level: line.match(/^#+/)[0].length,
        text: line.replace(/^#+\s/, '')
      })
      index++
      continue
    }
    if (line.includes('|') && /^\s*\|?\s*:?-{3}/.test(lines[index + 1] || '')) {
      const headers = cells(line)
      const rows = []
      index += 2
      while (index < lines.length && lines[index].includes('|') && lines[index].trim())
        rows.push(cells(lines[index++]))
      blocks.push({ type: 'table', headers, rows })
      continue
    }
    if (/^\s*([-*]|\d+\.)\s/.test(line)) {
      const items = []
      const ordered = /^\s*\d+\./.test(line)
      while (index < lines.length && /^\s*([-*]|\d+\.)\s/.test(lines[index]))
        items.push(lines[index++].replace(/^\s*([-*]|\d+\.)\s/, ''))
      blocks.push({ type: 'list', ordered, items })
      continue
    }
    const paragraph = [line.replace(/^>\s?/, '')]
    while (
      ++index < lines.length &&
      lines[index].trim() &&
      !/^(#{1,6}\s|```|[-*]\s|\d+\.\s|\|)/.test(lines[index])
    )
      paragraph.push(lines[index])
    blocks.push({ type: 'paragraph', text: paragraph.join('\n') })
  }
  return blocks
}

export function inlineTokens(source = '') {
  const pattern = /(`[^`]+`|\*\*[^*]+\*\*|\[[^\]]+\]\([^\s)]+\))/g
  return source
    .split(pattern)
    .filter(Boolean)
    .map((text) => {
      if (text.startsWith('`')) return { type: 'code', text: text.slice(1, -1) }
      if (text.startsWith('**')) return { type: 'strong', text: text.slice(2, -2) }
      const match = text.match(/^\[([^\]]+)\]\(([^)]+)\)$/)
      if (match) {
        const href = match[2]
        if (/^(https?:\/\/|#|\/api-reference\/)/.test(href))
          return { type: 'link', text: match[1], href }
        if (/^[\w./-]+\.(md|json|py|mjs|zip)$/.test(href) && !href.includes('..'))
          return { type: 'link', text: match[1], href: `/api-reference/${href}` }
        return { type: 'text', text: match[1] }
      }
      return { type: 'text', text }
    })
}
