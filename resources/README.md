# resources/ — shared test data for every member

Everything here is **fictional** (company "Helios Systems Inc.", `helios.example.com`). Safe to commit and to send to OpenAI.

| Path | What | Primary user |
|---|---|---|
| [`corpus/source/`](corpus/source/) | 19 Markdown sources of enterprise documents (6 departments, policies/SOPs/guides/reports, 2 versions of one policy, 3 confidential docs) | Vedanti |
| [`corpus/files/`](corpus/files/) | The same documents rendered as **PDF (15) and DOCX (4)** with real page breaks, tables, headers/footers | everyone — upload these |
| [`corpus/manifest.csv`](corpus/manifest.csv) | Metadata per file: docId, version, title, department, category, author, classification | ingestion script, eval |
| [`edge-cases/`](edge-cases/) | Scanned (no text), corrupt, encrypted, empty, unsupported `.txt`, 60-page PDF | Vedanti, Ayushi, Changhyun (error UI) |
| [`seed/`](seed/) | 12 dev users across roles/departments (+ SQL in `contracts/database/seed/`) | Ayushi, everyone for login |
| [`eval/golden_qa.jsonl`](eval/golden_qa.jsonl) | 40 golden questions incl. RBAC-leakage, versioning, unanswerable, prompt-injection cases | Vedanti (RAG eval), Ayushi (security tests), Manali (smoke tests) |
| [`eval/rbac_matrix.csv`](eval/rbac_matrix.csv) | Expected ALLOW/DENY for every user × document | Ayushi, Vedanti |
| [`load-test/queries.csv`](load-test/queries.csv) | Realistic queries for k6 load tests | Manali |
| [`scripts/`](scripts/) | `build_corpus.py` (re-render PDFs/DOCX from Markdown), `ingest_corpus.py` (upload everything through core-api) | everyone |

## Commands
```bash
make corpus          # re-render corpus/files and edge-cases from the Markdown sources
make ingest-corpus   # upload the whole corpus through the running core-api, wait for INDEXED
```

## Adding documents
Add a Markdown file to `corpus/source/` (use `<!-- pagebreak -->` to control pages), add a row to `manifest.csv`,
run `make corpus`, and add 1–3 questions to `eval/golden_qa.jsonl`. Keep page numbers in golden questions in sync.

## Demo-day corpus (Milestone 6)
For the final demo we can also ingest larger **public** policy documents (e.g. public-domain government
HR/IT policies or open-source company handbooks such as the GitLab Handbook, CC BY-SA). Put them in
`corpus/public/` with their license noted in `corpus/public/SOURCES.md`. Never commit real confidential documents.
