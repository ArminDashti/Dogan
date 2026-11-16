<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import AppLayout from '@/components/AppLayout.vue'
import CameraPanel from '@/components/CameraPanel.vue'
import ErrorAlert from '@/components/ErrorAlert.vue'
import { fetchDevices } from '@/services/api/devices'
import { fetchImages } from '@/services/api/gallery'
import { fetchHealth } from '@/services/api/health'
import { fetchSettings } from '@/services/api/settings'
import { fetchDeviceTelemetry } from '@/services/api/telemetry'
import { fetchWebRtcConnections } from '@/services/api/webrtc'
import { getErrorMessage } from '@/services/api/errors'
import type {
  AppSettings,
  Device,
  DeviceTelemetry,
  HealthStatus,
  WebRtcConnection,
} from '@/types/api'

const OPS_POLL_MS = 5000
const TELEMETRY_POLL_MS = 5000
const EMPTY_FLEET_MESSAGE =
  'No registered devices yet. Connect the Dogan Android app so a phone reports in — no seed script or mock API needed.'

const devices = ref<Device[]>([])
const selectedDeviceId = ref('')
const telemetry = ref<DeviceTelemetry | null>(null)
const health = ref<HealthStatus | null>(null)
const settings = ref<AppSettings | null>(null)
const galleryCount = ref(0)
const connections = ref<WebRtcConnection[]>([])
const loadingOps = ref(true)
const loadingTelemetry = ref(false)
const errorMessage = ref<string | null>(null)

let opsPollTimer: ReturnType<typeof setInterval> | null = null
let telemetryPollTimer: ReturnType<typeof setInterval> | null = null

const selectedDevice = computed(() =>
  devices.value.find((device) => device.id === selectedDeviceId.value),
)

const onlineCount = computed(
  () => devices.value.filter((device) => device.status === 'online').length,
)
const offlineCount = computed(
  () => devices.value.filter((device) => device.status !== 'online').length,
)

const hasDevices = computed(() => devices.value.length > 0)

function statusClass(status: Device['status']): string {
  switch (status) {
    case 'online':
      return 'status-pill status-pill--online'
    case 'offline':
      return 'status-pill status-pill--offline'
    default:
      return 'status-pill status-pill--unknown'
  }
}

function formatValue(value: number | undefined, suffix: string): string {
  if (value === undefined || Number.isNaN(value)) {
    return '--'
  }
  return `${value.toFixed(1)}${suffix}`
}

function formatPercent(value: number | undefined): string {
  if (value === undefined || Number.isNaN(value)) {
    return '--'
  }
  return `${Math.round(value)}%`
}

function formatLatency(value: number | undefined): string {
  if (value === undefined || Number.isNaN(value)) {
    return '--'
  }
  return `${Math.round(value)} ms`
}

function formatTimestamp(value: string | undefined): string {
  if (!value) {
    return '--'
  }
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}

const telemetryCards = computed(() => {
  const current = telemetry.value
  return [
    { label: 'Battery', value: formatPercent(current?.batteryPercent) },
    { label: 'Battery Temperature', value: formatValue(current?.batteryTemperatureCelsius, '°C') },
    { label: 'Noise', value: formatValue(current?.noiseDb, ' dB') },
    { label: 'Jolt', value: formatValue(current?.jolt, '') },
    { label: 'Signal', value: formatValue(current?.signalStrength, '') },
    { label: 'Network Type', value: current?.networkType ?? '--' },
    { label: 'Server-Phone Latency', value: formatLatency(current?.serverPhoneLatencyMs) },
    { label: 'Server-Web Latency', value: formatLatency(current?.serverWebLatencyMs) },
  ]
})

function stopOpsPolling(): void {
  if (opsPollTimer) {
    clearInterval(opsPollTimer)
    opsPollTimer = null
  }
}

function startOpsPolling(): void {
  stopOpsPolling()
  opsPollTimer = setInterval(() => {
    void loadOps({ silent: true })
  }, OPS_POLL_MS)
}

function stopTelemetryPolling(): void {
  if (telemetryPollTimer) {
    clearInterval(telemetryPollTimer)
    telemetryPollTimer = null
  }
}

function startTelemetryPolling(): void {
  stopTelemetryPolling()
  if (!hasDevices.value) {
    return
  }
  telemetryPollTimer = setInterval(() => {
    void loadTelemetry({ silent: true })
  }, TELEMETRY_POLL_MS)
}

