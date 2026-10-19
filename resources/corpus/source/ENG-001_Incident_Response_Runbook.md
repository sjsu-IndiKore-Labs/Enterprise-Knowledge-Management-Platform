# Production Incident Response Runbook

Document ID: ENG-001 | Department: ENGINEERING | Category: SOP | Owner: Site Reliability Engineering | Version: 1 | Effective: 2026-02-15

## 1. Severity Levels

| Severity | Definition | Response time | Update cadence |
|---|---|---|---|
| SEV1 | Full outage or data loss affecting many customers | 15 minutes | Every 30 minutes |
| SEV2 | Major feature broken, no workaround, many customers | 30 minutes | Every 60 minutes |
| SEV3 | Partial degradation or workaround exists | 4 business hours | Daily |
| SEV4 | Minor bug, cosmetic issue | Next sprint | None |

## 2. Declaring an Incident

Anyone can declare an incident. Type /incident declare in the #incidents Slack channel and choose a severity. The on-call engineer for the affected service becomes the Incident Commander (IC) until they hand off. For SEV1 and SEV2, the IC must page the Engineering Manager on call and open a Zoom bridge within 10 minutes.

## 3. Roles

- Incident Commander: coordinates the response and makes decisions; does not debug.
- Operations Lead: investigates and applies fixes.
- Communications Lead: posts status page updates and informs Customer Support.
- Scribe: keeps a timeline in the incident document.

<!-- pagebreak -->

## 4. Mitigation First

The first goal is to stop customer impact, not to find the root cause. Preferred mitigations, in order: roll back the latest deployment, disable the feature flag, fail over to the secondary region (us-east-2), scale out the affected service. Rollbacks do not require approval during an active SEV1 or SEV2.

## 5. Customer Communication

For SEV1 incidents the status page at status.helios.example.com must be updated within 20 minutes of declaration. Customer Support must be notified in #support-escalations. Enterprise customers with a premium SLA receive a direct email from their Customer Success Manager within 1 hour.

## 6. Postmortems

A blameless postmortem is required for every SEV1 and SEV2 incident. The postmortem document must be published within 5 business days of resolution and reviewed in the weekly Reliability Review meeting. Every postmortem must include a timeline, root cause, impact (customers affected and duration), and at least one action item with an owner and due date. Action items from SEV1 postmortems must be completed within 30 days.
