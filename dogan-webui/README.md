# Dogan Web

Vue 3 frontend for the Dogan device platform — login, device info, live stream, gallery, logs, settings, and device metrics.

## Stack

- Vue 3 + Vite + TypeScript
- Vue Router with auth guards
- Pinia for session state
- Tailwind CSS (Vite plugin)
- LiveKit client for WebRTC live streaming

## Setup

```bash
npm install
cp .env.example .env
```

Set `VITE_API_BASE_URL` in `.env` to your backend API base URL (no trailing slash), e.g. `http://localhost:8080/dogan/api/v1`.

## Development

Run the live Dogan API (Docker) first — this UI talks only to that backend, not a mock server:

```bash
# from dogan-api
docker compose up -d --build
# API: http://localhost:8080/dogan/api/v1/health
```

```bash
npm run dev
```

Open the URL shown in the terminal (default `http://localhost:5173`). For the Docker WebUI image use `http://localhost:8083` (login `admin@local.local` / `admin`). Devices appear when a real Android phone reports in; do not seed fake devices for the admin panel.

## Production build

```bash
npm run build
npm run preview
```

## Routes

| Path | Page |
|------|------|
| `/login` | Sign in |
| `/device` | Selected handset details (`/dashboard` redirects here) |
| `/stream` | LiveKit camera stream with Play/Stop/Capture |
| `/gallery` | Image gallery |
| `/logs` | Sign-in audit and WebRTC session history |
| `/settings` | Preferences |
| `/metrics` | Device metrics (temperature + noise history) |
| `/devices/:id/metrics` | Metrics for a specific device |

Protected routes redirect to `/login` when unauthenticated.

## Device

The `/device` page shows registration fields from `GET /devices` for the selected (or default) handset.

| Field | Source |
|-------|--------|
| Name | `GET /devices` → `name` |
| Location | `GET /devices` → `location` |
| Battery | `GET /devices` → `battery_percent` (list snapshot) |
| Temperature | `GET /devices` → `temperature_celsius` |
| Mode, OS, latency, last update | `GET /devices` list fields |

Device list refreshes every 5 seconds. Live telemetry and camera are on **Stream** and **Metrics**.

## API

The app expects a REST API at `VITE_API_BASE_URL` with bearer token auth.

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/auth/login` | Sign in |
| `POST` | `/auth/logout` | Sign out |
| `GET` | `/auth/me` | Current user |
| `GET` | `/auth/login-logs` | Sign-in audit (limit query) |
| `GET` | `/devices` | List devices |
| `GET` | `/devices/:id/metrics` | Temperature + noise history |
| `GET` | `/devices/:id/telemetry` | Live telemetry snapshot |
| `GET` | `/devices/:id/stream` | LiveKit stream credentials |
| `POST` | `/devices/:id/capture` | Trigger frame capture on device |
| `GET` | `/images` | Gallery images |
| `GET` | `/settings` | User preferences |
| `PATCH` | `/settings` | Update preferences |

See service modules under `src/services/api/` for request/response field mapping.
