<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import AppLayout from '@/components/AppLayout.vue'
import CameraPanel from '@/components/CameraPanel.vue'
import ErrorAlert from '@/components/ErrorAlert.vue'
import { deleteDevice, fetchDevices } from '@/services/api/devices'
import { fetchImages, revokeGalleryObjectUrls } from '@/services/api/gallery'
import { fetchSettings } from '@/services/api/settings'
import { fetchWebRtcConnections } from '@/services/api/logs'
import { getErrorMessage } from '@/services/api/errors'
import type { Device, GalleryImage, WebRtcConnection } from '@/types/api'

const POLL_MS = 5000
const EMPTY_MESSAGE =
  'No device registered yet. Open Dogan on an Android handset and sign in — it will appear here.'

const devices = ref<Device[]>([])
const selectedDeviceId = ref('')
const loading = ref(true)
const errorMessage = ref<string | null>(null)
const unpairingId = ref('')
const lastImage = ref<GalleryImage | null>(null)
const connections = ref<WebRtcConnection[]>([])

let pollTimer: ReturnType<typeof setInterval> | null = null
let imageRequest = 0

const selectedDevice = computed(() =>
  devices.value.find((d) => d.id === selectedDeviceId.value) ?? null,
)

const deviceLogs = computed(() =>
  connections.value.filter((entry) => entry.deviceId === selectedDeviceId.value),
)

function pad(value: number): string {
  return String(value).padStart(2, '0')
}

function formatBattery(percent: number | undefined, temperature: number | undefined): string {
  const hasPercent = percent !== undefined && !Number.isNaN(percent)
  const hasTemperature = temperature !== undefined && !Number.isNaN(temperature)
  if (!hasPercent && !hasTemperature) {
    return '—'
  }
  if (hasPercent && hasTemperature) {
    return `${Math.round(percent)}% (${temperature.toFixed(1)}°C)`
  }
  if (hasPercent) {
    return `${Math.round(percent)}%`
  }
  return `(${temperature!.toFixed(1)}°C)`
}

function formatLatency(value: number | undefined): string {
  if (value === undefined || Number.isNaN(value)) {
    return '—'
  }
  return `${Math.round(value)} ms`
}

function formatMode(value: string | undefined): string {
  if (!value) {
    return '—'
  }
  return value.charAt(0).toUpperCase() + value.slice(1)
}

