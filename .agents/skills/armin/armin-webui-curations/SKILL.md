---
name: armin-webui-curations
description: UI/UX design standards and component curations for Armin Dashti's WebUI. Use when creating new WebUI components, layouts, or apps from scratch, or when the user explicitly requests to redesign/restyle an existing UI. Do not enforce for minor edits or routine bug fixes.
author: "Armin Dashti"
uuid: 85159c7b-e318-458f-928d-08465bd037a9
---

# Armin WebUI Curations & Design Guidelines

This skill defines the user interface rules, typography, themes, and component specifications to ensure visual consistency across all WebUI projects.

---

## 1. Skill Activation & Scope

- **When to Apply:**
  - **New Creation (Default):** Always apply these guidelines when generating, scaffolding, or creating new WebUI layouts, pages, components, or applications from scratch.
  - **Explicit Redesign:** Apply these rules to existing code **only** if the user explicitly requests to redesign, restyle, or align a part of the interface with this skill.
- **When NOT to Apply:**
  - **Routine Editing & Maintenance:** Do **not** enforce these rules during standard edits, bug fixes, logic updates, or minor tweaks on existing components. Maintain the existing codebase style unless a redesign is requested.

---

## 2. Theme & Typography

- **Default Theme:** Always use **Dark+** as the default dark theme.
- **Yoda Admin (control panel) — preferred for dashboards:** Flat editor-style dark from the Yoda control panel (`http://127.0.0.1:43090/admin/overview` and siblings). Use when the user likes the overview look, asks for Yoda panel colors, or builds an admin tool in the same family as `yoda-webui`.
  - Tokens: copy `./references/themes/yoda-admin-theme.css`. Set `document.documentElement.dataset.theme = 'admin'` (or class `yoda-admin`) and use `var(--bg)`, `var(--surface)`, `var(--text)`, `var(--muted)`, `var(--border)`, `var(--accent)`; never hardcode hex in components.
  - Canvas `#1e1e1e`, chrome (sidebar, cards) `#252525`, text `#e0e0e0`, muted `#9d9d9d`, borders `#333333`, primary/accent `#2563eb`, danger `#b42318`, live/success `#15803d`, switches off `#39393d` / on `#34c759`.
  - Cards and tables: `background: var(--surface)`, `border: 1px solid var(--border)`, `border-radius: 8px`; grid headers use `background: var(--bg)`.
  - **`--accent` scope:** use for primary buttons and in-content links/focus—not for sidebar current-route styling (see §3 Sidebar Menu).
  - Browser chrome: `<meta name="theme-color" content="#252525">`.
  - Canonical implementation: `Yoda/webui/src/style.css` (`:root[data-theme='admin']`) and `admin-shell.css`.
- **Cursor Theme (available):** The colors of Cursor's built-in **Cursor Dark** and **Cursor Light** editor themes, mapped to web tokens. Use it when the user asks for the Cursor look.
  - Tokens: copy `./references/themes/cursor-theme.css` as-is. Dark is `:root` / `.dark`, light is `.light`. Use `var(--bg)`, `var(--fg)`, `var(--primary)`, etc.; never hardcode the hex values in components.
  - Dark: content `#181818`, sidebar/header/panels `#141414`, text `#F0F0F0`, borders `#F0F0F013`, primary button `#81A1C1` with text `#191C22`.
  - Light: content `#FCFCFC`, sidebar/header/panels `#F3F3F3`, text `#141414`, primary button `#2778C1` with text `#FCFCFC`.
  - Status: success `--success`, warning `--warning`, error `--danger` (both modes defined).
  - Browser chrome: `<meta name="theme-color">` `#141414` for dark, `#F3F3F3` for light.
- **Typography:**
  - **English-only WebUI:** Use **Inter Sans**.
  - **Bilingual (English / Persian) WebUI:** Use **Vazir Matn**.
- **Font Color:** Always use a bright white font color for optimal contrast and readability.
- **Accent vs primary (Helix and similar apps):**
  - **Settings switches** use green on-tracks (`--hx-switch-on`, Yoda `#34c759`)—that is control chrome, not page accent.
  - **Wizard / panel chrome** (progress, stepper, tiles, footer): use **neutral theme tokens** and **`--hx-btn-primary`** for Next/Save/Run—not secondary brand colors (e.g. `--hx-moss`) unless the user asks for accent styling.
  - **No gradient** progress bars, footer panels, or hero badges on theme-aligned wizards; flat borders and solid fills only.
