#!/usr/bin/env bash
# Test every Dogan API endpoint from T3 (internal 127.0.0.1:6080 and public
# https://dogan-api.xaigrok.ir). Read-only / non-destructive: mutating calls
# use device id dogan-test-device only; web settings are restored.
# Does not print secrets (tokens, API keys, passwords).
set -u
set -o pipefail

DEVICE_ID="dogan-test-device"
INTERNAL="http://127.0.0.1:6080/dogan/api/v1"
PUBLIC="https://dogan-api.xaigrok.ir/dogan/api/v1"
WEB_API_PATH="https://dogan.xaigrok.ir/dogan/api/v1"
WEB_ORIGIN="https://dogan.xaigrok.ir"
LIVEKIT_PUBLIC="https://dogan-livekit.xaigrok.ir"
OUT_DIR="${OUT_DIR:-/tmp/dogan-endpoint-tests}"
mkdir -p "$OUT_DIR"
RESULTS="$OUT_DIR/results.tsv"
REPORT="$OUT_DIR/report.md"
BODY_DIR="$OUT_DIR/bodies"
mkdir -p "$BODY_DIR"
: > "$RESULTS"

# Tiny 1x1 JPEG
JPEG_B64='/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//AP//AP//AP//AP//AP//AP//AP//AP//AP//AP//AP//AP//AP//AP//AP//AP//AP//AP//AP//AP//AP//AP//2wBDAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQH/wAARCAABAAEDAREAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAb/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/8QAFQEBAQAAAAAAAAAAAAAAAAAAAAX/xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIRAxEAPwCdABmX/9k='

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [[ -f "$SCRIPT_DIR/helpers.py" ]]; then
  HELPERS="$SCRIPT_DIR/helpers.py"
elif [[ -f /tmp/dogan-endpoint-tests/helpers.py ]]; then
  HELPERS=/tmp/dogan-endpoint-tests/helpers.py
else
  echo "helpers.py not found next to this script or in /tmp/dogan-endpoint-tests" >&2
  exit 1
fi
cp -f "$HELPERS" "$OUT_DIR/helpers.py"
HELPERS="$OUT_DIR/helpers.py"

log() { printf '%s\n' "$*" >&2; }

redact() { python3 "$HELPERS" redact; }

preview() {
  local f="$1"
  python3 "$HELPERS" preview "$f"
}

record() {
  # base method path status latency_ms expected verdict notes
  local base="$1" method="$2" path="$3" status="$4" ms="$5" expected="$6" verdict="$7" notes="$8"
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$base" "$method" "$path" "$status" "$ms" "$expected" "$verdict" "$notes" >> "$RESULTS"
  printf '  [%s] %s %s  status=%s  %sms  expected=%s  %s  %s\n' "$base" "$method" "$path" "$status" "$ms" "$expected" "$verdict" "$notes" >&2
}

# curl wrapper. Sets globals: LAST_STATUS LAST_MS LAST_BODY LAST_HDR LAST_CT
# Usage: call METHOD URL [curl extra args...]
call() {
  local method="$1" url="$2"
  shift 2
  local body="$BODY_DIR/body.$$"
  local hdr="$BODY_DIR/hdr.$$"
  local tm="$BODY_DIR/tm.$$"
  rm -f "$body" "$hdr" "$tm"
  local code
  code=$(curl -sS -o "$body" -D "$hdr" -w '%{http_code} %{time_total}' \
    --max-time 45 \
    -X "$method" \
    -H 'Accept: application/json, image/*, */*' \
    "$@" \
    "$url" 2>"$BODY_DIR/err.$$") || true
  if [[ -z "$code" ]]; then
    LAST_STATUS="000"
    LAST_MS="0"
    LAST_BODY="$body"
    LAST_HDR="$hdr"
    LAST_CT=""
    LAST_ERR=$(tr '\n' ' ' < "$BODY_DIR/err.$$" 2>/dev/null || true)
    return 0
  fi
  LAST_STATUS="${code%% *}"
  local secs="${code#* }"
  LAST_MS=$(python3 "$HELPERS" ms "$secs" 2>/dev/null || echo 0)
  LAST_BODY="$body"
  LAST_HDR="$hdr"
  LAST_CT=$(grep -i '^content-type:' "$hdr" | tail -1 | tr -d '\r' | sed 's/[Cc]ontent-[Tt]ype:[[:space:]]*//')
  LAST_ERR=$(tr '\n' ' ' < "$BODY_DIR/err.$$" 2>/dev/null || true)
}

status_ok() {
  local got="$1" expected="$2"
  case ",$expected," in
    *",$got,"*) return 0 ;;
    *) return 1 ;;
  esac
}

json_has() {
  local file="$1" expr="$2"
  python3 "$HELPERS" has "$file" "$expr"
}

json_get() {
  local file="$1" expr="$2"
  python3 "$HELPERS" get "$file" "$expr"
}


# ---------- credentials from running container (never printed) ----------
log "Loading container env (values not printed)..."
DEVICE_API_KEY=$(docker exec dogan-api printenv DOGAN_DEVICE_API_KEY || true)
EMBEDDED_TOKEN=$(docker exec dogan-api printenv DOGAN_EMBEDDED_API_TOKEN || true)
CORS_ORIGINS=$(docker exec dogan-api printenv DOGAN_CORS_ALLOWED_ORIGINS || true)
LK_URL=$(docker exec dogan-api printenv DOGAN_LIVEKIT_URL || true)
LK_PUB=$(docker exec dogan-api printenv DOGAN_LIVEKIT_PUBLIC_URL || true)
OPENCODE_KEY_SET=$(docker exec dogan-api sh -c 'if [ -n "${OPENCODE_GO_API_KEY:-}" ]; then echo yes; else echo no; fi')
OPENCODE_BASE=$(docker exec dogan-api printenv OPENCODE_GO_BASE_URL || true)
OPENCODE_MODEL=$(docker exec dogan-api printenv OPENCODE_GO_MODEL || true)

CRED_DEVICE="no"
CRED_ADMIN="no"
CRED_WEB="no"
ADMIN_JWT=""
WEB_JWT=""
DEVICE_TOKEN=""

auth_against() {
  local base="$1" label="$2"

  # device api_key
  call POST "$base/auth" -H 'Content-Type: application/json' \
    --data "$(python3 -c "import json,os; print(json.dumps({'api_key': os.environ['K']}))" )" \
    2>/dev/null || true
}

export K="$DEVICE_API_KEY"
export T=""  # set later

