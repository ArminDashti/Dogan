import { apiClient } from './client'
import type { HealthStatus } from '@/types/api'

interface HealthWire {
  status: string
  timestamp: string
  livekit_configured?: boolean
}

export async function fetchHealth(): Promise<HealthStatus> {
  const raw = await apiClient.get<HealthWire>('/health', { auth: false })
  return {
    status: raw.status,
    timestamp: raw.timestamp,
    livekitConfigured: Boolean(raw.livekit_configured),
  }
}
