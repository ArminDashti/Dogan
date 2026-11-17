<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import AppLayout from '@/components/AppLayout.vue'
import ErrorAlert from '@/components/ErrorAlert.vue'
import { fetchDevices } from '@/services/api/devices'
import { fetchSettings } from '@/services/api/settings'
import { getErrorMessage } from '@/services/api/errors'
import type { Device } from '@/types/api'

const POLL_MS = 5000
const EMPTY_MESSAGE =
  'No device registered yet. Open Dogan on an Android handset and sign in — it will appear here.'

const devices = ref<Device[]>([])
const selectedDeviceId = ref('')
const loading = ref(true)
const errorMessage = ref<string | null>(null)

let pollTimer: ReturnType<typeof setInterval> | null = null

const selectedDevice = computed(() =>
  devices.value.find((d) => d.id === selectedDeviceId.value) ?? null,
)

const infoRows = computed(() => {
  const d = selectedDevice.value
  if (!d) {
    return []
  }
  return [
    { label: 'Device ID', value: d.id, mono: true },
    { label: 'Operating system', value: d.os ?? 'Android' },
    { label: 'Mode', value: formatMode(d.mode) },
    { label: 'Location', value: d.location || '—' },
    { label: 'Battery', value: formatBattery(d.batteryPercent) },
    { label: 'Temperature', value: formatTemperature(d.temperatureCelsius) },
    { label: 'Latency', value: formatLatency(d.latencyMs) },
    { label: 'Last update', value: formatLastUpdate(d.lastUpdate) },
  ]
})

function formatBattery(value: number | undefined): string {
  if (value === undefined || Number.isNaN(value)) {
    return '—'
  }
  return `${Math.round(value)}%`
}

function formatTemperature(value: number | undefined): string {
  if (value === undefined || Number.isNaN(value)) {
    return '—'
  }
  return `${value.toFixed(1)}°C`
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
  return date.toLocaleString()
}

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

watch(selectedDeviceId, (id, prev) => {
  if (id && id !== prev && devices.value.length > 0) {
    const exists = devices.value.some((d) => d.id === id)
    if (!exists) {
      selectedDeviceId.value = pickDeviceId(devices.value)
    }
  }
})

onMounted(async () => {
  await loadDevices()
  startPolling()
})

onUnmounted(() => {
  stopPolling()
})
</script>

<template>
  <AppLayout title="Device" subtitle="Details for the selected handset." :scrollable="true">
    <div class="device-page">
      <ErrorAlert v-if="errorMessage" title="Device unavailable" :message="errorMessage" />

      <div v-if="loading" class="device-loading yoda-card">
        <span class="stream-live-dot" aria-hidden="true" />
        Loading device…
      </div>

      <template v-else>
        <div v-if="devices.length === 0" class="device-empty yoda-card">
          <p>{{ EMPTY_MESSAGE }}</p>
        </div>

        <template v-else>
          <div v-if="devices.length > 1" class="device-picker yoda-card">
            <label class="device-picker__label" for="device-select">Handset</label>
            <select id="device-select" v-model="selectedDeviceId" class="device-picker__select">
              <option v-for="d in devices" :key="d.id" :value="d.id">
                {{ d.name }} ({{ d.status }})
              </option>
            </select>
          </div>

          <section v-if="selectedDevice" class="device-info yoda-card" aria-labelledby="device-info-title">
            <header class="device-info__head">
              <h2 id="device-info-title" class="device-info__title">{{ selectedDevice.name }}</h2>
              <span :class="statusClass(selectedDevice.status)">{{ selectedDevice.status }}</span>
            </header>

            <dl class="device-info__grid">
              <template v-for="row in infoRows" :key="row.label">
                <dt>{{ row.label }}</dt>
                <dd :class="{ 'device-info__mono': row.mono }">{{ row.value }}</dd>
              </template>
            </dl>
          </section>
        </template>
      </template>
    </div>
  </AppLayout>
</template>

<style scoped>
.device-page {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding-bottom: 1rem;
  max-width: 36rem;
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

.device-picker {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  padding: 0.85rem 1rem;
}

.device-picker__label {
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--muted);
}

.device-picker__select {
  padding: 0.5rem 0.65rem;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border);
  background: var(--surface);
  color: var(--text);
  font-size: 0.875rem;
}

.device-info {
  padding: 1rem 1.1rem;
}

.device-info__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.65rem;
  margin-bottom: 1rem;
  padding-bottom: 0.85rem;
  border-bottom: 1px solid var(--border);
}

.device-info__title {
  margin: 0;
  font-size: 1.125rem;
  font-weight: 650;
  color: var(--text);
}

.device-info__grid {
  display: grid;
  grid-template-columns: minmax(7rem, 0.45fr) 1fr;
  gap: 0.55rem 1rem;
  margin: 0;
}

.device-info__grid dt {
  margin: 0;
  font-size: 0.8125rem;
  color: var(--muted);
}

.device-info__grid dd {
  margin: 0;
  font-size: 0.875rem;
  color: var(--text);
}

.device-info__mono {
  font-family: var(--font-mono);
  font-size: 0.8125rem;
  word-break: break-all;
}
</style>