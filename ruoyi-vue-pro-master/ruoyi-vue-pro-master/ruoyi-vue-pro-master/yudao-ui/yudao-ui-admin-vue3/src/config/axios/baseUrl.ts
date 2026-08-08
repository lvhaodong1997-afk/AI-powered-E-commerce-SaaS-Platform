const normalizeBaseUrl = (baseUrl?: string): string => {
  const value = (baseUrl || '').trim()
  return value && value !== '/' ? value.replace(/\/$/, '') : ''
}

const normalizeApiUrl = (apiUrl?: string): string => {
  const value = (apiUrl || '').trim()
  if (!value) {
    return ''
  }
  return value.startsWith('/') ? value : `/${value}`
}

export const getApiBaseUrl = (): string => {
  return normalizeBaseUrl(import.meta.env.VITE_BASE_URL) + normalizeApiUrl(import.meta.env.VITE_API_URL)
}