- **Icons:**
  - Ensure all icons are fully compatible with both dark and light modes.
  - Provide dedicated icon variants for dark and light themes respectively.

---

## 3. Layout & Navigation

- **General Layout:** Follow the predefined layout structures found in `./curations/layout/`.
- **Header:** Select and implement an appropriate header configuration based on samples in `./curations/header/`.
- **Sidebar Menu:**
  - Always include a **collapse/expand** toggle button.
  - Reference implementation: `./curations/side-bar-menu/`.
  - **Current route + hover (Yoda Admin):** inactive `var(--muted)`; hover and `.active` use `color: var(--text)` and `background: var(--bg)` on `var(--surface)` sidebar—never `var(--accent)` or link-colored active text. Spec: `./references/sidebar-menu/sidebar-nav-selection.md` (canonical: `Yoda/webui/src/style.css` `.nav a.active`).
  - **Signed-in user footer:** circular initials avatar, truncated display name, settings gear on the right; avatar/name → profile, gear → settings; no duplicate Settings nav item. Spec: `./references/sidebar-menu/sidebar-user-profile.md` (Helix reference: `helix-webui/src/components/Layout.jsx`).
- **Footer:**
  - **Placement:** Anchored at the end of the scrollable page area with a compact (low) height.
  - **Left Side:** Include a GitHub link to [https://github.com/armindashti](https://github.com/armindashti).
  - **Copyright Text:** `Created by Armin Dashti`.
  - Reference implementation: `./curations/footer/`.

---

## 4. UI Components

- **Buttons:**
  - Always design buttons with **rounded corners** and accompanying **icons**.
  - Reference implementation: `./curations/buttons/`.
- **Textboxes:**
  - Dynamically scale and size textboxes according to their intended input purpose.
  - Reference implementation: `./curations/textbox/`.
- **Dropdowns:**
  - Follow the sample components in `./curations/dropdown/`.
- **Settings / preferences panel (Cursor-style):**
  - **Layout:** categorized **section headings above** stacked **card** containers; consistent vertical rhythm between sections.
  - **Page column:** centered **`SettingsPanel`** (~48rem / `max-w-3xl`), **`h1` page title**, optional **lead** paragraph (`settingsPanelLead` / muted one-liner under title)—then section groups.
  - **Rows:** primary label on top, **helper text** (`text-xs`, muted) directly underneath; strong contrast vs section titles and controls.
  - **Controls (right-aligned in horizontal rows):** sliding **`SettingsSwitch`** (off `#39393d`, on `#34c759`), **`SettingsSelect`** with chevron, compact **`SettingsActionButton`** / **`SettingsOutlineButton`** (Configure, Open, external link).
  - **Stacked rows:** full-width inputs/textareas under the label block via **`SettingsRowStacked`**; form saves in **`SettingsFooterActions`** inside the card.
  - **Tokens:** card surface and switch tracks via CSS variables (Helix: `--hx-settings-card`, `--hx-switch-off`, `--hx-switch-on`); Yoda Admin: `var(--surface)` cards + global `input[type='checkbox']` switch styling (`--switch-off` / `--switch-on` in `style.css`).
  - Spec: `./references/settings/settings-panel-cursor-style.md`.
  - **Canonical (React):** `helix-webui/src/components/SettingsUi.jsx`, `SettingsPage.jsx`.
  - **Canonical (Yoda Admin, vanilla DOM):** `Yoda/webui/src/panel/settings-ui.ts`, page `settings.ts` (`/admin/settings`), styles `.settings-*` in `Yoda/webui/src/style.css`. Prefer these helpers over ad-hoc `panel-block` + `toggle-list` for new or redesigned panel settings.
- **Multi-step wizard (Settings width):**
  - **Same column** as preferences: **`SettingsPanel`** (`max-w-3xl`); do not wrap the whole flow in a full-width **`hx-card`**.
  - **Chrome:** thin **progress bar** (solid theme ink on line track), **segment stepper** inside one settings-card shell, optional intro badge + **Step X of Y** (muted, not accent-colored).
  - **Steps:** reuse **`SettingsSection`** / rows; large questions = stacked textarea; multi-output choice = **selectable tiles** + **`SettingsSwitch`** (stop propagation on the switch).
  - **Theme discipline:** wizard surfaces use **neutral theme tokens** (`--hx-ink`, `--hx-line`, `--hx-fog`, `--hx-settings-card`, **`--hx-btn-primary`** for Next/Run)—**no** gradient fills, glow shadows, or brand accent (e.g. moss) on wizard chrome unless the user explicitly requests accent color.
  - **Motion:** **`hx-rise`** on step change; honor reduced motion.
  - Spec: `./references/settings/multi-step-wizard-settings-width.md`. Canonical code: `helix-webui/src/pages/AnalysisPage.jsx`, `AnalysisWizardStepper.jsx`, `AnalysisSection.jsx`, `.hx-wizard-*` in `helix-webui/src/index.css`.

---

## 5. Modals & Animations

- **Dialogs & Overlays:** All modal dialogs (informational messages, warnings, errors, prompts) must include smooth **fade-in** (open) and **fade-out** (close) transitions.

---

## 6. Views, Settings & Templates

- **Settings shell:** Sidebar or tab list for categories (General, Provider, …) plus a scrollable content column. Content uses **`SettingsPanel`** + one or more **`SettingsSection`** blocks per tab—not a flat form without grouping.
- **Settings layout (required pattern):** Follow **§4 Settings / preferences panel** and `./references/settings/settings-panel-cursor-style.md` (Cursor desktop preferences: cards, label+helper rows, trailing switches/selects/buttons). **Yoda control panel** global settings: `http://127.0.0.1:43090/admin/settings` — verified reference for Services toggles + lead copy.
- **Default tabs** when scaffolding a new app (add product-specific tabs as needed):
  1. **Appearance**
  2. **Update** (app updates + **Install app** / PWA section)
  3. **Feedback**
  4. **About Me**
- **Full PWA (required for new WebUIs):** manifest, scoped service worker, offline SPA shell, install + update UX in Settings, nginx no-cache rules — spec `./references/pwa/full-progressive-web-app.md`. Canonical: `helix-webui/src/lib/pwa.js`, `public/sw.js`, `SettingsPage.jsx` `PwaSection`.
- **Example section groups** inside a tab (name to match product): **General**, **Startup**, **Notifications**, **Privacy** — each group is one **`SettingsSection`** title + card.
- **About Me & Feedback:** Use the templates and guidelines provided in `./curations/about-me/`.
- **Multi-step wizards:** Same **`SettingsPanel`** width and card rows as preferences; add progress + stepper chrome per `./references/settings/multi-step-wizard-settings-width.md` (Helix Analysis wizard is the reference implementation).

---

## 7. Directory References Summary

| Component | Path |
| :--- | :--- |
| **Layouts** | `./curations/layout/` |
| **Header** | `./curations/header/` |
| **Sidebar Menu** | `./curations/side-bar-menu/` |
| **Sidebar user profile row** | `./references/sidebar-menu/sidebar-user-profile.md` |
| **Sidebar nav selection (active/hover)** | `./references/sidebar-menu/sidebar-nav-selection.md` |
| **Footer** | `./curations/footer/` |
| **Buttons** | `./curations/buttons/` |
| **Textboxes** | `./curations/textbox/` |
| **Dropdowns** | `./curations/dropdown/` |
| **Settings panel (Cursor-style)** | `./references/settings/settings-panel-cursor-style.md` |
| **Yoda settings UI (vanilla TS + CSS)** | `Yoda/webui/src/panel/settings-ui.ts`, `Yoda/webui/src/style.css` (`.settings-*`) |
| **Multi-step wizard (Settings width)** | `./references/settings/multi-step-wizard-settings-width.md` |
| **Full PWA (installable WebUI)** | `./references/pwa/full-progressive-web-app.md` |
| **About Me & Feedback** | `./curations/about-me/` |
| **Cursor Theme Tokens** | `./references/themes/cursor-theme.css` |
| **Yoda Admin Theme Tokens** | `./references/themes/yoda-admin-theme.css` |