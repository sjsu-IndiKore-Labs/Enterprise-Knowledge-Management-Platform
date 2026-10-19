# questions/ — agent-to-agent mailbox

Each member's coding agent can ask a question to another member's agent. **One question = one file.**
No Slack screenshots, no "I told them in class" — if it affects code, it goes here so every agent can read it.

```
questions/
  to-ayushi/      ← questions for Ayushi's agent (backend, security, DB schema, SQS producers/consumers)
  to-changhyun/   ← questions for Changhyun's agent (frontend, UX)
  to-vedanti/     ← questions for Vedanti's agent (RAG, parsing, chunking, embeddings, prompts, eval)
  to-manali/      ← questions for Manali's agent (Docker, CI/CD, AWS, env vars, ports, LocalStack)
  to-all/         ← decisions / announcements that affect everyone
  _TEMPLATE.md
```

## Protocol (every agent must follow this)

**At the start of every session**: list `questions/to-<you>/` and `questions/to-all/`, open every file with
`status: open`, and answer before starting new work (answering is usually 5 minutes; being blocked costs a day).

**Asking**
1. Copy [`_TEMPLATE.md`](_TEMPLATE.md) to `questions/to-<recipient>/YYYY-MM-DD_from-<you>_<short-slug>.md`
   e.g. `questions/to-vedanti/2026-10-20_from-ayushi_chunk-table-ownership.md`.
2. Fill the front-matter and *Context / Question / Proposed answer*. Always propose an answer — the recipient
   can then just reply "agreed".
3. Set `priority: blocking` only if you literally cannot continue; otherwise `normal`.
4. Commit it (on your branch or directly to `develop`: `git commit -m "question: to-vedanti chunk table ownership"`).
5. Keep working against your proposed answer, behind an adapter that is easy to change.
6. Add the file link under "Blocked / waiting on" in your `TODO.md`.

**Answering**
1. Fill the `## Answer` section (who, date, decision, and what changes).
2. Change `status: open` → `status: answered`.
3. If the answer changes an interface → update `contracts/` in the same PR and add a line to `contracts/CHANGELOG.md`.
4. If it creates work for you → add it to your `TODO.md`.

**Closing**: the asker reads the answer, adapts their code, and sets `status: closed`. Never delete question
files — they are the project's decision log (useful for the final report).

**to-all**: use for decisions every member must acknowledge. Each member adds a line under `## Acknowledgements`;
when all four have acknowledged, the author sets `status: closed`.

## Find open questions quickly
```bash
grep -l "status: open" questions/to-*/*.md
make questions            # same, grouped by recipient
```
