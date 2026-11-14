---
name: sidebar-nav-selection
description: >-
  Yoda Admin sidebar current-route and hover styling. Use when building or
  restyling vertical nav in dashboard / control-panel WebUIs (Pont, Yoda, Helix family).
author: "Armin Dashti"
---

# Sidebar nav selection (Yoda Admin)

## Rule

**Current route and hover in the sidebar are neutral selection, not accent links.**

- **Inactive items:** `color: var(--muted)` (or project `--text-dim` aliased to `--muted`).
- **Hover and `.active`:** `color: var(--text)` and `background: var(--bg)`.
- **`--accent` (`#2563eb`):** primary buttons, destructive-adjacent actions, in-content links, focus rings—not sidebar “you are here”.

Do **not** use `var(--link)`, `var(--accent)`, or `color-mix(..., var(--accent), ...)` for sidebar nav active/hover. That reads as a hyperlink or CTA and drifts from Yoda admin.

## Canonical CSS (Yoda)

From `Yoda/webui/src/style.css`:

```css
.nav a {
  color: var(--muted);
}

.nav a.active,
.nav a:hover {
  color: var(--text);
  background: var(--bg);
}
```

Sidebar chrome stays `var(--surface)` (`#252525`); selection wash is canvas `var(--bg)` (`#1e1e1e`).

## App aliases (Pont pattern)

When bridging token names, keep nav on semantic text/surface tokens only:

```css
.nav-link {
  color: var(--text-dim); /* maps to --muted */
}

.nav-link:hover,
.nav-link.active {
  background: var(--bg);
  color: var(--text);
}
```

Reserve `--link: var(--accent)` for **main content** anchors, not `.nav-link.active`.

## Checklist

- [ ] Active sidebar item is light gray on darker gray, not blue text on blue tint
- [ ] Primary buttons still use `--accent` / `--primary`
- [ ] Collapsed sidebar uses the same active/hover tokens as expanded
