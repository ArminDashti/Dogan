#!/usr/bin/env bash
# Fail if Android app source references OpenCode / LLM provider hosts or API keys.
# Vision belongs on parkiroid-api only (Android → Dogan API → OpenCode).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PATTERN='opencode\.ai|OPENCODE_GO_|oc_sk_|openrouter\.ai|api\.openai\.com'
HITS="$(
  rg -n \
    --glob '*.kt' --glob '*.java' --glob '*.xml' --glob '*.properties' \
    --glob '*.gradle' --glob '*.gradle.kts' --glob '*.pro' \
    "$PATTERN" "$ROOT/app" 2>/dev/null || true
)"
# Also catch root gradle files next to the module (if any).
ROOT_HITS="$(
  rg -n "$PATTERN" "$ROOT"/*.gradle* 2>/dev/null || true
)"
ALL="$(printf '%s\n%s' "$HITS" "$ROOT_HITS" | sed '/^$/d' || true)"
if [[ -n "$ALL" ]]; then
  echo "Android must not call OpenCode; hits:" >&2
  echo "$ALL" >&2
  exit 1
fi
echo "OK: no OpenCode/LLM provider references in Android app source"
