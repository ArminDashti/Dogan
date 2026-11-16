# Dogan Server

Dogan is a Go REST API for a personal Android + web monitoring stack. It stores camera frames, Android telemetry, app settings, and phone actions in SQLite, and issues LiveKit tokens for WebRTC streaming between Android and the web client.

**Stack:** Go 1.26, Gin, SQLite, LiveKit, Docker Compose.

**Run (preferred):**
```powershell
.\.armin\docker-scripts\run-on-docker-local.ps1
```

Optional API smoke test only (does **not** populate the admin WebUI for day-to-day use â€” devices appear when Android phones report in):
```powershell
.\scripts\test-dummy-data.ps1
```

Local API URL after deploy: `http://localhost:30251/dogan/api/v1/health` (`publish_port` in `run-on-docker-local.yaml`; avoids host `8080` clashes).

Remote (Irancell-T3 â€” YAML already set for `ssh t3`):
```powershell
.\.armin\docker-scripts\run-on-docker-server.ps1
```

Production HAProxy hosts on t3: `https://dogan-api.xaigrok.ir`, `wss://dogan-livekit.xaigrok.ir`, web `https://dogan.xaigrok.ir` (separate `dogan-webui` container).

Legacy root wrappers (`.\run-on-docker-local.ps1`, `.\create-image.ps1`) still exist; prefer `.armin/docker-scripts/`.

**Entry point:** `cmd/server/main.go`  
**API base:** `/dogan/api/v1`  
**CORS:** `DOGAN_CORS_ALLOWED_ORIGINS` defaults to local web (`:30808`) plus `https://dogan.xaigrok.ir`. Remote `--domain` appends `https://<domain>`.  
**Login:** `admin` / `admin` (web: `admin@local.local` / `admin`) â€” hash in `internal/auth/credentials.go`
