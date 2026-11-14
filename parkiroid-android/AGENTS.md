## Learned User Preferences

- Use the `dogan` name and `/api/v1/` API path prefix
- Prefer a dedicated Settings screen for endpoints and mode options rather than inline configuration on the main screen
- Do not commit git changes unless explicitly asked

## Learned Workspace Facts

- Dogan is a Kotlin Android app for multi-mode vehicle monitoring (Watchman, Copilot, OFF)
- Targets Android 10+ (`minSdk` 29, `targetSdk` 33); rear camera required, front camera and microphone optional
- Main screen is a dark button grid (no live preview): Copilot|gear, Watchman|gear, Connectivity|gear, Record|Settings, Logs|Exit; re-tap active mode → OFF
- Preview opens from section settings only when a mode is already active (CameraActivity); Watchman preview shows jolt/sound HUD; Exit kills the process
- Connect/Disconnect lives in Connectivity settings (manual only; default Disconnected; no auto-connect); API URL / Stream URL are combined host:port fields
- Settings are section screens (Connectivity, Recording, Copilot, Watchman, General) ordered Configure→Retention→Browse→Danger; each change auto-saves to DataStore and `PUT /api/v1/settings` when connected
- Defaults: operating mode OFF, image upload on-demand, sync 15s, telemetry 5s, Watchman still cadence `capture_interval_ms` 5s (when Auto/server capture); recording off when present
- Capture camera is duty-cycled (open → still → close); Watchman skips camera when On demand and no pending capture, uses balanced GPS, no auto LiveKit, timed wake locks
- Frame quality UI: LOW / BALANCED / HIGH / ORIGINAL; FPS adjusted with +/- steppers
- Watchman/Recording link to History, Logs, Preview when those screens exist
- HTTP API prefix is `{base}/api/v1/`; auth is username/password via `POST /auth` (defaults `armin`/`dogan123` in `credentials.txt`); same bearer for API and LiveKit (`POST /webrtc/session`)
- Camera frames upload only to Dogan API telemetry (`POST /api/v1/telemetry`); never call OpenCode or put `OPENCODE_GO_*` / `oc_sk_*` in the Android app — the API forwards to OpenCode Go server-side
- Default Connectivity hosts: API `dogan-api.xaigrok.ir:443`, LiveKit `dogan-livekit.xaigrok.ir:443`
- Telemetry includes `ambient_light_lux`, `cpu_usage_percent`, `ram_usage_percent`
- SSL cert issues log warnings and use permissive trust for self-hosted servers
- Local Docker: API **8090**, LiveKit **7880**; Android `http://<host-ip>:8090/dogan`
- While connected, `ServerSettingsSync` polls `GET /api/v1/settings` every sync interval (default 15s)
- `CaptureService` runs when mode ≠ OFF; Watchman uses motion/sound alerts (no on-device OD AI)
- Section Logs filter by mode/area; main Logs shows all
- APK export: `scripts/export-apk.ps1` / `scripts/export-apk.sh`
- APK USB/ADB install: `install-on-phone-directly.ps1` (root; rebuilds by default; `-SkipBuild` / `-Launch` optional) / `scripts/install-apk.sh`
