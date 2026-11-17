import {
  ConnectionError,
  ConnectionErrorReason,
  Room,
  RoomEvent,
  type RoomConnectOptions,
} from 'livekit-client'

export const liveKitConnectOptions: RoomConnectOptions = {
  peerConnectionTimeout: 30_000,
}

export type StreamConnectionStage =
  | 'credentials'
  | 'signaling'
  | 'peer_connection'
  | 'connected'
  | 'unknown'

function stageFromMessage(message: string): StreamConnectionStage {
  const normalized = message.toLowerCase()
  if (normalized.includes('signal connection')) {
    return 'signaling'
  }
  if (normalized.includes('pc connection') || normalized.includes('peer connection')) {
    return 'peer_connection'
  }
  return 'unknown'
}

export function preflightLiveKitUrl(url: string): string | null {
  if (typeof window === 'undefined') {
    return null
  }
  const trimmed = url.trim()
  if (!trimmed) {
    return 'LiveKit signaling URL is empty. Check API DOGAN_LIVEKIT_PUBLIC_URL.'
  }

  const pageIsHttps = window.location.protocol === 'https:'
  if (pageIsHttps && trimmed.startsWith('ws://')) {
    return `LiveKit URL uses ws:// (${trimmed}) but this dashboard is HTTPS — the API must return wss:// (DOGAN_LIVEKIT_PUBLIC_URL).`
  }

  try {
    const parsed = new URL(trimmed)
    const host = parsed.hostname.toLowerCase()
    if (pageIsHttps && (host === 'localhost' || host === '127.0.0.1')) {
      return `LiveKit URL points at ${host} (${trimmed}), which browsers cannot reach from ${window.location.host}. Set DOGAN_LIVEKIT_PUBLIC_URL on the API.`
    }
  } catch {
    return `LiveKit URL is not valid: ${trimmed}`
  }

  return null
}

export function formatLiveKitStreamError(error: unknown, liveKitUrl: string): string {
  let base = 'Unable to connect to the live stream.'
  let stage: StreamConnectionStage = 'unknown'

  if (error instanceof ConnectionError) {
    base = error.message
    stage = stageFromMessage(base)
    switch (error.reason) {
      case ConnectionErrorReason.ServerUnreachable:
      case ConnectionErrorReason.WebSocket:
        stage = 'signaling'
        break
      case ConnectionErrorReason.Timeout:
      case ConnectionErrorReason.InternalError:
        if (stage === 'unknown') {
          stage = stageFromMessage(base)
        }
        break
      default:
        break
    }
  } else if (error instanceof Error) {
    base = error.message
    stage = stageFromMessage(base)
  }

  const hints: string[] = []
  if (stage === 'signaling') {
    hints.push(`Check wss signaling to ${liveKitUrl} (HAProxy → LiveKit :7880).`)
  }
  if (stage === 'peer_connection' || base.toLowerCase().includes('pc connection')) {
    hints.push(
      'WebRTC media (ICE) failed after signaling. On production, verify dogan-turn.xaigrok.ir DNS is grey-cloud (not proxied), HAProxy TURN TLS → :5349, and livekit.yaml copied from livekit.t3.yaml.',
    )
  }

  const stageLabel =
    stage === 'credentials'
      ? 'credentials'
      : stage === 'signaling'
        ? 'signaling (WebSocket)'
        : stage === 'peer_connection'
          ? 'peer connection (ICE)'
          : stage === 'connected'
            ? 'connected'
            : 'connection'

  const hintText = hints.length > 0 ? ` ${hints.join(' ')}` : ''
  return `${base} [failed at: ${stageLabel}]${hintText}`
}

export function attachLiveKitConnectionLogging(room: Room, deviceId: string): () => void {
  const prefix = `[dogan stream ${deviceId}]`
  const onSignalConnected = () => console.info(prefix, 'signaling connected')
  const onReconnecting = () => console.warn(prefix, 'reconnecting')
  const onConnectionState = (state: unknown) => console.info(prefix, 'connection state', state)
  const onMediaDevicesError = (error: Error) => console.error(prefix, 'media device error', error)

  room.on(RoomEvent.SignalConnected, onSignalConnected)
  room.on(RoomEvent.Reconnecting, onReconnecting)
  room.on(RoomEvent.ConnectionStateChanged, onConnectionState)
  room.on(RoomEvent.MediaDevicesError, onMediaDevicesError)

  return () => {
    room.off(RoomEvent.SignalConnected, onSignalConnected)
    room.off(RoomEvent.Reconnecting, onReconnecting)
    room.off(RoomEvent.ConnectionStateChanged, onConnectionState)
    room.off(RoomEvent.MediaDevicesError, onMediaDevicesError)
  }
}