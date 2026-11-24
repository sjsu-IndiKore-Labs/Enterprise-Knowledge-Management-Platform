# Architecture: PeopleHub

**Application ID:** `peoplehub`  
**Status:** illustrative synthetic architecture; not a deployed system

## Logical flow

```text
Employee / HR Manager
        │
        ▼
PeopleHub web portal
        │
        ├──► people-profile-service ──► PeopleHub employee database
        ├──► leave-service ───────────► PeopleHub leave database
        │          │
        │          ├──► payroll-adapter
        │          └──► notification-service
        └──► notification-service
```

## Services

| Service | Responsibility | Owner team | Main dependency |
|---|---|---|---|
| `people-profile-service` | Employee profiles and org relationships | People Platforms Team | Employee database |
| `leave-service` | Leave requests, balances, and approvals | People Platforms Team | HR policy data, notification-service |
| `payroll-adapter` | Sends approved leave events to payroll | People Platforms Team | Payroll system interface |
| `notification-service` | Email and in-app HR notifications | People Platforms Team | Notification provider |
| `peoplehub-web` | Employee and HR manager user interface | People Platforms Team | PeopleHub APIs |

## Key workflow: leave request

1. Employee submits a leave request through `peoplehub-web`.
2. `leave-service` validates policy and manager approval rules.
3. Manager approves or rejects the request.
4. An approved request updates the employee leave balance.
5. `payroll-adapter` receives only the payroll-relevant event.
6. `notification-service` informs the employee and manager.

## Failure handling

- If payroll delivery fails, the leave decision remains recorded and the payroll event is retried.
- If notification delivery fails, the request remains approved and the notification is retried.
- HR users should see the workflow status without being shown internal integration credentials.
