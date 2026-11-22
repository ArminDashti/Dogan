# Dogan public access: TCP 443 + subdomains

Public Dogan on Irancell-T3 uses **only TCP 443**. Unlimited subdomains; HAProxy SNI muxes them.

## Host map

| Hostname | Backend | Role |
|----------|---------|------|
| `dogan.xaigrok.ir` | `dogan-webui:80` | Web UI |
| `dogan-api.xaigrok.ir` | `dogan-api:8080` | REST API |
| `dogan-media.xaigrok.ir` | `mediamtx:8889` | MediaMTX WebRTC / HTTP control API |
| `dogan-hls.xaigrok.ir` | `mediamtx:8888` | HLS playback |
| `dogan-turn.xaigrok.ir` | `turn:5349` (plain TURN after TLS) | Media relay (`turns://…:443`) |

Android defaults: API `dogan-api.xaigrok.ir:443`, stream `dogan-media.xaigrok.ir:443`, TURN `dogan-turn.xaigrok.ir:443`.

API env:
- `DOGAN_MEDIAMTX_WEBRTC_PUBLIC_URL=https://dogan-media.xaigrok.ir`
- `DOGAN_MEDIAMTX_HLS_PUBLIC_URL=https://dogan-hls.xaigrok.ir`
- `DOGAN_TURN_SECRET=<secret>` (required by the T3 overlay; composition fails fast when unset)

MediaMTX env (T3 overlay):
- `DOGAN_MEDIAMTX_ADDITIONAL_HOSTS=2.144.27.124` → `MTX_WEBRTCADDITIONALHOSTS`, the public host placed in WebRTC candidates
- `DOGAN_MEDIAMTX_AUTH_URL=http://dogan-api:8080/dogan/api/v1/mediamtx/auth` → `MTX_AUTHHTTPADDRESS`, the HTTP auth callback for publish/read/playback

## Why TURN

MediaMTX delivers WebRTC media. UDP `8189` and TCP `8889` must **not** be exposed publicly. Clients use the separate coturn container over TLS on 443 (`turns://dogan-turn.xaigrok.ir:443?transport=tcp`), with short-lived REST credentials issued by dogan-api from `DOGAN_TURN_SECRET`.

## DNS

| Name | Target | Proxy |
|------|--------|-------|
| `dogan`, `dogan-api`, `dogan-media`, `dogan-hls` | existing (may be Cloudflare-proxied) | Orange cloud OK for HTTPS |
| `dogan-turn` | origin `2.144.27.124` A/AAAA | **DNS only (grey cloud)** — TURN is not HTTP; CF proxy breaks it |

## HAProxy (T3)

Config: `/cloud-admin/docker-volumes/reverse-proxy/haproxy/config/haproxy.cfg`  
Certs dir: `/cloud-admin/docker-volumes/reverse-proxy/haproxy/certs/` (combined PEM per host).

### 1) Cert

Add `dogan-turn.xaigrok.ir` to `DOMAINS` in `reverse-proxy.sh`, or:

```bash
domain=dogan-turn.xaigrok.ir
raw=/cloud-admin/docker-volumes/reverse-proxy/haproxy/certs-raw
pem=/cloud-admin/docker-volumes/reverse-proxy/haproxy/certs/${domain}.pem
openssl req -x509 -nodes -days 825 -newkey rsa:2048 \
  -keyout "${raw}/${domain}.key" -out "${raw}/${domain}.crt" \
  -subj "/CN=${domain}"
cat "${raw}/${domain}.crt" "${raw}/${domain}.key" > "${pem}"
chmod 644 "${pem}"
```

### 2) fe_tls_sni (mode tcp)

```
use_backend be_dogan_turn_edge if { req_ssl_sni -i dogan-turn.xaigrok.ir }
```

Keep existing `dogan` / `dogan-api` / `dogan-media` / `dogan-hls` → `be_ssl_offload`.

### 3) TCP TLS terminate → plain TURN

```
backend be_dogan_turn_edge
    mode tcp
    server turn_ssl 127.0.0.1:13443

frontend fe_dogan_turn_ssl
    bind 127.0.0.1:13443 ssl crt /usr/local/etc/haproxy/certs/dogan-turn.xaigrok.ir.pem
    mode tcp
    default_backend be_dogan_turn

backend be_dogan_turn
    mode tcp
    timeout connect 5s
    timeout client  1h
    timeout server  1h
    server dogan_turn turn:5349 check resolvers docker resolve-prefer ipv4
```

Soft-reload after edit (prefer over full restart when on SSH :443):

```bash
docker exec haproxy haproxy -c -f /usr/local/etc/haproxy/haproxy.cfg
docker kill -s USR2 haproxy
```

## Compose on T3

Active stack path: **`/cloud-admin/docker/dogan`** (service names `mediamtx`, `turn`, `api`, `webui`, `pgsql`).

```bash
cd /cloud-admin/docker/dogan
docker compose -f docker-compose.yml -f docker-compose.t3.yml up -d --force-recreate mediamtx turn api
```

`docker-compose.t3.yml` clears MediaMTX host port publishes and exposes `:5349` only through HAProxy.  
`.env` keeps `DOGAN_MEDIAMTX_WEBRTC_PUBLIC_URL=https://dogan-media.xaigrok.ir`, `DOGAN_MEDIAMTX_HLS_PUBLIC_URL=https://dogan-hls.xaigrok.ir`, `DOGAN_MEDIAMTX_ADDITIONAL_HOSTS=2.144.27.124`, and `DOGAN_MEDIAMTX_AUTH_URL=http://dogan-api:8080/dogan/api/v1/mediamtx/auth`.

Local PC keeps `mediamtx.yml` plus ICE ports for emulator testing.

**DNS still required:** create `dogan-turn.xaigrok.ir` A → `2.144.27.124`, Cloudflare **DNS only** (grey cloud).

## Pass checks (three levels)

| Level | What it proves | Command |
|-------|----------------|---------|
| 1 | API + MediaMTX control on 443 | `curl -fsS https://dogan-api.xaigrok.ir/dogan/api/v1/health` and `curl -fsSk https://dogan-media.xaigrok.ir/v3/paths/list` |
| 2 | `dogan-turn` TLS/SNI **and** TURN **ALLOCATE** (not ICE) | From T3: `DOGAN_TURN_SECRET=<secret> python3 scripts/verify-dogan-turn.py` |
| 3 | End-to-end WebRTC media | Watchman publishes to `device-*` in MediaMTX `/v3/paths/list`; WebUI **Play** shows video (no public UDP to T3) |

Level 2 failure modes: TLS to `dogan-turn` can succeed while allocate fails (wrong `DOGAN_TURN_SECRET`, HAProxy not routing SNI to `:5349`, or grey-cloud DNS broken). Level 2 success still does **not** replace level 3.

`scripts/test-endpoints-t3.sh` runs level 1 (API/MediaMTX) and level 2 (`verify-dogan-turn.py`) when the script bundle is present on the host.