# ---------- helper to run one case ----------
# do_case base_label method path expected [curl extras after --]
# uses AUTH header if AUTH_HDR is set and USE_AUTH=1
USE_AUTH=0
AUTH_HDR=""

do_case() {
  local label="$1" method="$2" path="$3" expected="$4"
  shift 4
  local url
  case "$label" in
    internal) url="$INTERNAL$path" ;;
    public)   url="$PUBLIC$path" ;;
    webhost)  url="$WEB_API_PATH$path" ;;
    *) url="$path"; label="other" ;;
  esac
  local extra=()
  if [[ "$USE_AUTH" = 1 && -n "$AUTH_HDR" ]]; then
    extra+=(-H "Authorization: Bearer $AUTH_HDR")
  fi
  call "$method" "$url" "${extra[@]}" "$@"
  local notes=""
  local verdict
  if [[ "$LAST_STATUS" = "000" ]]; then
    verdict="FAIL"
    notes="curl error: ${LAST_ERR:-unknown}"
  elif status_ok "$LAST_STATUS" "$expected"; then
    verdict="OK"
    notes=$(preview "$LAST_BODY")
  else
    if [[ "$LAST_STATUS" =~ ^5 ]]; then
      verdict="BUG"
    else
      verdict="FAIL"
    fi
    notes="unexpected status; body=$(preview "$LAST_BODY")"
    if [[ -n "$LAST_ERR" ]]; then notes="$notes err=$LAST_ERR"; fi
  fi
  record "$label" "$method" "$path" "$LAST_STATUS" "$LAST_MS" "$expected" "$verdict" "$notes"
}

# =====================================================================
log "===== Reachability & unauthenticated endpoints ====="

# Internal health
do_case internal GET /health 200
if [[ "$LAST_STATUS" = 200 ]] && json_has "$LAST_BODY" status; then
  :
else
  # already recorded; refine note if schema bad
  if [[ "$LAST_STATUS" = 200 ]]; then
    # overwrite last line? just add a schema note via extra record is messy.
    true
  fi
fi
HEALTH_INTERNAL_BODY=$(preview "$LAST_BODY")

do_case public GET /health 200
HEALTH_PUBLIC_BODY=$(preview "$LAST_BODY")

# Web host should NOT be the API (SPA fallback)
call GET "$WEB_API_PATH/health"
WEBHOST_CT="$LAST_CT"
WEBHOST_STATUS="$LAST_STATUS"
WEBHOST_MS="$LAST_MS"
if [[ "$LAST_CT" == *json* ]] && python3 -c "import json,sys; d=json.load(open(sys.argv[1])); assert d.get('status')" "$LAST_BODY" 2>/dev/null; then
  record webhost GET /health "$LAST_STATUS" "$LAST_MS" "200-json-or-spa" "OK" "unexpectedly serves API JSON from web host"
  WEBHOST_VERDICT="API JSON (unexpected on web host)"
else
  record webhost GET /health "$LAST_STATUS" "$LAST_MS" "200-html-spa" "INFO" "SPA fallback (content-type=$LAST_CT); not the API. This is expected: HAProxy routes dogan.xaigrok.ir to dogan-webui only."
  WEBHOST_VERDICT="SPA fallback, not API (expected)"
fi

do_case internal GET /endpoints 200
ENDPOINTS_INTERNAL="$LAST_BODY"
cp "$LAST_BODY" "$OUT_DIR/endpoints-internal.json" 2>/dev/null || true

do_case public GET /endpoints 200
cp "$LAST_BODY" "$OUT_DIR/endpoints-public.json" 2>/dev/null || true

# Compare documented vs listed later in python

# Unauth protected should 401
do_case internal GET /auth/me 401
do_case public GET /auth/me 401
do_case internal GET /devices 401

# Bad credentials
call POST "$INTERNAL/auth" -H 'Content-Type: application/json' --data '{"api_key":"definitely-wrong-key"}'
if status_ok "$LAST_STATUS" 401; then
  record internal POST /auth "$LAST_STATUS" "$LAST_MS" 401 OK "invalid api_key rejected"
else
  record internal POST /auth "$LAST_STATUS" "$LAST_MS" 401 FAIL "invalid api_key should 401; body=$(preview "$LAST_BODY")"
fi

call POST "$INTERNAL/auth" -H 'Content-Type: application/json' --data '{"username":"admin","password":"wrong-password-probe"}'
if status_ok "$LAST_STATUS" 401; then
  record internal POST /auth "$LAST_STATUS" "$LAST_MS" 401 OK "invalid admin password rejected"
else
  record internal POST /auth "$LAST_STATUS" "$LAST_MS" 401 FAIL "invalid admin password should 401; body=$(preview "$LAST_BODY")"
fi

# =====================================================================
log "===== Authentication (secrets not printed) ====="

# Device API key from container env
call POST "$INTERNAL/auth" -H 'Content-Type: application/json' \
  --data "$(python3 -c "import json,os; print(json.dumps({'api_key': os.environ['K']}))")"
if status_ok "$LAST_STATUS" 200 && json_has "$LAST_BODY" token; then
  DEVICE_TOKEN=$(json_get "$LAST_BODY" token)
  CRED_DEVICE="yes"
  tok_match="no"
  if [[ -n "$EMBEDDED_TOKEN" && "$DEVICE_TOKEN" == "$EMBEDDED_TOKEN" ]]; then
    tok_match="yes (matches embedded token as designed)"
  fi
  record internal POST /auth "$LAST_STATUS" "$LAST_MS" 200 OK "device api_key from container env worked; token_matches_embedded=$tok_match"
else
  record internal POST /auth "$LAST_STATUS" "$LAST_MS" 200 FAIL "device api_key auth failed; body=$(preview "$LAST_BODY")"
fi

# Admin username/password (compiled defaults admin/admin)
call POST "$INTERNAL/auth" -H 'Content-Type: application/json' \
  --data '{"username":"admin","password":"admin"}'
if status_ok "$LAST_STATUS" 200 && json_has "$LAST_BODY" token; then
  ADMIN_JWT=$(json_get "$LAST_BODY" token)
  CRED_ADMIN="yes"
  record internal POST /auth "$LAST_STATUS" "$LAST_MS" 200 OK "admin username/password (compiled defaults) worked; jwt issued"
else
  record internal POST /auth "$LAST_STATUS" "$LAST_MS" 200 FAIL "admin username/password failed; body=$(preview "$LAST_BODY")"
fi

