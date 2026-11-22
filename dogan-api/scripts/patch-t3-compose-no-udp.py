#!/usr/bin/env python3
from pathlib import Path

p = Path("/cloud-admin/docker/dogan/docker-compose.yml")
t = p.read_text()
old = '''    expose:
      - "8889"
      - "8888"
      - "9997"
    ports:
      - "8889:8889"
      - "8888:8888"
      - "8189:8189/udp"
      - "8189:8189/tcp"
'''
new = '''    expose:
      - "8889"
      - "8888"
      - "9997"
    ports:
      - target: 8189
        published: 8189
        protocol: udp
      - target: 8189
        published: 8189
        protocol: tcp
'''
if "8889:8889" not in t and "target: 8189" in t:
    print("already_patched")
elif old not in t:
    raise SystemExit("ports block not found")
else:
    p.write_text(t.replace(old, new, 1))
    print("compose_ports_removed")
