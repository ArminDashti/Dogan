# Dogan Android — Design System

**World:** Lot Signal (cabin dark adaptation)  
**Mode:** Operate  
**Composition:** Bay Gate Board (seed `seed:706882650aefd847`)  
**Anti-reference:** Prior dark Inter + generic green Material hub (`#121212` / `#2E7D32` full-bleed mode paints)

## Intent

Field-ops phone UI that shares Lot Signal with the Parkiroid web Control Center: bay teal, barrier amber, concrete-cool surfaces — remapped to a dark cabin scheme for in-vehicle glances. Precision and arming clarity over expression. Brand lives in the Dogan mark, Bricolage type, and signal lamp — not purple glow or dashboard clutter.

## Color (Material roles)

| Role | Hex | Resource |
|------|-----|----------|
| Background / surface | `#101820` | `dogan_surface` |
| Surface variant | `#15202B` | `dogan_surface_variant` |
| Elevated / container | `#1A2834` | `dogan_surface_elevated` |
| Outline | `#2A3A48` | `dogan_outline` |
| On surface | `#E8EDF1` | `dogan_on_surface` |
| On surface muted | `#8AA0B2` | `dogan_on_surface_secondary` |
| Primary / bay | `#0B6E4F` | `dogan_primary` |
| Primary dark | `#085A41` | `dogan_primary_dark` |
| Primary light | `#3D9B78` | `dogan_primary_light` |
| On primary | `#FFFFFF` | `dogan_on_primary` |
| Signal / live attention | `#C9780E` | `dogan_signal` |
| Danger | `#E07070` | `dogan_danger` |
| Mode selected | `#0B6E4F` | `dogan_mode_selected` |
| Mode unselected | `#15202B` | `dogan_mode_unselected` |

`color-scheme: dark`. Camera/media surfaces stay black when present.

## Typography

- **UI:** Bricolage Grotesque (400 / 600 / 700) via Material type scale
- **Readings / status:** IBM Plex Mono Medium for ping, API, LiveKit lines and logs
- Roles: Title Large for Dogan mark; Title Medium for bay labels; Body Medium for settings; Label Medium for service lane

## Shape & space

- Radius: 8dp bays/panels, 6dp service controls (no pill clusters except signal lamp)
- Hub padding: 16–20dp; bay min height 96dp; gear target 56dp (≥48dp)
- Atmosphere: solid tonal surfaces; no diagonal stall texture on phone (web-only)

## Components

- **Bay rows:** tonal surface + 1dp outline; armed = primary fill; gear is outline tonal
- **Signal chip:** mono label + amber lamp when connected-degraded/attention; teal when healthy; danger tint when disconnected
- **Service lane:** three equal outline buttons (Settings / Logs / Exit)
- **Settings:** Outlined TextInput; primary filled Connect when disconnected action needed; snackbars for transient feedback
- **Diagnostics cards:** surface-variant panels (never light `#F5F5F5` on dark)

## Motion

1. Mode bay color crossfade ~180ms on arm/disarm
2. Signal lamp alpha pulse while connecting (honor Remove animations → static)
3. Shared-axis feel on settings open via system default activity transition

## Raises applied

- From split-flap challenger: connection status updates cascade through signal chip text + lamp, not whole-chrome repaints

## Do not

- Dark-default generic green admin skins, Inter as brand face, purple accents, glow, cream+serif+terracotta
- Emoji icons; painting entire Connectivity chrome red/green as the only status
- Hero marketing clutter on Operate screens
- Bottom-nav cargo-cult from iOS; this hub is an arming console
