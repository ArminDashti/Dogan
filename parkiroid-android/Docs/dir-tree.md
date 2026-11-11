# Directory tree

```
parkiroid-android/
├── AGENTS.md                          # Agent preferences and workspace facts
├── Docs/
│   ├── description.md                 # Project overview
│   ├── dir-tree.md                    # This file
│   ├── features-and-capabilities.md   # Feature list
│   ├── settings-guide.md              # Settings sections and keys
│   ├── system-context.md              # Phone ↔ server context
│   ├── modules/
│   │   ├── main-ui-settings.md        # Main grid, settings, history
│   │   └── capture-camera.md          # CaptureService / DoganCamera
│   ├── suggestion/                    # Improvement ideas
│   └── potentional-bugs/              # Known risks
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/dogan/
│       │   ├── MainActivity.kt        # Button-grid hub
│       │   ├── CaptureService.kt      # FG service (duty-cycle camera, sensors)
│       │   ├── DoganCamera.kt         # CameraX bind / still / preview
│       │   ├── VehicleMotionDetector.kt
│       │   ├── ModeController.kt
│       │   ├── OperatingMode.kt
│       │   ├── SettingsStore.kt
│       │   └── …                      # API, telemetry, LiveKit, UI
│       └── res/
│           ├── layout/
│           ├── drawable/
│           └── values/
├── credentials.txt                    # Dev username/password (gitignored)
├── install-on-phone-directly.ps1      # ADB install over USB debugging (Windows)
└── scripts/                           # APK export + USB/ADB install helpers
```
