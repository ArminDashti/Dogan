# Design System Master File

> Aligned with `/DESIGN.md` (Lot Signal). Page overrides live in `design-system/pages/` when needed.

**Project:** Dogan Control Center  
**Category:** Field device operations  
**Mode:** Operate  

## Color Palette

| Role | Hex | CSS Variable |
|------|-----|--------------|
| Primary | `#0B6E4F` | `--accent` |
| On Primary | `#FFFFFF` | `--primary-fg` |
| Signal | `#C9780E` | `--signal` |
| Background | `#E8EDF1` | `--bg` |
| Surface | `#F7FAFC` | `--surface` |
| Raised | `#FFFFFF` | `--surface-raised` |
| Foreground | `#15202B` | `--text` |
| Muted | `#5A6B7A` | `--muted` |
| Border | `#C5D0DA` | `--border` |
| Destructive | `#B42318` | `--danger` |
| Live | `#0B6E4F` | `--live` |

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
