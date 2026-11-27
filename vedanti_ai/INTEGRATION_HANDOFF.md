# AI integration testing handoff

Owner: Vedanti. Verification date: November 27, 2026.

## Status

Local AI pipeline verification completed against the current working tree on `vedanti_ai`. Full application end-to-end testing is NOT complete. The local runner uses the production `IngestionPipeline.process_file` method over checked-in sample files, then asks questions against the in-memory index. It substitutes local files for S3/SQS transport.

The local test uses `resources/corpus/files/`, fake embeddings/generation and an in-memory index. It does not verify S3, SQS, PostgreSQL, employee login, or OpenAI. A passing component suite is not production sign-off.

## Results reproduced

- Automated suite: 21 passed, two dependency deprecation warnings (November 27).
- Ruff: passed for app, tests, and local runner.
- Local worker-path runner: all 19 document versions in the manifest pass through `IngestionPipeline.process_file`; representative query checks cover working-hours, on-call stipend, refusal, citations, and Engineering/HR department filters.
- Forty-question offline evaluation after query normalization: answered/refused classification accuracy 80%; expected answer-text match 64.52%; citation accuracy 70.97%; retrieval recall 96.77%; expected refusals correct 100%; department leakage observed 0; p95 8 ms. These are fake-provider measurements, not production quality or latency claims.

Reproduce from repository root:

```sh
PYTHONPATH=vedanti_ai vedanti_ai/.venv/bin/python -m pytest -q vedanti_ai/tests
PYTHONPATH=vedanti_ai vedanti_ai/.venv/bin/python -m ruff check vedanti_ai/app vedanti_ai/tests
PYTHONPATH=vedanti_ai vedanti_ai/.venv/bin/python -m eval.run_eval
PYTHONPATH=vedanti_ai vedanti_ai/.venv/bin/python vedanti_ai/scripts/local_worker_chat.py --department ENGINEERING
```

The local worker chat simulates the selected department plus GENERAL access; it does not authenticate a real employee. Omit `--department` only for local all-departments evaluation. The evaluator writes its report under `vedanti_ai/eval/results/`.

## Local and deployment limitations

Colima and Docker are installed and running. Compose currently exposes `postgres` and `localstack` as infrastructure services; it does not define a runnable `ai-worker` service in the current `develop` Compose file. Full Core API → SQS → worker → PostgreSQL Compose testing has therefore not been completed. Starting those containers alone is not an end-to-end test.

The available AWS profile authenticates account 944311260916. S3 listing returned AccessDenied; ECS list-tasks for `ekm-staging` in us-west-2 returned ClusterNotFoundException. These do not establish whether the worker in the owning account can read an object. No usable deployed API URL was found in the inspected repository configuration or repository GitHub variables/deployment records.

Infrastructure branch inspected: `manali/staging-infra`, commit `37c7a12369e76e21b0f60df352eac1447278bb8c`. It clears local AWS endpoint settings in ECS and defines worker object-read access under `documents/*`. It does not grant bucket-wide listing. A known-object read is the appropriate permission test. Terraform declarations are not proof of applied deployment state.

## AI corrections verified locally on November 27

These corrections are included in the AI branch candidate and require integration testing:

- Settings now consume shared POSTGRES_* names, including POSTGRES_SSLMODE. Connection strings use psycopg escaping. Environment mapping is regression-tested; a live database connection is not yet verified.
- Worker acknowledges only INDEXED results after status publication. FAILED results remain eligible for queue retry/DLQ. Regression tests exercise both paths with mocked AWS clients; actual redrive timing is not tested.
- Generated refusals and answers without valid citation indexes now return answered=false and no citations. Three regression cases cover refusal, missing citation and out-of-range citation. Valid markers alone do not prove semantic support; real-provider grounding evaluation remains necessary.
- Worker download filenames are reduced to their basename inside the temporary directory. ECS client configuration is tested to leave credential resolution to the SDK when the endpoint override is empty.
- Fake-provider quality is limited; punctuation normalization fixes hyphen/space tokenization but does not provide general semantic language understanding. Real-provider quality and cost remain unverified.

## Shared integration acceptance procedure

Manali owns deployment/runtime provisioning and sharing the approved test endpoint/access. Ayushi owns authenticated Core API uploads and department derivation. Vedanti owns worker and answer behavior and remains responsible for AI defects discovered during integration.

1. Record the actual image commit, ECS task role, bucket, queue names and database migration version. Use current backend code: the older Core API snapshot on the inspected infrastructure branch only persists upload metadata and does not implement the complete S3/SQS flow.
2. In the deployed worker runtime, read ONE known object under `documents/` using its normal SDK credentials. Record success or the sanitized AWS error. Do not use bucket listing as a prerequisite; do not share temporary credentials.
3. With an authenticated application test user, upload a PDF and a DOCX through Core API. Record document/version/trace IDs and S3 keys. Confirm the ingestion event matches `contracts/events/document-ingest-requested.schema.json`.
4. Verify PROCESSING then INDEXED status, persistent chunks in PostgreSQL, correct department/page/section metadata, and no duplicate chunks on event replay. Verify failure/retry behavior separately.
5. Ask a question containing a fact unique to each uploaded document through Core API. Confirm the answer and citation refer to that version. Restart the AI API and confirm indexed data persists.
6. Test another department's employee login; confirm forbidden documents, text and citations are absent. Verify GENERAL visibility, superseded-version exclusion and unrelated-question refusal. Selecting departments manually at the internal API is not a substitute for this login test.
7. Test SSE and non-streaming answers, citation downloads, and reindexing. Run production-provider evaluation separately and record quality/latency/cost.

For each check retain: timestamp, deployed commit, sanitized request/response, trace ID, expected/actual result and relevant logs. Route failures by evidence: AWS access/deployment to Manali, API/auth/events to Ayushi, parsing/retrieval/worker behavior to Vedanti. A failed read alone does not identify IAM as the cause; key, encryption and runtime configuration also require inspection.

## Handoff acceptance

Pending: full local stack testing after Compose includes the worker and compatible Core API; deployed known-object read; authenticated end-to-end flow; and real-provider evaluation. This document records verified AI behavior and remaining integration gates, not a claim that the entire platform is production-ready or that staging infrastructure is broken.
