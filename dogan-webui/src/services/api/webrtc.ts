import { apiClient } from './client'
import type { WebRtcConnection } from '@/types/api'

interface WebRtcConnectionWire {
  id: number
  device_id: string
  room: string
  identity: string
  role: string
  connected_at: string
  disconnected_at?: string
  status: string
}

interface WebRtcConnectionsWire {
  connections: WebRtcConnectionWire[]
}

export async function fetchWebRtcConnections(limit = 20): Promise<WebRtcConnection[]> {
  const raw = await apiClient.get<WebRtcConnectionsWire>(`/webrtc/connections?limit=${limit}`)
  return (raw.connections ?? []).map((connection) => ({
    id: connection.id,
    deviceId: connection.device_id,
    room: connection.room,
    identity: connection.identity,
    role: connection.role,
    connectedAt: connection.connected_at,
    disconnectedAt: connection.disconnected_at,
    status: connection.status,
  }))
}
