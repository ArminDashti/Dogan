# Server inventory (agent reference)

## Irancell-T3 (`t3` / `t3-new`)

| Field | Value |
|-------|-------|
| Hostname | `t3-new` |
| SSH alias | `t3` |
| Host | `2.144.27.74` |
| User | `cloud-admin` |
| Identity | `~/.ssh/id_ed25519_cloud-admin_2-144-27-74` (also skill mentions `id_ed25519_irancell`) |
| Docker network | `t3-net` |
| Resources | ~4 GB RAM available shown; skill notes 2C/2G/30G class — verify live |

### dogan stack (2026-07-24)

| Item | Value |
|------|-------|
| Volume dir | `/home/cloud-admin/docker/dogan` |
| Containers | `dogan-api` (SQLite on api volume), `dogan-mediamtx`, `dogan-turn` (coturn), `dogan-webui` (on `t3-net`) (unverified (post-migration)) |
| Image | `dogan-api:latest`; web UI `dogan-web:latest` |
| HAProxy hosts | `dogan-api.xaigrok.ir` → `dogan-api:8080`; `dogan-media.xaigrok.ir` → `mediamtx:8889` (WHIP/WHEP); `dogan-hls.xaigrok.ir` → `mediamtx:8888`; `dogan-turn.xaigrok.ir` → TLS terminate → `dogan-turn:5349`; `dogan.xaigrok.ir` → `dogan-webui:80` (unverified (post-migration)) |
| Web UI volume | `/cloud-admin/docker-volumes/dogan-web:/data` |

### Notes

- SSH MCP profile named `irancell` currently targets `2.144.13.55` (stale); prefer `ssh t3`
- Do not put passwords in this file when avoidable; use key auth
