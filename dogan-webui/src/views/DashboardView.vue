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
  'No phones on the beat yet. Open Dogan on an Android device and sign in — it will show up here automatically.'

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

const fleetOnBeatPercent = computed(() => {
  if (devices.value.length === 0) {
    return 0
  }
  return Math.round((onlineCount.value / devices.value.length) * 100)
})

const fleetRingDash = computed(() => {
  const circumference = 2 * Math.PI * 42
  const filled = (fleetOnBeatPercent.value / 100) * circumference
  return `${filled} ${circumference}`
})

const apiHealthy = computed(() => health.value?.status?.toLowerCase() === 'ok')

const activeStreamCount = computed(
  () => connections.value.filter((c) => c.status?.toLowerCase() === 'active').length,
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

function connectionTone(status: string | undefined): string {
  const normalized = status?.toLowerCase() ?? ''
  if (normalized === 'active') {
    return 'comms-row--live'
  }
  if (normalized === 'closed' || normalized === 'ended') {
    return 'comms-row--quiet'
  }
  return 'comms-row--idle'
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

function metricFill(value: number | undefined, max = 100): number {
  if (value === undefined || Number.isNaN(value)) {
    return 0
  }
  return Math.min(100, Math.max(0, (value / max) * 100))
}

const telemetryCards = computed(() => {
  const current = telemetry.value
  return [
    {
      label: 'Battery',
      value: formatPercent(current?.batteryPercent),
      fill: metricFill(current?.batteryPercent),
      kind: 'bar' as const,
    },
    {
      label: 'Battery temperature',
      value: formatValue(current?.batteryTemperatureCelsius, '°C'),
      fill: metricFill(current?.batteryTemperatureCelsius, 50),
      kind: 'bar' as const,
    },
    {
      label: 'Noise',
      value: formatValue(current?.noiseDb, ' dB'),
      fill: metricFill(current?.noiseDb, settings.value?.noiseAlertThresholdDb ?? 90),
      kind: 'bar' as const,
    },
    { label: 'Jolt', value: formatValue(current?.jolt, ''), kind: 'plain' as const },
    {
      label: 'Signal',
      value: formatValue(current?.signalStrength, ''),
      fill: metricFill(current?.signalStrength, 100),
      kind: 'bar' as const,
    },
    { label: 'Network', value: current?.networkType ?? '--', kind: 'plain' as const },
    {
      label: 'Phone latency',
      value: formatLatency(current?.serverPhoneLatencyMs),
      kind: 'plain' as const,
    },
    {
      label: 'Web latency',
      value: formatLatency(current?.serverWebLatencyMs),
      kind: 'plain' as const,
    },
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
    title="Beat board"
    subtitle="Fleet posture, live streams, and field readings in one place."
    :scrollable="false"
  >
    <div class="beat-board">
      <ErrorAlert v-if="errorMessage" title="Dashboard unavailable" :message="errorMessage" />

      <div v-if="loadingOps" class="beat-loading yoda-card">
        <span class="stream-live-dot" aria-hidden="true" />
        Pulling live ops data…
      </div>

      <template v-else>
        <header class="beat-hero yoda-card">
          <div class="beat-hero__fleet">
            <div class="fleet-ring" aria-hidden="true">
              <svg viewBox="0 0 96 96" role="presentation">
                <circle class="fleet-ring__track" cx="48" cy="48" r="42" />
                <circle
                  class="fleet-ring__fill"
                  cx="48"
                  cy="48"
                  r="42"
                  :style="{ strokeDasharray: fleetRingDash }"
                />
              </svg>
              <div class="fleet-ring__label">
                <span class="fleet-ring__value">{{ fleetOnBeatPercent }}%</span>
                <span class="fleet-ring__caption">on beat</span>
              </div>
            </div>
            <div class="beat-hero__copy">
              <p class="beat-hero__title">
                {{ onlineCount }} of {{ devices.length }} units reporting
              </p>
              <p class="beat-hero__detail">
                <span v-if="offlineCount > 0">{{ offlineCount }} offline</span>
                <span v-else-if="devices.length > 0">All assigned units are reachable</span>
                <span v-else>Waiting for the first handset</span>
              </p>
            </div>
          </div>

          <div class="beat-hero__signals">
            <div class="signal-card" :class="apiHealthy ? 'signal-card--good' : 'signal-card--warn'">
              <span class="signal-card__dot" aria-hidden="true" />
              <div>
                <p class="signal-card__name">API</p>
                <p class="signal-card__value">{{ health?.status ?? 'unknown' }}</p>
                <p class="signal-card__meta">{{ formatTimestamp(health?.timestamp) }}</p>
              </div>
            </div>
            <div
              class="signal-card"
              :class="health?.livekitConfigured ? 'signal-card--good' : 'signal-card--warn'"
            >
              <span class="signal-card__dot" aria-hidden="true" />
              <div>
                <p class="signal-card__name">LiveKit</p>
                <p class="signal-card__value">
                  {{ health?.livekitConfigured ? 'Ready for streams' : 'Not configured' }}
                </p>
              </div>
            </div>
          </div>
        </header>

        <div class="ops-strip">
          <article class="ops-strip__item">
            <p class="ops-strip__label">Captured frames</p>
            <p class="ops-strip__value">{{ galleryCount }}</p>
          </article>
          <article class="ops-strip__item">
            <p class="ops-strip__label">Noise threshold</p>
            <p class="ops-strip__value">
              {{ settings ? `${settings.noiseAlertThresholdDb} dB` : '--' }}
            </p>
            <p class="ops-strip__meta">
              Alerts {{ settings?.notificationsEnabled ? 'on' : 'off' }} ·
              {{ settings?.temperatureUnit ?? '--' }}
            </p>
          </article>
          <article class="ops-strip__item">
            <p class="ops-strip__label">Open streams</p>
            <p class="ops-strip__value">{{ activeStreamCount }}</p>
            <p class="ops-strip__meta">{{ connections.length }} sessions logged</p>
          </article>
        </div>

        <div class="beat-grid">
          <section class="comms-panel yoda-card">
            <header class="panel-head">
              <h2 class="panel-head__title">Stream log</h2>
              <p class="panel-head__hint">Recent publisher and viewer sessions</p>
            </header>

            <div v-if="connections.length === 0" class="comms-empty">
              No WebRTC sessions yet. Start a stream from a handset or the Stream page.
            </div>

            <ol v-else class="comms-list">
              <li
                v-for="connection in connections"
                :key="connection.id"
                class="comms-row"
                :class="connectionTone(connection.status)"
              >
                <div class="comms-row__main">
                  <p class="comms-row__device">{{ connection.deviceId }} · {{ connection.role }}</p>
                  <p class="comms-row__room">{{ connection.room }}</p>
                  <p class="comms-row__id">{{ connection.identity }}</p>
                </div>
                <div class="comms-row__aside">
                  <span class="status-pill">{{ connection.status }}</span>
                  <time class="comms-row__time">{{ formatTimestamp(connection.connectedAt) }}</time>
                </div>
              </li>
            </ol>
          </section>

          <section v-if="!hasDevices" class="fleet-empty yoda-card">
            <p>{{ EMPTY_FLEET_MESSAGE }}</p>
          </section>

          <div v-else class="fleet-workspace">
            <fieldset class="unit-picker">
              <legend class="unit-picker__legend">Choose a unit</legend>
              <div class="unit-picker__list">
                <label
                  v-for="device in devices"
                  :key="device.id"
                  class="unit-card"
                  :class="{ 'unit-card--active': device.id === selectedDeviceId }"
                >
                  <input
                    v-model="selectedDeviceId"
                    class="unit-card__input"
                    type="radio"
                    name="fleet-unit"
                    :value="device.id"
                  />
                  <span class="unit-card__name">{{ device.name }}</span>
                  <span :class="statusClass(device.status)">{{ device.status }}</span>
                </label>
              </div>
            </fieldset>

            <div v-if="selectedDevice" class="unit-meta">
              <p>
                Location
                <strong>{{ selectedDevice.location ?? selectedDevice.id }}</strong>
              </p>
            </div>

            <div class="fleet-panels">
              <section class="telemetry-panel yoda-card">
                <header class="panel-head">
                  <h2 class="panel-head__title">Field readings</h2>
                  <p class="panel-head__hint">Live telemetry from the selected unit</p>
                </header>

                <div v-if="loadingTelemetry" class="telemetry-loading">Loading telemetry…</div>

                <div v-else class="telemetry-grid">
                  <article
                    v-for="card in telemetryCards"
                    :key="card.label"
                    class="telemetry-cell"
                  >
                    <p class="telemetry-cell__label">{{ card.label }}</p>
                    <p class="telemetry-cell__value">{{ card.value }}</p>
                    <div
                      v-if="card.kind === 'bar'"
                      class="telemetry-cell__bar"
                      role="presentation"
                    >
                      <span :style="{ width: `${card.fill}%` }" />
                    </div>
                  </article>
                </div>

                <p v-if="telemetry" class="telemetry-updated">
                  Updated {{ formatTimestamp(telemetry.recordedAt) }}
                </p>
              </section>

              <section class="camera-panel-wrap">
                <header class="panel-head panel-head--compact">
                  <h2 class="panel-head__title">Camera</h2>
                  <p class="panel-head__hint">Manual stream controls for this unit</p>
                </header>
                <CameraPanel
                  class="camera-panel"
                  fill
                  :device-id="selectedDeviceId"
                  :device-name="selectedDevice?.name"
                />
              </section>
            </div>
          </div>
        </div>
      </template>
    </div>
  </AppLayout>
</template>

<style scoped>
.beat-board {
  display: flex;
  height: 100%;
  min-height: 0;
  flex-direction: column;
  gap: 0.75rem;
}

.beat-loading {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  gap: 0.65rem;
  padding: 2rem;
  color: var(--muted);
  font-size: 0.9375rem;
}

.beat-hero {
  display: grid;
  gap: 1.25rem;
  padding: 1.1rem 1.25rem;
  background: linear-gradient(
    135deg,
    color-mix(in srgb, var(--surface-raised) 92%, var(--signal) 8%),
    var(--surface-raised)
  );
}

@media (min-width: 900px) {
  .beat-hero {
    grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr);
    align-items: center;
  }
}

.beat-hero__fleet {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.fleet-ring {
  position: relative;
  width: 5.5rem;
  height: 5.5rem;
  flex-shrink: 0;
}

.fleet-ring svg {
  width: 100%;
  height: 100%;
  transform: rotate(-90deg);
}

.fleet-ring__track {
  fill: none;
  stroke: color-mix(in srgb, var(--border) 70%, transparent);
  stroke-width: 7;
}

.fleet-ring__fill {
  fill: none;
  stroke: var(--live);
  stroke-width: 7;
  stroke-linecap: round;
  transition: stroke-dasharray 0.35s var(--ease-out);
}

.fleet-ring__label {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  line-height: 1.1;
}

.fleet-ring__value {
  font-family: var(--font-mono);
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--text);
}

.fleet-ring__caption {
  font-size: 0.6875rem;
  color: var(--muted);
}

.beat-hero__title {
  margin: 0;
  font-size: 1.125rem;
  font-weight: 650;
  letter-spacing: -0.02em;
  color: var(--text);
}

.beat-hero__detail {
  margin: 0.35rem 0 0;
  font-size: 0.875rem;
  color: var(--muted);
  max-width: 28ch;
}

.beat-hero__signals {
  display: grid;
  gap: 0.6rem;
}

@media (min-width: 540px) {
  .beat-hero__signals {
    grid-template-columns: 1fr 1fr;
  }
}

.signal-card {
  display: flex;
  gap: 0.65rem;
  align-items: flex-start;
  padding: 0.75rem 0.85rem;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border);
  background: color-mix(in srgb, var(--surface) 85%, transparent);
}

.signal-card__dot {
  width: 0.55rem;
  height: 0.55rem;
  margin-top: 0.35rem;
  border-radius: 999px;
  flex-shrink: 0;
  background: var(--muted);
}

.signal-card--good .signal-card__dot {
  background: var(--live);
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--live) 22%, transparent);
}

.signal-card--warn .signal-card__dot {
  background: var(--signal);
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--signal) 22%, transparent);
}