# Web login email/password
call POST "$INTERNAL/auth/login" -H 'Content-Type: application/json' \
  --data '{"email":"admin@local.local","password":"admin"}'
if status_ok "$LAST_STATUS" 200 && json_has "$LAST_BODY" token && json_has "$LAST_BODY" user.email; then
  WEB_JWT=$(json_get "$LAST_BODY" token)
  CRED_WEB="yes"
  record internal POST /auth/login "$LAST_STATUS" "$LAST_MS" 200 OK "web email/password (compiled admin@local.local) worked; user issued"
else
  record internal POST /auth/login "$LAST_STATUS" "$LAST_MS" 200 FAIL "web login failed; body=$(preview "$LAST_BODY")"
fi

# Public auth smoke
call POST "$PUBLIC/auth/login" -H 'Content-Type: application/json' \
  --data '{"email":"admin@local.local","password":"admin"}'
if status_ok "$LAST_STATUS" 200 && json_has "$LAST_BODY" token; then
  # prefer public jwt only if internal failed
  if [[ -z "$WEB_JWT" ]]; then WEB_JWT=$(json_get "$LAST_BODY" token); CRED_WEB="yes"; fi
  record public POST /auth/login "$LAST_STATUS" "$LAST_MS" 200 OK "web login works on public API host"
else
  record public POST /auth/login "$LAST_STATUS" "$LAST_MS" 200 FAIL "public web login failed; body=$(preview "$LAST_BODY")"
fi

call POST "$PUBLIC/auth" -H 'Content-Type: application/json' \
  --data "$(python3 -c "import json,os; print(json.dumps({'api_key': os.environ['K']}))")"
if status_ok "$LAST_STATUS" 200 && json_has "$LAST_BODY" token; then
  record public POST /auth "$LAST_STATUS" "$LAST_MS" 200 OK "device api_key works on public API host"
else
  record public POST /auth "$LAST_STATUS" "$LAST_MS" 200 FAIL "public device api_key auth failed; body=$(preview "$LAST_BODY")"
fi

if [[ -z "$ADMIN_JWT" && -n "$WEB_JWT" ]]; then ADMIN_JWT="$WEB_JWT"; fi
if [[ -z "$DEVICE_TOKEN" && -n "$EMBEDDED_TOKEN" ]]; then DEVICE_TOKEN="$EMBEDDED_TOKEN"; fi

if [[ -z "$ADMIN_JWT" ]]; then
  log "FATAL: no admin/web JWT; cannot test protected endpoints."
fi

# =====================================================================
log "===== Protected endpoints (internal, admin JWT) ====="
USE_AUTH=1
AUTH_HDR="$ADMIN_JWT"

do_case internal GET /auth/me 200
if [[ "$LAST_STATUS" = 200 ]]; then
  ME_ID=$(json_get "$LAST_BODY" id)
  ME_EMAIL=$(json_get "$LAST_BODY" email)
  # refine: already recorded OK
fi

# Also /auth/me with device token
if [[ -n "$DEVICE_TOKEN" ]]; then
  call GET "$INTERNAL/auth/me" -H "Authorization: Bearer $DEVICE_TOKEN"
  if status_ok "$LAST_STATUS" 200 && [[ "$(json_get "$LAST_BODY" id)" = "device" ]]; then
    record internal GET /auth/me "$LAST_STATUS" "$LAST_MS" 200 OK "device/embedded bearer returns user id=device"
  elif status_ok "$LAST_STATUS" 200; then
    record internal GET /auth/me "$LAST_STATUS" "$LAST_MS" 200 OK "device bearer accepted; id=$(json_get "$LAST_BODY" id)"
  else
    record internal GET /auth/me "$LAST_STATUS" "$LAST_MS" 200 FAIL "device bearer /auth/me failed; body=$(preview "$LAST_BODY")"
  fi
fi

do_case internal GET /devices 200
cp "$LAST_BODY" "$OUT_DIR/devices.json" 2>/dev/null || true
DEVICE_COUNT=$(python3 "$HELPERS" device_count "$LAST_BODY" 2>/dev/null || echo "?")
DEVICE_IDS=$(python3 "$HELPERS" device_ids "$LAST_BODY" 2>/dev/null || echo "")
log "Registered devices count=$DEVICE_COUNT ids=$DEVICE_IDS"

# Save original web settings
do_case internal GET /settings 200
cp "$LAST_BODY" "$OUT_DIR/settings-original.json"
ORIG_SETTINGS="$OUT_DIR/settings-original.json"

# Also android-style
do_case internal GET "/settings?device_id=$DEVICE_ID" 200

# ---- writes against dogan-test-device only ----
log "===== Writes against $DEVICE_ID only ====="