function formatLastUpdate(value: string | undefined): string {
  if (!value) {
    return '—'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function formatLogTime(value: string | undefined): string {
  return formatLastUpdate(value)
}

function dash(value: string | undefined): string {
  return value?.trim() ? value : '—'
}

function stopPolling(): void {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

function startPolling(): void {
  stopPolling()
  pollTimer = setInterval(() => {
    void loadDevices({ silent: true })
  }, POLL_MS)
}

function pickDeviceId(list: Device[], preferredId?: string | null): string {
  if (list.length === 0) {
    return ''
  }
  if (preferredId && list.some((d) => d.id === preferredId)) {
    return preferredId
  }
  const online = list.find((d) => d.status === 'online')
  return online?.id ?? list[0].id
}

function clearLastImage(): void {
  if (lastImage.value) {
    revokeGalleryObjectUrls([lastImage.value])
    lastImage.value = null
  }
}

async function loadLastImage(deviceId: string): Promise<void> {
  const request = ++imageRequest
  if (!deviceId) {
    clearLastImage()
    return
  }
  try {
    const images = await fetchImages()
    if (request !== imageRequest) {
      revokeGalleryObjectUrls(images)
      return
    }
    const match = images
      .filter((image) => image.deviceId === deviceId || image.caption === deviceId)
      .sort((a, b) => Date.parse(b.capturedAt) - Date.parse(a.capturedAt))[0]
    const unused = match ? images.filter((image) => image.id !== match.id) : images
    revokeGalleryObjectUrls(unused)
    clearLastImage()
    lastImage.value = match ?? null
  } catch {
    if (request === imageRequest) {
      clearLastImage()
    }
  }
}

async function loadLogs(): Promise<void> {
  try {
    connections.value = await fetchWebRtcConnections(40)
  } catch {
    connections.value = []
  }
}

async function loadDevices(options: { silent?: boolean } = {}): Promise<void> {
  if (!options.silent) {
    loading.value = true
    errorMessage.value = null
  }

  try {
    const [list, settings] = await Promise.all([fetchDevices(), fetchSettings()])
    devices.value = list
    const keep =
      selectedDeviceId.value && list.some((d) => d.id === selectedDeviceId.value)
        ? selectedDeviceId.value
        : pickDeviceId(list, settings.defaultDeviceId)
    selectedDeviceId.value = keep
    if (options.silent) {
      void loadLogs()
    }
  } catch (error) {
    if (!options.silent) {
      errorMessage.value = getErrorMessage(error, 'Unable to load device info.')
    }
  } finally {
    if (!options.silent) {
      loading.value = false
    }
  }
}

async function unpair(device: Device): Promise<void> {
  if (unpairingId.value) {
    return
  }
  const confirmed = window.confirm(`Unpair ${device.name || device.id}?`)
  if (!confirmed) {
    return
  }
  unpairingId.value = device.id
  errorMessage.value = null
  try {
    await deleteDevice(device.id)
    if (selectedDeviceId.value === device.id) {
      selectedDeviceId.value = ''
    }
    await loadDevices({ silent: true })
  } catch (error) {
    errorMessage.value = getErrorMessage(error, 'Unable to unpair device.')
  } finally {
    unpairingId.value = ''
  }
}

watch(selectedDeviceId, (id) => {
  void loadLastImage(id)
  void loadLogs()
})

onMounted(async () => {
  await loadDevices()
  startPolling()
})

onUnmounted(() => {
  stopPolling()
  imageRequest += 1
  clearLastImage()
})
</script>

<template>
  <AppLayout title="Device" subtitle="Details for the selected handset." :scrollable="false">
    <div class="device-page">
      <ErrorAlert v-if="errorMessage" title="Device unavailable" :message="errorMessage" />

      <div v-if="loading" class="device-loading yoda-card">
        <span class="stream-live-dot" aria-hidden="true" />
        Loading device…
      </div>

      <div v-else-if="devices.length === 0" class="device-empty yoda-card">
        <p>{{ EMPTY_MESSAGE }}</p>
      </div>

      <template v-else>
        <div class="device-table-wrap yoda-card">
          <table class="device-table">
            <thead>
              <tr>
                <th>Device ID</th>
                <th>Name</th>
                <th>OS</th>
                <th>Mode</th>
                <th>Location</th>
                <th>Battery</th>
                <th>Latency</th>
                <th>Last update</th>
                <th>Unpair</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="device in devices"
                :key="device.id"
                :class="{ 'device-table__row--selected': device.id === selectedDeviceId }"
                @click="selectedDeviceId = device.id"
              >
                <td class="device-table__mono">{{ device.id }}</td>
                <td>{{ dash(device.name) }}</td>
                <td>{{ dash(device.os) }}</td>
                <td>{{ formatMode(device.mode) }}</td>
                <td>{{ dash(device.location) }}</td>
                <td>{{ formatBattery(device.batteryPercent, device.temperatureCelsius) }}</td>
                <td>{{ formatLatency(device.latencyMs) }}</td>
                <td class="device-table__nowrap">{{ formatLastUpdate(device.lastUpdate) }}</td>
                <td>
                  <button
                    type="button"
                    class="device-unpair"
                    :disabled="unpairingId === device.id"
                    @click.stop="unpair(device)"
                  >
                    Unpair
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="device-stage">
          <div class="device-squares">
            <section class="device-square yoda-card" aria-label="Stream">
              <h2 class="device-square__title">Stream</h2>
              <div class="device-square__body">
                <CameraPanel
                  v-if="selectedDevice"
                  fill
                  :device-id="selectedDevice.id"
                  :device-name="selectedDevice.name"
                />
              </div>
            </section>

            <section class="device-square yoda-card" aria-label="Last image">
              <h2 class="device-square__title">Last image</h2>
              <div class="device-square__body device-square__body--center">
                <img
                  v-if="lastImage"
                  class="device-last-image"
                  :src="lastImage.url"
                  alt="Last camera image"
                />
                <p v-else class="device-square__empty">No image yet</p>
              </div>
            </section>

            <section class="device-square yoda-card" aria-label="Logs">
              <h2 class="device-square__title">Logs</h2>
              <div class="device-square__body">
                <ul v-if="deviceLogs.length > 0" class="device-logs">
                  <li v-for="entry in deviceLogs" :key="entry.id">
                    <span class="device-table__mono">{{ formatLogTime(entry.connectedAt) }}</span>
                    {{ entry.status }} · {{ entry.role }}
                  </li>
                </ul>
                <p v-else class="device-square__empty">No logs yet</p>
              </div>
            </section>
          </div>
        </div>
      </template>
    </div>
  </AppLayout>
</template>

<style scoped>
.device-page {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.device-loading,
.device-empty {
  padding: 2rem 1.25rem;
  text-align: center;
  color: var(--muted);
  font-size: 0.9375rem;
}

.device-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.65rem;
}

.device-table-wrap {
  flex: 0 1 auto;
  max-height: 38%;
  overflow: hidden;
}

.device-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.8125rem;
}

.device-table th,
.device-table td {
  padding: 0.45rem 0.6rem;
  text-align: left;
  border-bottom: 1px solid var(--border);
  vertical-align: middle;
}

.device-table th {
  font-size: 0.75rem;
  font-weight: 650;
  color: var(--muted);
}

.device-table tbody tr {
  cursor: pointer;
}

.device-table__row--selected {
  background: color-mix(in srgb, var(--accent, #3b82f6) 14%, transparent);
}

.device-table__mono {
  font-family: var(--font-mono);
  font-size: 0.75rem;
  word-break: break-all;
}

.device-table__nowrap {
  white-space: nowrap;
}

.device-unpair {
  padding: 0.25rem 0.55rem;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border);
  background: var(--surface);
  color: var(--text);
  font-size: 0.75rem;
  cursor: pointer;
}

.device-unpair:disabled {
  opacity: 0.6;
  cursor: default;
}

.device-stage {
  flex: 1 1 auto;
  min-height: 0;
  container-type: size;
}

.device-squares {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0.75rem;
  width: min(100cqw, calc(300cqh + 1.5rem));
  margin-inline: auto;
}

.device-square {
  aspect-ratio: 1;
  min-width: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.device-square__title {
  margin: 0;
  padding: 0.45rem 0.7rem;
  font-size: 0.8125rem;
  font-weight: 650;
  border-bottom: 1px solid var(--border);
}

.device-square__body {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.device-square__body--center {
  display: flex;
  align-items: center;
  justify-content: center;
}

.device-last-image {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.device-square__empty {
  margin: 0;
  padding: 0.75rem;
  color: var(--muted);
  font-size: 0.8125rem;
  text-align: center;
}

.device-logs {
  margin: 0;
  padding: 0.45rem 0.7rem;
  list-style: none;
  font-size: 0.75rem;
}

.device-logs li {
  padding: 0.2rem 0;
  border-bottom: 1px solid var(--border);
}
</style>
