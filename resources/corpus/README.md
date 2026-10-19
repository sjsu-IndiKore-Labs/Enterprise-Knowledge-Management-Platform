# Sample corpus — Helios Systems Inc. (fictional)

| docId | Dept | Format | Pages | Notes |
|---|---|---|---|---|
| GEN-001 Employee Handbook | GENERAL | PDF | 2 | contact info, holidays |
| GEN-002 InfoSec Acceptable Use | GENERAL | PDF | 2 | table (data classification) |
| GEN-003 Remote Work Policy | GENERAL | PDF | 1 | **v1 and v2** with conflicting facts → versioning test |
| GEN-004 Travel & Expense | GENERAL | PDF | 2 | table (limits) |
| HR-001 PTO and Leave | HR | PDF | 2 | table (accrual) |
| HR-002 Onboarding SOP | HR | DOCX | – | numbered steps, table |
| HR-003 Performance Reviews | HR | PDF | 1 | |
| HR-004 Compensation Bands | HR | PDF | 1 | **CONFIDENTIAL** → leakage test |
| ENG-001 Incident Runbook | ENGINEERING | PDF | 2 | table (SEV levels) |
| ENG-002 Code Review & Deploy | ENGINEERING | DOCX | – | |
| ENG-003 On-Call Policy | ENGINEERING | PDF | 1 | |
| FIN-001 Procurement SOP | FINANCE | PDF | 2 | table (thresholds) |
| FIN-002 Q3 Budget Summary | FINANCE | PDF | 1 | **CONFIDENTIAL** → leakage test |
| LEG-001 Contract Review SOP | LEGAL | DOCX | – | table |
| LEG-002 Data Retention & Privacy | LEGAL | PDF | 2 | table |
| LEG-003 Pending Litigation | LEGAL | PDF | 1 | **RESTRICTED** → leakage test |
| MKT-001 Brand Guidelines | MARKETING | PDF | 1 | table (hex colors) |
| MKT-002 Social Media Policy | MARKETING | DOCX | – | |

Every PDF has a running footer (`Helios Systems Inc. — Internal — <title> … Page N`) that the parser should strip
or at least not let dominate chunks. Page numbers used in `eval/golden_qa.jsonl` come from the explicit page breaks.
