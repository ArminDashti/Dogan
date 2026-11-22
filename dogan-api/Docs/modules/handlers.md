# Handlers Module

Gin HTTP handlers for the Dogan REST API.

## Files
- `auth.go` — login, web login aliases, current user
- `frame.go` — upload/download frames
- `telemetry.go` — Android unified `POST /telemetry`
- `device_metrics.go` — legacy Android metrics
- `devices_web.go` — web device telemetry/metrics/capture/gallery
- `actions.go` — phone action queue
- `settings.go` — Android flat settings + web PATCH prefs
- `sounds.go` — alert sound manifest + file download
- `diagnostic_audio.go` — cabin diagnostic WAV upload
- `webrtc.go` — list WebRTC sessions with MediaMTX control API state
- `mediamtx.go` — issue MediaMTX stream tokens + authorize publish/read/playback callbacks
- `webrtc_session.go` — Android session alias + device stream credentials
- `health.go`, `endpoints.go` — health, streaming warnings, and self-describing API

## Auth
Protected routes accept JWT (from `/auth`) or embedded API token (`DOGAN_EMBEDDED_API_TOKEN`).
