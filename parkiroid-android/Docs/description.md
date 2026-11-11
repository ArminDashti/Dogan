# Dogan — Project Description

Dogan is a Kotlin Android app for multi-mode vehicle monitoring and driving assistance (Watchman, Copilot, OFF). It captures camera frames on a duty cycle (open → still → close), detects vehicle jolts and cabin sound spikes, uploads telemetry to a Dogan server, and can stream via LiveKit.

## Tech stack

- Kotlin, Android 10+ (`minSdk` 29, `targetSdk` 33)
- Android Gradle Plugin **8.5.2** (do not bump via Studio’s AGP upgrade prompt without clearing/re-downloading `aapt2` and aligning the Gradle wrapper)
- CameraX, DataStore, OkHttp, LiveKit Android SDK

## How to run

1. Build with Android Studio or `./gradlew :app:assembleDebug`.
2. Install on a physical Android 10+ device with a rear camera (USB debugging + ADB):
   - Windows: `.\install-on-phone-directly.ps1` (rebuilds from current sources by default; add `-Launch` to open the app, `-SkipBuild` to reuse an existing APK)
   - macOS/Linux: `./scripts/install-apk.sh --build --launch`
   - Or export only: `.\scripts\export-apk.ps1` / `./scripts/export-apk.sh`, then `adb install -r <apk>`
3. Open **Connectivity** (defaults: `dogan-api.xaigrok.ir:443`, `dogan-livekit.xaigrok.ir:443`, `armin`/`dogan123`), Connect manually if needed, then pick a mode on the main grid.

## Main UI

The main screen is a dark Material button hub (no embedded preview). Mode rows (Copilot / Watchman) with settings gears; re-tap active mode for OFF. Other buttons open Recording, General Settings, Connectivity, Logs, or Exit (full process kill). Defaults: mode OFF, recording off, Connectivity disconnected (manual connect only). While monitoring, the camera stays powered only for each scheduled capture (and while Preview or recording is on).

Settings section order: Configure → Retention → Browse → Danger.

Alert sounds download from the server when connected. Watchman uses motion/sound sensors for bump and cabin-noise alerts.
