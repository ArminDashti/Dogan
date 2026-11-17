import { apiClient } from './client'
import type { StreamResponse } from '@/types/api'

export async function fetchStreamCredentials(deviceId: string): Promise<StreamResponse> {
  const response = await apiClient.get<{
    device_id: string
    token: string
    url: string
    room: string
    identity: string
    expires_at: string
    url_warnings?: string[]
    ice_servers?: { urls: string[] }[]
  }>(`/devices/${encodeURIComponent(deviceId)}/stream`)

  if (response.url_warnings?.length) {
    console.warn('[dogan stream] API LiveKit URL warnings:', response.url_warnings)
  }

  return {
    deviceId: response.device_id,
    token: response.token,
    url: response.url,
    room: response.room,
    identity: response.identity,
    expiresAt: response.expires_at,
    urlWarnings: response.url_warnings,
    iceServers: response.ice_servers,
  }
}
