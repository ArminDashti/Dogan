# Camera panel module

**Path:** `src/components/CameraPanel.vue`

Reusable MediaMTX subscriber panel with manual stream controls.

## Props

- `deviceId` — target device (empty disables controls)
- `deviceName` — optional label for placeholder text
- `fill` — stretch the video to the parent height instead of locking to 16:9

## Controls

- **Play** — fetch stream credentials and start WHEP playback, falling back to HLS when available
- **Stop** — disconnect the active player and clear the video element
- **Use HLS fallback / Use WebRTC** — choose the transport for the next Play
- **Capture Frame** — `POST /devices/:id/capture` (enabled only when live)

## Emits

- `connectionStateChange` — `idle | connecting | live | error`
- `captureSuccess` — confirmation message
- `error` — error message string

## Dependencies

- `src/services/streaming/whep.ts` — browser WHEP client over `RTCPeerConnection`
- `src/services/streaming/hls.ts` — native HLS or `hls.js` with Bearer header via `xhrSetup`
- `fetchStreamCredentials`, `captureDeviceFrame`

## Invariants

- Does not auto-connect on mount
- Disconnects when `deviceId` changes or component unmounts
- WHEP sessions send `DELETE` to the `Location` returned by the media server
- Native HLS uses the API's `?token=` query support because video elements cannot set headers
