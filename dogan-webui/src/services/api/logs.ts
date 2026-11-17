import { apiClient } from './client'
import type { LoginLogEntry, WebRtcConnection } from '@/types/api'

interface LoginLogWire {
  id: number
  ip: string
  username: string
  browser_info: string
  attempted_at: string
  success: boolean
}

interface LoginLogsWire {
  login_logs: LoginLogWire[]
}

export async function fetchLoginLogs(limit = 50): Promise<LoginLogEntry[]> {
  const raw = await apiClient.get<LoginLogsWire>(`/auth/login-logs?limit=${limit}`)
  return (raw.login_logs ?? []).map((entry) => ({
    id: entry.id,
    ip: entry.ip,
    username: entry.username,
    browserInfo: entry.browser_info,
    attemptedAt: entry.attempted_at,
    success: entry.success,
  }))
}

export { fetchWebRtcConnections } from './webrtc'
export type { WebRtcConnection }