"""Post-deployment smoke test (used by the CD workflow and by hand).

    python manali_devops/tests/smoke/smoke_test.py --base-url http://<alb-dns>            # strict
    python manali_devops/tests/smoke/smoke_test.py --base-url http://localhost:8080 --api-only
    --allow-missing   treat 404/502/503 from services that are not implemented yet as warnings

Checks: web health, core-api reachable through the ALB (/api/v1/*), login with a seed user,
/users/me, /departments, and (if allowed) one chat question. Exit code 0 = healthy.
"""
import argparse
import json
import sys
from pathlib import Path

import requests

SEED = json.loads((Path(__file__).resolve().parents[3] / "resources/seed/users.json").read_text())
MISSING = {404, 501, 502, 503, 504}


class Smoke:
    def __init__(self, base, allow_missing):
        self.base, self.allow_missing, self.failed, self.warned = base.rstrip("/"), allow_missing, 0, 0

    def check(self, name, resp, ok=(200,)):
        if resp.status_code in ok:
            print(f"PASS {name} ({resp.status_code}, {resp.elapsed.total_seconds() * 1000:.0f} ms)")
            return True
        if self.allow_missing and resp.status_code in MISSING:
            print(f"WARN {name}: {resp.status_code} (not deployed yet?)")
            self.warned += 1
            return False
        print(f"FAIL {name}: {resp.status_code} {resp.text[:200]}")
        self.failed += 1
        return False

    def get(self, path, **kw):
        return requests.get(self.base + path, timeout=15, **kw)

    def post(self, path, **kw):
        return requests.post(self.base + path, timeout=60, **kw)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base-url", required=True)
    ap.add_argument("--allow-missing", action="store_true")
    ap.add_argument("--api-only", action="store_true", help="base URL is core-api itself (skip web)")
    ap.add_argument("--ask", default="How many PTO days can I carry over?")
    args = ap.parse_args()

    s = Smoke(args.base_url, args.allow_missing)
    if not args.api_only:
        s.check("web /api/health", s.get("/api/health"))

    user = next(u for u in SEED["users"] if u["email"].startswith("hannah.hr"))
    login = s.post("/api/v1/auth/login", json={"email": user["email"], "password": SEED["devPassword"]})
    if s.check("core-api login", login):
        headers = {"Authorization": f"Bearer {login.json()['accessToken']}"}
        me = s.get("/api/v1/users/me", headers=headers)
        if s.check("GET /api/v1/users/me", me):
            allowed = set(me.json().get("allowedDepartments", []))
            if allowed != {"HR", "GENERAL"}:
                print(f"FAIL allowedDepartments for an HR employee should be HR+GENERAL, got {allowed}")
                s.failed += 1
        s.check("GET /api/v1/departments", s.get("/api/v1/departments", headers=headers))
        chat = s.post("/api/v1/chat", json={"message": args.ask}, headers=headers)
        if s.check("POST /api/v1/chat", chat):
            body = chat.json()
            print(f"     answered={body.get('answered')} citations={len(body.get('citations', []))}")
    bad = s.post("/api/v1/auth/login", json={"email": user["email"], "password": "wrong-password"})
    s.check("login rejects wrong password", bad, ok=(401,))

    print(f"\n{s.failed} failed, {s.warned} warnings")
    sys.exit(1 if s.failed else 0)


if __name__ == "__main__":
    main()
