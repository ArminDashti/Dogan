# Store Module

SQLite persistence for all Dogan data.

## Tables
- `login_logs` â€” login audit (IP, user, browser, success/fail)
- `stored_images` â€” JPEG file paths per device
- `phone_actions` â€” commands queued for Android
- `webrtc_connections` â€” LiveKit session log
- `app_settings` â€” key/value settings per platform (web/android)
- `android_telemetry` â€” battery, signal, network, temp, GPS, cabin noise, latency
- `diagnostic_audio` â€” uploaded cabin WAV segment metadata
- `devices` â€” device registry helper

## Key files
- `schema.go` â€” DDL + migrations applied on startup
- `sqlite.go` â€” CRUD (devices, frames, telemetry history, gallery, diagnostic audio, vision)
- `retention.go` â€” background cleanup of old frames/metrics
- `frame_storage.go` â€” base64 frame decode + disk write

## Config
`DOGAN_DATABASE_URL` (default `postgres://dogan:dogan@postgres:5432/dogan?sslmode=disable`)  
`DOGAN_SOUNDS_DIR` (default `sounds`) â€” alert sound files `{id}.ogg|wav|mp3`  
`DOGAN_AUDIO_DIR` (default `diagnostic-audio`) â€” diagnostic WAV storage
