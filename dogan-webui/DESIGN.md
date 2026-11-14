# Dogan Web — Design System

**World:** Lot Signal  
**Mode:** Operate  
**Anti-reference:** Previous dark Inter + generic blue admin chrome (`#1e1e1e` / `#2563eb`)

## Intent

A light field-ops console that feels like a municipal lot board and barrier arm: cool concrete, bay-open teal, barrier amber for live attention. Precision and scanability over expression. Brand lives in the mark, type, and signal color — not purple glow or dashboard clutter.

## Color

| Role | Hex | Token |
|------|-----|-------|
| Background | `#E8EDF1` | `--bg` |
| Surface | `#F7FAFC` | `--surface` |
| Raised | `#FFFFFF` | `--surface-raised` |
| Border | `#C5D0DA` | `--border` |
| Text | `#15202B` | `--text` |
| Muted | `#5A6B7A` | `--muted` |
| Primary / bay | `#0B6E4F` | `--accent` |
| On primary | `#FFFFFF` | `--primary-fg` |
| Accent soft | `#D4EFE4` | `--accent-soft` |
| Signal / live | `#C9780E` | `--signal` |
| Live (status) | `#0B6E4F` | `--live` |
| Danger | `#B42318` | `--danger` |
| Danger bg | `#FCEBEA` | `--danger-bg` |
| Switch off | `#C5D0DA` | `--switch-off` |
| Switch on | `#0B6E4F` | `--switch-on` |

`color-scheme: light`. Camera viewport stays black (media surface).

## Typography

- **UI:** Bricolage Grotesque (400–700)
- **Readings:** IBM Plex Mono (500–600)
- Body 16px / 1.5; page titles ~1.75rem semibold; telemetry values mono 1.5–2rem

## Shape & space

- Radius: 6px controls, 8px panels (no pill clusters except status/switch)
- Sidebar: 15.5rem / collapsed 3.25rem
- Page padding: 1.5–2rem; denser instrument cells than marketing cards
- Atmosphere: faint stall-line diagonal on `--bg` (CSS only)

## Components

- **Panels (`.yoda-card`):** raised white, 1px border, no heavy shadow
- **Buttons:** primary teal fill; outline border; 180ms transitions
- **Nav:** muted links; active = left signal bar + soft teal wash
- **Status pills:** online = teal wash; offline = danger wash; unknown = muted
- **Inputs:** white fill, focus ring teal 25%

## Motion

1. Page content soft fade-in (`reveal`)
2. Live status amber pulse when streaming
3. Sidebar width + nav color transitions (existing, retimed)

Respect `prefers-reduced-motion`.

## Do not

- Dark-default admin skins, Inter, purple accents, glow, cream+serif+terracotta
- Emoji icons; layout-shifting scale hovers; invisible focus rings
- Hero marketing clutter on Operate screens
