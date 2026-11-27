# Architecture: ServiceDesk

**Application ID:** `servicedesk`  
**Status:** illustrative synthetic architecture; not a deployed system

## Logical flow

```text
Employee / Support Agent
        │
        ▼
ServiceDesk portal
        │
        ├──► ticket-service ───────► Ticket database
        ├──► incident-service ─────► Incident database
        ├──► asset-service ────────► Asset inventory
        ├──► knowledge-service ────► Approved support articles
        └──► notification-service ─► Email / chat notifications
```

## Services

| Service | Responsibility | Owner team | Main dependency |
|---|---|---|---|
| `ticket-service` | Requests, assignments, comments, and status | IT Service Management Team | Ticket database |
| `incident-service` | Severity, response, escalation, and resolution | IT Operations Team | Ticket-service, alert sources |
| `asset-service` | Devices, software, and ownership relationships | IT Asset Team | Asset inventory |
| `knowledge-service` | Approved troubleshooting procedures | IT Service Management Team | EKM document links |
| `notification-service` | Ticket and incident notifications | IT Operations Team | Notification provider |

## Key workflow: critical incident

1. An alert or employee report creates a ticket.
2. `incident-service` classifies the issue as critical.
3. An incident commander and resolver group are assigned.
4. `notification-service` sends updates to affected stakeholders.
5. `knowledge-service` links approved recovery procedures.
6. The incident is resolved, reviewed, and linked to a post-incident record.

## Failure handling

- A notification failure must not lose the incident record.
- Duplicate alerts should be correlated to the existing incident.
- Asset inventory may be temporarily stale; the ticket should retain the reported asset identifier.
