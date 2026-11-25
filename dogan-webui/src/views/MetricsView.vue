<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppLayout from '@/components/AppLayout.vue'
import ErrorAlert from '@/components/ErrorAlert.vue'
import { fetchDevices } from '@/services/api/devices'
import { fetchDeviceMetrics } from '@/services/api/metrics'
import { getErrorMessage } from '@/services/api/errors'
import { formatTehranDateTime } from '@/utils/tehranTime'
import type { Device, DeviceMetrics, MetricReading, MetricsHistoryRange } from '@/types/api'

const METRIC_DEFS = {
  battery: {
    label: 'Battery',
    max: 100,
    kind: 'number' as const,
    value: (m: DeviceMetrics) => m.current.batteryPercent,
    reading: (r: MetricReading) => r.batteryPercent,
    format: (n: number) => `${n.toFixed(0)}%`,
  },
  'battery-temperature': {
    label: 'Battery Temperature',
    max: 50,
    kind: 'number' as const,
    value: (m: DeviceMetrics) => m.current.batteryTemperatureCelsius,
    reading: (r: MetricReading) => r.batteryTemperatureCelsius,
    format: (n: number) => `${n.toFixed(1)}°C`,
  },
  noise: {
    label: 'Noise',
    max: 100,
    kind: 'number' as const,
    value: (m: DeviceMetrics) => m.current.noiseDb,
    reading: (r: MetricReading) => r.noiseDb,
    format: (n: number) => `${n.toFixed(1)} dB`,
  },
  jolt: {
    label: 'Jolt',
    max: 5,
    kind: 'number' as const,
    value: (m: DeviceMetrics) => m.current.jolt,
    reading: (r: MetricReading) => r.jolt,
    format: (n: number) => n.toFixed(2),
  },
  signal: {
    label: 'Signal',
    max: 0,
    kind: 'number' as const,
    value: (m: DeviceMetrics) => m.current.signalStrength,
    reading: (r: MetricReading) => r.signalStrength,
    format: (n: number) => `${n.toFixed(0)} dBm`,
  },
  'network-type': {
    label: 'Network Type',
    max: 0,
    kind: 'text' as const,
    value: (m: DeviceMetrics) => m.current.networkType,
    reading: (r: MetricReading) => r.networkType,
    format: (n: string | number) => String(n || '--'),
  },
  'server-phone-latency': {
    label: 'Server-Phone Latency',
    max: 1000,
    kind: 'number' as const,
    value: (m: DeviceMetrics) => m.current.serverPhoneLatencyMs,
    reading: (r: MetricReading) => r.serverPhoneLatencyMs,
    format: (n: number) => `${n.toFixed(0)} ms`,
  },
} as const

type MetricSlug = keyof typeof METRIC_DEFS

const RANGES: { id: MetricsHistoryRange; label: string }[] = [
  { id: 'today', label: 'Today' },
  { id: 'yesterday', label: 'Yesterday' },
  { id: 'this_week', label: 'This week' },
  { id: 'this_month', label: 'This month' },
  { id: 'last_week', label: 'Last week' },
  { id: 'last_month', label: 'Last month' },
]

const route = useRoute()
const router = useRouter()

const devices = ref<Device[]>([])
const selectedDeviceId = ref('')
const metrics = ref<DeviceMetrics | null>(null)
const loadingDevices = ref(true)
const loadingMetrics = ref(false)
const errorMessage = ref<string | null>(null)
const selectedRange = ref<MetricsHistoryRange>('today')

const metricSlug = computed<MetricSlug>(() => {
  const raw = typeof route.params.metric === 'string' ? route.params.metric : 'battery'
  return raw in METRIC_DEFS ? (raw as MetricSlug) : 'battery'
})

const metricDef = computed(() => METRIC_DEFS[metricSlug.value])

const routeDeviceId = computed(() => {
  if (typeof route.query.device === 'string' && route.query.device.length > 0) {
    return route.query.device
  }
  return undefined
})

const currentDisplay = computed(() => {
  if (!metrics.value) {
    return '--'
  }
  const value = metricDef.value.value(metrics.value)
  return metricDef.value.format(value as never)
})

const chartReadings = computed(() => {
  if (!metrics.value) {
    return [] as MetricReading[]
  }
  return [...metrics.value.history].reverse().slice(-48)
})

const numericMax = computed(() => {
  if (metricSlug.value === 'signal') {
    const values = chartReadings.value.map((r) => Math.abs(metricDef.value.reading(r) as number))
    return Math.max(50, ...values, 1)
  }
  return metricDef.value.max || 100
})

function barHeight(value: number): string {
  if (metricSlug.value === 'signal') {
    const ratio = Math.max(0, Math.min(Math.abs(value) / numericMax.value, 1))
    return `${Math.round(ratio * 100)}%`
  }
  const ratio = Math.max(0, Math.min(value / numericMax.value, 1))
  return `${Math.round(ratio * 100)}%`
}

function formatTimestamp(value: string): string {
  return formatTehranDateTime(value)
}

