#!/usr/bin/env python3
"""Verify dogan-turn on public 443: TLS/SNI reachability + TURN ALLOCATE (not WebRTC ICE).

Level 1 (tls): TCP 443 + TLS + correct SNI - HAProxy path only.
Level 2 (allocate): TURN REST credentials + coturn turnutils_uclient.
Level 3 (e2e): Watchman WHIP publish + WebUI WHEP play (manual; this script does not run it).

Exit 0 when tls + allocate pass; 1 on usage/unknown failure; 2 tls fail; 3 allocate fail.
"""
from __future__ import annotations

import argparse
import base64
import hashlib
import hmac
import os
import shutil
import socket
import ssl
import subprocess
import sys
import time

DEFAULT_HOST = "dogan-turn.xaigrok.ir"
DEFAULT_PORT = 443
DEFAULT_IDENTITY = "dogan-turn-verify"
DEFAULT_TTL_SECONDS = 300


def turn_rest_credentials(secret: str, identity: str = DEFAULT_IDENTITY, ttl: int = DEFAULT_TTL_SECONDS) -> tuple[str, str]:
    expiry = int(time.time()) + ttl
    username = f"{expiry}:{identity}"
    digest = hmac.new(secret.encode(), username.encode(), hashlib.sha1).digest()
    return username, base64.b64encode(digest).decode()


def tls_sni_probe(host: str, port: int, *, insecure: bool, timeout: float = 12.0) -> tuple[bool, str]:
    ctx = ssl.create_default_context()
    if insecure:
        ctx.check_hostname = False
        ctx.verify_mode = ssl.CERT_NONE
    try:
        with socket.create_connection((host, port), timeout=timeout) as raw:
            with ctx.wrap_socket(raw, server_hostname=host) as sock:
                peer = sock.getpeercert()
                subj = dict(x[0] for x in peer.get("subject", ()))
                cn = subj.get("commonName", "")
                if cn and cn != host and not cn.endswith(host.split(".", 1)[-1]):
                    return True, f"TLS OK (peer CN={cn!r})"
                return True, f"TLS OK (peer CN={cn or 'n/a'})"
    except Exception as exc:
        return False, f"TLS/SNI failed: {type(exc).__name__}: {exc}"


def _turn_client_prefix() -> list[str] | None:
    exe = shutil.which("turnutils_uclient")
    if exe:
        return [exe]
    docker = shutil.which("docker")
    if not docker:
        return None
    prefix = [docker, "run", "--rm", "--entrypoint", "turnutils_uclient"]
    if sys.platform != "win32":
        prefix += ["--network", "host"]
    prefix.append("coturn/coturn:4.19")
    return prefix


def turn_allocate(
    host: str,
    port: int,
    username: str,
    password: str,
    timeout: int = 60,
) -> tuple[bool, str]:
    base = _turn_client_prefix()
    if not base:
        return False, "turnutils_uclient not found (install coturn or Docker)"
    peer = os.environ.get("DOGAN_TURN_TEST_PEER", "8.8.8.8")
    cmd = [
        *base,
        "-v",
        "-S",
        "-p",
        str(port),
        "-u",
        username,
        "-w",
        password,
        "-e",
        peer,
        "-n",
        "1",
        host,
    ]
    try:
        proc = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            timeout=timeout,
        )
    except subprocess.TimeoutExpired:
        return False, "turnutils_uclient timed out"
    except OSError as exc:
        return False, f"turnutils_uclient failed to start: {exc}"
    out = (proc.stdout or "") + (proc.stderr or "")
    low = out.lower()
    if proc.returncode == 0 and ("allocate" in low or "relay" in low or "success" in low):
        return True, "TURN allocate OK (turnutils_uclient)"
    if proc.returncode == 0:
        return True, "turnutils_uclient exited 0"
    tail = " ".join(out.strip().split())[-400:]
    return False, f"turnutils_uclient rc={proc.returncode}; tail={tail!r}"


def _env_or(arg: str | None, name: str, default: str) -> str:
    if arg:
        return arg
    return os.environ.get(name, default)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--host", default=_env_or(None, "DOGAN_TURN_HOST", DEFAULT_HOST))
    parser.add_argument("--port", type=int, default=int(os.environ.get("DOGAN_TURN_PORT", str(DEFAULT_PORT))))
    parser.add_argument("--secret", default=_env_or(None, "DOGAN_TURN_SECRET", ""))
    parser.add_argument("--identity", default=_env_or(None, "DOGAN_TURN_IDENTITY", DEFAULT_IDENTITY))
    parser.add_argument("--ttl", type=int, default=int(os.environ.get("DOGAN_TURN_TTL", str(DEFAULT_TTL_SECONDS))))
    parser.add_argument("--skip-allocate", action="store_true", help="TLS/SNI probe only (level 1)")
    parser.add_argument(
        "--tls-insecure",
        action=argparse.BooleanOptionalAction,
        default=True,
        help="Skip TLS cert verify (T3 uses self-signed dogan-turn PEM; default true)",
    )
    args = parser.parse_args()

    if not args.secret and not args.skip_allocate:
        print("DOGAN_TURN_SECRET or --secret is required for TURN allocate")
        return 1

    print(f"[1/2] TLS/SNI {args.host}:{args.port} ...")
    tls_ok, tls_msg = tls_sni_probe(args.host, args.port, insecure=args.tls_insecure)
    print(f"      {'PASS' if tls_ok else 'FAIL'}: {tls_msg}")
    if not tls_ok:
        print("Note: TLS pass does not prove TURN allocation or WebRTC ICE.")
        return 2

    if args.skip_allocate:
        print("Skipped TURN allocate (--skip-allocate). Does not prove ICE or Watchman/WebUI play.")
        return 0

    user, passwd = turn_rest_credentials(args.secret, args.identity, args.ttl)
    print(f"[2/2] TURN allocate (turns://{args.host}:{args.port}) ...")
    alloc_ok, alloc_msg = turn_allocate(args.host, args.port, user, passwd)
    print(f"      {'PASS' if alloc_ok else 'FAIL'}: {alloc_msg}")
    if not alloc_ok:
        print("Full WebRTC ICE still needs Watchman publish + WebUI play (level 3).")
        return 3

    print("Levels 1-2 pass. Level 3: Watchman connected + mediaTrack published; WebUI play shows video.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
