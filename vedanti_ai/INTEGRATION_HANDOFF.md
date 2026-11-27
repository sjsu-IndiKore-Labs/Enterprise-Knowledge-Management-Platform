# AI integration testing handoff

Owner: Vedanti. Verification updated: November 27, 2026.

## Status

I tested the AI worker's document-processing pipeline locally using the sample files under `resources/corpus/files/`. The local runner passes those files through the same `IngestionPipeline.process_file` method used by `ai-worker`, then asks questions against the in-memory index. This verifies local extraction, chunking, indexing, retrieval, and answer behavior. It does not run the live SQS worker loop or read documents from S3, so full application end-to-end testing is NOT complete.

The local test uses files in `resources/corpus/files/`, fake embeddings/generation, and an in-memory index. It does not verify S3, SQS transport, PostgreSQL, employee login, or OpenAI. Real S3 access and the deployed worker flow will be tested after the staging infrastructure is finalized, deployed, and a working test path is available. A passing local test is not production sign-off.

## Results reproduced

- Automated suite: 21 passed, two dependency deprecation warnings (November 27).
- Ruff: passed across the complete `vedanti_ai` package, including tests and scripts.
- PR #41 CI: all five checks passed on code commit `03125ea` (AI lint/tests, backend build, frontend checks/build, and contracts). The handoff-only update is being added after that run.
- Local worker-path runner: all 19 document versions in the manifest pass through `IngestionPipeline.process_file`; representative query checks cover working-hours, on-call stipend, refusal, citations, and Engineering/HR department filters.
- Forty-question offline evaluation after query normalization: answered/refused classification accuracy 80%; expected answer-text match 64.52%; citation accuracy 70.97%; retrieval recall 96.77%; expected refusals correct 100%; department leakage observed 0; p95 8 ms. These are fake-provider measurements, not production quality or latency claims.

Reproduce from repository root:

```sh
PYTHONPATH=vedanti_ai vedanti_ai/.venv/bin/python -m pytest -q vedanti_ai/tests
(cd vedanti_ai && .venv/bin/ruff check .)
PYTHONPATH=vedanti_ai vedanti_ai/.venv/bin/python -m eval.run_eval
PYTHONPATH=vedanti_ai vedanti_ai/.venv/bin/python vedanti_ai/scripts/local_worker_chat.py --department ENGINEERING
```

The local worker chat simulates the selected department plus GENERAL access; it does not authenticate a real employee. Omit `--department` only for local all-departments evaluation. The evaluator writes its report under `vedanti_ai/eval/results/`.

## Local and deployment limitations

The `develop` Compose file at the tested base commit exposes `postgres` and `localstack` but does not define a runnable `ai-worker`, so full local platform testing from `develop` has not been completed. Manali's newer `manali/staging-infra-fixes` branch (`b0e7a0b`) adds an `ai-worker` Compose service and ECS task configuration. Its service card documents S3 object reads under `documents/*`, KMS decrypt, and the required SQS permissions; the ECS config clears local AWS endpoint/key overrides so the task role can be used. That branch is separate from `develop` and its configuration has not been applied or verified in the deployed AWS environment. It may enable the missing integration path once the infrastructure owner finalizes and deploys it.

The available AWS profile authenticates account 944311260916. S3 listing returned AccessDenied; ECS list-tasks for `ekm-staging` in us-west-2 returned ClusterNotFoundException. These do not establish whether the worker in the owning account can read an object. No usable deployed API URL was found in the inspected repository configuration or repository GitHub variables/deployment records.

Earlier infrastructure branch inspected: `manali/staging-infra`, commit `37c7a12369e76e21b0f60df352eac1447278bb8c`. Newer proposed configuration is on `manali/staging-infra-fixes` (`b0e7a0b`). Neither branch by itself proves that the configuration has been applied to AWS. The proposed worker policy allows reading objects under `documents/*`; it does not grant bucket-wide listing. A known-object read from the deployed worker is the appropriate permission test.

## AI corrections verified locally on November 27

These corrections are included in the AI branch candidate and require integration testing:

- Settings now consume shared POSTGRES_* names, including POSTGRES_SSLMODE. Connection strings use psycopg escaping. Environment mapping is regression-tested; a live database connection is not yet verified.
- Worker acknowledges only INDEXED results after status publication. FAILED results remain eligible for queue retry/DLQ. Regression tests exercise both paths with mocked AWS clients; actual redrive timing is not tested.
- Generated refusals and answers without valid citation indexes now return answered=false and no citations. Three regression cases cover refusal, missing citation and out-of-range citation. Valid markers alone do not prove semantic support; real-provider grounding evaluation remains necessary.
- Worker download filenames are reduced to their basename inside the temporary directory. ECS client configuration is tested to leave credential resolution to the SDK when the endpoint override is empty.
- Fake-provider quality is limited; punctuation normalization fixes hyphen/space tokenization but does not provide general semantic language understanding. Real-provider quality and cost remain unverified.

## Shared integration acceptance procedure

Manali owns deployment/runtime provisioning and sharing the approved test endpoint/access. Ayushi owns authenticated Core API uploads and department derivation. Vedanti owns worker and answer behavior and remains responsible for AI defects discovered during integration.

1. After the infrastructure branch is finalized and deployed, record the actual image commit, ECS task role, bucket, queue names and database migration version. Confirm the deployed Core API image includes the current upload-to-S3 and SQS publishing flow; do not infer deployment state from source files alone.
2. In the deployed worker runtime, read ONE known object under `documents/` using its normal SDK credentials. Record success or the sanitized AWS error. Do not use bucket listing as a prerequisite; do not share temporary credentials.
3. With an authenticated application test user, upload a PDF and a DOCX through Core API. Record document/version/trace IDs and S3 keys. Confirm the ingestion event matches `contracts/events/document-ingest-requested.schema.json`.
4. Verify PROCESSING then INDEXED status, persistent chunks in PostgreSQL, correct department/page/section metadata, and no duplicate chunks on event replay. Verify failure/retry behavior separately.
5. Ask a question containing a fact unique to each uploaded document through Core API. Confirm the answer and citation refer to that version. Restart the AI API and confirm indexed data persists.
6. Test another department's employee login; confirm forbidden documents, text and citations are absent. Verify GENERAL visibility, superseded-version exclusion and unrelated-question refusal. Selecting departments manually at the internal API is not a substitute for this login test.
7. Test SSE and non-streaming answers, citation downloads, and reindexing. Run production-provider evaluation separately and record quality/latency/cost.

For each check retain: timestamp, deployed commit, sanitized request/response, trace ID, expected/actual result and relevant logs. Route failures by evidence: AWS access/deployment to Manali, API/auth/events to Ayushi, parsing/retrieval/worker behavior to Vedanti. A failed read alone does not identify IAM as the cause; key, encryption and runtime configuration also require inspection.

## Handoff acceptance

Pending: full local stack testing on the latest infrastructure branch; real S3 known-object read and deployed worker test after infrastructure is finalized and deployed; authenticated end-to-end flow; and real-provider evaluation. This document records verified local AI worker-pipeline behavior and remaining integration gates, not a claim that the entire platform is production-ready or that staging infrastructure is broken.
