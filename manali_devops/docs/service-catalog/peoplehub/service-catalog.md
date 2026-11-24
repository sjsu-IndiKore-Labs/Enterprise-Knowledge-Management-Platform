# Service catalog: PeopleHub

**Application ID:** `peoplehub`  
**Data status:** Helios Demo Enterprise Data; synthetic

| Service | Owner | Purpose | Data handled | Support question |
|---|---|---|---|---|
| `people-profile-service` | People Platforms Team | Employee profiles and reporting relationships | HR-restricted employee data | Who owns employee profile updates? |
| `leave-service` | People Platforms Team | Leave requests, balances, and approvals | HR-restricted leave data | Why is a leave request pending? |
| `payroll-adapter` | People Platforms Team | Payroll integration for approved leave events | Restricted payroll event data | Did approved leave reach payroll? |
| `notification-service` | People Platforms Team | HR workflow notifications | Notification metadata | Why was an approval notification delayed? |
| `peoplehub-web` | People Platforms Team | Employee and manager portal | No system-of-record ownership | Where do users submit leave? |

## Ownership and escalation

- Primary owner: People Platforms Team.
- Escalation role: HR Systems Manager.
- Data steward: HR Operations.
- Platform escalation: EKM Platform Owner only for EKM indexing or document-access issues.

## Document access

PeopleHub documentation is labeled `Internal HR` in this synthetic dataset. A document containing
employee records or leave details should not be visible to users outside the authorized HR scope.

## Example questions

- What service owns leave balances?
- Who supports the payroll integration?
- Which PeopleHub service sends notifications?
- What data does `leave-service` handle?
