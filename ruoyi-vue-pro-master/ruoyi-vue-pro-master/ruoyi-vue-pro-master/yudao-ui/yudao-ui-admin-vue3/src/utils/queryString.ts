type QueryInput = Record<string, any>

interface StringifyOptions {
  allowDots?: boolean
  arrayFormat?: 'indices' | 'repeat' | 'brackets'
}

const isPlainObject = (value: unknown): value is QueryInput =>
  Object.prototype.toString.call(value) === '[object Object]'

const encode = (value: string) => encodeURIComponent(value)

const appendParam = (
  parts: string[],
  key: string,
  value: any,
  options: Required<StringifyOptions>
) => {
  if (value === undefined) {
    return
  }

  if (value === null) {
    parts.push(`${encode(key)}=`)
    return
  }

  if (Array.isArray(value)) {
    value.forEach((item, index) => {
      if (options.arrayFormat === 'repeat') {
        appendParam(parts, key, item, options)
      } else if (options.arrayFormat === 'brackets') {
        appendParam(parts, `${key}[]`, item, options)
      } else {
        appendParam(parts, `${key}[${index}]`, item, options)
      }
    })
    return
  }

  if (value instanceof Date) {
    parts.push(`${encode(key)}=${encode(value.toISOString())}`)
    return
  }

  if (isPlainObject(value)) {
    Object.keys(value).forEach((childKey) => {
      const nextKey = options.allowDots ? `${key}.${childKey}` : `${key}[${childKey}]`
      appendParam(parts, nextKey, value[childKey], options)
    })
    return
  }

  parts.push(`${encode(key)}=${encode(String(value))}`)
}

export const stringifyQuery = (params: QueryInput = {}, options: StringifyOptions = {}) => {
  const resolvedOptions: Required<StringifyOptions> = {
    allowDots: options.allowDots ?? false,
    arrayFormat: options.arrayFormat ?? 'indices'
  }
  const parts: string[] = []
  Object.keys(params).forEach((key) => appendParam(parts, key, params[key], resolvedOptions))
  return parts.join('&')
}

export const parseQueryString = (queryString = ''): Record<string, any> => {
  const searchParams = new URLSearchParams(queryString.replace(/^\?/, ''))
  const query: Record<string, any> = {}
  searchParams.forEach((value, key) => {
    if (Object.prototype.hasOwnProperty.call(query, key)) {
      query[key] = Array.isArray(query[key]) ? [...query[key], value] : [query[key], value]
    } else {
      query[key] = value
    }
  })
  return query
}
