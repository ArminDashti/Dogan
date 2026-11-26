import { apiClient } from './client'
import type { DeviceMetrics, MetricReading, MetricsHistoryRange } from '@/types/api'

interface MetricReadingWire {
  timestamp: string
  battery_percent: number
  battery_temperature_celsius: number
  noise_db: number
  jolt: number
  signal_strength: number
  sim1_signal_dbm?: number
  sim2_signal_dbm?: number
  network_type: string
  server_phone_latency_ms: number
}

interface DeviceMetricsWire {
  device_id: string
  device_name: string
  current: {
    battery_percent: number
    battery_temperature_celsius: number
    noise_db: number
    jolt: number
    signal_strength: number
    sim1_signal_dbm?: number
    sim2_signal_dbm?: number
    network_type: string
    server_phone_latency_ms: number
    recorded_at: string
  }
  history: MetricReadingWire[]
}

function mapReading(reading: MetricReadingWire): MetricReading {
  return {
    timestamp: reading.timestamp,
    batteryPercent: reading.battery_percent,
    batteryTemperatureCelsius: reading.battery_temperature_celsius,
    noiseDb: reading.noise_db,
    jolt: reading.jolt,
    signalStrength: reading.signal_strength,
    sim1SignalDbm: reading.sim1_signal_dbm ?? reading.signal_strength ?? 0,
    sim2SignalDbm: reading.sim2_signal_dbm ?? 0,
    networkType: reading.network_type ?? '',
    serverPhoneLatencyMs: reading.server_phone_latency_ms,
  }
}

export async function fetchDeviceMetrics(
  deviceId: string,
  range: MetricsHistoryRange = 'today',
  bucketSeconds?: number,
): Promise<DeviceMetrics> {
  const query = new URLSearchParams({ range })
  if (bucketSeconds) {
    query.set('bucket', String(bucketSeconds))
  }
  const raw = await apiClient.get<DeviceMetricsWire>(
    `/devices/${encodeURIComponent(deviceId)}/metrics?${query.toString()}`,
  )

  return {
    deviceId: raw.device_id,
    deviceName: raw.device_name,
    current: {
      batteryPercent: raw.current.battery_percent,
      batteryTemperatureCelsius: raw.current.battery_temperature_celsius,
      noiseDb: raw.current.noise_db,
      jolt: raw.current.jolt,
      signalStrength: raw.current.signal_strength,
      sim1SignalDbm: raw.current.sim1_signal_dbm ?? raw.current.signal_strength ?? 0,
      sim2SignalDbm: raw.current.sim2_signal_dbm ?? 0,
      networkType: raw.current.network_type ?? '',
      serverPhoneLatencyMs: raw.current.server_phone_latency_ms,
      recordedAt: raw.current.recorded_at,
    },
    history: (raw.history ?? []).map(mapReading),
  }
}
