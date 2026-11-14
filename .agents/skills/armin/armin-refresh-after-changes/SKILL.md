---
name: armin-refresh-after-changes
description: >-
  ALWAYS run at the end of a task that changed a Docker or Windows app.
  Rebuild/reinstall so the user sees the latest code; keep configs, settings,
  and data; reuse packages; clear only stale-view caches. Wipe data only if
  the user explicitly says to.
author: "Armin Dashti"
uuid: 4f6a2c1e-9b3d-4e7a-8c52-1d0e7b9a3f61
---
# Refresh After Changes

Done = latest build is **running** and the user can see it. Prefer the project's own rebuild script when it already keeps data.

## Always

1. Scope to units you changed this task (Docker service or Windows app). Check what is running first; never start a duplicate.
2. Keep configs, settings, volumes, AppData, DBs, `.env`. Wipe them **only** if the user said to.
3. Reuse installed packages and image layers. Download only new packages; sync so removed packages go away. No full wipe of `node_modules` / `.venv` / build cache by default.
4. Clear only caches that hide the new build (browser hard-reload; app asset/temp cache). Do not prune Docker volumes or wipe user data.
5. Prove it is new: new container `Created` + image id, or new process `StartTime`.
6. Web UI: open the real URL in Cursor's built-in browser, hard-reload, exercise the change. Screenshot in the report.

## Docker

```powershell
docker compose -f <file> -p <proj> up -d --build --force-recreate <service>
```

- Keep volumes and mounts. Never `down -v`, `volume rm`, or prune project data.
- No `--no-cache` / `--pull` unless a layer is proven stale or the user asked.
- `docker restart` alone is not enough when code or the image changed.

## Windows apps

1. Stop the running process/service.
2. Sync deps in place (`npm install` / `uv sync` / etc., prefer offline when cached).
3. Build latest; replace in place (no uninstall that deletes settings).
4. Start cold; confirm new `StartTime`.

## Skip

- Docs/comments/skills-only → `Refresh skipped: docs-only`.
- User said "do not rebuild" → honor it and say what was skipped.

## Never

- Delete configs/settings/data unless the user asked.
- Rely on HMR / hot reload / `docker restart` alone.
- Claim done while an old process or container is still what the user hits.
- Skip the built-in browser check for a web UI change.
