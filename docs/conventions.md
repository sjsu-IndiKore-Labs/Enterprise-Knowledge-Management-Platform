# Team conventions (git, code, reviews)

## Branches
- `main` — demo-ready; updated from `develop` at the end of each milestone (tag `m1`…`m6`).
- `develop` — integration branch; default target for PRs.
- Feature branches: `<member>/<short-topic>` → `ayushi/upload-api`, `changhyun/chat-stream`, `vedanti/pdf-extractor`, `manali/terraform-sqs`.
- Shared-file PRs: title starts with `[contracts]`, `[shared]` or `[docs]`.

## Commits
Conventional Commits: `feat(core-api): upload endpoint`, `fix(ai): strip pdf footer`, `docs: update timeline`,
`question: to-vedanti chunk ownership`, `chore(ci): cache gradle`.

## Reviews
At least 1 approval. Contract changes need approval from every consumer. Reviewers check the PR template checklist.

## Code style
| Area | Tooling |
|---|---|
| Java | Google Java Style via Spotless; packages `com.helios.ekm.*` |
| TypeScript | ESLint (next/core-web-vitals) + Prettier; strict TS; no `any` for API types |
| Python | ruff (lint + format), mypy optional; type hints required on public functions |
| SQL | lowercase keywords allowed; snake_case identifiers; one migration per change |

## Secrets
Never commit `.env`, keys, or tokens. Local defaults live in `.env.example`; real secrets go to GitHub Actions secrets / AWS Secrets Manager.
