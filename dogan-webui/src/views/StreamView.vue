<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppLayout from '@/components/AppLayout.vue'
import CameraPanel from '@/components/CameraPanel.vue'
import ErrorAlert from '@/components/ErrorAlert.vue'
import { useSelectedDevice } from '@/composables/useSelectedDevice'

type ConnectionState = 'idle' | 'connecting' | 'live' | 'error'
type StreamMode = 'hls' | 'webrtc'

const { deviceId, deviceName, loading: loadingDevices, load: loadDevice } = useSelectedDevice()

const panel = ref<InstanceType<typeof CameraPanel> | null>(null)
const connectionState = ref<ConnectionState>('idle')
const protocol = ref<StreamMode>('hls')

const statusLabel = computed(() => {
  switch (connectionState.value) {
    case 'connecting':
      return `Connecting via ${protocol.value === 'hls' ? 'HLS' : 'WebRTC'}…`
    case 'live':
      return 'Live'
    case 'error':
      return 'Connection failed'
    default:
      return 'Stopped'
  }
})

const canPlay = computed(
  () => Boolean(deviceId.value) && connectionState.value !== 'connecting' && connectionState.value !== 'live',
)
const canStop = computed(
  () => connectionState.value === 'live' || connectionState.value === 'connecting',
)
const canChangeProtocol = computed(
  () => connectionState.value !== 'connecting' && connectionState.value !== 'live',
)

function selectProtocol(mode: StreamMode): void {
  protocol.value = mode
  panel.value?.setMode(mode)
}

function play(): void {
  void panel.value?.play()
}

function stop(): void {
  void panel.value?.stop()
}

onMounted(async () => {
  await loadDevice()
})
</script>

<template>
  <AppLayout
    title="Camera Stream"
    subtitle="Live feed from the device selected in Settings."
    :scrollable="false"
  >
    <div class="space-y-6">
      <ErrorAlert v-if="!loadingDevices && !deviceId" title="No device" message="Select a device in Settings to start the stream." />

      <div
        v-if="!loadingDevices && !deviceId"
        class="yoda-card border-dashed px-5 py-12 text-center text-[var(--muted)]"
      >
        No registered devices yet. Connect the Dogan Android app so a phone reports in — no seed
        script or mock API needed.
      </div>

      <div v-else class="stream-layout">
        <CameraPanel
          ref="panel"
          hide-controls
          :device-id="deviceId"
          :device-name="deviceName"
          @connection-state-change="connectionState = $event"
        />

        <section class="yoda-card stream-controls" aria-label="Streaming controls">
          <header class="stream-controls__head">
            <h2 class="stream-controls__title">Streaming</h2>
            <span
              class="stream-controls__status"
              :class="{
                'stream-controls__status--live': connectionState === 'live' || connectionState === 'connecting',
                'stream-controls__status--error': connectionState === 'error',
              }"
            >
              <span v-if="connectionState === 'live'" class="stream-live-dot" aria-hidden="true" />
              {{ statusLabel }}
            </span>
          </header>

          <p class="stream-controls__device">
            {{ loadingDevices ? 'Loading device…' : deviceName || 'No device selected' }}
          </p>

          <fieldset class="stream-controls__group" :disabled="!canChangeProtocol">
            <legend class="stream-controls__legend">Protocol</legend>
            <label class="stream-protocol" :class="{ 'stream-protocol--active': protocol === 'hls' }">
              <input
                type="radio"
                name="stream-protocol"
                value="hls"
                :checked="protocol === 'hls'"
                @change="selectProtocol('hls')"
              />
              <span>
                HLS
                <small>Compatible fallback, a few seconds behind.</small>
              </span>
            </label>
            <label class="stream-protocol" :class="{ 'stream-protocol--active': protocol === 'webrtc' }">
              <input
                type="radio"
                name="stream-protocol"
                value="webrtc"
                :checked="protocol === 'webrtc'"
                @change="selectProtocol('webrtc')"
              />
              <span>
                WebRTC
                <small>Lowest delay, needs a reachable WHEP host.</small>
              </span>
            </label>
          </fieldset>

          <div class="stream-controls__actions">
            <button
              type="button"
              class="yoda-btn yoda-btn--primary cursor-pointer"
              :disabled="!canPlay"
              @click="play"
            >
              <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <path d="M5 5l12 7-12 7V5Z" fill="currentColor" />
              </svg>
              Play
            </button>
            <button
              type="button"
              class="yoda-btn yoda-btn--outline cursor-pointer"
              :disabled="!canStop"
              @click="stop"
            >
              <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <rect x="6" y="6" width="12" height="12" rx="1" fill="currentColor" />
              </svg>
              Stop
            </button>
          </div>
        </section>
      </div>
    </div>
  </AppLayout>
</template>

<style scoped>
.stream-layout {
  display: grid;
  gap: 0.75rem;
  grid-template-columns: minmax(0, 1fr) 300px;
  align-items: start;
}

@media (max-width: 1100px) {
  .stream-layout {
    grid-template-columns: minmax(0, 1fr);
  }
}

.stream-controls {
  display: flex;
  flex-direction: column;
  gap: 0.9rem;
  padding: 1rem;
}

.stream-controls__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}

.stream-controls__title {
  margin: 0;
  font-size: 0.9375rem;
  font-weight: 650;
}

.stream-controls__status {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--muted);
}

.stream-controls__status--live {
  color: var(--signal);
}

.stream-controls__status--error {
  color: var(--danger);
}

.stream-controls__device {
  margin: 0;
  font-size: 0.8125rem;
  color: var(--muted);
}

.stream-controls__group {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  margin: 0;
  padding: 0;
  border: 0;
}

.stream-controls__legend {
  padding: 0;
  margin-bottom: 0.35rem;
  font-size: 0.6875rem;
  font-weight: 650;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--muted);
}

.stream-protocol {
  display: flex;
  gap: 0.5rem;
  padding: 0.5rem 0.6rem;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  font-size: 0.8125rem;
  cursor: pointer;
}

.stream-protocol--active {
  border-color: var(--accent);
  background: color-mix(in srgb, var(--accent) 10%, transparent);
}

.stream-protocol input {
  margin-top: 0.15rem;
}

.stream-protocol small {
  display: block;
  margin-top: 0.1rem;
  font-size: 0.6875rem;
  color: var(--muted);
}

.stream-controls__actions {
  display: flex;
  gap: 0.5rem;
}
</style>
