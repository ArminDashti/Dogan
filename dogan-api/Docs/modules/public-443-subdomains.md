# Dogan public access: TCP 443 + subdomains

Public Dogan on Irancell-T3 uses **only TCP 443**. Unlimited subdomains; HAProxy SNI muxes them.

## Host map

| Hostname | Backend | Role |
|----------|---------|------|
| `dogan.xaigrok.ir` | `dogan-webui:80` | Web UI |
| `dogan-api.xaigrok.ir` | `dogan-api:8080` | REST API |
| `dogan-livekit.xaigrok.ir` | `dogan-livekit:7880` | LiveKit signaling (`wss://`) |
| `dogan-turn.xaigrok.ir` | `dogan-livekit:5349` (plain TURN after TLS) | Media relay (`turns://…:443`) |

Android defaults: API `dogan-api.xaigrok.ir:443`, stream `dogan-livekit.xaigrok.ir:443`.

API env: `DOGAN_LIVEKIT_PUBLIC_URL=wss://dogan-livekit.xaigrok.ir`.

## Why TURN

LiveKit media is WebRTC. UDP `50000–50050` and TCP `7881` must **not** be public. Clients use **LiveKit-embedded TURN** (coturn-class relay inside `dogan-livekit`, not a separate coturn container) over TLS on 443 (`external_tls: true` in `livekit.t3.yaml`).

## DNS

| Name | Target | Proxy |
|------|--------|-------|
| `dogan`, `dogan-api`, `dogan-livekit` | existing (may be Cloudflare-proxied) | Orange cloud OK for HTTPS/WSS |
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

Keep existing `dogan` / `dogan-api` / `dogan-livekit` → `be_ssl_offload`.

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
    server dogan_livekit_turn dogan-livekit:5349 check resolvers docker resolve-prefer ipv4
```

Soft-reload after edit (prefer over full restart when on SSH :443):

```bash
docker exec haproxy haproxy -c -f /usr/local/etc/haproxy/haproxy.cfg
docker kill -s USR2 haproxy
```

## Compose on T3

Active stack path: **`/cloud-admin/docker/dogan`** (service names `livekit`, `api`, `webui`, `pgsql`).

```bash
cd /cloud-admin/docker/dogan
cp livekit.t3.yaml livekit.yaml
docker compose -f docker-compose.yml -f docker-compose.t3.yml up -d --force-recreate livekit
```

`docker-compose.t3.yml` clears LiveKit host port publishes and exposes `:5349`.  
`.env` should keep `DOGAN_LIVEKIT_PUBLIC_URL=wss://dogan-livekit.xaigrok.ir`.

Local PC keeps `livekit.yaml` + UDP publish for emulator ICE.

**DNS still required:** create `dogan-turn.xaigrok.ir` A → `2.144.27.124`, Cloudflare **DNS only** (grey cloud).

## Pass checks (three levels)

| Level | What it proves | Command |
|-------|----------------|---------|
| 1 | API + LiveKit signaling on 443 | `curl -fsS https://dogan-api.xaigrok.ir/dogan/api/v1/health` and `curl -fsSk https://dogan-livekit.xaigrok.ir/` |
| 2 | `dogan-turn` TLS/SNI **and** TURN **ALLOCATE** (not ICE) | From T3: `python3 scripts/verify-dogan-turn.py` (uses LiveKit API key/secret from `dogan-api` env; `turnutils_uclient` via host `coturn` or `docker run --entrypoint turnutils_uclient coturn/coturn:4.6.2 …`) |
| 3 | End-to-end WebRTC media | Watchman on device → LiveKit logs: participant active + `mediaTrack published`; WebUI **Play** shows video (no public UDP to T3) |

Level 2 failure modes: TLS to `dogan-turn` can succeed while allocate fails (wrong LiveKit keys, HAProxy not routing SNI to `:5349`, or grey-cloud DNS broken). Level 2 success still does **not** replace level 3.

`scripts/test-endpoints-t3.sh` runs level 1 (API/LiveKit) and level 2 (`verify-dogan-turn.py`) when the script bundle is present on the host.