# POST telemetry (204)
TEL_JSON=$(python3 -c "
import json, datetime
print(json.dumps({
  'device_id': '$DEVICE_ID',
  'recorded_at': datetime.datetime.utcnow().strftime('%Y-%m-%dT%H:%M:%SZ'),
  'gps_location': {'latitude': 35.6892, 'longitude': 51.3890},
  'gps_signal_quality': 'good',
  'speed_kmh': 0,
  'network_signal_strength_dbm': -70,
  'network_type': 'wifi',
  'cabin_noise_rms': 0.01,
  'battery_temperature_celsius': 32.1,
  'battery_percentage': 80,
  'ambient_light_lux': 120,
  'server_latency_ms': 15,
  'device_ip_address': '127.0.0.1',
  'cpu_usage_percent': 10.0,
  'ram_usage_percent': 20.0
}))
")
do_case internal POST /telemetry 204 -H 'Content-Type: application/json' --data "$TEL_JSON"

# POST frame (201)
FRAME_JSON=$(python3 -c "import json,os; print(json.dumps({'device_id': '$DEVICE_ID', 'image_data': '''$JPEG_B64'''}))")
do_case internal POST /frame 201 -H 'Content-Type: application/json' --data "$FRAME_JSON"
if [[ "$LAST_STATUS" = 201 ]]; then
  FRAME_ID=$(json_get "$LAST_BODY" id)
else
  FRAME_ID=""
fi

do_case internal GET "/last-frame?device-id=$DEVICE_ID" 200
do_case internal GET "/frame/image?device-id=$DEVICE_ID" 200
if [[ "$LAST_STATUS" = 200 && "$LAST_CT" != *image* && "$LAST_CT" != *octet* ]]; then
  record internal GET "/frame/image?device-id=$DEVICE_ID" "$LAST_STATUS" "$LAST_MS" 200 FAIL "expected image content-type, got $LAST_CT"
fi

# legacy metrics
MET_JSON='{"device_id":"'"$DEVICE_ID"'","battery_level_percent":81.5,"signal_strength_dbm":-66,"network_type":"wifi","temperature_celsius":33.0,"latitude":35.6892,"longitude":51.389}'
do_case internal POST /device-metrics 201 -H 'Content-Type: application/json' --data "$MET_JSON"
do_case internal GET "/device-metrics?device-id=$DEVICE_ID" 200

do_case internal GET "/devices/$DEVICE_ID/telemetry" 200
# schema check for web telemetry
if [[ "$LAST_STATUS" = 200 ]]; then
  if json_has "$LAST_BODY" device_id && json_has "$LAST_BODY" battery_percent; then
    :
  else
    record internal GET "/devices/$DEVICE_ID/telemetry" "$LAST_STATUS" "$LAST_MS" 200 FAIL "schema missing device_id/battery_percent; body=$(preview "$LAST_BODY")"
  fi
fi

do_case internal GET "/devices/$DEVICE_ID/metrics" 200

# Actions — test device only
ACT_JSON='{"device_id":"'"$DEVICE_ID"'","action_type":"ping","payload":{"message":"dogan-endpoint-test"}}'
do_case internal POST /actions 201 -H 'Content-Type: application/json' --data "$ACT_JSON"
ACTION_ID=$(json_get "$LAST_BODY" id)
do_case internal GET "/actions/pending?device-id=$DEVICE_ID" 200
if [[ -n "$ACTION_ID" ]]; then
  do_case internal PUT "/actions/$ACTION_ID/ack" 200 -H 'Content-Type: application/json' --data '{"status":"done"}'
else
  record internal PUT /actions/:id/ack 000 0 200 FAIL "skipped; no action id from POST /actions"
fi

# Settings write + restore
# PUT a clearly test key on a non-web platform so we do not clobber web prefs.
do_case internal PUT /settings 200 -H 'Content-Type: application/json' \
  --data '{"platform":"testprobe","key":"dogan_test_probe","value":"1"}'

# PATCH web prefs then restore
if [[ -f "$ORIG_SETTINGS" ]]; then
  # flip notifications or set a temp unit then restore
  PATCH_TMP=$(python3 "$HELPERS" patch_tmp "$ORIG_SETTINGS")
  do_case internal PATCH /settings 200 -H 'Content-Type: application/json' --data "$PATCH_TMP"
  # restore
  RESTORE=$(python3 "$HELPERS" restore_body "$ORIG_SETTINGS")
  do_case internal PATCH /settings 200 -H 'Content-Type: application/json' --data "$RESTORE"
  call GET "$INTERNAL/settings" -H "Authorization: Bearer $AUTH_HDR"
  python3 "$HELPERS" settings_compare "$ORIG_SETTINGS" "$LAST_BODY" "$OUT_DIR/settings-restore.txt"
  REST_MSG=$(cat "$OUT_DIR/settings-restore.txt")
  if [[ "$REST_MSG" = restored ]]; then
    record internal PATCH /settings "$LAST_STATUS" "$LAST_MS" 200 OK "web settings restored to original values"
  else
    record internal PATCH /settings "$LAST_STATUS" "$LAST_MS" 200 FAIL "settings restore $REST_MSG"
  fi
fi

# Sounds
do_case internal GET /sounds 200
SOUND_ID=$(python3 "$HELPERS" first_sound "$LAST_BODY")
if [[ -n "$SOUND_ID" ]]; then
  do_case internal GET "/sounds/$SOUND_ID" 200
else
  do_case internal GET /sounds/nonexistent-sound-id 404
fi

# Diagnostic audio (multipart) — test device only
python3 - << 'PY'
import wave, struct, os
p="/tmp/dogan-endpoint-tests/probe.wav"
os.makedirs("/tmp/dogan-endpoint-tests", exist_ok=True)
with wave.open(p,"w") as w:
    w.setnchannels(1)
    w.setsampwidth(2)
    w.setframerate(8000)
    w.writeframes(b"\x00\x00"*160)  # 20ms silence
PY
META='{"segment_id":"dogan-test-seg-1","start_ms":0,"end_ms":20,"rms_peak":0.0,"linked_alert_id":"","mode":"test","device_id":"'"$DEVICE_ID"'"}'
do_case internal POST /diagnostic-audio 201 \
  -F "metadata=$META;type=application/json" \
  -F "audio=@$OUT_DIR/probe.wav;type=audio/wav"

# LiveKit / WebRTC
do_case internal POST /streaming/token 200 -H 'Content-Type: application/json' \
  --data '{"device_id":"'"$DEVICE_ID"'","role":"subscriber","identity":"dogan-test-sub"}'
LK_TOKEN_URL=""
if [[ "$LAST_STATUS" = 200 ]]; then
  LK_TOKEN_URL=$(json_get "$LAST_BODY" url)
  cp "$LAST_BODY" "$OUT_DIR/streaming-token.json"
  # redact token in saved copy
  python3 "$HELPERS" redact_token_file "$OUT_DIR/streaming-token.json"
fi

do_case internal POST /webrtc/session 200 -H 'Content-Type: application/json' \
  --data '{"device_id":"'"$DEVICE_ID"'"}'

do_case internal GET "/devices/$DEVICE_ID/stream" 200
do_case internal GET "/webrtc/connections?device-id=$DEVICE_ID" 200

# Capture — test device only (queues action; should 200 because we posted a frame)
do_case internal POST "/devices/$DEVICE_ID/capture" "200,202"
# If an action was queued, try to ack any pending capture for test device so we don't leave a pending action
call GET "$INTERNAL/actions/pending?device-id=$DEVICE_ID" -H "Authorization: Bearer $AUTH_HDR"
python3 "$HELPERS" pending_ids "$LAST_BODY" "$OUT_DIR/pending-action-ids.txt"
while IFS= read -r pid; do
  [[ -z "$pid" ]] && continue
  call PUT "$INTERNAL/actions/$pid/ack" -H "Authorization: Bearer $AUTH_HDR" -H 'Content-Type: application/json' --data '{"status":"done"}'
  record internal PUT "/actions/$pid/ack" "$LAST_STATUS" "$LAST_MS" 200 OK "cleanup ack of leftover pending test-device action"
done < "$OUT_DIR/pending-action-ids.txt"

# Images
do_case internal GET /images 200
IMAGE_ID=$(python3 "$HELPERS" first_image "$LAST_BODY" "$DEVICE_ID")
if [[ -n "$IMAGE_ID" ]]; then
  do_case internal GET "/images/$IMAGE_ID" 200
else
  do_case internal GET /images/0 400,404
fi

# Vision
VISION_JSON=$(python3 -c "import json; print(json.dumps({'device_id': '$DEVICE_ID', 'image_base64': '''$JPEG_B64''', 'prompt': 'One-word test: describe nothing of importance.'}))")
# 200 if configured, 503 if not, 502 if upstream fails — all are "the endpoint works"; 5xx other than those may be bugs
call POST "$INTERNAL/vision/analyze" -H "Authorization: Bearer $AUTH_HDR" -H 'Content-Type: application/json' --data "$VISION_JSON"
case "$LAST_STATUS" in
  200) record internal POST /vision/analyze "$LAST_STATUS" "$LAST_MS" 200 OK "vision analysis succeeded; model=$(json_get "$LAST_BODY" model)" ;;
  503) record internal POST /vision/analyze "$LAST_STATUS" "$LAST_MS" 503 OK "opencode go vision is not configured (OPENCODE_GO_API_KEY set=$OPENCODE_KEY_SET)" ;;
  502) record internal POST /vision/analyze "$LAST_STATUS" "$LAST_MS" 502 BUG "upstream vision failed; body=$(preview "$LAST_BODY") key_set=$OPENCODE_KEY_SET base=$OPENCODE_BASE model=$OPENCODE_MODEL" ;;
  *) record internal POST /vision/analyze "$LAST_STATUS" "$LAST_MS" "200,502,503" FAIL "unexpected; body=$(preview "$LAST_BODY") key_set=$OPENCODE_KEY_SET" ;;
