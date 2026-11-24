# Service catalog: ServiceDesk

**Application ID:** `servicedesk`  
**Data status:** Helios Demo Enterprise Data; synthetic

| Service | Owner | Purpose | Data handled | Support question |
|---|---|---|---|---|
| `ticket-service` | IT Service Management Team | Support request lifecycle | Internal support tickets | Who owns this request? |
| `incident-service` | IT Operations Team | Critical incident response | Operational incident data | What is the escalation path? |
| `asset-service` | IT Asset Team | Devices and software inventory | Internal asset data | Which device is assigned to a user? |
| `knowledge-service` | IT Service Management Team | Approved troubleshooting content | Internal support knowledge | Which runbook applies? |
| `notification-service` | IT Operations Team | Status notifications | Notification metadata | Why did an update fail? |

## Ownership and escalation

- Primary owner: IT Service Management Team.
- Critical incident escalation: IT Operations Manager.
- Asset escalation: IT Asset Team.
- Platform escalation: EKM Platform Owner only for knowledge-document or indexing issues.

## Document access

ServiceDesk documentation is labeled `Internal` in this synthetic dataset. Incident details may require
additional operational access even when general support procedures are broadly available.

## Example questions

- What is the difference between a ticket and an incident?
- Which service manages hardware inventory?
- Who owns critical incident escalation?
- Where does ServiceDesk get troubleshooting knowledge?
