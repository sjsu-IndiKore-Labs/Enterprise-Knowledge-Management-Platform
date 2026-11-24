# Application overview: ProcureFlow

**Application ID:** `procureflow`  
**Application:** ProcureFlow  
**Domain:** Procurement and finance  
**Demo owner:** Finance Technology Team  
**Escalation role:** Procurement Systems Manager  
**Demo access label:** Internal Finance  
**Data status:** Helios Demo Enterprise Data; synthetic

## Purpose

ProcureFlow is the fictional Helios platform for vendor onboarding, purchase requests, approval chains,
purchase orders, invoice matching, and procurement notifications.

## Main capabilities

- Register and review vendors.
- Create purchase requests and route them to the correct approvers.
- Issue purchase orders after approval.
- Match invoices to purchase orders and receipts.
- Notify requesters, approvers, vendors, and finance operations.

## Application boundary

ProcureFlow owns procurement transactions and vendor records. It may use employee identity from PeopleHub
for approval routing, but it does not own employee profiles or payroll.

## Questions this pack should answer

- Who owns ProcureFlow?
- Which service handles vendor onboarding?
- How does a purchase request become a purchase order?
- Which approvals are required for a high-value request?
- What service matches invoices to purchase orders?