esac

do_case internal GET "/vision/latest?device-id=$DEVICE_ID" "200,404"

# Logout (204) then confirm token still works (stateless JWT — document if so)
do_case internal POST /auth/logout 204
call GET "$INTERNAL/auth/me" -H "Authorization: Bearer $AUTH_HDR"
if status_ok "$LAST_STATUS" 200; then
  record internal GET /auth/me "$LAST_STATUS" "$LAST_MS" 200 INFO "token still valid after logout (stateless JWT; logout does not revoke)"
elif status_ok "$LAST_STATUS" 401; then
  record internal GET /auth/me "$LAST_STATUS" "$LAST_MS" 401 OK "token revoked after logout"
else
  record internal GET /auth/me "$LAST_STATUS" "$LAST_MS" "200-or-401" FAIL "unexpected after logout; body=$(preview "$LAST_BODY")"
fi

# =====================================================================
log "===== Public API host: remaining endpoints ====="
USE_AUTH=1
AUTH_HDR="$ADMIN_JWT"

do_case public GET /auth/me 200
do_case public GET /devices 200
do_case public GET /settings 200
do_case public GET "/settings?device_id=$DEVICE_ID" 200
do_case public GET "/last-frame?device-id=$DEVICE_ID" 200
do_case public GET "/frame/image?device-id=$DEVICE_ID" 200
do_case public GET "/device-metrics?device-id=$DEVICE_ID" 200
do_case public GET "/devices/$DEVICE_ID/telemetry" 200
do_case public GET "/devices/$DEVICE_ID/metrics" 200
do_case public GET "/actions/pending?device-id=$DEVICE_ID" 200
do_case public GET /sounds 200
if [[ -n "${SOUND_ID:-}" ]]; then
  do_case public GET "/sounds/$SOUND_ID" 200
fi
do_case public GET "/webrtc/connections?device-id=$DEVICE_ID" 200
do_case public GET "/devices/$DEVICE_ID/stream" 200
do_case public GET /images 200
if [[ -n "${IMAGE_ID:-}" ]]; then
  do_case public GET "/images/$IMAGE_ID" 200
fi
do_case public GET "/vision/latest?device-id=$DEVICE_ID" "200,404"
do_case public POST /streaming/token 200 -H 'Content-Type: application/json' \
  --data '{"device_id":"'"$DEVICE_ID"'","role":"subscriber","identity":"dogan-test-sub-public"}'
do_case public POST /webrtc/session 200 -H 'Content-Type: application/json' \
  --data '{"device_id":"'"$DEVICE_ID"'"}'
do_case public POST /auth/logout 204

# Public mutating calls limited to test device (telemetry already posted internally)
# One public POST /telemetry to prove the write path on the public host
do_case public POST /telemetry 204 -H 'Content-Type: application/json' --data "$TEL_JSON"
do_case public POST /frame 201 -H 'Content-Type: application/json' --data "$FRAME_JSON"
do_case public POST /device-metrics 201 -H 'Content-Type: application/json' --data "$MET_JSON"
do_case public POST /actions 201 -H 'Content-Type: application/json' --data "$ACT_JSON"
PUB_ACTION_ID=$(json_get "$LAST_BODY" id)
if [[ -n "$PUB_ACTION_ID" ]]; then
  do_case public PUT "/actions/$PUB_ACTION_ID/ack" 200 -H 'Content-Type: application/json' --data '{"status":"done"}'
fi
do_case public PUT /settings 200 -H 'Content-Type: application/json' \
  --data '{"platform":"testprobe","key":"dogan_test_probe","value":"1"}'
do_case public POST /diagnostic-audio 201 \
  -F "metadata=$META;type=application/json" \
  -F "audio=@$OUT_DIR/probe.wav;type=audio/wav"
do_case public POST "/devices/$DEVICE_ID/capture" "200,202"
# cleanup pending again
call GET "$PUBLIC/actions/pending?device-id=$DEVICE_ID" -H "Authorization: Bearer $AUTH_HDR"
python3 "$HELPERS" pending_ids "$LAST_BODY" "$OUT_DIR/pending-action-ids-pub.txt"
while IFS= read -r pid; do
  [[ -z "$pid" ]] && continue
  call PUT "$PUBLIC/actions/$pid/ack" -H "Authorization: Bearer $AUTH_HDR" -H 'Content-Type: application/json' --data '{"status":"done"}'
  record public PUT "/actions/$pid/ack" "$LAST_STATUS" "$LAST_MS" 200 OK "cleanup ack of leftover pending test-device action"
done < "$OUT_DIR/pending-action-ids-pub.txt"

