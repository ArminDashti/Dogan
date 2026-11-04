<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import AppLayout from '@/components/AppLayout.vue'
import CameraPanel from '@/components/CameraPanel.vue'
import ErrorAlert from '@/components/ErrorAlert.vue'
import { fetchDevices } from '@/services/api/devices'
import { fetchDeviceTelemetry } from '@/services/api/telemetry'
import { getErrorMessage } from '@/services/api/errors'
import type { Device, DeviceTelemetry } from '@/types/api'

const TELEMETRY_POLL_MS = 5000

const devices = ref<Device[]>([])
const selectedDeviceId = ref('')
const telemetry = ref<DeviceTelemetry | null>(null)
const loadingDevices = ref(true)
const loadingTelemetry = ref(false)
const errorMessage = ref<string | null>(null)

let telemetryPollTimer: ReturnType<typeof setInterval> | null = null

const selectedDevice = computed(() =>
  devices.value.find((device) => device.id === selectedDeviceId.value),
)

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

function stopTelemetryPolling(): void {
  if (telemetryPollTimer) {
    clearInterval(telemetryPollTimer)
    telemetryPollTimer = null
  }
}

function startTelemetryPolling(): void {
  stopTelemetryPolling()
  telemetryPollTimer = setInterval(() => {
    void loadTelemetry({ silent: true })
  }, TELEMETRY_POLL_MS)
}

async function loadDevices(): Promise<void> {
  loadingDevices.value = true
  errorMessage.value = null

  try {
    devices.value = await fetchDevices()
    if (!selectedDeviceId.value && devices.value.length > 0) {
      selectedDeviceId.value = devices.value[0].id
    }
  } catch (error) {
    errorMessage.value = getErrorMessage(error, 'Unable to load devices.')
  } finally {
    loadingDevices.value = false
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

watch(selectedDeviceId, async () => {
  await loadTelemetry()
  startTelemetryPolling()
})

onMounted(async () => {
  await loadDevices()
  await loadTelemetry()
  startTelemetryPolling()
})

onUnmounted(() => {
  stopTelemetryPolling()
})
</script>

<template>
  <AppLayout
    title="Dashboard"
    subtitle="Live device telemetry and camera controls."
    :scrollable="false"
  >
    <div class="space-y-6">
      <div class="flex flex-wrap items-end gap-4">
        <label class="min-w-64 flex-1">
          <span class="mb-1.5 block text-sm font-medium text-[var(--text)]">Device</span>
          <select
            v-model="selectedDeviceId"
            class="yoda-select"
            :disabled="loadingDevices || devices.length === 0"
          >
            <option v-if="devices.length === 0" value="">No devices available</option>
            <option v-for="device in devices" :key="device.id" :value="device.id">
              {{ device.name }}
            </option>
          </select>
        </label>

        <div
          v-if="selectedDevice"
          class="yoda-card px-4 py-2.5 text-sm text-[var(--muted)]"
        >
          <span class="text-[var(--muted)]">Name:</span>
          <span class="ml-1 font-medium text-[var(--text)]">{{ selectedDevice.name }}</span>
        </div>

        <div
          v-if="selectedDevice"
          class="yoda-card px-4 py-2.5 text-sm text-[var(--muted)]"
        >
          <span class="text-[var(--muted)]">Location:</span>
          <span class="ml-1 font-medium text-[var(--text)]">{{ selectedDevice.location ?? selectedDevice.id }}</span>
        </div>

        <span v-if="selectedDevice" :class="statusClass(selectedDevice.status)">
          {{ selectedDevice.status }}
        </span>
      </div>

      <ErrorAlert v-if="errorMessage" title="Dashboard unavailable" :message="errorMessage" />

      <div class="grid gap-6 lg:grid-cols-2">
        <section class="yoda-card p-6">
          <div class="mb-4">
            <h2 class="text-lg font-semibold text-[var(--text)]">Telemetry</h2>
            <p class="text-sm text-[var(--muted)]">
              Live readings from
              <code class="text-[var(--text)]">GET /devices/:id/telemetry</code>
            </p>
          </div>

          <div v-if="loadingTelemetry" class="py-8 text-center text-sm text-[var(--muted)]">
            Loading telemetry…
          </div>

          <div v-else class="grid gap-4 sm:grid-cols-2">
            <article
              v-for="card in telemetryCards"
              :key="card.label"
              class="rounded-lg border border-[var(--border)] bg-[var(--bg)] px-4 py-3"
            >
              <p class="text-sm text-[var(--muted)]">{{ card.label }}</p>
              <p class="mt-1 text-2xl font-semibold text-[var(--text)]">{{ card.value }}</p>
            </article>
          </div>

          <p v-if="telemetry" class="mt-4 text-xs text-[var(--muted)]">
            Last updated {{ formatTimestamp(telemetry.recordedAt) }}
          </p>
        </section>

        <div>
          <div class="mb-4">
            <h2 class="text-lg font-semibold text-[var(--text)]">Camera</h2>
            <p class="text-sm text-[var(--muted)]">Live feed with manual stream controls.</p>
          </div>

          <CameraPanel
            :device-id="selectedDeviceId"
            :device-name="selectedDevice?.name"
          />
        </div>
      </div>
    </div>
  </AppLayout>
</template>