.signal-card__name {
  margin: 0;
  font-size: 0.75rem;
  color: var(--muted);
}

.signal-card__value {
  margin: 0.15rem 0 0;
  font-size: 0.9375rem;
  font-weight: 600;
  color: var(--text);
  text-transform: capitalize;
}

.signal-card__meta {
  margin: 0.25rem 0 0;
  font-size: 0.6875rem;
  font-family: var(--font-mono);
  color: var(--muted);
}

.ops-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0.55rem;
}

@media (max-width: 720px) {
  .ops-strip {
    grid-template-columns: 1fr;
  }
}

.ops-strip__item {
  padding: 0.7rem 0.85rem;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border);
  background: var(--surface);
}

.ops-strip__label {
  margin: 0;
  font-size: 0.75rem;
  color: var(--muted);
}

.ops-strip__value {
  margin: 0.2rem 0 0;
  font-family: var(--font-mono);
  font-size: 1.2rem;
  font-weight: 600;
  color: var(--text);
}

.ops-strip__meta {
  margin: 0.2rem 0 0;
  font-size: 0.6875rem;
  color: var(--muted);
}

.beat-grid {
  display: grid;
  min-height: 0;
  flex: 1;
  gap: 0.75rem;
  grid-template-columns: minmax(0, 0.95fr) minmax(0, 1.05fr);
}

