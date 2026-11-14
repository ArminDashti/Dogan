# Dogan (Android 10+)

Multi-mode Kotlin Android app for vehicle monitoring and driving assistance. Supports Watchman and Copilot modes with motion/sound alerts, LiveKit WebRTC streaming, and telemetry upload to a configurable **Dogan** HTTP server.

## Overview

| | |
|---|---|
| **Purpose** | Parked-vehicle security and driving copilot assistance |
| **Platform** | Android 10+ (Kotlin), rear camera required |
| **Backend** | Dogan REST API on a server you configure |

## Modes

- **Watchman** — Detects jolts, people near car, sharp sounds
- **Copilot** — Driving assistance: intrusion warnings, speed limits, speed cameras

## How to use

1. Install and open the app. Grant Camera, Microphone, and Location permissions.
2. Open **Settings** and set server address and API key (`dogan-dev-key` default).
3. Select operating mode on the main screen.
4. Connect to server in Settings, then select a camera to start monitoring.
5. Use **Diagnostics** to test internet, server API, and LiveKit WebRTC session.

## Telemetry

The app uploads a fixed 14-field telemetry payload every second (default) via `POST /api/v1/telemetry`. See [Dogan Server API](Docs/dogan-server-api.md).

## Build

Open in Android Studio (Giraffe+), sync Gradle, run on a physical device with rear camera.

Server base URL must include `/dogan` (e.g. `http://192.168.1.10:8090/dogan`). Docker compose publishes the API on host port **8090**.

## Permissions

Camera, Microphone, Location, Internet, Notifications, Foreground Service (camera/microphone/location).
