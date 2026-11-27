# Application overview: PeopleHub

**Application ID:** `peoplehub`  
**Application:** PeopleHub  
**Domain:** HR and employee management  
**Demo owner:** People Platforms Team  
**Escalation role:** HR Systems Manager  
**Demo access label:** Internal HR  
**Data status:** Helios Demo Enterprise Data; synthetic

## Purpose

PeopleHub is the fictional Helios employee-management application. It supports employee profiles,
leave requests, leave balances, payroll integration, and HR notifications.

## Main capabilities

- View and update employee profile information.
- Submit, approve, and track leave requests.
- Calculate leave balances using approved policy rules.
- Exchange approved payroll-impacting events with a payroll adapter.
- Send request and approval notifications.

## Application boundary

PeopleHub owns HR workflows and employee-facing HR records. It does not own enterprise knowledge
documents, IT tickets, procurement vendors, or invoices. Those belong to EKM, ServiceDesk, and ProcureFlow.

## Questions this pack should answer

- Who owns PeopleHub?
- Which service handles leave requests?
- What happens after a manager approves leave?
- Which service communicates with payroll?
- What access label applies to PeopleHub documents?
