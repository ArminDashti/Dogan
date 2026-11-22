import type { IceServerConfig } from '@/types/api'

export interface WhepConnectionOptions {
  whepUrl: string
  token: string
  iceServers?: IceServerConfig[]
  videoElement: HTMLVideoElement
  onLive?: () => void
  onError?: (error: Error) => void
}

export interface StreamPlayerSession {
  stop(): Promise<void>
}

const ICE_GATHERING_TIMEOUT_MS = 2_000

export function preflightStreamUrl(url: string): string | null {
  const trimmed = url.trim()
  if (!trimmed) {
    return 'The API did not return a stream URL. Check DOGAN_MEDIAMTX_WEBRTC_PUBLIC_URL on the API.'
  }

  let parsed: URL
  try {
    parsed = new URL(trimmed)
  } catch {
    return `The stream URL is not valid: ${trimmed}`
  }

  if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
    return `The stream URL must use http or https: ${trimmed}`
  }

  if (
    typeof window !== 'undefined' &&
    window.location.protocol === 'https:' &&
    parsed.protocol === 'http:'
  ) {
    return `The stream URL uses http:// (${trimmed}) but this dashboard is HTTPS. Set an https:// MediaMTX public URL on the API.`
  }

  return null
}

export function formatStreamError(error: unknown, streamUrl: string): string {
  const message = error instanceof Error ? error.message : String(error)
  const details = message.trim() || 'Unknown streaming error.'
  const normalized = details.toLowerCase()

  if (normalized.includes('stream not found') || details.includes('404')) {
    return `${details} The publisher may be offline or the path may have changed (${streamUrl}).`
  }
  if (
    normalized.includes('token') ||
    normalized.includes('unauthorized') ||
    normalized.includes('forbidden') ||
    details.includes('401') ||
    details.includes('403')
  ) {
    return `${details} The short-lived stream token may have expired; restart the stream to get a fresh one.`
  }
  if (normalized.includes('ice') || normalized.includes('peer connection')) {
    return `${details} Check TURN relay access and the MediaMTX WebRTC port on the API host.`
  }

  return details
}

function toIceServers(configs: IceServerConfig[] = []): RTCIceServer[] {
  return configs
    .filter((server) => server.urls?.length)
    .map((server) => {
      const iceServer: RTCIceServer = { urls: [...server.urls] }
      if (server.username) {
        iceServer.username = server.username
      }
      if (server.credential) {
        iceServer.credential = server.credential
      }
      return iceServer
    })
}

function waitForIceGathering(
  peerConnection: RTCPeerConnection,
  timeoutMs: number,
): Promise<void> {
  if (peerConnection.iceGatheringState === 'complete') {
    return Promise.resolve()
  }

  return new Promise((resolve) => {
    const finish = (): void => {
      window.clearTimeout(timer)
      peerConnection.removeEventListener('icegatheringstatechange', handleStateChange)
      resolve()
    }
    const handleStateChange = (): void => {
      if (peerConnection.iceGatheringState === 'complete') {
        finish()
      }
    }
    const timer = window.setTimeout(finish, timeoutMs)
    peerConnection.addEventListener('icegatheringstatechange', handleStateChange)
  })
}

async function readResponseError(response: Response): Promise<string> {
  const contentType = response.headers.get('content-type') ?? ''
  let detail = ''

  if (contentType.includes('application/json')) {
    const body = (await response.json().catch(() => null)) as
      | { error?: string; message?: string }
      | null
    detail = body?.error ?? body?.message ?? ''
  } else {
    detail = (await response.text().catch(() => '')).trim()
  }

  return `${detail || response.statusText || 'Request failed.'} (HTTP ${response.status})`
}

export async function connectWhep(
  options: WhepConnectionOptions,
): Promise<StreamPlayerSession> {
  const urlBlock = preflightStreamUrl(options.whepUrl)
  if (urlBlock) {
    throw new Error(urlBlock)
  }

  const peerConnection = new RTCPeerConnection({
    iceServers: toIceServers(options.iceServers),
  })
  const tracks = new Set<MediaStreamTrack>()
  let closed = false
  let disposed = false
  let sessionUrl: string | null = null

  peerConnection.addTransceiver('video', { direction: 'recvonly' })
  peerConnection.addTransceiver('audio', { direction: 'recvonly' })

  peerConnection.ontrack = (event) => {
    if (closed || tracks.has(event.track)) {
      return
    }
    tracks.add(event.track)
    const stream =
      options.videoElement.srcObject instanceof MediaStream
        ? options.videoElement.srcObject
        : new MediaStream()
    stream.addTrack(event.track)
    options.videoElement.srcObject = stream
    void options.videoElement.play().catch(() => undefined)
  }

  peerConnection.onconnectionstatechange = () => {
    if (closed) {
      return
    }
    const state = peerConnection.connectionState
    if (state === 'connected') {
      options.onLive?.()
      return
    }
    if (state === 'failed' || state === 'closed' || state === 'disconnected') {
      options.onError?.(new Error(`WebRTC peer connection ${state}.`))
    }
  }

  const closePeerConnection = (): void => {
    peerConnection.ontrack = null
    peerConnection.onconnectionstatechange = null
    peerConnection.close()
    tracks.forEach((track) => track.stop())
    tracks.clear()
  }

  const deleteSession = async (): Promise<void> => {
    if (!sessionUrl) {
      return
    }
    const target = sessionUrl
    sessionUrl = null
    try {
      await fetch(target, {
        method: 'DELETE',
        headers: { Authorization: `Bearer ${options.token}` },
      })
    } catch {
      return
    }
  }

  try {
    const offer = await peerConnection.createOffer()
    await peerConnection.setLocalDescription(offer)
    await waitForIceGathering(peerConnection, ICE_GATHERING_TIMEOUT_MS)

    const localDescription = peerConnection.localDescription
    if (!localDescription?.sdp) {
      throw new Error('No WebRTC offer SDP was produced.')
    }

    const response = await fetch(options.whepUrl, {
      method: 'POST',
      headers: {
        Accept: 'application/sdp',
        Authorization: `Bearer ${options.token}`,
        'Content-Type': 'application/sdp',
      },
      body: localDescription.sdp,
    })

    if (response.status !== 201) {
      const detail = await readResponseError(response)
      if (response.status === 404) {
        throw new Error(`Stream not found at ${options.whepUrl}. ${detail}`)
      }
      throw new Error(`WHEP negotiation failed. ${detail}`)
    }

    const location = response.headers.get('location')
    if (location) {
      try {
        sessionUrl = new URL(location, options.whepUrl).toString()
      } catch {
        sessionUrl = null
      }
    }

    const answer = (await response.text()).trim()
    if (!answer) {
      throw new Error('The media server returned an empty WebRTC answer.')
    }
    await peerConnection.setRemoteDescription({ type: 'answer', sdp: answer })

    return {
      stop: async () => {
        if (disposed) {
          return
        }
        disposed = true
        closed = true
        await deleteSession()
        closePeerConnection()
      },
    }
  } catch (error) {
    closed = true
    await deleteSession()
    closePeerConnection()
    throw error
  }
}
