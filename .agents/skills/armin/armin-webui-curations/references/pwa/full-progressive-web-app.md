---
name: full-progressive-web-app
description: >-
  Installable PWA baseline for Armin WebUIs: manifest, scoped service worker,
  offline app shell, update UX, and Settings install section. Use when scaffolding
  a new WebUI or when the user requires full PWA support.
author: "Armin Dashti"
---

# Full PWA (installable WebUI)

## When

- User asks for **full PWA**, **installable app**, or **offline shell**.
- Scaffolding any new browser WebUI in this skill’s scope.
- Auditing Helix or sibling apps for Lighthouse installability.

## Non-negotiables

1. **Web app manifest** — `name`, `short_name`, `start_url`, `scope`, `display: standalone`, icons **192** and **512** (`any` + `maskable`), `theme_color`, `background_color`, `prefer_related_applications: false`.
2. **Service worker** — registered from the app **base path** with matching **scope**; `updateViaCache: "none"`; production build injects precache list + unique cache id (stable id in dev).
3. **Offline** — `GET` navigations: network-first, fallback to precached **index.html** (SPA shell); never cache `/api` or auth responses.
4. **Updates** — detect waiting worker; user-triggered `SKIP_WAITING` + single reload (no dev reload loops from changing cache names).
5. **HTML head** — `<link rel="manifest">`, `theme-color`, Apple web-app meta + touch icon, `viewport-fit=cover` when standalone safe-area matters.
6. **Settings UX** — **Update** tab includes **Install app** row: `beforeinstallprompt`, iOS Add to Home Screen hint, apply-update action when a worker is waiting.
7. **Deploy** — nginx (or equivalent): `no-cache` on `sw.js`, manifest, and HTML; `Service-Worker-Allowed` when scope is a subpath.

## Helix canonical paths

| Piece | Path |
|--------|------|
| Manifest | `helix-webui/public/manifest.webmanifest` |
| SW source | `helix-webui/public/sw.js` |
| SW build plugin | `helix-webui/vite.config.js` (`pwaServiceWorker`) |
| Client API | `helix-webui/src/lib/pwa.js` |
| Bootstrap | `helix-webui/src/main.jsx` (`initPwa`) |
| Settings install | `helix-webui/src/pages/SettingsPage.jsx` (`PwaSection`) |
| Global update banner | `helix-webui/src/components/Layout.jsx` |
| nginx | `helix-webui/nginx.conf.template` |

## Dev vs production

- **Dev SW** must use a **stable** cache name (not per-request timestamp) so updates do not loop reloads.
- Dev SW sets `DEV=true` and **does not** cache Vite modules (HMR stays live); navigations still network-first.
- Verify installability on **`npm run build`** + nginx image, or on dev host once SW registers without reload loops.

## Checklist

- [ ] Manifest linked and served with `application/manifest+json`
- [ ] SW registered at `{base}sw.js` with `{ scope: base }`
- [ ] Precache includes `index.html`, icons, manifest, hashed assets
- [ ] Offline navigation returns SPA shell (not blank 404)
- [ ] API routes bypass SW cache
- [ ] Settings **Install app** + update copy in i18n
- [ ] Layout offline banner when `navigator.onLine === false`

## Avoid

- Disabling SW in dev **without** documenting that install only works in production builds.
- Workbox-sized dependency for a small precache + network-first shell (Helix uses a filled `public/sw.js`).
- Caching authenticated API JSON as “offline data” unless product explicitly requires it.
