# RAG evaluation set (owner: Vedanti; used by everyone)

[`golden_qa.jsonl`](golden_qa.jsonl) — 40 hand-written questions over the sample corpus, one JSON object per line.

| Field | Meaning |
|---|---|
| `id` | stable id (`Q001`…) — reference it in bug reports and questions |
| `type` | `ANSWERABLE`, `ANSWERABLE_GENERAL` (GENERAL doc, any department), `VERSIONING`, `TABLE`, `MULTI_DOC`, `FOLLOW_UP` (has `history`), `RBAC_DENIED`, `RBAC_ALLOWED_ADMIN`, `UNANSWERABLE`, `PROMPT_INJECTION`, `OUT_OF_SCOPE` |
| `askedBy` | seed user email from `resources/seed/users.json` → determines `allowedDepartments` |
| `expectedAnswered` | value of `answered` in the response |
| `expectedAnswerContains` | every string must appear in the answer (case-insensitive) |
| `forbiddenAnswerContains` | none may appear (leakage / stale version checks) |
| `expectedSources` | `docId` (+ `page`, null for DOCX; + `version` when relevant) that must be among citations |
| `forbiddenDocIds` | must never appear in citations or retrieved context |

## Metrics to report (Milestone 5)
| Metric | How | Target |
|---|---|---|
| Retrieval Recall@k | expected doc/page in top-k retrieved chunks (k = `RAG_TOP_K`) | ≥ 0.90 |
| Citation accuracy | expected doc among returned citations | ≥ 0.90 |
| Answer correctness | all `expectedAnswerContains` present | ≥ 0.85 |
| Faithfulness | LLM-as-judge: every claim supported by cited chunks (or RAGAS `faithfulness`) | ≥ 0.90 |
| Answer relevance | LLM-as-judge 1–5 or RAGAS `answer_relevancy` | ≥ 4.0 |
| Refusal accuracy | `expectedAnswered=false` cases return the not-found answer | 1.00 |
| **Department leakage** | any `forbiddenDocIds` / `forbiddenAnswerContains` hit | **must be 0** |
| p95 latency | end-to-end `/api/v1/chat` | < 6 s |

Results go to `resources/eval/results/` (git-ignored); copy the final summary table into `docs/` for the report.

## How doc ids map to database ids
The `docId` (e.g. `HR-001`) is the corpus id, not the UUID. After running `make ingest-corpus`, the loader prints a
mapping `docId -> documentId`; the eval runner matches citations by `documentTitle` or by that mapping.

[`rbac_matrix.csv`](rbac_matrix.csv) — which seed user may see which corpus document (expected truth for leakage tests).