@media (max-width: 1100px) {
  .beat-grid {
    grid-template-columns: 1fr;
    overflow: auto;
  }
}

.comms-panel {
  display: flex;
  min-height: 0;
  flex-direction: column;
  padding: 0.85rem 0.95rem;
}

.panel-head {
  margin-bottom: 0.65rem;
}

.panel-head--compact {
  margin-bottom: 0.35rem;
}

.panel-head__title {
  margin: 0;
  font-size: 0.9375rem;
  font-weight: 650;
  color: var(--text);
}

.panel-head__hint {
  margin: 0.2rem 0 0;
  font-size: 0.75rem;
  color: var(--muted);
  max-width: 42ch;
}

.comms-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1.25rem;
  border: 1px dashed var(--border);
  border-radius: var(--radius-sm);
  font-size: 0.8125rem;
  text-align: center;
  color: var(--muted);
}

.comms-list {
  margin: 0;
  padding: 0;
  list-style: none;
  overflow: auto;
  flex: 1;
  min-height: 0;
}

.comms-row {
  display: flex;
  gap: 0.75rem;
  justify-content: space-between;
  padding: 0.65rem 0.5rem 0.65rem 0.75rem;
  border-bottom: 1px solid color-mix(in srgb, var(--border) 80%, transparent);
  border-left: 3px solid var(--border);
}

