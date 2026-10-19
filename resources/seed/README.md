# Dev seed users (LOCAL / STAGING DEMO ONLY)

All users share the dev password stored in [`users.json`](users.json) (`devPassword`). They are loaded by `make seed`
(runs `contracts/database/seed/dev_seed.sql`, idempotent). Never load this seed in production.

| Email | Role | Department | Use it to test |
|---|---|---|---|
| alice.admin@helios.example.com | ADMIN | GENERAL | everything, analytics dashboard, all departments |
| harper.hrmgr@helios.example.com | MANAGER | HR | uploading HR docs |
| hannah.hr@helios.example.com | EMPLOYEE | HR | HR + GENERAL only |
| ethan.engmgr@helios.example.com | MANAGER | ENGINEERING | uploading ENGINEERING docs |
| eric.eng@helios.example.com | EMPLOYEE | ENGINEERING | **main leakage tester** — must never see HR-004 / FIN-002 / LEG-003 |
| frank.finmgr@helios.example.com | MANAGER | FINANCE | uploading FINANCE docs |
| fiona.fin@helios.example.com | EMPLOYEE | FINANCE | |
| lena.legalmgr@helios.example.com | MANAGER | LEGAL | uploading LEGAL docs |
| leo.legal@helios.example.com | EMPLOYEE | LEGAL | |
| mark.mktmgr@helios.example.com | MANAGER | MARKETING | uploading MARKETING docs |
| maya.mkt@helios.example.com | EMPLOYEE | MARKETING | |
| ivan.inactive@helios.example.com | EMPLOYEE | MARKETING | `active=false` → login must fail with 401 |

User ids are fixed (`11111111-1111-4111-8111-0000000000NN`, NN = row number) so tests and examples can reference them.
To regenerate hashes: see `resources/scripts/requirements.txt` (bcrypt) — Spring's `BCryptPasswordEncoder` accepts the `$2a$10$` hashes.