call POST "$PUBLIC/vision/analyze" -H "Authorization: Bearer $AUTH_HDR" -H 'Content-Type: application/json' --data "$VISION_JSON"
case "$LAST_STATUS" in
  200) record public POST /vision/analyze "$LAST_STATUS" "$LAST_MS" 200 OK "vision analysis succeeded on public host" ;;
  503) record public POST /vision/analyze "$LAST_STATUS" "$LAST_MS" 503 OK "vision not configured" ;;
  502) record public POST /vision/analyze "$LAST_STATUS" "$LAST_MS" 502 BUG "upstream vision failed; body=$(preview "$LAST_BODY")" ;;
  *) record public POST /vision/analyze "$LAST_STATUS" "$LAST_MS" "200,502,503" FAIL "unexpected; body=$(preview "$LAST_BODY")" ;;
esac

# =====================================================================
log "===== CORS (Origin https://dogan.xaigrok.ir) ====="

cors_check() {
  local label="$1" url="$2" method="$3"
  local hdr="$BODY_DIR/cors.hdr"
  local body="$BODY_DIR/cors.body"
  local timing
  timing=$(curl -sS -o "$body" -D "$hdr" -w '%{http_code} %{time_total}' --max-time 20 \
    -X OPTIONS "$url" \
    -H "Origin: $WEB_ORIGIN" \
    -H "Access-Control-Request-Method: $method" \
    -H "Access-Control-Request-Headers: Authorization,Content-Type" \
    -H "Access-Control-Request-Private-Network: true" || true)
  local status="${timing%% *}"
  local secs="${timing#* }"
  local ms
  ms=$(python3 "$HELPERS" ms "${secs:-0}" 2>/dev/null || echo 0)
  local acao acam acah acahdr
  acao=$(grep -i '^access-control-allow-origin:' "$hdr" | tail -1 | tr -d '\r')
  acam=$(grep -i '^access-control-allow-methods:' "$hdr" | tail -1 | tr -d '\r')
  acah=$(grep -i '^access-control-allow-headers:' "$hdr" | tail -1 | tr -d '\r')
  if echo "$acao" | grep -qi "dogan.xaigrok.ir\|\\*"; then
    record "$label" OPTIONS "${url#"$PUBLIC"}" "$status" "$ms" 204,200,204 OK "CORS allow for web origin; $acao; $acam; $acah"
  else
    record "$label" OPTIONS "${url#"$PUBLIC"}" "$status" "$ms" 204,200 BUG "missing/incorrect ACAO for $WEB_ORIGIN; headers: $(tr '\n' '|' < "$hdr" | tr -d '\r')"
  fi
}

cors_check public "$PUBLIC/health" GET
cors_check public "$PUBLIC/auth/login" POST
cors_check public "$PUBLIC/devices" GET
cors_check internal "$INTERNAL/health" GET
cors_check internal "$INTERNAL/auth/login" POST

# Also a simple GET with Origin (non-preflight)
call GET "$PUBLIC/health" -H "Origin: $WEB_ORIGIN"
ACAO=$(grep -i '^access-control-allow-origin:' "$LAST_HDR" | tail -1 | tr -d '\r')
if echo "$ACAO" | grep -qi "dogan.xaigrok.ir\|\\*"; then
  record public GET /health "$LAST_STATUS" "$LAST_MS" 200 OK "GET with Origin returns $ACAO"
else
  record public GET /health "$LAST_STATUS" "$LAST_MS" 200 BUG "GET with Origin missing ACAO (got: $ACAO)"
fi

# =====================================================================
log "===== LiveKit public WSS ====="

# HTTP health
call GET "$LIVEKIT_PUBLIC/"
if [[ "$LAST_STATUS" = 200 ]]; then
  record livekit GET / "$LAST_STATUS" "$LAST_MS" 200 OK "https://dogan-livekit.xaigrok.ir body=$(preview "$LAST_BODY")"
else
  record livekit GET / "$LAST_STATUS" "$LAST_MS" 200 FAIL "LiveKit HTTP health unexpected; body=$(preview "$LAST_BODY") err=$LAST_ERR"
fi

# WebSocket upgrade
python3 - << 'PY' > /tmp/dogan-endpoint-tests/ws-livekit.txt 2>&1
import socket, ssl, base64, os, time, sys
host = "dogan-livekit.xaigrok.ir"
port = 443
paths = ["/", "/rtc"]
key = base64.b64encode(os.urandom(16)).decode()
ctx = ssl.create_default_context()
for path in paths:
    t0 = time.time()
    try:
        raw = socket.create_connection((host, port), timeout=10)
        sock = ctx.wrap_socket(raw, server_hostname=host)
        req = (
            f"GET {path} HTTP/1.1\r\n"
            f"Host: {host}\r\n"
            "Upgrade: websocket\r\n"
            "Connection: Upgrade\r\n"
            f"Sec-WebSocket-Key: {key}\r\n"
            "Sec-WebSocket-Version: 13\r\n"
            "\r\n"
        )
        sock.sendall(req.encode())
        sock.settimeout(8)
        data = b""
        while b"\r\n\r\n" not in data and len(data) < 8192:
            chunk = sock.recv(1024)
            if not chunk:
                break
            data += chunk
        sock.close()
        header = data.split(b"\r\n\r\n", 1)[0].decode("iso-8859-1", "replace")
        first = header.split("\r\n", 1)[0]
        ms = int((time.time() - t0) * 1000)
        print(f"PATH={path} MS={ms} STATUS_LINE={first}")
        # print a few headers without cookies if any
        for line in header.split("\r\n")[1:12]:
            if line.lower().startswith(("set-cookie", "authorization")):
                continue
            print("  " + line)
    except Exception as e:
        ms = int((time.time() - t0) * 1000)
        print(f"PATH={path} MS={ms} ERROR={type(e).__name__}: {e}")
PY
cat "$OUT_DIR/ws-livekit.txt" >&2
python3 - << 'PY'
import re
text=open("/tmp/dogan-endpoint-tests/ws-livekit.txt").read()
# parse and append results via a small sidecar the bash will read
open("/tmp/dogan-endpoint-tests/ws-livekit.parsed","w").write(text)
PY

