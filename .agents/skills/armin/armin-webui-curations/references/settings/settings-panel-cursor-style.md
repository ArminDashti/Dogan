---
name: settings-panel-cursor-style
description: >-
  Cursor-style desktop preferences layout: categorized section headings, grouped
  cards, label + helper rows, right-aligned toggles/dropdowns/action buttons. Use
  when building or restyling Settings, Analysis options, or any configuration panel.
author: "Armin Dashti"
---

# Settings panel (Cursor-style)

## When

- User asks for **Cursor-like settings**, **desktop preferences**, or **configuration panel** UX.
- New **Settings**, **Appearance**, **Startup**, **Notifications**, **Privacy**, or wizard **run options** views.
- Restyle existing preference pages (explicit redesign only per skill scope).

## Layout hierarchy

1. **Page shell** — optional sidebar tab list (categories) + scrollable content column.
2. **`SettingsPanel`** — max-width column (~48rem / `max-w-3xl`), page title (`h1`), optional **lead** line (muted, 13px, directly under title), vertical gap between section groups.
3. **`SettingsSection`** — **section heading outside the card** (`h2`, ~13px semibold), then one **grouped card**.
4. **Card** — rounded container, subtle border, elevated surface (`--hx-settings-card` or Yoda `var(--surface)`), rows separated by bottom borders (not full box per row).
5. **Row** — primary label + helper text **stacked on the left**; **one control flush right** (or stacked full-width for large inputs).

```
Section title (outside)
┌─────────────────────────────────────────────┐
│  Label                           [ control ] │
│  Helper text (xs, muted)                     │
├─────────────────────────────────────────────┤
│  Label                           [ control ] │
│  Helper                                      │
└─────────────────────────────────────────────┘
```

## Typography contrast

| Element | Treatment |
|--------|-----------|
| Page title | `h1`, semibold, largest on page |
| Page lead | Optional; muted; 13px; explains scope of the page |
| Section title | `h2`, ~13px, semibold, **above** card |
| Row label | ~13px, medium weight |
| Row helper | `text-xs`, muted, directly under label |
| Controls | Same size as label row; align **end** in horizontal rows |

## Controls (right-aligned unless stacked)

| Control | Use | Notes |
|--------|-----|--------|
| **`SettingsSwitch`** | Boolean prefs (notifications, startup, feature flags) | Track off `#39393d`, on `#34c759` (Yoda switch tokens); white thumb. Helix: `role="switch"` button. Yoda Admin: styled `input[type='checkbox']` in the row control column (`aria-label` on input). |
| **`SettingsSelect`** | Mode / enum (theme, language, window restore) | Rounded select + **ChevronDown**; `appearance-none`, padding for chevron. |
| **`SettingsActionButton`** | Configure, Open, Check for updates | Compact bordered pill; optional leading icon; trailing **ChevronRight** or **ExternalLink** when `external`. |
| **`SettingsOutlineButton`** | Secondary actions in row (Clear, Reset) | Same shell as action button, no trailing chevron by default. |
| **Stacked inputs** | Textareas, file upload, multi-field blocks | Use **`SettingsRowStacked`**; keep label/helper pattern. |
| **Save bar** | Form submit | **`SettingsFooterActions`** — right-aligned, top border inside card. |

Do **not** use plain unstyled checkboxes for on/off preferences when a switch fits; use switches for instant toggles. Avoid legacy **`toggle-list`** (label wraps control on one line)—use **`SettingsRow`** instead.

## Theme tokens

Map to project CSS variables; never hardcode switch hex in components when tokens exist.

**Helix (`helix-webui/src/index.css`):**

- `--hx-settings-card` — card fill (dark `#252526`, light `#ffffff`)
- `--hx-switch-off` / `--hx-switch-on` — align with Yoda Admin switch colors (`#39393d` / `#34c759`)

**Yoda Admin (`Yoda/webui/src/style.css`):**

- Cards: `var(--surface)`, `border: 1px solid var(--border)`, `border-radius: 8px`
- Settings layout classes: `.settings-panel`, `.settings-section`, `.settings-card`, `.settings-row`, `.settings-row__title`, `.settings-row__desc`, `.settings-row__control`
- Switches: global admin rule on `input[type='checkbox']` uses `--switch-off` / `--switch-on` (admin theme: off `#39393d`, on `#34c759`)

**Cursor theme:** prefer Cursor tokens for chrome when user asked for Cursor look; settings **structure** stays the same.

