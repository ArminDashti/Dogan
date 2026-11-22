# Directory tree

```
dogan-web/
├── .armin/
│   └── docker-scripts/
│       ├── run-on-docker-local.ps1    # Local Docker deploy (YAML-only)
│       ├── run-on-docker-local.yaml   # Local stack/port/image settings
│       ├── run-on-docker-server.ps1   # Remote SSH deploy (YAML-only)
│       └── run-on-docker-server.yaml  # Remote ssh/volume/build settings
├── .docker/
│   └── stack.manifest.json   # Stack name, ports, network, remote paths
├── .env.example              # API base URL template
├── Dockerfile                # Production multi-stage nginx image
├── README.md                 # Project overview and setup
├── PRODUCT.md                # Product context and constraints
├── create-image.ps1          # Build Docker image
├── docker-compose.yml        # dogan-webui; IMAGE_TAG/DOCKER_NETWORK; expose 80
├── docker-compose.override.yml # Local PUBLISH_PORT host bind (not synced to t3)
├── env.d.ts                  # Vite and PWA client type refs
├── index.html                # SPA shell; PWA meta; font links
├── metadata.json             # Product version metadata
├── nginx.conf                # SPA hosting; no-cache for SW/manifest
├── package.json              # Dependencies and npm scripts
├── docs/
│   ├── description.md        # Project overview and stack
│   ├── dir-tree.md           # This file tree
│   ├── endpoints.md          # API endpoint reference
│   ├── modules/
│   │   ├── api-services.md   # Fetch client and API modules
│   │   ├── camera-panel.md   # WHEP/HLS camera panel
│   │   ├── docker-deploy.md  # Docker build and deploy scripts
│   │   └── pwa.md            # PWA manifest and service worker
│   ├── potentional-bugs/
│   │   ├── red.md            # Critical bug notes
│   │   └── yellow.md         # Minor bug / smell notes
│   └── suggestion/
│       ├── suggestion1.md    # Camera bundle improvement idea
│       ├── suggestion2.md    # Docker skill alignment (done)
│       └── suggestion3.md    # Flat YAML comment handling idea
├── public/
│   ├── apple-touch-icon.png  # iOS home-screen icon (180x180)
│   ├── favicon.svg           # Browser tab icon
│   ├── pwa-192x192.png       # PWA icon 192
│   └── pwa-512x512.png       # PWA icon 512 (also maskable)
├── src/
│   ├── App.vue               # Root Vue component
│   ├── main.ts               # Bootstrap, Pinia, router, SW register
│   ├── assets/
│   │   └── main.css          # Global Tailwind styles
│   ├── components/
│   │   ├── AppLayout.vue     # Sidebar + header page shell
│   │   ├── CameraPanel.vue   # WHEP stream with HLS fallback and Capture
│   │   ├── ErrorAlert.vue    # Reusable error banner
│   │   └── NavSidebar.vue    # App navigation links
│   ├── router/
│   │   └── index.ts          # Route definitions and auth guard
│   ├── services/
│   │   ├── api/
│   │   │   ├── auth.ts       # Login/logout/me endpoints
│   │   │   ├── capture.ts    # POST frame capture
│   │   │   ├── client.ts     # Shared fetch client with auth
│   │   │   ├── devices.ts    # Device list endpoint
│   │   │   ├── errors.ts     # ApiError helper and message parsing
│   │   │   ├── gallery.ts    # Gallery images endpoint
│   │   │   ├── health.ts     # Health probe
│   │   │   ├── logs.ts       # Sign-in and WebRTC session logs
│   │   │   ├── metrics.ts    # Temperature/noise metrics endpoint
│   │   │   ├── settings.ts   # User settings endpoints
│   │   │   ├── stream.ts     # WHEP/HLS credentials endpoint
│   │   │   ├── telemetry.ts  # Live telemetry snapshot endpoint
│   │   │   └── webrtc.ts     # WebRTC session logs
│   │   └── streaming/
│   │       ├── hls.ts        # Native HLS / hls.js fallback player
│   │       └── whep.ts       # Browser WHEP player
│   ├── stores/
│   │   └── auth.ts           # Pinia auth session store
│   ├── types/
│   │   └── api.ts            # Shared TypeScript API types
│   └── views/
│       ├── DeviceView.vue    # Device detail and embedded camera
│       ├── GalleryView.vue   # Captured image gallery
│       ├── LoginView.vue     # Sign-in form
│       ├── LogsView.vue      # Audit and stream session history
│       ├── MetricsView.vue   # Temperature/noise charts
│       ├── ProfileView.vue   # Account details
│       ├── SettingsView.vue  # User preferences form
│       └── StreamView.vue    # Full-page camera stream
└── vite.config.ts            # Vite + vite-plugin-pwa configuration
```
