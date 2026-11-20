# Device module

**Path:** `src/views/DeviceView.vue`

Handset table plus stream, last image, and WebRTC logs. The page does not scroll.

## Responsibilities

- Poll `GET /devices` every 5 seconds
- Honor Settings default device when present
- Show one row per device: ID, name, OS, mode, location, battery with temperature, latency, last update (`YYYY-MM-DD HH:MM:SS`), Unpair
- Select a row to drive the three squares
- Unpair calls `DELETE /devices/:id`
- Stream uses `CameraPanel`
- Last image is the newest gallery frame for that device
- Logs are WebRTC connections for that device

## Dependencies

- `fetchDevices`, `deleteDevice`, `fetchSettings`, `fetchImages`, `fetchWebRtcConnections`
- `AppLayout`, `ErrorAlert`, `CameraPanel`

## Invariants

- Polling stops on component unmount
- Gallery blob URLs are revoked when the image changes or the view unmounts
- `/dashboard` redirects to `/device`