**Wizard / guided flows:** same **row/card** primitives inside **`SettingsPanel`**; add progress + stepper per `./multi-step-wizard-settings-width.md`. Wizard chrome uses **theme neutrals + primary button tokens**, not accent gradients.

## Canonical implementation

| Stack | Helpers | Example page | CSS |
|-------|---------|--------------|-----|
| **React (Helix)** | `helix-webui/src/components/SettingsUi.jsx` | `SettingsPage.jsx`, `AnalysisSection.jsx` | Tailwind + `--hx-*` |
| **Vanilla DOM (Yoda Admin)** | `Yoda/webui/src/panel/settings-ui.ts` | `settings.ts` → `/admin/settings` | `Yoda/webui/src/style.css` (`.settings-*`) |

**Yoda helper API (mirror React names):**

- `settingsPanel(title, …children)` — wraps `h1.settings-panel__title`
- `settingsPanelLead(text)` — muted intro under title
- `settingsSection(sectionTitle, …rows)` — `h2` + `.settings-card`
- `settingsRow({ title, description, control, last? })` — label block left, control right; `last` omits bottom border on final row
- `settingsSwitch(checked, onChange, label)` — checkbox with switch styling + `aria-label`

**Anti-patterns (Yoda):** `panel-block` + `h2` inside card only + `ul.toggle-list` for service toggles—migrate to `settings-ui.ts` on redesign.

## Example (React)

```jsx
<SettingsPanel title={t("settings.tabGeneral")}>
  <SettingsSection title={t("settings.sectionAppearance")}>
    <SettingsRow title={t("layout.themeAria")} description={t("settings.themeDesc")} last>
      <SettingsSelect id="theme" value={theme} onChange={(e) => setTheme(e.target.value)}>
        <option value="dark">{t("layout.theme.dark")}</option>
      </SettingsSelect>
    </SettingsRow>
  </SettingsSection>
  <SettingsSection title={t("settings.sectionStartup")}>
    <SettingsRow title={…} description={…} last={false}>
      <SettingsSwitch checked={openAtLogin} onChange={setOpenAtLogin} label={…} />
    </SettingsRow>
    <SettingsRow title={…} description={…} last>
      <SettingsActionButton onClick={openAdvanced}>Configure</SettingsActionButton>
    </SettingsRow>
  </SettingsSection>
</SettingsPanel>
```

## Example (Yoda vanilla TS)

```typescript
content.replaceChildren(
  settingsPanel(
    'Settings',
    settingsPanelLead('Control which gateway services accept traffic. Changes apply immediately.'),
    settingsSection(
      'Services',
      settingsRow({
        title: 'LLM gateway',
        description: 'When disabled, the gateway refuses LLM requests with HTTP 403.',
        last: false,
        control: settingsSwitch(enabled, onToggle, 'Enable LLM gateway'),
      }),
      settingsRow({
        title: 'Unidentified clients',
        description: 'Traffic from clients that do not send a recognized app identifier.',
        last: true,
        control: settingsSwitch(enabled, onToggle, 'Enable unidentified clients'),
      }),
    ),
  ),
);
```

## Default settings tabs (product)

Minimum tabs when scaffolding a new app (extend per product):

1. **Appearance**
2. **Update** (version check + **Install app** / PWA — `./references/pwa/full-progressive-web-app.md`)
3. **Feedback**
4. **About Me**

Helix adds Provider, Data source, Connectors, Logs, Status, Help — same **row/card** pattern inside each tab. Yoda per-app Settings tabs (Chat, Hawking, …) should adopt **`settings-ui.ts`** when restyled.

## Checklist

- [ ] Section titles sit **above** cards, not inside card headers only
- [ ] Page has **`h1`** inside the width-constrained panel column (not a duplicate top bar title elsewhere)
- [ ] Non-trivial pages include a **lead** line or first-section helper explaining scope
- [ ] Every non-trivial row has **helper text** under the label
- [ ] Toggles are **switches**, right-aligned in **`SettingsRow`**
- [ ] Dropdowns show a **chevron**; action buttons are compact with icon hint
- [ ] Large fields use **stacked** rows, not cramped horizontal layout
- [ ] Switch colors use **tokens** (`--hx-switch-on/off` or Yoda `--switch-on/off`)
- [ ] Multi-step wizards: see **`multi-step-wizard-settings-width.md`** (width, flat theme, stepper, tiles)
