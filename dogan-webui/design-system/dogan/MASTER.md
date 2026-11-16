# Design System Master File

> Aligned with `/DESIGN.md` (Cursor Dark). Page overrides live in `design-system/pages/` when needed.

**Project:** Dogan Control Center  
**Category:** Field device operations  
**Mode:** Operate  

## Color Palette

| Role | Hex | CSS Variable |
|------|-----|--------------|
| Primary | `#81A1C1` | `--accent` |
| On Primary | `#191C22` | `--primary-fg` |
| Signal | `#C9780E` | `--signal` |
| Background | `#181818` | `--bg` |
| Surface | `#141414` | `--surface` |
| Raised | `#1C1C1C` | `--surface-raised` |
| Foreground | `#F0F0F0` | `--text` |
| Muted | `#F0F0F0BD` | `--muted` |
| Border | `#2E2E2E` | `--border` |
| Destructive | `#E34671` | `--danger` |
| Live | `#3FA266` | `--live` |

## Typography

- **UI:** Bricolage Grotesque
- **Mono:** IBM Plex Mono (telemetry, codes)
- **Mood:** municipal, instrument-clear, calm, field-ready

## Spacing

| Token | Value |
|-------|-------|
| `--space-xs` | `4px` |
| `--space-sm` | `8px` |
| `--space-md` | `16px` |
| `--space-lg` | `24px` |
| `--space-xl` | `32px` |

## Style

Lot Signal — cool concrete lot board + barrier amber + bay-open teal. High legibility, restrained chrome, no decorative dashboard clutter.

## Anti-Patterns

- Emojis as icons
- Missing `cursor:pointer` on clickable controls
- Layout-shifting hovers
- Low-contrast text
- Instant state changes (use 150–300ms)
- Invisible focus states
- Generic dark Inter + blue admin look
