import { apiClient } from './client'
import type { AppSettings } from '@/types/api'

export const SENSOR_INTERVAL_DEFAULTS = {
  gpsIntervalSec: 60,
  batteryIntervalSec: 60,
  temperatureIntervalSec: 60,
  noiseIntervalSec: 1,
  joltIntervalSec: 1,
  networkTypeIntervalSec: 60,
  sim1SignalIntervalSec: 30,
  sim2SignalIntervalSec: 30,
  latencyIntervalSec: 1,
  imageIntervalSec: 5,
} as const

interface AppSettingsWire {
  notifications_enabled: boolean
  temperature_unit: 'celsius' | 'fahrenheit'
  noise_alert_threshold_db: number
  default_device_id?: string
  gps_interval_sec?: number
  battery_interval_sec?: number
  temperature_interval_sec?: number
  noise_interval_sec?: number
  jolt_interval_sec?: number
  network_type_interval_sec?: number
  sim1_signal_interval_sec?: number
  sim2_signal_interval_sec?: number
  latency_interval_sec?: number
  image_interval_sec?: number
}

function interval(value: number | undefined, fallback: number): number {
  return typeof value === 'number' && Number.isFinite(value) ? value : fallback
}

function mapSettings(raw: AppSettingsWire): AppSettings {
  return {
    notificationsEnabled: raw.notifications_enabled,
    temperatureUnit: raw.temperature_unit,
    noiseAlertThresholdDb: raw.noise_alert_threshold_db,
    defaultDeviceId: raw.default_device_id,
    gpsIntervalSec: interval(raw.gps_interval_sec, SENSOR_INTERVAL_DEFAULTS.gpsIntervalSec),
    batteryIntervalSec: interval(raw.battery_interval_sec, SENSOR_INTERVAL_DEFAULTS.batteryIntervalSec),
    temperatureIntervalSec: interval(raw.temperature_interval_sec, SENSOR_INTERVAL_DEFAULTS.temperatureIntervalSec),
    noiseIntervalSec: interval(raw.noise_interval_sec, SENSOR_INTERVAL_DEFAULTS.noiseIntervalSec),
    joltIntervalSec: interval(raw.jolt_interval_sec, SENSOR_INTERVAL_DEFAULTS.joltIntervalSec),
    networkTypeIntervalSec: interval(raw.network_type_interval_sec, SENSOR_INTERVAL_DEFAULTS.networkTypeIntervalSec),
    sim1SignalIntervalSec: interval(raw.sim1_signal_interval_sec, SENSOR_INTERVAL_DEFAULTS.sim1SignalIntervalSec),
    sim2SignalIntervalSec: interval(raw.sim2_signal_interval_sec, SENSOR_INTERVAL_DEFAULTS.sim2SignalIntervalSec),
    latencyIntervalSec: interval(raw.latency_interval_sec, SENSOR_INTERVAL_DEFAULTS.latencyIntervalSec),
    imageIntervalSec: interval(raw.image_interval_sec, SENSOR_INTERVAL_DEFAULTS.imageIntervalSec),
  }
}

function toWire(settings: Partial<AppSettings>): Record<string, unknown> {
  const body: Record<string, unknown> = {}
  if (settings.notificationsEnabled !== undefined) {
    body.notifications_enabled = settings.notificationsEnabled
  }
  if (settings.temperatureUnit !== undefined) {
    body.temperature_unit = settings.temperatureUnit
  }
  if (settings.noiseAlertThresholdDb !== undefined) {
    body.noise_alert_threshold_db = settings.noiseAlertThresholdDb
  }
  if (settings.defaultDeviceId !== undefined) {
    body.default_device_id = settings.defaultDeviceId
  }
  if (settings.gpsIntervalSec !== undefined) body.gps_interval_sec = settings.gpsIntervalSec
  if (settings.batteryIntervalSec !== undefined) body.battery_interval_sec = settings.batteryIntervalSec
  if (settings.temperatureIntervalSec !== undefined) body.temperature_interval_sec = settings.temperatureIntervalSec
  if (settings.noiseIntervalSec !== undefined) body.noise_interval_sec = settings.noiseIntervalSec
  if (settings.joltIntervalSec !== undefined) body.jolt_interval_sec = settings.joltIntervalSec
  if (settings.networkTypeIntervalSec !== undefined) body.network_type_interval_sec = settings.networkTypeIntervalSec
  if (settings.sim1SignalIntervalSec !== undefined) body.sim1_signal_interval_sec = settings.sim1SignalIntervalSec
  if (settings.sim2SignalIntervalSec !== undefined) body.sim2_signal_interval_sec = settings.sim2SignalIntervalSec
  if (settings.latencyIntervalSec !== undefined) body.latency_interval_sec = settings.latencyIntervalSec
  if (settings.imageIntervalSec !== undefined) body.image_interval_sec = settings.imageIntervalSec
  return body
}

export async function fetchSettings(): Promise<AppSettings> {
  const raw = await apiClient.get<AppSettingsWire>('/settings')
  return mapSettings(raw)
}

export async function updateSettings(settings: Partial<AppSettings>): Promise<AppSettings> {
  const raw = await apiClient.patch<AppSettingsWire>('/settings', toWire(settings))
  return mapSettings(raw)
}
