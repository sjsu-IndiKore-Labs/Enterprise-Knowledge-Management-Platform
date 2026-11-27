# Service catalog: ProcureFlow

**Application ID:** `procureflow`  
**Data status:** Helios Demo Enterprise Data; synthetic

| Service | Owner | Purpose | Data handled | Support question |
|---|---|---|---|---|
| `vendor-service` | Finance Technology Team | Vendor onboarding and review | Internal Finance vendor data | Why is a vendor pending review? |
| `purchase-order-service` | Procurement Systems Team | Requests and purchase orders | Internal Finance procurement data | Where is the purchase order? |
| `approval-service` | Finance Technology Team | Approval chain and policy rules | Restricted approval data | Who must approve this request? |
| `invoice-service` | Accounts Payable Systems Team | Invoice and receipt matching | Restricted invoice data | Why did invoice matching fail? |
| `notification-service` | Finance Technology Team | Procurement status notifications | Notification metadata | Why was an approval notification delayed? |

## Ownership and escalation

- Primary owner: Finance Technology Team.
- Procurement escalation: Procurement Systems Manager.
- Invoice escalation: Accounts Payable Systems Team.
- Platform escalation: EKM Platform Owner only for knowledge-document or indexing issues.

## Document access

ProcureFlow documentation is labeled `Internal Finance` in this synthetic dataset. Vendor, invoice, and
approval details should not be visible outside the authorized Finance or Procurement scope.

## Example questions

- Which service owns purchase orders?
- Who approves high-value requests?
- What is the dependency between vendor-service and purchase-order-service?
- Which service handles invoice mismatches?
