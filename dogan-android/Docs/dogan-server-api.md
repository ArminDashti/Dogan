# Dogan Server API Specification

API prefix: `{base}/api/v1/` where `{base}` is the server root including `/dogan` (e.g. `http://192.168.1.10:8090/dogan`). Docker host port is **8090** (`DOGAN_API_PORT`).

## Authentication

### POST /auth

Android request (username/password only):
```json
{ "username": "armin", "password": "dogan123" }
```

Admin/web may still accept the same username/password. Server also supports legacy device `{ "api_key": "..." }` for other clients; the Android app does not use API-key login.

Response:
```json
{ "token": "<bearer>", "expires_at": "2026-07-11T12:00:00Z" }
```

The bearer token authorizes REST calls and MediaMTX WHIP streaming session creation.

## Health

### GET /health

Returns HTTP 200 when the server is reachable. Used for latency measurement; reports `streaming_configured` for MediaMTX availability.

## Sounds

### GET /sounds

Requires bearer token.

Response:
```json
{
  "sounds": [
    {
      "id": "bump",
      "url": "https://.../bump.ogg",
      "sha256": "...",
      "alert_type": "bump",
      "format": "ogg"
    }
  ]
}
```

Alert types: `bump`, `person`, `sound_spike`, `vehicle_departed`, `intrusion`, `overspeed`, `speed_camera`, `generic_warning`

## Settings

### GET /settings?device_id={device_id}

Requires bearer token. Returns the operational settings the device should apply. Omitted keys are left unchanged on the client.

Response:
```json
{
  "device_id": "<android_id>",
  "operating_mode": "watchman",
  "capture_interval_ms": 15000,
  "telemetry_interval_ms": 1000,
  "screen_on_interval_min": 5,
  "sending_image_quality": "balanced",
  "alert_volume": "balanced",
  "alert_duration": "3",
  "min_detection_confidence": 0.45,
  "stream_mode": "video_audio",
  "wifi_only_downloads": false,
  "settings_sync_interval_sec": 60
}
```

The client polls this endpoint every `settings_sync_interval_sec` (default 15) while connected. Server address, username/password, and active camera remain device-local. The Android client ignores `wifi_only_downloads` if present (alert sounds always download when connected).

## Telemetry

### POST /telemetry

Requires bearer token. **Fixed 14-field schema only.**

Request:
```json
{
  "device_id": "<android_id>",
  "recorded_at": "2026-07-11T12:00:00Z",
  "gps_location": { "latitude": 35.6892, "longitude": 51.3890 },
  "gps_signal_quality": "good",
  "speed_kmh": 45.0,
  "network_signal_strength_dbm": -85,
  "network_type": "4G",
  "cabin_noise_rms": 1200.5,
  "battery_temperature_celsius": 32.0,
  "battery_percentage": 78,
  "rear_camera_frame_base64": "<JPEG base64>",
  "front_camera_frame_base64": "<JPEG base64>",
  "ambient_light_lux": 150.0,
  "server_latency_ms": 42,
  "device_ip_address": "192.168.1.5"
}
```

Response: HTTP 2xx on success. Client deletes the SQLite row after successful upload.

## Diagnostic Audio

### POST /diagnostic-audio

Multipart form:
- `metadata` — JSON with `segment_id`, `start_ms`, `end_ms`, `rms_peak`, `linked_alert_id`, `mode`, `device_id`
- `audio` — WAV file

## WebRTC (MediaMTX WHIP)

Streaming uses **MediaMTX over WHIP/WHEP**. The server issues short-lived stream tokens; Android publishes with an HTTP POST of SDP to the WHIP URL, and the web plays over WHEP. Custom WebSocket signaling is not used.

### POST /streaming/token

Request:
```json
{
  "device_id": "<android_id>",
  "role": "publisher",
  "identity": "optional-custom-identity"
}
```

Roles: `publisher` (Android camera/audio source) or `subscriber` (web viewer).

Response:
```json
{
  "token": "<jwt>",
  "url": "http://localhost:8889/device-<android_id>/whip",
  "whip_url": "http://localhost:8889/device-<android_id>/whip",
  "path": "device-<android_id>",
  "room": "device-<android_id>",
  "identity": "publisher-<android_id>",
  "expires_at": "2026-07-14T10:00:00Z"
}
```

### POST /webrtc/session

Android convenience alias that always issues a **publisher** token.

Request:
```json
{ "device_id": "<android_id>" }
```

Response:
```json
{
  "session_id": "device-abc123",
  "token": "<jwt>",
  "url": "http://localhost:8889/device-abc123/whip",
  "whip_url": "http://localhost:8889/device-abc123/whip",
  "path": "device-abc123",
  "room": "device-abc123",
  "identity": "publisher-abc123",
  "expires_at": "2026-07-14T10:00:00Z",
  "ice_servers": [
    { "urls": ["stun:stun.l.google.com:19302"] }
  ]
}
```

### GET /webrtc/connections?device-id=

Lists recent WHIP streaming session records for a device.

## Deprecated Endpoints

- `POST /frame` — still supported; prefer `/telemetry` (frames included in payload)
- `POST /device-metrics` — still supported; prefer `/telemetry` (battery fields included in payload)

## Server alignment

This document is the Android client contract. The Go server at `/dogan/api/v1` implements these routes plus web routes (`/devices/:id/*`, `/images`, `PATCH /settings`). Default Android base URL must include the `/dogan` path prefix.

Docker compose (`dogan.yml`) publishes:
- API: host **8090** → container `8080` → clients use `http://host:8090/dogan`
- Web UI: host **8092**; baked `VITE_API_BASE_URL` = `http://host:8090/dogan/api/v1`
- MediaMTX WebRTC: **8889** (WHIP/WHEP HTTP); HLS **8888**; ICE UDP/TCP **8189**; public WHIP URL `http://host:8889`
- Device API key default: `dogan-dev-key`

