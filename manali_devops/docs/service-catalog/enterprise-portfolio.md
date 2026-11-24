# Helios enterprise application portfolio

**Document type:** enterprise application catalog  
**Application:** Helios enterprise portfolio  
**Status:** synthetic demo data for EKM grounding  
**Last verified:** 2026-11-23

## Portfolio purpose

The EKM Knowledge Portal manages knowledge for several Helios enterprise applications. EKM itself is
one application in the portfolio; it is not the same thing as the applications whose documents it stores.

## Application catalog

| Application ID | Application | Business domain | Demo access label | Owner team |
|---|---|---|---|---|
| `ekm` | EKM Knowledge Portal | Enterprise knowledge management | Internal | Platform Engineering |
| `peoplehub` | PeopleHub | HR and employee management | Internal HR | People Platforms Team |
| `servicedesk` | ServiceDesk | IT service management | Internal | IT Service Management Team |
| `procureflow` | ProcureFlow | Procurement and finance | Internal Finance | Finance Technology Team |

## Portfolio relationships

- EKM stores and retrieves the approved documentation for all three managed applications.
- PeopleHub owns employee and leave workflows.
- ServiceDesk owns internal support tickets, incidents, and assets.
- ProcureFlow owns vendors, purchase orders, approvals, and invoices.
- ServiceDesk may link to PeopleHub for requester identity, but it does not own HR records.
- ProcureFlow may reference PeopleHub employee identity for approvals, but it does not own employee profiles.

## Ownership model

Ownership is represented by a team rather than an invented individual employee:

- PeopleHub: People Platforms Team; escalation role: HR Systems Manager.
- ServiceDesk: IT Service Management Team; escalation role: IT Operations Manager.
- ProcureFlow: Finance Technology Team; escalation role: Procurement Systems Manager.
- EKM: Platform Engineering; escalation role: EKM Platform Owner.

## Example questions

- Which applications are managed by the Helios enterprise?
- Who owns the leave-service?
- Which application manages purchase orders?
- Which services belong to ServiceDesk?
- Which applications contain HR-restricted information?
- What is the difference between EKM and PeopleHub?

## Synthetic-data notice

This portfolio is created for demonstration and retrieval testing. Application names, service names,
owners, dependencies, classifications, and workflows are fictional. Do not present them as real college
or company systems.
