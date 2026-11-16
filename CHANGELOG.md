# Changelog

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
