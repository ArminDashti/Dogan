import { computed, ref } from 'vue'
import { fetchDevices } from '@/services/api/devices'
import { fetchSettings, updateSettings } from '@/services/api/settings'
import { getErrorMessage } from '@/services/api/errors'
import type { Device } from '@/types/api'

const devices = ref<Device[]>([])
const deviceId = ref('')
const loading = ref(false)
const errorMessage = ref<string | null>(null)

let loaded = false
let inflight: Promise<void> | null = null

function pickDeviceId(list: Device[], preferredId?: string | null): string {
  if (list.length === 0) {
    return ''
  }
  if (preferredId && list.some((device) => device.id === preferredId)) {
    return preferredId
  }
  const online = list.find((device) => device.status === 'online')
  return online?.id ?? list[0].id
}

async function load(force = false): Promise<void> {
  if (loaded && !force) {
    return
  }
  if (inflight) {
    return inflight
  }

  loading.value = true
  errorMessage.value = null
  const task = (async () => {
    try {
      const [list, settings] = await Promise.all([fetchDevices(), fetchSettings()])
      devices.value = list
      const keep = list.some((device) => device.id === deviceId.value) ? deviceId.value : ''
      deviceId.value = keep || pickDeviceId(list, settings.defaultDeviceId)
      loaded = true
    } catch (error) {
      errorMessage.value = getErrorMessage(error, 'Unable to load devices.')
    } finally {
      loading.value = false
      inflight = null
    }
  })()
  inflight = task

  await task
}

/** Re-reads the device list without touching the saved selection. */
async function refreshDevices(): Promise<void> {
  try {
    const list = await fetchDevices()
    devices.value = list
    if (!list.some((device) => device.id === deviceId.value)) {
      deviceId.value = pickDeviceId(list)
    }
  } catch {
    // Keep the last known list; the page shows its own error state.
  }
}

/** Persists the selection to Settings so every section uses the same device. */
async function setDevice(nextId: string): Promise<void> {
  deviceId.value = nextId
  try {
    await updateSettings({ defaultDeviceId: nextId })
  } catch {
    // The selection still applies for this session.
  }
}

export function useSelectedDevice() {
  const selectedDevice = computed(
    () => devices.value.find((device) => device.id === deviceId.value) ?? null,
  )
  const deviceName = computed(() => selectedDevice.value?.name ?? deviceId.value)

  return {
    devices,
    deviceId,
    deviceName,
    selectedDevice,
    loading,
    errorMessage,
    load,
    refreshDevices,
    setDevice,
  }
}
