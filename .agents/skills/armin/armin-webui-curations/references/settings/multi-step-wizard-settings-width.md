---
name: multi-step-wizard-settings-width
description: >-
  Multi-step wizard inside the same max-width column as Settings: flat theme
  tokens, progress + stepper chrome, selectable tiles, SettingsSection steps,
  themed primary actions. Use when building wizards, guided flows, or step forms
  that should feel like Settings—not a full-width card or marketing layout.
author: "Armin Dashti"
---

# Multi-step wizard (Settings width)

## When

- User wants a **wizard**, **guided flow**, or **step-by-step form** that matches **Settings → General** size and tone.
- Restyling an existing wizard to be **minimal, modern, and on-theme** (explicit redesign per skill scope).
- Product examples: Helix **Analysis** run wizard (`AnalysisPage.jsx`).

## Non-negotiables

1. **Same column width as Settings** — wrap in **`SettingsPanel`** (`max-w-3xl`, centered in the main scroll area).
2. **Same card grammar** — step bodies use **`SettingsSection`** / **`SettingsRow`** / **`SettingsRowStacked`** (or thin wrappers like `AnalysisSection.jsx` that delegate to `SettingsUi.jsx`).
3. **Theme tokens only for chrome** — use project neutrals and primary button tokens; **no** decorative gradients, glow shadows, or brand accent (e.g. Helix moss green) on wizard chrome unless the user explicitly asks for accent color.
4. **Flat surfaces** — 1px borders, solid fills (`--hx-settings-card`, `--hx-fog`, `--hx-line`, `--hx-ink`); no gradient progress bars or gradient footer panels.

## Layout stack (top → bottom)

```
SettingsPanel (title = flow name)
  ├─ Intro row (optional icon badge + muted subtitle + "Step X of Y")
  ├─ Progress bar (thin, solid fill)
  ├─ Stepper (segment grid inside one settings-card shell)
  ├─ Step content (hx-rise on step change, key={step})
  └─ Footer nav card (Back outline + Next/Run primary)
```

## Chrome details

| Piece | Spec |
|--------|------|
| **Progress** | ~3px height, track `--hx-line` (or `var(--border)`), fill **`--hx-ink`** (or `var(--text)`); width = `(step + 1) / total`. |
| **Stepper** | Grid inside rounded card (`--hx-settings-card`); **current** step: `--hx-fog` background + `--hx-line` border; **current** index dot: `--hx-ink` on `--hx-paper`; **done** dot: check icon on `--hx-fog`; **todo** muted, not clickable. Allow click-back to completed steps only. |
| **Step label** | Short label under index (truncate on narrow screens); 2×2 grid on mobile, 4 columns on `sm+`. |
| **Intro badge** | Optional lucide icon in `border-line` + `bg-fog`; icon `text-muted`—not accent-colored. |
| **Step counter** | `text-xs` / `11px`, uppercase tracking, **`text-muted`** (not accent). |
| **Motion** | **`hx-rise`** on intro + step body; respect `prefers-reduced-motion`. |

Helix CSS classes (optional copy): `.hx-wizard-progress`, `.hx-wizard-stepper`, `.hx-wizard-step-btn`, `.hx-wizard-panel`, `.hx-wizard-prompt` in `helix-webui/src/index.css`.

## Step content patterns

| Step type | Pattern |
|-----------|---------|
| **Single large field** (question) | One **`SettingsSection`** + **`SettingsRowStacked`**; textarea class with flat `--hx-fog` fill; focus ring from **`--hx-ink`** (subtle), not accent. |
| **Multi-select outputs** | **Selectable tiles** (grid `sm:grid-cols-3`): icon chip, title, helper, **`SettingsSwitch`** on the right; tile click toggles; **stop propagation** on switch so one click ≠ double toggle. Selected tile: **`border-line` + `bg-fog`**, not green tint. |
| **Configure enabled outputs** | One **`SettingsSection`** per output; optional neutral icon chip (`border-line`, `bg-fog/50`, `text-muted`) in section lead. |
| **Chart / enum picks** | Icon buttons in a wrap row; pressed = **`border-line` + `bg-fog` + light ink ring**—no accent fill. |
| **Review + run options** | Same as Settings: speed/language rows, switches for booleans; review as **`SettingsRow`** list (label left, value right). |

## Footer actions

- Container: flat **`--hx-settings-card`**, `border-line`, same radius as settings cards (Helix: `.hx-wizard-panel`).
- **Back:** **`SettingsOutlineButton`** + leading chevron; disabled on step 0.
- **Next / Run:** **`--hx-btn-primary`** background, **`--hx-btn-primary-ink`** text, rounded-lg, **`hx-press`**; optional trailing chevron or play icon. No colored drop shadows.
- Do **not** use a sticky full-bleed bar that breaks out of `max-w-3xl`.

## Theme mapping (Helix)

| Role | Token |
|------|--------|
| Card / stepper shell | `--hx-settings-card` |
| Muted copy | `--hx-muted` |
| Borders | `--hx-line` |
| Selected / emphasis surfaces | `--hx-fog` |
| Progress fill, current dot, focus | `--hx-ink` |
| Primary CTA | `--hx-btn-primary` / `--hx-btn-primary-ink` |
| Switches | `--hx-switch-off` / `--hx-switch-on` (same as Settings) |

On Yoda Admin or Cursor-themed apps, map the same **roles** to that theme’s `--surface`, `--text`, `--border`, `--primary`—still **no gradients** on wizard chrome.

## Avoid

- Full-width **`hx-card`** wrapping the entire wizard (reads as a different product surface).
- Pill steppers with heavy borders per step (noisy vs settings).
- Moss / teal / blue **gradient** progress or footer panels when user asked for **theme** alignment.
- **`bg-moss`** (or `--accent`) on wizard primary buttons when `--hx-btn-primary` (or theme primary) is defined.
- Plain slash-separated text-only steps with no progress affordance (too bare after polish pass).

## Canonical implementation (Helix)

| Piece | Path |
|--------|------|
| Page | `helix-webui/src/pages/AnalysisPage.jsx` |
| Stepper | `helix-webui/src/components/AnalysisWizardStepper.jsx` |
| Section wrapper | `helix-webui/src/components/AnalysisSection.jsx` |
| Settings primitives | `helix-webui/src/components/SettingsUi.jsx` |
| Wizard CSS | `helix-webui/src/index.css` (`.hx-wizard-*`) |

## Checklist

- [ ] Wizard lives in **`SettingsPanel`** (`max-w-3xl`)
- [ ] Progress + stepper use **flat** theme tokens (no gradients)
- [ ] Primary actions use **theme primary button** tokens, not accent green
- [ ] Output toggles use **tiles + SettingsSwitch**, not orphan checkboxes
- [ ] Step change uses **`hx-rise`** (or equivalent) without layout shift
- [ ] Footer nav inside same column, not viewport-wide sticky strip
- [ ] Switches still use **`--hx-switch-on/off`** like Settings
