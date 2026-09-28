#!/usr/bin/env python3
"""Mint a DEV-ONLY HS256 JWT accepted by the gateway's local shared-secret decoder.

  python3 scripts/dev_token.py                       # default user
  python3 scripts/dev_token.py --sub <uuid> --ttl 600
Secret comes from GATEWAY_JWT_SECRET (same default as application.yml).
"""
import argparse, base64, hashlib, hmac, json, os, time

DEFAULT_SECRET = "dev-only-shared-secret-change-me-0123456789abcdef"


def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def mint(sub: str, secret: str, ttl: int, scope: str) -> str:
    now = int(time.time())
    header = {"alg": "HS256", "typ": "JWT"}
    claims = {"sub": sub, "iat": now, "exp": now + ttl, "scope": scope}
    signing_input = f"{b64url(json.dumps(header, separators=(',', ':')).encode())}." \
                    f"{b64url(json.dumps(claims, separators=(',', ':')).encode())}"
    sig = hmac.new(secret.encode(), signing_input.encode(), hashlib.sha256).digest()
    return f"{signing_input}.{b64url(sig)}"


if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("--sub", default="11111111-1111-1111-1111-111111111111", help="user id; must be a UUID")
    p.add_argument("--ttl", type=int, default=3600)
    p.add_argument("--scope", default="orders")
    a = p.parse_args()
    print(mint(a.sub, os.environ.get("GATEWAY_JWT_SECRET", DEFAULT_SECRET), a.ttl, a.scope))
