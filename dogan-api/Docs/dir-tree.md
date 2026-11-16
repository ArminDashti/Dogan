# Directory Tree

```
.
â”œâ”€â”€ .armin/
â”‚   â””â”€â”€ docker-scripts/          # YAML-only local/server Docker deploy
â”‚       â”œâ”€â”€ run-on-docker-local.ps1
â”‚       â”œâ”€â”€ run-on-docker-local.yaml
â”‚       â”œâ”€â”€ run-on-docker-server.ps1
â”‚       â””â”€â”€ run-on-docker-server.yaml
â”œâ”€â”€ create-image.ps1             # Legacy image build helper
â”œâ”€â”€ build-docker-image.ps1       # Compat wrapper â†’ create-image.ps1
â”œâ”€â”€ run-on-docker-local.ps1      # Legacy CLI local deploy wrapper
â”œâ”€â”€ run-on-docker-server.ps1     # Legacy CLI remote deploy wrapper
â”œâ”€â”€ run-on-docker.ps1            # Legacy deploy engine
â”œâ”€â”€ cmd/
â”‚   â”œâ”€â”€ issue-token/main.go      # Generate embedded API token (dg_*)
â”‚   â””â”€â”€ server/main.go           # HTTP server entry point
â”œâ”€â”€ docker-compose.yml           # sqlite (api volume) + livekit + dogan-api
â”œâ”€â”€ Dockerfile                   # Multi-stage Go build image
â”œâ”€â”€ .docker/
â”‚   â””â”€â”€ stack.manifest.json      # Stack/image/network defaults
â”œâ”€â”€ docker/
â”‚   â””â”€â”€ agent-export/            # Optional agent-export sidecar image
â”œâ”€â”€ Docs/                        # Project documentation (this folder)
â”œâ”€â”€ endpoints.md                 # Short public endpoint table
â”œâ”€â”€ internal/
â”‚   â”œâ”€â”€ auth/                    # JWT, bcrypt login, embedded token
â”‚   â”œâ”€â”€ config/config.go         # DOGAN_* env config (incl. CORS origins)
â”‚   â”œâ”€â”€ handlers/                # REST HTTP handlers
â”‚   â”œâ”€â”€ livekit/                 # LiveKit token and room naming
â”‚   â”œâ”€â”€ middleware/auth.go       # Bearer token middleware
â”‚   â”œâ”€â”€ models/models.go         # Request/response DTOs
â”‚   â”œâ”€â”€ router/router.go         # Routes + CORS middleware
â”‚   â””â”€â”€ store/                   # SQLite persistence layer
â”œâ”€â”€ livekit.yaml                 # LiveKit server config
â”œâ”€â”€ README.md                    # Quick start guide
â””â”€â”€ scripts/
    â””â”€â”€ test-dummy-data.ps1      # Docker integration smoke test
```
