import { apiClient } from './client'
import type { Device } from '@/types/api'

interface DeviceWire {
  id: string
  name: string
  status: Device['status']
  os?: string
  mode?: string
  location?: string
  battery_percent?: number
  temperature_celsius?: number
  latency_ms?: number
  last_update?: string
}

function mapDevice(item: DeviceWire): Device {
  return {
    id: item.id,
    name: item.name,
    status: item.status,
    location: item.location,
    os: item.os,
    mode: item.mode,
    batteryPercent: item.battery_percent,
    temperatureCelsius: item.temperature_celsius,
    latencyMs: item.latency_ms,
    lastUpdate: item.last_update,
  }
}

export async function fetchDevices(): Promise<Device[]> {
  const list = await apiClient.get<DeviceWire[]>('/devices')
  return list.map(mapDevice)
}

export async function deleteDevice(deviceId: string): Promise<void> {
  await apiClient.delete(`/devices/${encodeURIComponent(deviceId)}`)
}
