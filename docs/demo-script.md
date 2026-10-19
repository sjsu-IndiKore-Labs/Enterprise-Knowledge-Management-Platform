# Final demo script (Dec 2, 2026) — draft, ~12 minutes

1. **Problem & architecture** (1 min) — slide with `docs/architecture.md` diagram.
2. **Upload** (2 min) — log in as `harper.hrmgr` (HR manager), drag `HR-001_PTO_and_Leave_Policy.pdf`, show QUEUED → PROCESSING → INDEXED.
3. **Ask** (2 min) — log in as `hannah.hr`: "How many PTO days can I carry over?" → streamed answer with citation [1] → click → preview page 1.
4. **Versioning** (1 min) — admin uploads Remote Work Policy v2; "How many remote days per week?" changes from 2 to 3 with v2 citation.
5. **Security** (2 min) — log in as `eric.eng`: "What is the salary range for E3?" → not found (HR-only doc). Show audit log `ACCESS_DENIED`/query log.
6. **Knowledge gaps & analytics** (2 min) — admin dashboard: usage, top questions, "pet insurance" gap → mark IN_PROGRESS.
7. **Ops** (1 min) — CI/CD pipeline run, ECS services, CloudWatch dashboard + alarm.
8. **Evaluation** (1 min) — RAG metrics table (faithfulness, relevance, recall, leakage = 0).
