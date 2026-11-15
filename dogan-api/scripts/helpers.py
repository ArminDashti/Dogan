#!/usr/bin/env python3
import json, re, sys

def redact_text(s: str) -> str:
    s = re.sub(
        r'("(?:token|access_token|refresh_token|api_key|password|secret|authorization|credential)"\s*:\s*")[^"]*(")',
        r"\1<redacted>\2",
        s,
        flags=re.I,
    )
    s = re.sub(r"(Bearer )[A-Za-z0-9._\-+/=]+", r"\1<redacted>", s, flags=re.I)
    return s

def cmd_redact():
    sys.stdout.write(redact_text(sys.stdin.read()))

def cmd_preview():
    p = sys.argv[2]
    try:
        data = open(p, "rb").read()
    except FileNotFoundError:
        print("(no body)")
        return
    if b"\x00" in data[:200] or data.startswith(b"\xff\xd8") or data.startswith(b"RIFF") or data.startswith(b"\x89PNG"):
        print(f"(binary {len(data)} bytes, magic={data[:8]!r})")
        return
    t = data.decode("utf-8", "replace")
    if len(t) > 240:
        t = t[:240] + "..."
    print(redact_text(t.replace("\n", " ")))

def _load(path):
    return json.load(open(path, encoding="utf-8"))

def _get(o, path):
    cur = o
    for part in path.split("."):
        if part.endswith("[]"):
            part = part[:-2]
            return isinstance(cur, dict) and part in cur and isinstance(cur[part], list)
        if isinstance(cur, dict) and part in cur:
            cur = cur[part]
        else:
            return None
    return cur

def cmd_has():
    try:
        data = _load(sys.argv[2])
    except Exception:
        sys.exit(1)
    cur = _get(data, sys.argv[3])
    sys.exit(0 if cur is not None and cur != "" else 1)

def cmd_get():
    try:
        data = _load(sys.argv[2])
    except Exception:
        print("")
        return
    cur = _get(data, sys.argv[3])
    if cur is None:
        print("")
    else:
        print(cur)

def cmd_ms():
    print(int(round(float(sys.argv[2]) * 1000)))

def cmd_device_count():
    d = _load(sys.argv[2])
    items = d if isinstance(d, list) else d.get("devices", [])
    print(len(items))

def cmd_device_ids():
    d = _load(sys.argv[2])
    items = d if isinstance(d, list) else d.get("devices", [])
    ids = []
    for it in items:
        if isinstance(it, dict):
            ids.append(str(it.get("id") or it.get("device_id") or it.get("device_name") or "?"))
        else:
            ids.append(str(it))
    print(",".join(ids[:20]))

def cmd_first_sound():
    try:
        d = _load(sys.argv[2])
    except Exception:
        print("")
        return
    items = d.get("sounds") if isinstance(d, dict) else d
    if items and isinstance(items, list) and items:
        it = items[0]
        print(it.get("id") if isinstance(it, dict) else it)
    else:
        print("")

def cmd_first_image():
    try:
        d = _load(sys.argv[2])
    except Exception:
        print("")
        return
    want = sys.argv[3] if len(sys.argv) > 3 else ""
    items = d if isinstance(d, list) else d.get("images") or d.get("items") or []
    chosen = ""
    if isinstance(items, list):
        for it in items:
            if not isinstance(it, dict):
                continue
            iid = str(it.get("id") or "")
            if it.get("device_id") == want:
                chosen = iid
                break
            if not chosen:
                chosen = iid
    print(chosen)

def cmd_pending_ids():
    src, dest = sys.argv[2], sys.argv[3]
    try:
        d = _load(src)
    except Exception:
        open(dest, "w").write("")
        return
    items = d.get("actions") if isinstance(d, dict) else d
    ids = []
    if isinstance(items, list):
        for it in items:
            if isinstance(it, dict) and it.get("id") is not None:
                ids.append(str(it["id"]))
    open(dest, "w").write("\n".join(ids))

def cmd_patch_tmp():
    orig = _load(sys.argv[2])
    unit = orig.get("temperature_unit") or "C"
    new = "F" if str(unit).upper() in ("C", "CELSIUS") else "C"
    print(json.dumps({"temperature_unit": new}))

def cmd_restore_body():
    orig = _load(sys.argv[2])
    body = {}
    for k in ("notifications_enabled", "temperature_unit", "noise_alert_threshold_db", "default_device_id"):
        if k in orig:
            body[k] = orig[k]
    print(json.dumps(body))

def cmd_settings_compare():
    orig = _load(sys.argv[2])
    now = _load(sys.argv[3])
    dest = sys.argv[4]
    keys = ("notifications_enabled", "temperature_unit", "noise_alert_threshold_db", "default_device_id")
    mism = []
    for k in keys:
        if orig.get(k) != now.get(k):
            mism.append(f"{k}: {orig.get(k)!r}->{now.get(k)!r}")
    open(dest, "w").write("restored" if not mism else "MISMATCH " + ", ".join(mism))

def cmd_redact_token_file():
    p = sys.argv[2]
    d = _load(p)
    if "token" in d:
        tok = d["token"]
        d["token"] = f"<redacted len={len(tok)}>" if isinstance(tok, str) else "<redacted>"
    json.dump(d, open(p, "w"), indent=2)

def cmd_redact_logs():
    p = sys.argv[2]
    t = open(p, encoding="utf-8", errors="replace").read()
    t = re.sub(r"Bearer [A-Za-z0-9._\-+/=]+", "Bearer <redacted>", t)
    t = re.sub(r"(api_key|password|token|secret)=([^\s&]+)", r"\1=<redacted>", t, flags=re.I)
    open(p, "w", encoding="utf-8").write(t)

COMMANDS = {
    "redact": cmd_redact,
    "preview": cmd_preview,
    "has": cmd_has,
    "get": cmd_get,
    "ms": cmd_ms,
    "device_count": cmd_device_count,
    "device_ids": cmd_device_ids,
    "first_sound": cmd_first_sound,
    "first_image": cmd_first_image,
    "pending_ids": cmd_pending_ids,
    "patch_tmp": cmd_patch_tmp,
    "restore_body": cmd_restore_body,
    "settings_compare": cmd_settings_compare,
    "redact_token_file": cmd_redact_token_file,
    "redact_logs": cmd_redact_logs,
}

if __name__ == "__main__":
    cmd = sys.argv[1]
    COMMANDS[cmd]()
