# Features and capabilities

## Operating modes

| Mode | Behavior |
|------|----------|
| **Watchman** | Jolts, people near car, sharp sound alerts |
| **Copilot** | Road intrusion (when distance control on), overspeed, speed camera warnings (when alerts on) |
| **OFF** | No capture/detection; enter by re-tapping the active mode |

## Sensing / capture

- Camera duty-cycled (open → still → close) unless Preview or recording is on
- Watchman: vehicle jolt and cabin sound spike alerts (no on-device object-detection AI)
- Activating Watchman from the main hub starts CaptureService immediately

## Telemetry

Sent every `telemetry_interval_sec` when connected:

- GPS, speed, network, battery, cabin noise, ambient light (lux)
- CPU and RAM usage percent
- Camera frames only when upload policy is `auto` or server requests capture (default policy: on-demand)

## Connectivity

- Connect/Disconnect in Connectivity settings (Connected/Disconnected button; ping + API + LiveKit status above)
- Defaults: API `dogan-api.xaigrok.ir:443`, LiveKit `dogan-livekit.xaigrok.ir:443`, username `armin` / password `dogan123`
- Username/password login; same bearer token for API and LiveKit
- Diagnostics: Internet (1.1.1.1 + 8.8.8.8), Server (8 packets), LiveKit session
- SSL certificate warnings logged; self-signed certs accepted with warning
- Setting changes push via `PUT /api/v1/settings` when connected
- Section Logs (Watchman / Connectivity / General) filter by section; main **Logs** shows all

## Media storage

- Per-mode processed images/video under `filesDir/detection-media/{mode}/`
- Local **frame history** ring buffer (`FrameHistoryStore`) with detections JSON; tap a frame to open viewer with previous/next
- General Settings: sync interval, keep alive, keep logs, Logs button
- Flush history / Flush logs live on Watchman, Recording, Connectivity sections

## UI

- Dark Material harmony theme
- Main: mode|gear rows, Connectivity|gear, Record|Settings, Logs|Exit; re-tap mode → OFF
- CameraActivity: Preview from section settings only when a mode is already active (Watchman HUD when present)
- Recording: `recording_enabled` switch (default off) in Recording settings
- Section settings: Configure → Retention → Browse → Danger
- HistoryFramesActivity + HistoryFrameViewerActivity from Watchman / Recording / Copilot when present
