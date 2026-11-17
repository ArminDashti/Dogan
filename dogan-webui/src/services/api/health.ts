import { apiClient } from './client'
import type { HealthStatus } from '@/types/api'

interface HealthWire {
  status: string
  timestamp: string
  livekit_configured?: boolean
  livekit_client_url?: string
  livekit_warnings?: string[]
  turn_domain?: string
}

export async function fetchHealth(): Promise<HealthStatus> {
  const raw = await apiClient.get<HealthWire>('/health', { auth: false })
  return {
    status: raw.status,
    timestamp: raw.timestamp,
    livekitConfigured: Boolean(raw.livekit_configured),
    livekitClientUrl: raw.livekit_client_url,
    livekitWarnings: raw.livekit_warnings,
    turnDomain: raw.turn_domain,
  }
}