async function loadOps(options: { silent?: boolean } = {}): Promise<void> {
  if (!options.silent) {
    loadingOps.value = true
    errorMessage.value = null
  }

  try {
    const [healthResult, deviceList, images, settingsResult, connectionList] = await Promise.all([
      fetchHealth(),
      fetchDevices(),
      fetchImages(),
      fetchSettings(),
      fetchWebRtcConnections(12),
    ])

    health.value = healthResult
    devices.value = deviceList
    galleryCount.value = images.length
    settings.value = settingsResult
    connections.value = connectionList

    if (!selectedDeviceId.value && deviceList.length > 0) {
      selectedDeviceId.value = deviceList[0].id
    }
    if (deviceList.length === 0) {
      selectedDeviceId.value = ''
      telemetry.value = null
      stopTelemetryPolling()
    }
  } catch (error) {
    if (!options.silent) {
      errorMessage.value = getErrorMessage(error, 'Unable to load ops dashboard.')
    }
  } finally {
    if (!options.silent) {
      loadingOps.value = false
    }
  }
}

async function loadTelemetry(options: { silent?: boolean } = {}): Promise<void> {
  if (!selectedDeviceId.value) {
    telemetry.value = null
    return
  }

  if (!options.silent) {
    loadingTelemetry.value = true
    errorMessage.value = null
  }

  try {
    telemetry.value = await fetchDeviceTelemetry(selectedDeviceId.value)
  } catch (error) {
    telemetry.value = null
    if (!options.silent) {
      errorMessage.value = getErrorMessage(error, 'Unable to load device telemetry.')
    }
  } finally {
    if (!options.silent) {
      loadingTelemetry.value = false
    }
  }
}

watch(selectedDeviceId, async (deviceId) => {
  if (!deviceId) {
    telemetry.value = null
    stopTelemetryPolling()
    return
  }
  await loadTelemetry()
  startTelemetryPolling()
})

onMounted(async () => {
  await loadOps()
  if (hasDevices.value) {
    await loadTelemetry()
    startTelemetryPolling()
  }
  startOpsPolling()
})

onUnmounted(() => {
  stopOpsPolling()
  stopTelemetryPolling()
})
</script>

