export interface User {
  id: string
  email: string
  name: string
}

export interface AuthResponse {
  token: string
  user: User
}

export interface Device {
  id: string
  name: string
  status: 'online' | 'offline' | 'unknown'
  location?: string
  os?: string
  mode?: string
  batteryPercent?: number
  temperatureCelsius?: number
  latencyMs?: number
  lastUpdate?: string
}

export type MetricsHistoryRange =
  | 'today'
  | 'yesterday'
  | 'this_week'
  | 'this_month'
  | 'last_week'
  | 'last_month'

export interface MetricReading {
  timestamp: string
  batteryPercent: number
  batteryTemperatureCelsius: number
  noiseDb: number
  jolt: number
  signalStrength: number
  networkType: string
  serverPhoneLatencyMs: number
}

export interface DeviceMetrics {
  deviceId: string
  deviceName: string
  current: {
    batteryPercent: number
    batteryTemperatureCelsius: number
    noiseDb: number
    jolt: number
    signalStrength: number
    networkType: string
    serverPhoneLatencyMs: number
    recordedAt: string
  }
  history: MetricReading[]
}

export interface DeviceTelemetry {
  deviceId: string
  batteryPercent: number
  batteryTemperatureCelsius: number
  noiseDb: number
  jolt: number
  signalStrength: number
  networkType: string
  serverPhoneLatencyMs: number
  serverWebLatencyMs: number
  recordedAt: string
}

export interface CaptureResponse {
  imageId: string
  url?: string
  capturedAt: string
}

export interface StreamResponse {
  deviceId: string
  token: string
  url: string
  room: string
  identity: string
  expiresAt: string
}

export interface GalleryImage {
  id: string
  url: string
  thumbnailUrl?: string
  caption?: string
  capturedAt: string
}

export interface AppSettings {
  notificationsEnabled: boolean
  temperatureUnit: 'celsius' | 'fahrenheit'
  noiseAlertThresholdDb: number
  defaultDeviceId?: string
}

export interface ApiErrorBody {
  message?: string
  errors?: Record<string, string[]>
}

export interface HealthStatus {
  status: string
  timestamp: string
  livekitConfigured: boolean
}

export interface WebRtcConnection {
  id: number
  deviceId: string
  room: string
  identity: string
  role: string
  connectedAt: string
  disconnectedAt?: string
  status: string
}

export interface LoginLogEntry {
  id: number
  ip: string
  username: string
  browserInfo: string
  attemptedAt: string
  success: boolean
}
