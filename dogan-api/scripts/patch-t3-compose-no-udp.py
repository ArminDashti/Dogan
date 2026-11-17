#!/usr/bin/env python3
from pathlib import Path

p = Path("/cloud-admin/docker/dogan/docker-compose.yml")
t = p.read_text()
old = '''    expose:
      - "7880"
      - "7881"
      - "7882"
    ports:
      - "7881:7881"
      - "50000-50050:50000-50050/udp"
'''
new = '''    expose:
      - "7880"
      - "7881"
      - "7882"
      - "5349"
    # Public media via TURN on 443 (dogan-turn); do not publish UDP/7881
'''
if "5349" in t and "50000-50050" not in t:
    print("already_patched")
elif old not in t:
    raise SystemExit("ports block not found")
else:
    p.write_text(t.replace(old, new, 1))
    print("compose_ports_removed")
