<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { captureDeviceFrame } from '@/services/api/capture'
import { fetchStreamCredentials } from '@/services/api/stream'
import { getErrorMessage } from '@/services/api/errors'
import { connectHls } from '@/services/streaming/hls'
import {
  connectWhep,
  formatStreamError,
  preflightStreamUrl,
} from '@/services/streaming/whep'
import type { StreamResponse } from '@/types/api'

export type ConnectionState = 'idle' | 'connecting' | 'live' | 'error'

type StreamMode = 'webrtc' | 'hls'

interface PlayerSession {
  stop(): Promise<void>
}

const props = defineProps<{
  deviceId: string
  deviceName?: string
  fill?: boolean
}>()

const emit = defineEmits<{
  connectionStateChange: [state: ConnectionState]
  captureSuccess: [message: string]
  error: [message: string]
}>()

const connectionState = ref<ConnectionState>('idle')
const loadingStream = ref(false)
const capturing = ref(false)
const captureMessage = ref<string | null>(null)
const errorMessage = ref<string | null>(null)
const streamNotice = ref<string | null>(null)
const streamMode = ref<StreamMode>('webrtc')
const credentials = ref<StreamResponse | null>(null)
const videoElement = ref<HTMLVideoElement | null>(null)

let player: PlayerSession | null = null
let connectionGeneration = 0

const statusLabel = computed(() => {
  switch (connectionState.value) {
    case 'connecting':
      return `Connecting via ${streamMode.value === 'hls' ? 'HLS' : 'WebRTC'}…`
    case 'live':
      return 'Live'
    case 'error':
      return 'Connection failed'
    default:
      return loadingStream.value ? 'Loading stream…' : 'Not connected'
  }
})

const canPlay = computed(
  () => Boolean(props.deviceId) && connectionState.value !== 'connecting' && connectionState.value !== 'live',
)
const canStop = computed(() => connectionState.value === 'live' || connectionState.value === 'connecting')
const canCapture = computed(() => Boolean(props.deviceId) && connectionState.value === 'live' && !capturing.value)
const canToggleMode = computed(
  () => connectionState.value !== 'connecting' && connectionState.value !== 'live',
)
const modeButtonLabel = computed(() =>
  streamMode.value === 'webrtc' ? 'Use HLS fallback' : 'Use WebRTC',
)

function clearVideo(): void {
  const element = videoElement.value
  if (!element) {
    return
  }
  element.pause()
  if (element.srcObject instanceof MediaStream) {
    element.srcObject.getTracks().forEach((track) => track.stop())
    element.srcObject = null
  }
  element.removeAttribute('src')
  element.load()
}

async function teardownPlayer(): Promise<void> {
  const session = player
  player = null
  if (session) {
    await session.stop()
  }
  clearVideo()
}

function markLive(): void {
  if (connectionState.value === 'connecting') {
    connectionState.value = 'live'
    emit('connectionStateChange', connectionState.value)
  }
}

function reportStreamError(error: Error): void {
  if (connectionState.value === 'idle') {
    return
  }
  const url = credentials.value?.whepUrl ?? credentials.value?.url ?? ''
  const message = formatStreamError(error, url)
  connectionState.value = 'error'
  errorMessage.value = message
  emit('connectionStateChange', connectionState.value)
  emit('error', message)
}

async function connectSession(
  streamCredentials: StreamResponse,
  mode: StreamMode,
): Promise<PlayerSession> {
  const element = videoElement.value
  if (!element) {
    throw new Error('The video element is unavailable.')
  }

  if (mode === 'hls') {
    if (!streamCredentials.hlsUrl) {
      throw new Error('The API did not provide an HLS URL for this device.')
    }
    return connectHls({
      hlsUrl: streamCredentials.hlsUrl,
      token: streamCredentials.token,
      videoElement: element,
      onLive: markLive,
      onError: reportStreamError,
    })
  }

  const whepUrl = streamCredentials.whepUrl || streamCredentials.url
  const urlBlock = preflightStreamUrl(whepUrl)
  if (urlBlock) {
    throw new Error(urlBlock)
  }
  return connectWhep({
    whepUrl,
    token: streamCredentials.token,
    iceServers: streamCredentials.iceServers,
    videoElement: element,
    onLive: markLive,
    onError: reportStreamError,
  })
}

async function disconnectStream(): Promise<void> {
  connectionGeneration += 1
  await teardownPlayer()
  credentials.value = null
  streamNotice.value = null
  if (connectionState.value !== 'idle') {
    connectionState.value = 'idle'
    emit('connectionStateChange', connectionState.value)
  }
}