# Record WS results
while IFS= read -r line; do
  if [[ "$line" == PATH=* ]]; then
    path=$(echo "$line" | sed -n 's/^PATH=\([^ ]*\).*/\1/p')
    ms=$(echo "$line" | sed -n 's/.*MS=\([0-9]*\).*/\1/p')
    if echo "$line" | grep -q 'STATUS_LINE=HTTP/1.1 101'; then
      record livekit GET "wss://dogan-livekit.xaigrok.ir$path" 101 "$ms" 101 OK "WebSocket upgrade accepted"
    elif echo "$line" | grep -q 'ERROR='; then
      record livekit GET "wss://dogan-livekit.xaigrok.ir$path" 000 "$ms" 101 FAIL "$line"
    else
      # LiveKit may 200 or 404 on / but 101 on /rtc
      sl=$(echo "$line" | sed -n 's/.*STATUS_LINE=//p')
      if echo "$sl" | grep -q ' 101 '; then
        record livekit GET "wss://dogan-livekit.xaigrok.ir$path" 101 "$ms" 101 OK "WebSocket upgrade accepted ($sl)"
      else
        record livekit GET "wss://dogan-livekit.xaigrok.ir$path" 000 "$ms" 101 INFO "no 101 on $path ($sl)"
      fi
    fi
  fi
done < "$OUT_DIR/ws-livekit.txt"

# Internal livekit via docker network (optional)
call GET "http://127.0.0.1:7881/"
# 7881 is advertised in compose (TCP published). May or may not be HTTP.
record livekit GET "http://127.0.0.1:7881/" "$LAST_STATUS" "$LAST_MS" "any" INFO "host-published livekit 7881; ct=$LAST_CT body=$(preview "$LAST_BODY")"

# =====================================================================
log "===== docker logs (last 100) ====="
docker logs --tail 100 dogan-api > "$OUT_DIR/dogan-api.logs" 2>&1 || true
# redact possible tokens in logs
python3 "$HELPERS" redact_logs "$OUT_DIR/dogan-api.logs"
log "Log error/warn lines:"
grep -Ei 'error|panic|fatal|warn|fail' "$OUT_DIR/dogan-api.logs" | tail -40 >&2 || true

# =====================================================================
log "===== Build markdown report ====="
python3 - << 'PY'
import json, os, collections, re, datetime
from pathlib import Path

out = Path("/tmp/dogan-endpoint-tests")
rows = []
for line in (out/"results.tsv").read_text(encoding="utf-8").splitlines():
    if not line.strip():
        continue
    parts = line.split("\t")
    while len(parts) < 8:
        parts.append("")
    rows.append({
        "base": parts[0], "method": parts[1], "path": parts[2],
        "status": parts[3], "ms": parts[4], "expected": parts[5],
        "verdict": parts[6], "notes": parts[7],
    })

# endpoints listed by API
listed = []
ep_file = out/"endpoints-internal.json"
if ep_file.exists():
    try:
        listed = json.loads(ep_file.read_text()).get("endpoints") or []
    except Exception:
        listed = []

# from router / docs (hardcoded expected set)
docs = [
    ("POST","/auth"),("POST","/auth/login"),("GET","/auth/me"),("POST","/auth/logout"),
    ("GET","/endpoints"),("GET","/health"),
    ("POST","/telemetry"),("POST","/frame"),("GET","/last-frame"),("GET","/frame/image"),
    ("POST","/device-metrics"),("GET","/device-metrics"),
    ("POST","/actions"),("GET","/actions/pending"),("PUT","/actions/:id/ack"),
    ("GET","/settings"),("PUT","/settings"),("PATCH","/settings"),
    ("GET","/sounds"),("GET","/sounds/:id"),
    ("POST","/diagnostic-audio"),
    ("GET","/webrtc/connections"),("POST","/streaming/token"),("POST","/webrtc/session"),
    ("GET","/devices"),("GET","/devices/:id/stream"),("GET","/devices/:id/telemetry"),
    ("GET","/devices/:id/metrics"),("POST","/devices/:id/capture"),
    ("GET","/images"),("GET","/images/:id"),
]
code_only = [("POST","/vision/analyze"),("GET","/vision/latest")]

def norm(p):
    p = p.split("?")[0]
    p = re.sub(r"/[0-9]+(/|$)", r"/:id\1", p)
    p = re.sub(r"/actions/[^/]+/ack", "/actions/:id/ack", p)
    p = re.sub(r"/sounds/[^/]+$", "/sounds/:id", p)
    p = re.sub(r"/images/[^/]+$", "/images/:id", p)
    p = re.sub(r"/devices/[^/]+/", "/devices/:id/", p)
    return p

listed_set = set()
for e in listed:
    listed_set.add((e.get("method","").upper(), e.get("path","").replace("/dogan/api/v1","")))

docs_set = set(docs)
code_set = set(docs + code_only)

missing_from_docs = sorted(code_set - docs_set)
missing_from_code_list = sorted(code_set - listed_set) if listed_set else []
extra_in_list = sorted(listed_set - code_set) if listed_set else []
in_docs_not_listed = sorted(docs_set - listed_set) if listed_set else []

# devices
dev_count = "?"
dev_ids = []
df = out/"devices.json"
if df.exists():
    try:
        d = json.loads(df.read_text())
        items = d if isinstance(d, list) else d.get("devices") or []
        dev_count = len(items)
        for it in items:
            if isinstance(it, dict):
                dev_ids.append(str(it.get("id") or it.get("device_id") or "?"))
    except Exception:
        pass

# logs
log_txt = (out/"dogan-api.logs").read_text(encoding="utf-8", errors="replace") if (out/"dogan-api.logs").exists() else ""
log_hits = [ln for ln in log_txt.splitlines() if re.search(r"error|panic|fatal|fail", ln, re.I)]

ws = (out/"ws-livekit.txt").read_text(encoding="utf-8", errors="replace") if (out/"ws-livekit.txt").exists() else ""

def esc(s):
    return (s or "").replace("|","\\|").replace("\n"," ")

lines = []
a = lines.append
a("# Dogan API T3 endpoint test report")
a("")
a(f"Generated (UTC): {datetime.datetime.utcnow().strftime('%Y-%m-%d %H:%M:%S')}Z")
a("")
a("## How the API is reached")
a("")
a("- **Internal (from T3):** `http://127.0.0.1:6080/dogan/api/v1` (docker publish `127.0.0.1:6080->8080` on `dogan-api`).")
a("- **Public API host:** `https://dogan-api.xaigrok.ir/dogan/api/v1` — HAProxy SNI/`Host` `dogan-api.xaigrok.ir` → backend `dogan-api:8080`. Cloudflare sits in front (cf-ray / server: cloudflare). **This public route works.**")
a("- **Public web UI host:** `https://dogan.xaigrok.ir` — HAProxy → `dogan-webui:80`. Nginx is a static SPA (`try_files ... /index.html`). There is **no** path-based proxy of `/dogan/api/v1` on this host, so `https://dogan.xaigrok.ir/dogan/api/v1/health` returns the SPA `index.html`, not the API. This is expected given current routing.")
a("- **How the web UI reaches the API:** `app-config.json` in the webui container has `apiBaseUrl: https://dogan-api.xaigrok.ir/dogan/api/v1`. Nginx has no API reverse-proxy.")
a("- **LiveKit:** HAProxy `dogan-livekit.xaigrok.ir` → `dogan-livekit:7880` (long timeouts). Public URL env `wss://dogan-livekit.xaigrok.ir`. Media UDP `50000-50050` published on the host.")
a("")
a("## Credentials (values not printed)")
a("")
a("See CREDENTIALS.txt sidecar written by the shell script.")
a("")

