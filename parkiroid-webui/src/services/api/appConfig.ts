const LOCAL_DEFAULT = 'http://localhost:8080/dogan/api/v1'

interface AppConfigFile {
  apiBaseUrl?: string
}

function normalizeBaseUrl(url: string): string {
  return url.replace(/\/$/, '')
}

export async function resolveApiBaseUrl(): Promise<string> {
  try {
    const response = await fetch('/app-config.json', { cache: 'no-store' })
    if (response.ok) {
      const data = (await response.json()) as AppConfigFile
      const fromFile = data.apiBaseUrl?.trim() ?? ''
      if (fromFile.length > 0) {
        return normalizeBaseUrl(fromFile)
      }
    }
  } catch {
    // Offline or missing config file — fall through to dev defaults.
  }

  const fromVite = import.meta.env.VITE_API_BASE_URL?.trim()
  if (fromVite) {
    return normalizeBaseUrl(fromVite)
  }

  return normalizeBaseUrl(LOCAL_DEFAULT)
}