<template>
  <AppLayout
    title="Dashboard"
    subtitle="Live ops status for API, fleet, gallery, settings, and streams."
    :scrollable="false"
  >
    <div class="flex h-full min-h-0 flex-col gap-3">
      <ErrorAlert v-if="errorMessage" title="Dashboard unavailable" :message="errorMessage" />

      <div
        v-if="loadingOps"
        class="yoda-card flex flex-1 items-center justify-center px-5 py-8 text-[var(--muted)]"
      >
        Loading ops status…
      </div>

      <template v-else>
        <div class="grid shrink-0 gap-3 xl:grid-cols-[minmax(0,1fr)_minmax(220px,0.34fr)]">
          <section class="grid grid-cols-2 gap-2 xl:grid-cols-3">
            <article class="instrument-cell !p-2.5">
              <p class="instrument-label">API health</p>
              <p class="instrument-value !mt-0.5 !text-lg">{{ health?.status ?? '--' }}</p>
              <p class="mt-0.5 truncate font-mono text-[0.65rem] text-[var(--muted)]">
                {{ formatTimestamp(health?.timestamp) }}
              </p>
            </article>

            <article class="instrument-cell !p-2.5">
              <p class="instrument-label">LiveKit</p>
              <p class="instrument-value !mt-0.5 !text-lg">
                {{ health?.livekitConfigured ? 'Configured' : 'Not configured' }}
              </p>
            </article>

            <article class="instrument-cell !p-2.5">
              <p class="instrument-label">Devices</p>
              <p class="instrument-value !mt-0.5 !text-lg">{{ devices.length }}</p>
              <p class="mt-0.5 text-[0.65rem] text-[var(--muted)]">
                {{ onlineCount }} online · {{ offlineCount }} offline
              </p>
            </article>

            <article class="instrument-cell !p-2.5">
              <p class="instrument-label">Gallery images</p>
              <p class="instrument-value !mt-0.5 !text-lg">{{ galleryCount }}</p>
            </article>

            <article class="instrument-cell !p-2.5">
              <p class="instrument-label">Settings</p>
              <p class="instrument-value !mt-0.5 !text-lg">
                {{ settings ? `${settings.noiseAlertThresholdDb} dB` : '--' }}
              </p>
              <p class="mt-0.5 text-[0.65rem] text-[var(--muted)]">
                Alerts {{ settings?.notificationsEnabled ? 'on' : 'off' }} ·
                {{ settings?.temperatureUnit ?? '--' }}
              </p>
            </article>

            <article class="instrument-cell !p-2.5">
              <p class="instrument-label">WebRTC sessions</p>
              <p class="instrument-value !mt-0.5 !text-lg">{{ connections.length }}</p>
            </article>
          </section>

          <section class="yoda-card flex max-h-28 min-h-0 flex-col overflow-hidden p-3 xl:max-h-none">
            <div class="mb-2 shrink-0">
              <h2 class="text-sm font-semibold tracking-tight text-[var(--text)]">Recent WebRTC</h2>
              <p class="text-xs text-[var(--muted)]">Latest publisher / subscriber sessions.</p>
            </div>

            <div
              v-if="connections.length === 0"
              class="flex flex-1 items-center justify-center border border-dashed border-[var(--border)] px-3 py-2 text-center text-xs text-[var(--muted)]"
            >
              No WebRTC sessions recorded yet.
            </div>

            <ul v-else class="min-h-0 flex-1 divide-y divide-[var(--border)] overflow-y-auto">
              <li
                v-for="connection in connections"
                :key="connection.id"
                class="flex flex-wrap items-center justify-between gap-2 py-1.5 text-xs"
              >
                <div class="min-w-0">
                  <p class="truncate font-medium text-[var(--text)]">
                    {{ connection.deviceId }} · {{ connection.role }}
                  </p>
                  <p class="truncate font-mono text-[0.65rem] text-[var(--muted)]">
                    {{ connection.room }} · {{ connection.identity }}
                  </p>
                </div>
                <div class="shrink-0 text-right">
                  <span class="status-pill">{{ connection.status }}</span>
                  <p class="mt-0.5 font-mono text-[0.65rem] text-[var(--muted)]">
                    {{ formatTimestamp(connection.connectedAt) }}
                  </p>
                </div>
              </li>
            </ul>
          </section>
        </div>

        <section
          v-if="!hasDevices"
          class="yoda-card flex flex-1 items-center justify-center border-dashed px-5 py-8 text-center text-sm text-[var(--muted)]"
        >
          {{ EMPTY_FLEET_MESSAGE }}
        </section>

        <template v-else>
          <div class="flex shrink-0 flex-wrap items-center gap-2">
            <label class="min-w-48 flex-1">
              <span class="mb-1 block text-xs font-medium text-[var(--text)]">Device</span>
              <select v-model="selectedDeviceId" class="yoda-select py-2 text-sm">
                <option v-for="device in devices" :key="device.id" :value="device.id">
                  {{ device.name }}
                </option>
              </select>
            </label>

            <div v-if="selectedDevice" class="meta-chip !py-1.5 !text-xs">
              Name <strong>{{ selectedDevice.name }}</strong>
            </div>

            <div v-if="selectedDevice" class="meta-chip !py-1.5 !text-xs">
              Location <strong>{{ selectedDevice.location ?? selectedDevice.id }}</strong>
            </div>

            <span v-if="selectedDevice" :class="statusClass(selectedDevice.status)">
              {{ selectedDevice.status }}
            </span>
          </div>

          <div
            class="grid min-h-0 flex-1 grid-rows-[minmax(0,1fr)_minmax(0,1fr)] gap-3 md:grid-rows-none md:grid-cols-2"
          >
            <section class="yoda-card flex min-h-0 flex-col overflow-hidden p-3">
              <div class="mb-2 shrink-0">
                <h2 class="text-sm font-semibold tracking-tight text-[var(--text)]">Telemetry</h2>
                <p class="text-xs text-[var(--muted)]">Live readings from the selected device.</p>
              </div>

              <div
                v-if="loadingTelemetry"
                class="flex flex-1 items-center justify-center text-sm text-[var(--muted)]"
              >
                Loading telemetry…
              </div>

              <div v-else class="grid min-h-0 flex-1 grid-cols-2 content-start gap-2 overflow-y-auto">
                <article
                  v-for="card in telemetryCards"
                  :key="card.label"
                  class="instrument-cell !p-2"
                >
                  <p class="instrument-label">{{ card.label }}</p>
                  <p class="instrument-value !mt-0.5 !text-base">{{ card.value }}</p>
                </article>
              </div>

              <p
                v-if="telemetry"
                class="mt-2 shrink-0 font-mono text-[0.65rem] text-[var(--muted)]"
              >
                Last updated {{ formatTimestamp(telemetry.recordedAt) }}
              </p>
            </section>

            <div class="flex min-h-0 flex-col gap-2">
              <div class="shrink-0">
                <h2 class="text-sm font-semibold tracking-tight text-[var(--text)]">Camera</h2>
                <p class="text-xs text-[var(--muted)]">Live feed with manual stream controls.</p>
              </div>

              <CameraPanel
                class="min-h-0 flex-1"
                fill
                :device-id="selectedDeviceId"
                :device-name="selectedDevice?.name"
              />
            </div>
          </div>
        </template>
      </template>
    </div>
  </AppLayout>
</template>
