# Parkiroid Web — Product Context

## Product

Parkiroid Control Center is the operator web app for monitoring remote field devices (edge cameras and sensors). Operators sign in, pick a device, read live telemetry, watch LiveKit streams, capture frames, browse the gallery, and review metrics.

## Users

- **Operators / viewers** — check device health, watch live feeds, capture snapshots
- **Administrators** — same surface today; settings and account identity live here

## Jobs to be done

1. Sign in and reach a working device quickly
2. Scan telemetry and status without hunting
3. Start/stop a live camera stream and capture a frame
4. Review captured images and recent metric history
5. Adjust alert preferences and default device

## Platform

- **Surface:** Web SPA (Vue 3 + Vite + Tailwind)
- **Mode:** Operate (task completion, scanability, consistent chrome)
- **Companion:** Android edge app (Dogan) reports into the API; this UI consumes the API

## Constraints

- Preserve routes, auth flow, LiveKit stream controls, and API field mappings
- Self-hosted; default local account is `admin` / `admin` (`admin@local.local`)
- No new UI framework dependency

## Brand truth

Parkiroid is infrastructure for field visibility — parking lots, streets, warehouses — not a consumer SaaS marketing site. Clarity and signal beat decoration.
