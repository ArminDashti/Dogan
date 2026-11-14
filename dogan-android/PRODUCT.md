# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

Primary users are vehicle owners and operators who leave an Android phone in or near a parked car as a field monitor. They configure Connectivity once, pick Watchman or Copilot, then leave the phone unattended while the companion Dogan Control Center (web) reviews telemetry and streams.

*(inferred from Docs/product-overview.md, Docs/how-to-use.md, and dogan-webui/PRODUCT.md companion note)*

## Product Purpose

Dogan is the Android field device for Dogan: multi-mode vehicle monitoring and driving assistance. It captures camera stills on a duty cycle, detects jolts and cabin sound spikes (Watchman), supports Copilot driving alerts, uploads telemetry to a Dogan server, and can stream via LiveKit. Success means reliable background monitoring with low-friction start/stop and clear connection state before the owner walks away.

*(inferred from README.md, Docs/product-overview.md, Docs/features-and-capabilities.md)*

## Positioning

The phone is the sensor and capture edge, not a review dashboard. Remote archive and live review belong to the Dogan server and Dogan web Control Center; this app owns on-device modes, capture, alerts, and connectivity to that backend.

*(inferred from Docs/product-overview.md “How it fits” and webui PRODUCT.md)*

## Operating Context

- Physical Android 10+ phone with a rear camera, typically mounted or left in the vehicle cabin
- Brief setup glances in daylight or dim cabin light; long unattended runs while parked
- Manual Connectivity connect (username/password → API + LiveKit bearer); modes start CaptureService as a foreground service
- Settings sections: Configure → Retention → Browse → Danger
- Companion operator workflow lives in the Dogan web UI

*(inferred from Docs/how-to-use.md, Docs/modules/main-ui-settings.md, Docs/description.md)*

## Capabilities and Constraints

Confirmed in code/docs:

- Modes: Watchman, Copilot, OFF (re-tap active mode → OFF)
- Main hub navigates to section settings, Connectivity, Logs, Exit (process kill); Diagnostics available
- Camera duty-cycled unless preview/recording needs continuous power
- Telemetry upload on interval; LiveKit streaming when configured
- Material Components (Views), Kotlin, minSdk 29; CameraX, DataStore, OkHttp, LiveKit
- Preserve existing IDs, navigation intents, SettingsStore keys, and CaptureService behavior unless a redesign requires a layout ID remount with matching Kotlin wiring

Open / undecided:

- Whether recording/history preview surfaces return in this redesign pass (docs mention them; current tree’s main hub does not expose Record/Camera buttons)
- Tablet / multi-pane layouts — phone-first only for now

*(inferred from source layouts, AndroidManifest, features docs; open items labeled)*

## Brand Commitments

- Product name: **Dogan** (app label); Dogan is the broader system / web Control Center name
- Shared visual world with the web Control Center: **Lot Signal** (committed in `dogan-webui/DESIGN.md`) — Android redesign adapts that world through Material 3 theming for the in-car scene rather than inventing a second identity
- Voice: operational, direct, low-friction control language (mode names Watchman / Copilot / OFF; Connected / Disconnected)

*(brand world inheritance inferred from monorepo DESIGN.md + product companion relationship; confirmable by user)*

## Evidence on Hand

- `Docs/` product and module docs; `growth-log/`
- Current XML layouts under `app/src/main/res/layout/`
- Web Lot Signal tokens in `dogan-webui/DESIGN.md`
- Inter font files present under `res/font/` (incumbent; Lot Signal uses Bricolage Grotesque + IBM Plex Mono on web — Android adaptation TBD in DESIGN.md)
- No committed Android screenshot goldens in-repo

Do not fabricate testimonials, fleet sizes, or performance claims.

## Product Principles

1. **Background-first** — monitoring continues with the screen off; the foreground UI is for arming and diagnosing, not watching.
2. **Low-friction control** — one hub to pick mode and reach Connectivity; details live in Settings.
3. **Signal over chrome** — connection and mode state must be unmistakable before the owner leaves the vehicle.
4. **Configurable backend** — each deployment points at its own API/LiveKit hosts and credentials.
5. **Battery-conscious capture** — duty-cycle the camera; keep retention and intervals operator-tunable.

*(derived from Docs/product-overview.md design principles + features docs)*

## Accessibility & Inclusion

No product-specific accessibility standard was confirmed beyond Android platform defaults. Honor system font scale (`sp`), touch targets ≥48dp, and Material contrast roles. Dark theme is first-class for cabin use.

*(platform defaults; no WCAG product mandate found)*