.comms-row:last-child {
  border-bottom: none;
}

.comms-row--live {
  border-left-color: var(--live);
}

.comms-row--idle {
  border-left-color: var(--accent);
}

.comms-row--quiet {
  border-left-color: color-mix(in srgb, var(--muted) 40%, var(--border));
}

.comms-row__device {
  margin: 0;
  font-size: 0.8125rem;
  font-weight: 600;
  color: var(--text);
}

.comms-row__room,
.comms-row__id {
  margin: 0.15rem 0 0;
  font-family: var(--font-mono);
  font-size: 0.65rem;
  color: var(--muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 22rem;
}

.comms-row__aside {
  flex-shrink: 0;
  text-align: right;
}

.comms-row__time {
  display: block;
  margin-top: 0.25rem;
  font-family: var(--font-mono);
  font-size: 0.65rem;
  color: var(--muted);
}

.fleet-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2rem 1.25rem;
  text-align: center;
  font-size: 0.9rem;
  color: var(--muted);
  border-style: dashed;
}

.fleet-workspace {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
  gap: 0.55rem;
}

.unit-picker {
  margin: 0;
  padding: 0;
  border: none;
  min-inline-size: 0;
}

.unit-picker__legend {
  padding: 0;
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--muted);
  margin-bottom: 0.35rem;
}

.unit-picker__list {
  display: flex;
  flex-wrap: wrap;
  gap: 0.45rem;
}

.unit-card {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.45rem 0.65rem;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border);
  background: var(--surface);
  cursor: pointer;
  transition: border-color 0.18s ease, background 0.18s ease;
}

.unit-card:hover {
  border-color: color-mix(in srgb, var(--accent) 45%, var(--border));
}

.unit-card--active {
  border-color: var(--accent);
  background: color-mix(in srgb, var(--accent-soft) 65%, var(--surface));
}

.unit-card__input {
  position: absolute;
  opacity: 0;
  pointer-events: none;
}

.unit-card__name {
  font-size: 0.8125rem;
  font-weight: 600;
  color: var(--text);
}

.unit-meta {
  font-size: 0.8125rem;
  color: var(--muted);
}

.unit-meta strong {
  color: var(--text);
  font-weight: 650;
}

.fleet-panels {
  display: grid;
  min-height: 0;
  flex: 1;
  gap: 0.65rem;
  grid-template-rows: minmax(0, 1fr) minmax(0, 1fr);
}

@media (min-width: 900px) {
  .fleet-panels {
    grid-template-rows: none;
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  }
}

.telemetry-panel {
  display: flex;
  min-height: 0;
  flex-direction: column;
  padding: 0.85rem 0.95rem;
}

.telemetry-loading {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.875rem;
  color: var(--muted);
}

.telemetry-grid {
  display: grid;
  min-height: 0;
  flex: 1;
  gap: 0.45rem;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  overflow: auto;
  align-content: start;
}

.telemetry-cell {
  padding: 0.55rem 0.65rem;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border);
  background: var(--surface);
}

.telemetry-cell__label {
  margin: 0;
  font-size: 0.6875rem;
  color: var(--muted);
}

.telemetry-cell__value {
  margin: 0.15rem 0 0;
  font-family: var(--font-mono);
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--text);
}

.telemetry-cell__bar {
  margin-top: 0.35rem;
  height: 3px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--border) 80%, transparent);
  overflow: hidden;
}

.telemetry-cell__bar span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(
    90deg,
    color-mix(in srgb, var(--accent) 70%, var(--live)),
    var(--live)
  );
  transition: width 0.35s var(--ease-out);
}

.telemetry-updated {
  margin: 0.5rem 0 0;
  font-family: var(--font-mono);
  font-size: 0.65rem;
  color: var(--muted);
}

.camera-panel-wrap {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
}

.camera-panel {
  min-height: 0;
  flex: 1;
}
</style>