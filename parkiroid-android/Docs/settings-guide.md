# Settings guide

Settings open as **focused section screens** from the main button grid. There is no global Save button: each change is written to DataStore immediately and, when connected, pushed with `PUT /api/v1/settings` (`platform=android`, `key`, `value`).

Each section (except Copilot, unchanged) follows **Configure → Retention → Browse → Danger**.

## Connectivity

| Setting | Key | Notes |
|---------|-----|-------|
| Username | `username` | Required; default `armin` |
| Password | `password` | Required; default `dogan123` |
| API URL | `api_endpoint` + `api_port` | Combined `host:port` field |
| Stream URL | `stream_endpoint` + `stream_port` | Combined `host:port` field |
| Status panel | — | Ping ms, API, LiveKit |
| Connect / Disconnect | — | Manual only; default Disconnected; no auto-connect |
| Keep logs (days) | `log_retention_days` | Shared app-wide |
| Logs / Flush history / Flush logs | — | Local actions |

## Recording

| Setting | Key | Default |
|---------|-----|---------|
| Record video | `recording_enabled` | **off** |
| Audio | `recording_sound_enabled` | on |
| FPS (+/−) | `recording_fps` | 15 |
| Frame quality | `recording_quality` | BALANCED |
| Chunk (minutes) | `recording_chunk_minutes` | 15 |
| Keep logs | `log_retention_days` | 7 |
| Preview / History / Logs | — | Browse |
| Flush history / Flush logs | — | Danger |

Recording runs only when **Record video** is on and mode ≠ OFF (keeps camera continuous while recording). Preview requires an already-active mode (does not auto-enable Copilot/Watchman).

## Copilot

Field set covers alerts, distance, history frames, alert duration, frame quality, and History frames button when those controls exist.

## Watchman

| Setting | Key | Default |
|---------|-----|---------|
| Still cadence | `capture_interval_ms` | **5000** (1 frame / 5s when Auto or server capture) |
| Image upload | `watcher_image_upload_policy` | On demand (camera stays cold until request/Auto) |
| Sound sensitivity | `sound_sensitivity` | Medium (Low / Medium / High / Custom) |
| Custom sound threshold | `custom_sound_threshold` | 2500 RMS (shown when Custom) |
| Jolt sensitivity | `jolt_sensitivity` | Medium (Low / Medium / High / Custom) |
| Custom jolt scale | `custom_jolt_scale` | 1.0 (shown when Custom) |
| Keep logs | `log_retention_days` | 7 |

Battery: Watchman uses duty-cycled stills, balanced GPS, no auto LiveKit, and timed wake locks between work. Jolt/mic stay on for alerts.

UI label is **Watchman**; stored/API mode value remains `watcher`.

## Settings (general)

| Setting | Key | Notes |
|---------|-----|-------|
| Telemetry interval (sec) | `telemetry_interval_sec` | Default **5** |
| Sync interval (sec) | `settings_sync_interval_sec` | Default **15** (min 10) |
| Keep alive (min) | `screen_on_interval_min` | 0 = off |
| Keep logs (days) | `log_retention_days` | |
| Logs | — | Opens LogsActivity |

## Sync behavior

- Device → server: every local change when connected
- Server → device: `ServerSettingsSync` polls `GET /api/v1/settings` every sync interval while connected
- Username/password are local only (not in full snapshot push)
