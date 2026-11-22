# Features

- REST API for Android + web monitoring (frames, telemetry, settings)
- SQLite persistence and MediaMTX stream token issuance
- Docker Compose stack: dogan-api + MediaMTX + coturn (SQLite on api volume)
- YAML-only deploy under `.armin/docker-scripts/` (local and remote SSH)
- Deployed on Irancell-T3 (`t3-new`) at `/home/cloud-admin/docker/dogan` behind HAProxy
