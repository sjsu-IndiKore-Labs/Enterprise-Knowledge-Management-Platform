#!/usr/bin/env python3
"""Demo-readiness check: walks docs/demo-script.md end to end against one environment. Owner: Manali.

    python manali_devops/tests/smoke/demo_check.py --api http://localhost:8080            # local (make up-apps)
    python manali_devops/tests/smoke/demo_check.py --api http://<alb-dns>                 # staging (ALB routes /api/v1)

Steps (each prints PASS/FAIL/WARN, exit code 1 if any FAIL):
  1. login as the demo users           5. versioning: upload v2, wait INDEXED
  2. upload HR-001 PDF → INDEXED       6. RBAC: eric.eng gets no HR citations
  3. upload a DOCX → accepted           7. admin analytics / knowledge gaps / audit logs respond
  4. search + chat (cited) + stream
With AI_PROVIDER=fake, chat may answer "not found" (fake embeddings): that is a WARN, not a FAIL.
Uploads create new documents every run; use a throwaway DB or delete them afterwards.
"""
import argparse
import json
import sys
import time
from pathlib import Path

import requests

ROOT = Path(__file__).resolve().parents[3]
SEED = json.loads((ROOT / "resources/seed/users.json").read_text())
FILES = ROOT / "resources/corpus/files"
PDF, DOCX = "application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
results = []


def report(status, name, detail=""):
    results.append(status)
    print(f"{status:4} {name}" + (f"  ({detail})" if detail else ""))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--api", required=True, help="core-api base URL (or the ALB URL)")
    ap.add_argument("--timeout", type=int, default=300, help="seconds to wait for INDEXED")
    a = ap.parse_args()
    api = a.api.rstrip("/")

    def login(email):
        r = requests.post(f"{api}/api/v1/auth/login", json={"email": email, "password": SEED["devPassword"]}, timeout=15)
        r.raise_for_status()
        return {"Authorization": f"Bearer {r.json()['accessToken']}"}

    def upload(h, path, mime, title, dept, category, version_of=None):
        files = {"file": (path.name, path.open("rb"), mime)}
        if version_of:
            return requests.post(f"{api}/api/v1/documents/{version_of}/versions", headers=h, files=files,
                                 data={"changeNote": "demo_check"}, timeout=60)
        return requests.post(f"{api}/api/v1/documents", headers=h, files=files, timeout=60,
                             data={"title": title, "department": dept, "category": category, "author": "demo_check"})

    def wait_indexed(h, doc_id, version=1):
        end, doc = time.time() + a.timeout, {}
        while time.time() < end:
            doc = requests.get(f"{api}/api/v1/documents/{doc_id}", headers=h, timeout=15).json()
            if doc.get("status") in ("INDEXED", "FAILED") and doc.get("currentVersion", version) >= version:
                break
            time.sleep(3)
        return doc

    # 1. login
    try:
        hr_mgr, hr, eng, admin = (login(e) for e in ("harper.hrmgr@helios.example.com", "hannah.hr@helios.example.com",
                                                     "eric.eng@helios.example.com", "alice.admin@helios.example.com"))
        report("PASS", "login demo users")
    except Exception as e:  # noqa: BLE001
        report("FAIL", "login demo users", str(e)[:200])
        return finish()

    # 2. upload PDF and wait for indexing
    pdf = next(FILES.glob("HR-001*.pdf"))
    r = upload(hr_mgr, pdf, PDF, "PTO and Leave Policy (demo_check)", "HR", "POLICY")
    if not 200 <= r.status_code < 300:
        report("FAIL", "upload PDF", f"HTTP {r.status_code} {r.text[:150]}")
        return finish()
    doc_id = r.json()["id"]
    doc = wait_indexed(hr_mgr, doc_id)
    report("PASS" if doc.get("status") == "INDEXED" else "FAIL", "PDF reaches INDEXED",
           f"status={doc.get('status')} error={doc.get('errorCode') or doc.get('errorMessage') or '-'}")

    # 3. DOCX upload accepted
    docx = next(FILES.glob("*.docx"))
    r = upload(hr_mgr, docx, DOCX, "DOCX upload (demo_check)", "HR", "SOP")
    report("PASS" if 200 <= r.status_code < 300 else "FAIL", "upload DOCX", f"HTTP {r.status_code}")

    # 4. search, chat, stream
    q = "How many PTO days can I carry over?"
    r = requests.post(f"{api}/api/v1/search", headers=hr, json={"query": q}, timeout=30)
    report("PASS" if r.ok else "FAIL", "POST /search", f"HTTP {r.status_code}")
    r = requests.post(f"{api}/api/v1/chat", headers=hr, json={"message": q}, timeout=90)
    if not r.ok:
        report("FAIL", "POST /chat", f"HTTP {r.status_code} {r.text[:150]}")
    else:
        j = r.json()
        cited = [c for c in j.get("citations", []) if c.get("department") == "HR"]
        report("PASS" if j.get("answered") and cited else "WARN", "chat answered with HR citation",
               f"answered={j.get('answered')} citations={len(j.get('citations', []))}")
    r = requests.post(f"{api}/api/v1/chat/stream", headers={**hr, "Accept": "text/event-stream"},
                      json={"message": q}, stream=True, timeout=90)
    events = {line.decode()[6:].strip() for line in r.iter_lines() if line.startswith(b"event:")}
    report("PASS" if r.ok and {"meta", "done"} <= events else "FAIL", "chat/stream SSE", f"events={sorted(events)}")

    # 5. versioning
    r = upload(hr_mgr, pdf, PDF, None, None, None, version_of=doc_id)
    if 200 <= r.status_code < 300:
        doc = wait_indexed(hr_mgr, doc_id, version=2)
        report("PASS" if doc.get("status") == "INDEXED" and doc.get("currentVersion") == 2 else "FAIL",
               "new version reaches INDEXED", f"status={doc.get('status')} currentVersion={doc.get('currentVersion')}")
    else:
        report("FAIL", "upload new version", f"HTTP {r.status_code}")

    # 6. RBAC: engineering user must never see HR content
    r = requests.post(f"{api}/api/v1/chat", headers=eng, json={"message": "What is the salary range for E3? PTO policy?"},
                      timeout=90)
    leaked = [c for c in (r.json().get("citations", []) if r.ok else []) if c.get("department") == "HR"]
    report("PASS" if r.ok and not leaked else "FAIL", "RBAC: no HR citations for ENGINEERING user",
           f"HTTP {r.status_code} leaked={len(leaked)}")
    r = requests.get(f"{api}/api/v1/documents/{doc_id}", headers=eng, timeout=15)
    report("PASS" if r.status_code == 404 else "FAIL", "RBAC: HR document hidden from ENGINEERING (404)",
           f"HTTP {r.status_code}")

    # 7. admin pages
    for path in ("analytics/summary", "analytics/top-queries", "knowledge-gaps", "audit-logs", "users"):
        r = requests.get(f"{api}/api/v1/admin/{path}", headers=admin, timeout=30)
        report("PASS" if r.ok else "FAIL", f"GET /admin/{path}", f"HTTP {r.status_code}")
    r = requests.get(f"{api}/api/v1/admin/users", headers=hr, timeout=15)
    report("PASS" if r.status_code == 403 else "FAIL", "non-admin gets 403 on /admin", f"HTTP {r.status_code}")
    return finish()


def finish():
    print(f"\n{results.count('PASS')} passed, {results.count('WARN')} warnings, {results.count('FAIL')} failed")
    return 1 if "FAIL" in results else 0


if __name__ == "__main__":
    sys.exit(main())
