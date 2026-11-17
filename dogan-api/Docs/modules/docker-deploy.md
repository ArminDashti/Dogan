# Module: Docker deploy scripts

## Responsibility

Build and deploy the dogan Compose stack (API + SQLite + LiveKit) locally or over SSH via YAML-only scripts under `.armin/docker-scripts/`.

## Entry points

| Script | Role |
|--------|------|
| `.armin/docker-scripts/run-on-docker-local.ps1` | Local daemon deploy (reads sibling YAML) |
| `.armin/docker-scripts/run-on-docker-server.ps1` | Remote SSH deploy (reads sibling YAML) |
| `create-image.ps1` | Legacy image build helper at repo root |
| `run-on-docker*.ps1` (repo root) | Older CLI-flag wrappers; prefer `.armin/docker-scripts/` |

Behavior is controlled only by YAML â€” no CLI `--` flags on the `.armin` scripts.

## YAML settings

| Key | Local | Server | Notes |
|-----|-------|--------|-------|
| `stack_name` | `dogan` | `dogan` | Compose project name (`-p`) |
| `image_tag` | `dogan-api:latest` | same | Passed as `API_IMAGE_TAG` |
| `compose_file` | `../../docker-compose.yml` | same | Relative to `.armin/docker-scripts/` |
| `dockerfile` | `../../Dockerfile` | same | Relative to `.armin/docker-scripts/` |
| `docker_network` | `t3-net` | same | External network; created if missing |
| `internal_port` | empty | empty | Unused by this compose (container listens on 8080) |
| `publish_port` | `30251` | empty | Maps to `DOGAN_PUBLISH_PORT`; local avoids clash with other stacks on 8080; empty on server clears host bind |
| `ssh` / `volume_dir` | â€” | `ssh t3` / `/home/cloud-admin/docker/dogan` | Irancell-T3 (`t3-new`) |
| `build_image_on` | â€” | `local` | Prefer local build+upload; remote GOPROXY returns 403 on this host |
| `delete_image` | â€” | `yes` | Teardown runs before image load so the new image is not deleted |

## Compose env overrides

Scripts set env vars that match `docker-compose.yml`:

- `API_IMAGE_TAG`
- `DOCKER_NETWORK`
- `DOGAN_PUBLISH_PORT` (local when `publish_port` set; always on server)

## CORS on deploy

Compose sets `DOGAN_CORS_ALLOWED_ORIGINS` (local web + `https://dogan.xaigrok.ir`).

## Public T3 (443/TCP only)

Use overlay + TURN profile (no public UDP / `7881`):

| File | Role |
|------|------|
| `docker-compose.t3.yml` | Clears LiveKit host ports; sets `DOGAN_LIVEKIT_PUBLIC_URL=wss://dogan-livekit.xaigrok.ir` |
| `livekit.t3.yaml` | `use_external_ip` + LiveKit-embedded TURN (`dogan-turn.xaigrok.ir`, `external_tls`) |

Full subdomain / HAProxy / DNS rules: [public-443-subdomains.md](public-443-subdomains.md).

```bash
docker compose -f docker-compose.yml -f docker-compose.t3.yml up -d
```

## Dependencies

- `Dockerfile`, `docker-compose.yml`, `docker-compose.t3.yml`, `livekit.yaml`, `livekit.t3.yaml`
- Docker CLI; remote also needs SSH alias (or `sshpass` for password mode) + `scp`
- Local publish defaults to host port `30251` via YAML (`lexmora-api` already owns `8080` on shared hosts)
