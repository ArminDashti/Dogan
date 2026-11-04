<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { Room, RoomEvent, Track } from 'livekit-client'
import { captureDeviceFrame } from '@/services/api/capture'
import { fetchStreamCredentials } from '@/services/api/stream'
import { getErrorMessage } from '@/services/api/errors'

export type ConnectionState = 'idle' | 'connecting' | 'live' | 'error'

const props = defineProps<{
  deviceId: string
  deviceName?: string
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
const videoContainer = ref<HTMLDivElement | null>(null)

let room: Room | null = null

const statusLabel = computed(() => {
  switch (connectionState.value) {
    case 'connecting':
      return 'Connecting to LiveKit…'
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

function clearMediaElements(): void {
  if (videoContainer.value) {
    videoContainer.value.replaceChildren()
  }
}

async function disconnectStream(): Promise<void> {
  if (room) {
    room.removeAllListeners()
    await room.disconnect()
    room = null
  }
  clearMediaElements()
  connectionState.value = 'idle'
  emit('connectionStateChange', connectionState.value)
}

async function playStream(): Promise<void> {
  if (!props.deviceId || connectionState.value === 'connecting' || connectionState.value === 'live') {
    return
  }

  loadingStream.value = true
  errorMessage.value = null
  captureMessage.value = null
  connectionState.value = 'connecting'
  emit('connectionStateChange', connectionState.value)

  try {
    await disconnectStream()
    connectionState.value = 'connecting'
    emit('connectionStateChange', connectionState.value)

    const credentials = await fetchStreamCredentials(props.deviceId)

    const liveKitRoom = new Room({ adaptiveStream: true })
    liveKitRoom.on(RoomEvent.TrackSubscribed, (track) => {
      if (track.kind === Track.Kind.Video || track.kind === Track.Kind.Audio) {
        const element = track.attach()
        element.classList.add('h-full', 'w-full', 'object-cover')
        videoContainer.value?.appendChild(element)
      }
    })
    liveKitRoom.on(RoomEvent.TrackUnsubscribed, (track) => {
      track.detach().forEach((element) => element.remove())
    })
    liveKitRoom.on(RoomEvent.Connected, () => {
      connectionState.value = 'live'
      emit('connectionStateChange', connectionState.value)
    })
    liveKitRoom.on(RoomEvent.Disconnected, () => {
      connectionState.value = 'idle'
      emit('connectionStateChange', connectionState.value)
    })

    await liveKitRoom.connect(credentials.url, credentials.token)
    room = liveKitRoom
  } catch (error) {
    connectionState.value = 'error'
    emit('connectionStateChange', connectionState.value)
    const message = getErrorMessage(error, 'Unable to connect to the live stream.')
    errorMessage.value = message
    emit('error', message)
    await disconnectStream()
  } finally {
    loadingStream.value = false
  }
}

async function stopStream(): Promise<void> {
  errorMessage.value = null
  await disconnectStream()
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
  <section class="yoda-card overflow-hidden">
    <div class="relative aspect-video bg-black">
      <div ref="videoContainer" class="absolute inset-0" />
      <div
        v-if="connectionState !== 'live'"
        class="absolute inset-0 flex flex-col items-center justify-center gap-2 px-6 text-center text-[var(--muted)]"
      >
        <p class="text-lg font-medium text-[var(--text)]">{{ statusLabel }}</p>
        <p class="text-sm">
          {{
            deviceId
              ? `Press Play to start the stream from ${deviceName ?? 'the selected device'}.`
              : 'Select a device to view the camera feed.'
          }}
        </p>
      </div>
    </div>

    <div class="flex flex-wrap items-center gap-3 border-t border-[var(--border)] px-5 py-4">
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
        class="ml-auto text-sm font-medium"
        :class="{
          'text-[#86efac]': connectionState === 'live',
          'text-[#fde047]': connectionState === 'connecting',
          'text-[#fca5a5]': connectionState === 'error',
          'text-[var(--muted)]': connectionState === 'idle',
        }"
      >
        {{ statusLabel }}
      </span>
    </div>

    <p v-if="captureMessage" class="border-t border-[var(--border)] px-5 py-3 text-sm text-[#86efac]">
      {{ captureMessage }}
    </p>
    <p v-if="errorMessage" class="border-t border-[var(--border)] px-5 py-3 text-sm text-[#fca5a5]">
      {{ errorMessage }}
    </p>
  </section>
</template>