async function playStream(): Promise<void> {
  if (!props.deviceId || connectionState.value === 'connecting' || connectionState.value === 'live') {
    return
  }

  const generation = ++connectionGeneration
  loadingStream.value = true
  errorMessage.value = null
  captureMessage.value = null
  streamNotice.value = null
  connectionState.value = 'connecting'
  emit('connectionStateChange', connectionState.value)

  try {
    await teardownPlayer()

    const streamCredentials = await fetchStreamCredentials(props.deviceId)
    if (generation !== connectionGeneration) {
      return
    }
    credentials.value = streamCredentials

    let session: PlayerSession
    if (streamMode.value === 'hls' && streamCredentials.hlsUrl) {
      session = await connectSession(streamCredentials, 'hls')
    } else {
      try {
        session = await connectSession(streamCredentials, 'webrtc')
      } catch (error) {
        if (!streamCredentials.hlsUrl) {
          throw error
        }
        streamNotice.value = `${formatStreamError(error, streamCredentials.whepUrl)} Switched to HLS.`
        console.warn('[dogan stream] WebRTC playback failed, using HLS', error)
        streamMode.value = 'hls'
        session = await connectSession(streamCredentials, 'hls')
      }
    }

    if (generation !== connectionGeneration) {
      await session.stop()
      return
    }
    player = session
  } catch (error) {
    if (generation !== connectionGeneration) {
      return
    }
    connectionState.value = 'error'
    emit('connectionStateChange', connectionState.value)
    const message = credentials.value
      ? formatStreamError(error, credentials.value.whepUrl || credentials.value.url)
      : getErrorMessage(error, 'Unable to fetch stream credentials from the API.')
    errorMessage.value = message
    emit('error', message)
    await teardownPlayer()
  } finally {
    if (generation === connectionGeneration) {
      loadingStream.value = false
    }
  }
}

async function stopStream(): Promise<void> {
  errorMessage.value = null
  await disconnectStream()
}

function toggleStreamMode(): void {
  if (!canToggleMode.value) {
    return
  }
  streamMode.value = streamMode.value === 'webrtc' ? 'hls' : 'webrtc'
  streamNotice.value = null
}

async function captureFrame(): Promise<void> {
  if (!canCapture.value) {
    return
  }

  capturing.value = true
  errorMessage.value = null
  captureMessage.value = null

  try {
    const response = await captureDeviceFrame(props.deviceId)
    const message = `Frame captured at ${new Date(response.capturedAt).toLocaleTimeString()}.`
    captureMessage.value = message
    emit('captureSuccess', message)
  } catch (error) {
    const message = getErrorMessage(error, 'Unable to capture frame.')
    errorMessage.value = message
    emit('error', message)
  } finally {
    capturing.value = false
  }
}

watch(
  () => props.deviceId,
  async (newId, oldId) => {
    if (newId !== oldId) {
      captureMessage.value = null
      errorMessage.value = null
      await disconnectStream()
    }
  },
)

onUnmounted(() => {
  void disconnectStream()
})
</script>

<template>
  <section
    class="yoda-card overflow-hidden"
    :class="fill ? 'flex h-full min-h-0 flex-col' : undefined"
  >
    <div
      class="relative bg-[#0d1218]"
      :class="fill ? 'min-h-0 flex-1' : 'aspect-video'"
    >
      <video
        ref="videoElement"
        class="absolute inset-0 h-full w-full object-cover"
        autoplay
        muted
        playsinline
      />
      <div
        v-if="connectionState !== 'live'"
        class="absolute inset-0 flex flex-col items-center justify-center gap-2 px-6 text-center"
      >
        <p class="text-lg font-medium text-white/90">{{ statusLabel }}</p>
        <p class="text-sm text-white/55">
          {{
            deviceId
              ? `Press Play to start the stream from ${deviceName ?? 'the selected device'}.`
              : 'Select a device to view the camera feed.'
          }}
        </p>
      </div>
    </div>

    <div
      class="flex flex-wrap items-center gap-3 border-t border-[var(--border)]"
      :class="fill ? 'shrink-0 px-3 py-2' : 'px-5 py-4'"
    >
      <button
        type="button"
        class="yoda-btn yoda-btn--primary cursor-pointer"
        :disabled="!canPlay"
        @click="playStream"
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
        @click="stopStream"
      >
        <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <rect x="6" y="6" width="12" height="12" rx="1" fill="currentColor" />
        </svg>
        Stop
      </button>
      <button
        type="button"
        class="yoda-btn yoda-btn--outline cursor-pointer"
        :disabled="!canToggleMode"
        @click="toggleStreamMode"
      >
        {{ modeButtonLabel }}
      </button>
      <button
        type="button"
        class="yoda-btn yoda-btn--outline cursor-pointer"
        :disabled="!canCapture"
        @click="captureFrame"
      >
        <svg class="h-4 w-4" viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <path d="M4 7h4l2-2h4l2 2h4v12H4V7Z" stroke="currentColor" stroke-width="1.75" />
          <circle cx="12" cy="13" r="3" stroke="currentColor" stroke-width="1.75" />
        </svg>
        {{ capturing ? 'Capturing…' : 'Capture Frame' }}
      </button>

      <span
        class="ml-auto inline-flex items-center gap-2 text-sm font-semibold"
        :class="{
          'text-[var(--signal)]': connectionState === 'live' || connectionState === 'connecting',
          'text-[var(--danger)]': connectionState === 'error',
          'text-[var(--muted)]': connectionState === 'idle',
        }"
      >
        <span v-if="connectionState === 'live'" class="stream-live-dot" aria-hidden="true" />
        {{ statusLabel }}
      </span>
    </div>

    <p v-if="streamNotice" class="border-t border-[var(--border)] px-5 py-3 text-sm text-[var(--muted)]">
      {{ streamNotice }}
    </p>
    <p v-if="captureMessage" class="border-t border-[var(--border)] px-5 py-3 text-sm text-[var(--live)]">
      {{ captureMessage }}
    </p>
    <p v-if="errorMessage" class="border-t border-[var(--border)] px-5 py-3 text-sm text-[var(--danger)]">
      {{ errorMessage }}
    </p>
  </section>
</template>
