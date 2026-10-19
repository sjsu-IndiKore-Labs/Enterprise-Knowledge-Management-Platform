"""Upload the sample corpus through the real core-api (end-to-end ingestion test).

Logs in as the department manager (or admin for GENERAL) for each document, uploads every
manifest row with version 1, then uploads version 2 rows as a new version of the same document,
waits until all are INDEXED/FAILED and prints a docId -> documentId mapping
(saved to resources/eval/results/doc_id_map.json for the eval runner).

Usage:  python resources/scripts/ingest_corpus.py [--api http://localhost:8080] [--no-wait]
Requires: pip install requests
"""
import argparse
import csv
import json
import time
from pathlib import Path

import requests

ROOT = Path(__file__).resolve().parents[1]
PASSWORD = json.loads((ROOT / "seed" / "users.json").read_text())["devPassword"]
UPLOADER = {
    "GENERAL": "alice.admin@helios.example.com",
    "HR": "harper.hrmgr@helios.example.com",
    "ENGINEERING": "ethan.engmgr@helios.example.com",
    "FINANCE": "frank.finmgr@helios.example.com",
    "LEGAL": "lena.legalmgr@helios.example.com",
    "MARKETING": "mark.mktmgr@helios.example.com",
}
MIME = {"pdf": "application/pdf",
        "docx": "application/vnd.openxmlformats-officedocument.wordprocessingml.document"}


def login(api, email):
    r = requests.post(f"{api}/api/v1/auth/login", json={"email": email, "password": PASSWORD}, timeout=10)
    r.raise_for_status()
    return {"Authorization": f"Bearer {r.json()['accessToken']}"}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--api", default="http://localhost:8080")
    ap.add_argument("--no-wait", action="store_true")
    args = ap.parse_args()

    rows = list(csv.DictReader(open(ROOT / "corpus" / "manifest.csv")))
    tokens, mapping = {}, {}
    for row in sorted(rows, key=lambda r: int(r["version"])):
        dept = row["department"]
        headers = tokens.setdefault(dept, login(args.api, UPLOADER[dept]))
        path = ROOT / "corpus" / "files" / row["file"]
        files = {"file": (path.name, path.open("rb"), MIME[row["format"]])}
        if row["version"] == "1":
            data = {"title": row["title"], "department": dept, "category": row["category"],
                    "author": row["author"], "tags": row["docId"].lower()}
            r = requests.post(f"{args.api}/api/v1/documents", headers=headers, files=files, data=data, timeout=60)
            r.raise_for_status()
            mapping[row["docId"]] = r.json()["id"]
        else:
            doc_id = mapping[row["docId"]]
            r = requests.post(f"{args.api}/api/v1/documents/{doc_id}/versions", headers=headers, files=files,
                              data={"changeNote": f"v{row['version']} from sample corpus"}, timeout=60)
            r.raise_for_status()
        print(f"uploaded {row['docId']} v{row['version']} -> {mapping[row['docId']]}")

    out = ROOT / "eval" / "results"
    out.mkdir(parents=True, exist_ok=True)
    (out / "doc_id_map.json").write_text(json.dumps(mapping, indent=2))

    if args.no_wait:
        return
    admin = login(args.api, UPLOADER["GENERAL"])
    pending = set(mapping.values())
    deadline = time.time() + 600
    while pending and time.time() < deadline:
        for doc_id in list(pending):
            status = requests.get(f"{args.api}/api/v1/documents/{doc_id}", headers=admin, timeout=10).json()["status"]
            if status in ("INDEXED", "FAILED"):
                print(f"{doc_id}: {status}")
                pending.discard(doc_id)
        time.sleep(3)
    print("still pending:", pending or "none")


if __name__ == "__main__":
    main()
