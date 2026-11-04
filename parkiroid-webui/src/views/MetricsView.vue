<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AppLayout from '@/components/AppLayout.vue'
import ErrorAlert from '@/components/ErrorAlert.vue'
import { fetchDevices } from '@/services/api/devices'
import { fetchDeviceMetrics } from '@/services/api/metrics'
import { getErrorMessage } from '@/services/api/errors'
import type { Device, DeviceMetrics, MetricReading } from '@/types/api'

const route = useRoute()

const devices = ref<Device[]>([])
const selectedDeviceId = ref('')
const metrics = ref<DeviceMetrics | null>(null)
const loadingDevices = ref(true)
const loadingMetrics = ref(false)
const errorMessage = ref<string | null>(null)

const routeDeviceId = computed(() =>
  typeof route.params.id === 'string' ? route.params.id : undefined,
)

const temperatureDisplay = computed(() => {
  if (!metrics.value) {
    return '--'
  }

  return `${metrics.value.current.temperatureCelsius.toFixed(1)}°C`
})

const noiseDisplay = computed(() => {
  if (!metrics.value) {
    return '--'
  }

  return `${metrics.value.current.noiseDb.toFixed(1)} dB`
})

function barHeight(value: number, max: number): string {
  const ratio = Math.max(0, Math.min(value / max, 1))
  return `${Math.round(ratio * 100)}%`
}

function formatTimestamp(value: string): string {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}

function recentHistory(readings: MetricReading[]): MetricReading[] {
  return readings.slice(-12)
}

async function loadDevices(): Promise<void> {
  loadingDevices.value = true

  try {
    devices.value = await fetchDevices()
    const preferredId = routeDeviceId.value ?? selectedDeviceId.value ?? devices.value[0]?.id
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
    metrics.value = await fetchDeviceMetrics(selectedDeviceId.value)
  } catch (error) {
    metrics.value = null
    errorMessage.value = getErrorMessage(error, 'Unable to load device metrics.')
  } finally {
    loadingMetrics.value = false
  }
}

watch(selectedDeviceId, () => {
  void loadMetrics()
})

watch(routeDeviceId, (deviceId) => {
  if (deviceId) {
    selectedDeviceId.value = deviceId
  }
})

onMounted(async () => {
  await loadDevices()
  await loadMetrics()
})
</script>

<template>
  <AppLayout title="Device Metrics" subtitle="Temperature and noise monitoring.">
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
      </div>

      <ErrorAlert v-if="errorMessage" title="Metrics unavailable" :message="errorMessage" />

      <div class="grid gap-4 md:grid-cols-2">
        <article class="yoda-card p-6">
          <p class="text-sm text-[var(--muted)]">Current temperature</p>
          <p class="mt-2 text-4xl font-semibold text-[var(--text)]">{{ temperatureDisplay }}</p>
          <p v-if="metrics" class="mt-2 text-xs text-[var(--muted)]">
            Updated {{ formatTimestamp(metrics.current.recordedAt) }}
          </p>
        </article>

        <article class="yoda-card p-6">
          <p class="text-sm text-[var(--muted)]">Current noise level</p>
          <p class="mt-2 text-4xl font-semibold text-[var(--text)]">{{ noiseDisplay }}</p>
          <p v-if="metrics" class="mt-2 text-xs text-[var(--muted)]">
            Updated {{ formatTimestamp(metrics.current.recordedAt) }}
          </p>
        </article>
      </div>

      <section class="yoda-card p-6">
        <div class="mb-4">
          <h2 class="text-lg font-semibold text-[var(--text)]">Recent history</h2>
          <p class="text-sm text-[var(--muted)]">Lightweight bar view of the latest readings.</p>
        </div>

        <div v-if="loadingMetrics" class="py-8 text-center text-sm text-[var(--muted)]">
          Loading metrics…
        </div>

        <div v-else-if="!metrics || metrics.history.length === 0" class="py-8 text-center text-sm text-[var(--muted)]">
          No historical readings yet.
        </div>

        <div v-else class="grid gap-6 lg:grid-cols-2">
          <div>
            <p class="mb-3 text-sm font-medium text-[var(--text)]">Temperature (°C)</p>
            <div class="flex h-40 items-end gap-2">
              <div
                v-for="reading in recentHistory(metrics.history)"
                :key="`${reading.timestamp}-temp`"
                class="flex flex-1 flex-col items-center gap-2"
              >
                <div class="flex h-32 w-full items-end rounded-md bg-[var(--bg)]">
                  <div
                    class="w-full rounded-md bg-[var(--accent)]/80"
                    :style="{ height: barHeight(reading.temperatureCelsius, 40) }"
                  />
                </div>
                <span class="text-[10px] text-[var(--muted)]">{{ formatTimestamp(reading.timestamp) }}</span>
              </div>
            </div>
          </div>

          <div>
            <p class="mb-3 text-sm font-medium text-[var(--text)]">Noise (dB)</p>
            <div class="flex h-40 items-end gap-2">
              <div
                v-for="reading in recentHistory(metrics.history)"
                :key="`${reading.timestamp}-noise`"
                class="flex flex-1 flex-col items-center gap-2"
              >
                <div class="flex h-32 w-full items-end rounded-md bg-[var(--bg)]">
                  <div
                    class="w-full rounded-md bg-[#64748b]/80"
                    :style="{ height: barHeight(reading.noiseDb, 100) }"
                  />
                </div>
                <span class="text-[10px] text-[var(--muted)]">{{ formatTimestamp(reading.timestamp) }}</span>
              </div>
            </div>
          </div>
        </div>
      </section>
    </div>
  </AppLayout>
</template>
