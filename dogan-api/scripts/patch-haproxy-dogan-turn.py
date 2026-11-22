#!/usr/bin/env python3
"""Idempotent HAProxy patch for dogan-turn SNI to coturn TURN :5349."""
from pathlib import Path

CFG = Path("/cloud-admin/docker-volumes/reverse-proxy/haproxy/config/haproxy.cfg")
text = CFG.read_text()

needle = "    use_backend be_ssl_offload if { req_ssl_sni -i dogan-turn.xaigrok.ir }\n"
insert = needle + "    use_backend be_dogan_turn_edge if { req_ssl_sni -i dogan-turn.xaigrok.ir }\n"
block = """
# Dogan TURN TLS (443 SNI) to plain TURN :5349
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
    server dogan_turn dogan-turn:5349 check resolvers docker resolve-prefer ipv4
"""

changed = False
if "be_dogan_turn_edge" not in text:
    if needle not in text:
        raise SystemExit("needle for dogan-turn SNI not found")
    text = text.replace(needle, insert, 1)
    changed = True

if "backend be_dogan_turn\n" not in text and "backend be_dogan_turn\r\n" not in text:
    text = text.rstrip() + "\n" + block + "\n"
    changed = True

if changed:
    CFG.write_text(text)
    print("patched")
else:
    print("already_patched")
