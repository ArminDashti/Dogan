# Dogan Web — Design System

**World:** Cursor Dark  
**Mode:** Operate  
**Anti-reference:** Light Lot Signal teal console; generic Dark+ blue admin (`#1e1e1e` / `#2563eb`)

## Intent

A dark ops console using Cursor Dark editor chrome: near-black surfaces, soft Nord-blue primary, barrier amber for live attention. Precision and scanability over expression. Brand lives in the mark, type, and signal color — not purple glow or dashboard clutter.

## Color

| Role | Hex | Token |
|------|-----|-------|
| Background | `#181818` | `--bg` |
| Surface | `#141414` | `--surface` |
| Raised | `#1C1C1C` | `--surface-raised` |
| Border | `#2E2E2E` | `--border` |
| Text | `#F0F0F0` | `--text` |
| Muted | `#F0F0F0BD` | `--muted` |
| Primary | `#81A1C1` | `--accent` |
| On primary | `#191C22` | `--primary-fg` |
| Accent soft | `#81A1C126` | `--accent-soft` |
| Signal / live | `#C9780E` | `--signal` |
| Live (status) | `#3FA266` | `--live` |
| Danger | `#E34671` | `--danger` |
| Danger bg | `#3A1524` | `--danger-bg` |
| Switch off | `#39393D` | `--switch-off` |
| Switch on | `#34C759` | `--switch-on` |

`color-scheme: dark`. `data-theme="cursor-dark"`. Camera viewport stays black (media surface).

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

- **Panels (`.yoda-card`):** raised dark surface, 1px border, no heavy shadow
- **Buttons:** primary Cursor blue fill + dark label; outline border; 180ms transitions
- **Nav:** muted links; active = left signal bar + soft accent wash
- **Status pills:** online = accent wash; offline = danger wash; unknown = muted
- **Inputs:** raised fill, focus ring accent ~22%

## Motion

1. Page content soft fade-in (`reveal`)
2. Live status amber pulse when streaming
3. Sidebar width + nav color transitions (existing, retimed)

Respect `prefers-reduced-motion`.

## Do not

- Light Lot Signal default; Inter; purple accents; glow; cream+serif+terracotta; Dark+ `#2563eb` chrome
- Emoji icons; layout-shifting scale hovers; invisible focus rings
- Hero marketing clutter on Operate screens
