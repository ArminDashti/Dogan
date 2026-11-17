import { apiClient } from './client'
import type { Device } from '@/types/api'

export async function fetchDevices(): Promise<Device[]> {
  return apiClient.get<Device[]>('/devices')
}

export async function deleteDevice(deviceId: string): Promise<void> {
  await apiClient.delete(`/devices/${encodeURIComponent(deviceId)}`)
}