async function loadDevices(): Promise<void> {
  loadingDevices.value = true
  try {
    devices.value = await fetchDevices()
    const preferredId = routeDeviceId.value || selectedDeviceId.value || devices.value[0]?.id
    if (preferredId) {
      selectedDeviceId.value = preferredId
    }
  } catch (error) {
    errorMessage.value = getErrorMessage(error, 'Unable to load devices.')
  } finally {
    loadingDevices.value = false
  }
}

async function loadMetrics(): Promise<void> {
  if (!selectedDeviceId.value) {
    metrics.value = null
    return
  }

  loadingMetrics.value = true
  errorMessage.value = null

  try {
    metrics.value = await fetchDeviceMetrics(selectedDeviceId.value, selectedRange.value)
  } catch (error) {
    metrics.value = null
    errorMessage.value = getErrorMessage(error, 'Unable to load device metrics.')
  } finally {
    loadingMetrics.value = false
  }
}

watch(metricSlug, (slug) => {
  if (!(slug in METRIC_DEFS)) {
    void router.replace('/metrics/battery')
  }
})

watch(selectedDeviceId, () => {
  void loadMetrics()
})

watch(selectedRange, () => {
  void loadMetrics()
})

watch(routeDeviceId, (deviceId) => {
  if (deviceId) {
    selectedDeviceId.value = deviceId
  }
})

onMounted(async () => {
  if (!(metricSlug.value in METRIC_DEFS)) {
    await router.replace('/metrics/battery')
  }
  await loadDevices()
  await loadMetrics()
})
</script>

<template>
  <AppLayout>
    <div class="space-y-6">
      <div>
        <h1 class="text-2xl font-semibold tracking-tight text-[var(--text)]">
          {{ metricDef.label }}
        </h1>
        <p class="mt-1 text-sm text-[var(--muted)]">History for the selected device and range.</p>
      </div>

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
      </div>

      <div class="flex flex-wrap gap-2" role="tablist" aria-label="History range">
        <button
          v-for="range in RANGES"
          :key="range.id"
          type="button"
          class="rounded-[6px] border px-3 py-1.5 text-sm transition"
          :class="
            selectedRange === range.id
              ? 'border-[var(--accent)] bg-[var(--accent)] text-white'
              : 'border-[var(--border)] text-[var(--muted)] hover:bg-[var(--surface)] hover:text-[var(--text)]'
          "
          :aria-selected="selectedRange === range.id"
          @click="selectedRange = range.id"
        >
          {{ range.label }}
        </button>
      </div>

      <ErrorAlert v-if="errorMessage" title="Metrics unavailable" :message="errorMessage" />

      <article class="yoda-card p-6">
        <p class="instrument-label">Current {{ metricDef.label.toLowerCase() }}</p>
        <p class="mt-2 font-mono text-4xl font-semibold tracking-tight text-[var(--text)]">
          {{ currentDisplay }}
        </p>
        <p v-if="metrics" class="mt-2 font-mono text-xs text-[var(--muted)]">
          Updated {{ formatTimestamp(metrics.current.recordedAt) }}
        </p>
      </article>

      <section class="yoda-card p-6">
        <div class="mb-4">
          <h2 class="text-lg font-semibold text-[var(--text)]">History</h2>
          <p class="text-sm text-[var(--muted)]">
            {{ RANGES.find((r) => r.id === selectedRange)?.label }} readings.
          </p>
        </div>

        <div v-if="loadingMetrics" class="py-8 text-center text-sm text-[var(--muted)]">
          Loading metrics…
        </div>

        <div
          v-else-if="!metrics || metrics.history.length === 0"
          class="py-8 text-center text-sm text-[var(--muted)]"
        >
          No historical readings for this range.
        </div>

        <div v-else-if="metricDef.kind === 'text'" class="space-y-2">
          <div
            v-for="reading in chartReadings"
            :key="reading.timestamp"
            class="flex items-center justify-between border-b border-[var(--border)] py-2 text-sm last:border-0"
          >
            <span class="font-mono text-[var(--muted)]">{{ formatTimestamp(reading.timestamp) }}</span>
            <span class="font-medium text-[var(--text)]">{{
              metricDef.format(metricDef.reading(reading) as never)
            }}</span>
          </div>
        </div>

        <div v-else class="flex h-40 items-end gap-1 overflow-x-auto">
          <div
            v-for="reading in chartReadings"
            :key="reading.timestamp"
            class="flex min-w-[18px] flex-1 flex-col items-center gap-2"
          >
            <div class="flex h-32 w-full items-end rounded-[6px] bg-[var(--surface)]">
              <div
                class="w-full rounded-[6px] bg-[var(--accent)]"
                :style="{ height: barHeight(metricDef.reading(reading) as number) }"
                :title="metricDef.format(metricDef.reading(reading) as never)"
              />
            </div>
            <span class="truncate text-[9px] text-[var(--muted)]">{{
              formatTimestamp(reading.timestamp)
            }}</span>
          </div>
        </div>
      </section>
    </div>
  </AppLayout>
</template>
