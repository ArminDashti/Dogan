<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import AppLayout from '@/components/AppLayout.vue'
import ErrorAlert from '@/components/ErrorAlert.vue'
import MetricChart from '@/components/MetricChart.vue'
import { useSelectedDevice } from '@/composables/useSelectedDevice'
import { fetchDeviceMetrics } from '@/services/api/metrics'
import { getErrorMessage } from '@/services/api/errors'
import {
  formatTehranDayLabel,
  formatTehranDateTime,
  tehranDateKey,
  tehranHourOfDay,
  tehranTodayKey,
  tehranYesterdayKey,
} from '@/utils/tehranTime'
import type { DeviceMetrics, MetricReading } from '@/types/api'
import type { ChartPoint } from '@/types/api'

const REFRESH_MS = 60_000
const BUCKET_SECONDS = 900

interface MetricDefinition {
  key: string
  title: string
  unit?: string
  min?: number
  max?: number
  colorByNetwork?: boolean
  emptyMessage?: string
  value: (reading: MetricReading) => number
  valid: (value: number) => boolean
  format?: (value: number) => string
}

const METRICS: MetricDefinition[] = [
  {
    key: 'battery',
    title: 'Battery',
    unit: 'Charge level',
    min: 0,
    max: 100,
    value: (reading) => reading.batteryPercent,
    valid: (value) => value > 0,
    format: (value) => `${value.toFixed(0)}%`,
  },
  {
    key: 'battery-temperature',
    title: 'Battery temperature',
    unit: '°C',
    value: (reading) => reading.batteryTemperatureCelsius,
    valid: (value) => value > 0,
    format: (value) => `${value.toFixed(1)}°`,
  },
  {
    key: 'noise',
    title: 'Noise',
    unit: 'Cabin level, dB',
    value: (reading) => reading.noiseDb,
    valid: (value) => Number.isFinite(value) && value !== 0,
    format: (value) => `${value.toFixed(0)} dB`,
  },
  {
    key: 'jolt',
    title: 'Jolt',
    unit: 'm/s²',
    value: (reading) => reading.jolt,
    valid: (value) => Number.isFinite(value),
    format: (value) => value.toFixed(2),
  },
  {
    key: 'signal-1',
    title: 'Signal SIM 1',
    unit: 'dBm',
    min: -120,
    max: -40,
    colorByNetwork: true,
    value: (reading) => reading.sim1SignalDbm || reading.signalStrength,
    valid: (value) => value < 0,
    format: (value) => `${value.toFixed(0)}`,
  },
  {
    key: 'signal-2',
    title: 'Signal SIM 2',
    unit: 'dBm',
    min: -120,
    max: -40,
    colorByNetwork: true,
    emptyMessage: 'Not reported yet — the phone sends one SIM today.',
    value: (reading) => reading.sim2SignalDbm,
    valid: (value) => value < 0,
    format: (value) => `${value.toFixed(0)}`,
  },
  {
    key: 'latency',
    title: 'Server–phone latency',
    unit: 'ms',
    value: (reading) => reading.serverPhoneLatencyMs,
    valid: (value) => value > 0,
    format: (value) => `${value.toFixed(0)} ms`,
  },
]

const { deviceId, deviceName, loading: loadingDevices, load: loadDevice } = useSelectedDevice()

const metrics = ref<DeviceMetrics | null>(null)
const loading = ref(false)
const errorMessage = ref<string | null>(null)
const lastLoadedAt = ref<Date | null>(null)

let refreshTimer: ReturnType<typeof setInterval> | null = null

const todayKey = computed(() => tehranTodayKey())
const yesterdayKey = computed(() => tehranYesterdayKey())
const todayLabel = computed(() => `Today · ${formatTehranDayLabel(todayKey.value)}`)
const yesterdayLabel = computed(() => `Yesterday · ${formatTehranDayLabel(yesterdayKey.value)}`)

