# Architecture: ProcureFlow

**Application ID:** `procureflow`  
**Status:** illustrative synthetic architecture; not a deployed system

## Logical flow

```text
Employee / Procurement Analyst / Approver
        │
        ▼
ProcureFlow portal
        │
        ├──► vendor-service ───────► Vendor database
        ├──► purchase-order-service ► Procurement database
        ├──► approval-service ─────► Approval rules and identity
        ├──► invoice-service ──────► Invoice and receipt records
        └──► notification-service ─► Email / finance notifications
```

## Services

| Service | Responsibility | Owner team | Main dependency |
|---|---|---|---|
| `vendor-service` | Vendor registration and review | Finance Technology Team | Vendor database |
| `purchase-order-service` | Purchase requests and purchase orders | Procurement Systems Team | Vendor-service, approval-service |
| `approval-service` | Approval routing and policy checks | Finance Technology Team | PeopleHub identity, approval rules |
| `invoice-service` | Invoice capture and matching | Accounts Payable Systems Team | Purchase-order-service |
| `notification-service` | Procurement workflow notifications | Finance Technology Team | Notification provider |

## Key workflow: purchase request

1. An employee creates a purchase request.
2. `vendor-service` validates the selected vendor.
3. `approval-service` determines the required approval chain.
4. Approvers approve or reject the request.
5. `purchase-order-service` issues a purchase order after approval.
6. `invoice-service` matches the invoice to the purchase order and receipt.
7. `notification-service` sends status updates.

## Failure handling

- A rejected request must not produce a purchase order.
- Invoice mismatches enter a review queue and do not silently pass approval.
- Notification failures must not change the transaction state.