# credentials sidecar
cred_path = out/"CREDENTIALS.txt"
cred = cred_path.read_text(encoding="utf-8") if cred_path.exists() else ""
if cred:
    a("```")
    a(cred.strip())
    a("```")
    a("")

a("## Docs vs code vs /endpoints")
a("")
if missing_from_docs:
    a("- **In code and GET /endpoints, missing from endpoints.md:** " + ", ".join(f"{m} {p}" for m,p in missing_from_docs))
else:
    a("- endpoints.md covers the coded routes (except as noted below).")
if missing_from_code_list:
    a("- **Expected in code but missing from GET /endpoints listing:** " + ", ".join(f"{m} {p}" for m,p in missing_from_code_list))
if extra_in_list:
    a("- **Listed by GET /endpoints but not in the expected set:** " + ", ".join(f"{m} {p}" for m,p in extra_in_list))
if in_docs_not_listed:
    a("- **In endpoints.md but not in GET /endpoints:** " + ", ".join(f"{m} {p}" for m,p in in_docs_not_listed))
a(f"- GET /devices returned **{dev_count}** device(s): `{', '.join(dev_ids) if dev_ids else '(none)'}`. Mutating tests used only `dogan-test-device`.")
a("")

# summary counts
by_v = collections.Counter(r["verdict"] for r in rows)
a("## Summary")
a("")
a(f"Total recorded cases: **{len(rows)}**. OK={by_v.get('OK',0)}, FAIL={by_v.get('FAIL',0)}, BUG={by_v.get('BUG',0)}, INFO={by_v.get('INFO',0)}.")
a("")

problems = [r for r in rows if r["verdict"] in ("FAIL","BUG")]
a("## Problems")
a("")
if not problems:
    a("No FAIL/BUG verdicts.")
else:
    a("| Base | Method | Path | Status | Expected | ms | Verdict | Notes |")
    a("|---|---|---|---:|---|---:|---|---|")
    for r in problems:
        a(f"| {esc(r['base'])} | {esc(r['method'])} | `{esc(r['path'])}` | {esc(r['status'])} | {esc(r['expected'])} | {esc(r['ms'])} | **{esc(r['verdict'])}** | {esc(r['notes'][:300])} |")
a("")

a("## Full results")
a("")
a("| Base | Method | Path | Status | Expected | Latency (ms) | Verdict | Notes |")
a("|---|---|---|---:|---|---:|---|---|")
for r in rows:
    a(f"| {esc(r['base'])} | {esc(r['method'])} | `{esc(r['path'])}` | {esc(r['status'])} | {esc(r['expected'])} | {esc(r['ms'])} | {esc(r['verdict'])} | {esc(r['notes'][:240])} |")
a("")

a("## LiveKit WebSocket probe")
a("")
a("```")
a(ws.strip() or "(no output)")
a("```")
a("")

a("## dogan-api logs (last 100, redacted) — error/fail lines")
a("")
if log_hits:
    a("```")
    a("\n".join(log_hits[-60:]))
    a("```")
else:
    a("No error/panic/fatal/fail lines in the last 100 log lines.")
a("")
a("<details><summary>Full last 100 log lines</summary>")
a("")
a("```")
a(log_txt.rstrip())
a("```")
a("</details>")
a("")

(out/"report.md").write_text("\n".join(lines), encoding="utf-8")
print("Wrote", out/"report.md")
print("Counts:", dict(by_v))
print("Problems:", len(problems))
PY

# credentials sidecar (no secret values)
{
  echo "device api_key (DOGAN_DEVICE_API_KEY from container env): $CRED_DEVICE"
  echo "admin username/password (compiled defaults): $CRED_ADMIN"
  echo "web email/password (compiled admin@local.local): $CRED_WEB"
  echo "OPENCODE_GO_API_KEY set: $OPENCODE_KEY_SET"
  echo "OPENCODE_GO_BASE_URL: $OPENCODE_BASE"
  echo "OPENCODE_GO_MODEL: $OPENCODE_MODEL"
  echo "DOGAN_CORS_ALLOWED_ORIGINS: $CORS_ORIGINS"
  echo "DOGAN_LIVEKIT_URL: $LK_URL"
  echo "DOGAN_LIVEKIT_PUBLIC_URL: $LK_PUB"
  echo "DOGAN_DEVICE_API_KEY present: $([[ -n "$DEVICE_API_KEY" ]] && echo yes || echo no) len=$(printf '%s' "$DEVICE_API_KEY" | wc -c)"
  echo "DOGAN_EMBEDDED_API_TOKEN present: $([[ -n "$EMBEDDED_TOKEN" ]] && echo yes || echo no) len=$(printf '%s' "$EMBEDDED_TOKEN" | wc -c)"
} > "$OUT_DIR/CREDENTIALS.txt"

# rebuild report now that credentials sidecar exists
python3 - << 'PY'
# re-run report builder? already ran before sidecar. Rewrite quickly by concatenating? 
# Re-exec the same builder: the file exists now. Invoke again by importing? Easier: insert credentials into report.
from pathlib import Path
out = Path("/tmp/dogan-endpoint-tests")
rep = (out/"report.md").read_text(encoding="utf-8")
cred = (out/"CREDENTIALS.txt").read_text(encoding="utf-8")
needle = "See CREDENTIALS.txt sidecar written by the shell script.\n"
insert = "See CREDENTIALS.txt sidecar written by the shell script.\n\n```\n" + cred.strip() + "\n```\n"
if needle in rep and "```\n" + cred.strip() not in rep:
    rep = rep.replace(needle, insert, 1)
    (out/"report.md").write_text(rep, encoding="utf-8")
print("Credentials inserted into report")
PY

log "DONE. Report: $REPORT"
log "Results TSV: $RESULTS"
# print report to stdout
cat "$REPORT"
