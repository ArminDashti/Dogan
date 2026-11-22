import { apiClient } from './client'
import type { IceServerConfig, StreamResponse } from '@/types/api'

interface StreamWireResponse {
  device_id: string
  token: string
  url: string
  whep_url?: string
  hls_url?: string
  path?: string
  room: string
  identity: string
  expires_at: string
  url_warnings?: string[]
  ice_servers?: IceServerConfig[]
}

function requireValue(value: string | undefined, field: string): string {
  if (!value) {
    throw new Error(`Stream credentials are missing ${field}.`)
  }
  return value
}

export async function fetchStreamCredentials(deviceId: string): Promise<StreamResponse> {
  const response = await apiClient.get<StreamWireResponse>(
    `/devices/${encodeURIComponent(deviceId)}/stream`,
  )

  if (response.url_warnings?.length) {
    console.warn('[dogan stream] MediaMTX URL warnings:', response.url_warnings)
  }

  const whepUrl = requireValue(response.whep_url ?? response.url, 'whep_url')

  return {
    deviceId: requireValue(response.device_id, 'device_id'),
    token: requireValue(response.token, 'token'),
    url: response.url || whepUrl,
    whepUrl,
    hlsUrl: response.hls_url || undefined,
    path: requireValue(response.path ?? response.room, 'path'),
    room: requireValue(response.room, 'room'),
    identity: requireValue(response.identity, 'identity'),
    expiresAt: requireValue(response.expires_at, 'expires_at'),
    urlWarnings: response.url_warnings,
    iceServers: response.ice_servers,
  }
}
