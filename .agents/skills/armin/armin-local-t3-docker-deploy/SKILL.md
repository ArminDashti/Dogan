---
name: armin-local-t3-docker-deploy
description: >-
  Use to create or update a Docker app on local Docker (this PC) or on the T3
  server (Irancell Ubuntu), or both. New app: compose + volumes + port + start.
  Update: rebuild image and recreate the container from the latest code, keeping
  configs, settings and data. Ask which target if the user did not say.
author: "Armin Dashti"
---
# Docker Deploy (Local + T3)

Goal: the app runs from the latest code on the chosen target, and no config, setting or data is lost.

> Follow `armin-refresh-after-changes` for rebuild rules and `irancell-ubuntu-t3` for every T3 fact and safety rule. This skill only adds the create/update flow for both targets.

## 0. Pick the target

| User says | Target |
|---|---|
| "local", "my PC", nothing about a server | **Local** |
| "T3", "server", "production" | **T3** |
| "both" | Local first, verify, then T3 |
| Unclear | Ask: Local, T3, or both |

## 1. Create or update?

- **Create**: no container and no compose project for this app yet on that target.
- **Update**: it already exists (`docker compose ls`, `docker ps -a`). Never create a second copy next to it.

## 2. Local Docker

**Create**
1. Add `Dockerfile` (if missing) and `docker-compose.yml` with a fixed project name (`-p <app>`).
2. Persistent data goes in **named volumes** or bind mounts; secrets in `.env` (not committed, not printed).
3. Choose a free port: `Get-NetTCPConnection -State Listen -LocalPort <port>` must be empty.
4. `docker compose -p <app> up -d --build`

**Update**
1. Read the compose file and any project script (`install-local-docker.ps1`, etc.). Confirm it keeps volumes.
2. `docker compose -p <app> up -d --build --force-recreate <service>`
3. No `down -v`, no volume prune, no `--no-cache` unless asked.

## 3. T3 server

1. Connect in the order from `irancell-ubuntu-t3` (SSH MCP, then :443, then :22).
2. Check health and free resources first: `free -h; df -h /; docker ps`. T3 is small; no heavy builds unless needed.
3. Ship code: `git pull` in the app folder on T3, or copy the build context (`scp`/`rsync`). Do not overwrite `.env` or data folders.

**Create**
1. Put the app in its own folder with its own compose project name.
2. Use a port that is not already taken (`ss -tulnp`). Never take :22, :443, :500, :4500 or UDP 443.
3. If it needs a public URL through HAProxy: draft the backend/SNI change, validate, then **ask before reloading HAProxy**.
4. `docker compose -p <app> up -d --build`

**Update**
1. `docker compose -p <app> up -d --build --force-recreate <service>`
2. Keep volumes. Never touch SoftEther, Mullvad, Xray or HAProxy containers unless that is the task, and ask first.
3. After a recreate, check the coupling list in `irancell-ubuntu-t3` (`docker.md`).

## 4. Verify (each target)

- Container is `Up`/healthy with a **new** Created time and image id: `docker inspect <ctr> --format "{{.Created}} {{.Image}}"`.
- Logs have no crash loop: `docker logs --tail 50 <ctr>`.
- URL or health endpoint returns 200 (local URL, or T3 URL from outside for public apps).
- Web UI: test in Cursor's built-in browser with a hard reload.

## 5. Report

| Target | App | Action | Status |
|---|---|---|---|
| Local / T3 | `<app>` | Created / Updated | ✅ Running / ❌ Failed |

Below the table: URL, commands run, new Created time, kept volumes, approvals used, and the reason for any failure.

## Never

- Delete volumes, configs, `.env` or data unless the user says so.
- Reload or restart HAProxy, SoftEther, Mullvad or Xray on T3 without asking.
- Hardcode IPs, passwords or tokens in files; use env vars and `.env`.
- Deploy to T3 when the user only asked for local.
- Claim done before the new container is verified.
