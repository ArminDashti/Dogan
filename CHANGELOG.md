# Changelog

## 2.3.0

### Added
- Public T3 443/TCP-only LiveKit path: `livekit.t3.yaml` + `docker-compose.t3.yml` (LiveKit-embedded TURN with `external_tls` for `dogan-turn.xaigrok.ir`), and HAProxy/DNS docs in `dogan-api/Docs/modules/public-443-subdomains.md`.

## 2.2.1

### Fixed
- Local LiveKit ICE: disable `use_external_ip` and advertise host LAN `node_ip` so Android emulator Watchman can complete media publish (STUN/public IP was unreachable from the AVD).

## 2.2.0

### Changed
- Android LiveKit publisher is Watchman-only; removed Copilot LiveKit switch and `copilot_live_stream_enabled` setting/sync.

## 2.1.1

### Fixed
- Android: CameraX still-capture finish (`unbindAll` / rebind) now runs on the main thread so Watchman/Copilot no longer crash with `Not in application's main thread`.
- Android: cabin-noise segment IDs continue from `MAX(id)` after process restart so audio monitoring no longer dies on `UNIQUE constraint failed: cabin_segments.id`.

## 2.1.0

### Added
- Android battery-related Settings: telemetry/upload intervals, ambient light toggle, sound monitoring toggle, motion sampling rate, Watchman/Copilot GPS accuracy, Copilot wake-lock and LiveKit switches (defaults preserve prior behavior).

## 2.0.1


### Fixed
- SQLite timestamp round-trip: store/scan RFC3339Nano (and migrate legacy Go `time.String()` values) so `GET /webrtc/connections` and `GET /devices/:id/telemetry` no longer 500.
- Local LiveKit signaling published on host `:7880`; default `DOGAN_LIVEKIT_PUBLIC_URL` is `ws://localhost:7880` for local WebUI Play.
- Android LiveKit publisher prefers Connectivity stream URL (e.g. `10.0.2.2:7880`) over the API public URL so the emulator can reach host LiveKit.

## 2.0.0

### Breaking
- dogan-api now uses SQLite (`file:/data/dogan.db` on the API volume) instead of PostgreSQL; `dogan-pgsql` Compose service and `pgx` are removed. Fresh empty DB on upgrade (no Postgres dump migration).

## [0.3.2] - 2026-11-16

### Changed
- Dashboard fits one viewport without page scroll: denser KPI/WebRTC/device chrome and a fill-height Telemetry + Camera split (`CameraPanel` `fill` mode; tighter non-scrollable `AppLayout` padding).

## [0.3.1] - 2026-11-16

### Changed
- Removed the global WebUI page header from AppLayout.
- Nested Metrics sidebar children for Battery, Battery Temperature, Noise, Jolt, Signal, Network Type, and Server-Phone Latency.
- Metric pages show history ranges (today, yesterday, this week, this month, last week, last month) via GET /devices/:id/metrics?range=.

## [0.3.0] - 2026-11-16

### Added

- Ops dashboard usable with **zero devices**: API health, LiveKit configured flag, fleet counts, gallery count, settings summary, and recent WebRTC sessions from the live API.
- `GET /health` now returns `livekit_configured`.
- `GET /webrtc/connections` accepts an optional `device-id` (omit to list recent sessions across all devices).

### Changed

- Admin WebUI no longer depends on `mock:api` / dummy devices; empty states tell operators to connect Android.
- Settings link added to the main sidebar.

### Removed

- `dogan-webui/scripts/mock-api.mjs` and the `mock:api` npm script.

## [0.2.0] - 2026-11-16

### Changed

- Dogan admin WebUI default theme switched from light Lot Signal to **Cursor Dark** (`data-theme="cursor-dark"`): `#181818` canvas, `#141414` chrome, `#81A1C1` primary, dark `theme-color`.
