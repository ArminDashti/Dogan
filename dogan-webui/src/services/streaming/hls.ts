import type Hls from 'hls.js'
import { preflightStreamUrl } from './whep'

export interface HlsConnectionOptions {
  hlsUrl: string
  token: string
  videoElement: HTMLVideoElement
  onLive?: () => void
  onError?: (error: Error) => void
}

export interface HlsPlayerSession {
  stop(): Promise<void>
}

interface HlsErrorPayload {
  fatal?: boolean
  details?: string
  type?: string
}

const HLS_LOAD_TIMEOUT_MS = 10_000

function waitForNativePlayback(
  videoElement: HTMLVideoElement,
  timeoutMs: number,
): Promise<void> {
  return new Promise((resolve, reject) => {
    const finish = (error?: Error): void => {
      window.clearTimeout(timer)
      videoElement.removeEventListener('playing', handlePlaying)
      videoElement.removeEventListener('error', handleError)
      if (error) {
        reject(error)
      } else {
        resolve()
      }
    }
    const handlePlaying = (): void => finish()
    const handleError = (): void => finish(new Error('Native HLS playback failed.'))
    const timer = window.setTimeout(
      () => finish(new Error('Timed out waiting for HLS playback.')),
      timeoutMs,
    )
    videoElement.addEventListener('playing', handlePlaying)
    videoElement.addEventListener('error', handleError)
  })
}

function resetVideo(videoElement: HTMLVideoElement): void {
  videoElement.pause()
  videoElement.srcObject = null
  videoElement.removeAttribute('src')
  videoElement.load()
}

export async function connectHls(
  options: HlsConnectionOptions,
): Promise<HlsPlayerSession> {
  const urlBlock = preflightStreamUrl(options.hlsUrl)
  if (urlBlock) {
    throw new Error(urlBlock)
  }

  const videoElement = options.videoElement
  let hls: Hls | null = null
  let disposed = false

  const session: HlsPlayerSession = {
    stop: async () => {
      if (disposed) {
        return
      }
      disposed = true
      hls?.destroy()
      hls = null
      resetVideo(videoElement)
    },
  }

  try {
    const { default: HlsPlayer } = await import('hls.js')
    if (HlsPlayer.isSupported()) {
      hls = new HlsPlayer({
        lowLatencyMode: true,
        liveDurationInfinity: true,
        enableWorker: false,
        xhrSetup: (xhr) => {
          xhr.setRequestHeader('Authorization', `Bearer ${options.token}`)
        },
      })
      const player = hls

      await new Promise<void>((resolve, reject) => {
        let settled = false
        const finish = (error?: Error): void => {
          if (settled) {
            return
          }
          settled = true
          window.clearTimeout(timer)
          player.off(HlsPlayer.Events.MEDIA_ATTACHED, handleMediaAttached)
          player.off(HlsPlayer.Events.MANIFEST_PARSED, handleManifestParsed)
          player.off(HlsPlayer.Events.ERROR, handleError)
          if (error) {
            reject(error)
          } else {
            resolve()
          }
        }
        const handleMediaAttached = (): void => player.loadSource(options.hlsUrl)
        const handleManifestParsed = (): void => {
          void videoElement.play().catch(() => undefined)
          finish()
        }
        const handleError = (_event: string, data: HlsErrorPayload): void => {
          if (!data.fatal) {
            return
          }
          const error = new Error(`HLS playback failed: ${data.details ?? data.type ?? 'unknown error'}`)
          if (settled) {
            if (!disposed) {
              options.onError?.(error)
            }
          } else {
            finish(error)
          }
        }
        const timer = window.setTimeout(
          () => finish(new Error('Timed out loading the HLS stream.')),
          HLS_LOAD_TIMEOUT_MS,
        )
        player.on(HlsPlayer.Events.MEDIA_ATTACHED, handleMediaAttached)
        player.on(HlsPlayer.Events.MANIFEST_PARSED, handleManifestParsed)
        player.on(HlsPlayer.Events.ERROR, handleError)
        player.attachMedia(videoElement)
      })
    } else if (videoElement.canPlayType('application/vnd.apple.mpegurl')) {
      const nativeUrl = new URL(options.hlsUrl)
      nativeUrl.searchParams.set('token', options.token)
      videoElement.src = nativeUrl.toString()
      void videoElement.play().catch(() => undefined)
      await waitForNativePlayback(videoElement, HLS_LOAD_TIMEOUT_MS)
    } else {
      throw new Error('HLS playback requires Media Source Extensions or native HLS support.')
    }

    if (!disposed) {
      options.onLive?.()
    }
    return session
  } catch (error) {
    hls?.destroy()
    hls = null
    resetVideo(videoElement)
    throw error
  }
}
