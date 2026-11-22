import { apiClient } from './client'
import type { HealthStatus } from '@/types/api'

interface HealthWire {
  status: string
  timestamp: string
  streaming_configured?: boolean
  streaming_webrtc_url?: string
  streaming_hls_url?: string
  streaming_warnings?: string[]
  turn_domain?: string
}

export async function fetchHealth(): Promise<HealthStatus> {
  const raw = await apiClient.get<HealthWire>('/health', { auth: false })
  return {
    status: raw.status,
    timestamp: raw.timestamp,
    streamingConfigured: Boolean(raw.streaming_configured),
    streamingWebRtcUrl: raw.streaming_webrtc_url,
    streamingHlsUrl: raw.streaming_hls_url,
    streamingWarnings: raw.streaming_warnings,
    turnDomain: raw.turn_domain,
  }
}