function splitPoints(definition: MetricDefinition): { today: ChartPoint[]; yesterday: ChartPoint[] } {
  const reading = metrics.value
  if (!reading) {
    return { today: [], yesterday: [] }
  }

  const today: ChartPoint[] = []
  const yesterday: ChartPoint[] = []
  for (const item of reading.history) {
    const value = definition.value(item)
    if (!definition.valid(value)) {
      continue
    }
    const point: ChartPoint = {
      hour: tehranHourOfDay(item.timestamp),
      value,
      networkType: item.networkType,
    }
    const key = tehranDateKey(item.timestamp)
    if (key === todayKey.value) {
      today.push(point)
    } else if (key === yesterdayKey.value) {
      yesterday.push(point)
    }
  }

  today.sort((a, b) => a.hour - b.hour)
  yesterday.sort((a, b) => a.hour - b.hour)
  return { today, yesterday }
}

const chartData = computed(() =>
  METRICS.map((definition) => ({
    definition,
    ...splitPoints(definition),
  })),
)

const hasAnyData = computed(() => chartData.value.some((chart) => chart.today.length > 0))

async function loadMetrics(): Promise<void> {
  if (!deviceId.value) {
    metrics.value = null
    return
  }

  loading.value = true
  errorMessage.value = null
  try {
    metrics.value = await fetchDeviceMetrics(deviceId.value, 'today_yesterday', BUCKET_SECONDS)
    lastLoadedAt.value = new Date()
  } catch (error) {
    metrics.value = null
    errorMessage.value = getErrorMessage(error, 'Unable to load device metrics.')
  } finally {
    loading.value = false
  }
}

watch(deviceId, () => {
  void loadMetrics()
})

onMounted(async () => {
  await loadDevice()
  await loadMetrics()
  refreshTimer = setInterval(() => {
    void loadMetrics()
  }, REFRESH_MS)
})

onUnmounted(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
})
</script>

<template>
  <AppLayout title="Metrics" subtitle="Battery, motion, cabin and network readings for today and yesterday.">
    <div class="space-y-6">
      <div class="flex flex-wrap items-end justify-between gap-3">
        <div class="min-w-0">
          <p class="page-kicker">Selected device</p>
          <p class="truncate text-lg font-semibold text-[var(--text)]">
            {{ loadingDevices ? 'Loading…' : deviceName || 'No device selected' }}
          </p>
          <p v-if="lastLoadedAt" class="mt-0.5 font-mono text-xs text-[var(--muted)]">
            Updated {{ formatTehranDateTime(lastLoadedAt) }}
          </p>
        </div>
        <div class="flex items-center gap-2">
          <button
            type="button"
            class="yoda-btn yoda-btn--outline cursor-pointer"
            :disabled="loading || !deviceId"
            @click="loadMetrics"
          >
            {{ loading ? 'Refreshing…' : 'Refresh' }}
          </button>
        </div>
      </div>

      <ErrorAlert v-if="errorMessage" title="Metrics unavailable" :message="errorMessage" />

      <div
        v-if="!deviceId && !loadingDevices"
        class="yoda-card border-dashed px-5 py-12 text-center text-[var(--muted)]"
      >
        Select a device in Settings to see its metrics.
      </div>

      <div v-else-if="loading && !metrics" class="yoda-card px-5 py-12 text-center text-[var(--muted)]">
        Loading metrics…
      </div>

      <div
        v-else-if="metrics && !hasAnyData"
        class="yoda-card border-dashed px-5 py-12 text-center text-[var(--muted)]"
      >
        No readings for today or yesterday yet.
      </div>

      <div v-else class="metric-grid">
        <MetricChart
          v-for="chart in chartData"
          :key="chart.definition.key"
          :title="chart.definition.title"
          :unit="chart.definition.unit"
          :today="chart.today"
          :yesterday="chart.yesterday"
          :today-label="todayLabel"
          :yesterday-label="yesterdayLabel"
          :min="chart.definition.min"
          :max="chart.definition.max"
          :color-by-network="chart.definition.colorByNetwork"
          :empty-message="chart.definition.emptyMessage"
          :format-value="chart.definition.format"
        />
      </div>
    </div>
  </AppLayout>
</template>

<style scoped>
.metric-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 0.75rem;
}
</style>
