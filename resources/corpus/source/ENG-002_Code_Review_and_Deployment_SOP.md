# Code Review and Deployment SOP

Document ID: ENG-002 | Department: ENGINEERING | Category: SOP | Owner: Developer Productivity | Version: 1 | Effective: 2026-01-10

## 1. Branching

All work happens on short-lived feature branches created from main. Branch names follow the format type/TICKET-123-short-description, where type is feat, fix, or chore. Branches older than 14 days are flagged in the weekly engineering report.

## 2. Pull Request Requirements

- Every pull request needs at least 2 approving reviews, one of which must be from a code owner of the changed files.
- All CI checks must pass: unit tests, lint, security scan (Snyk), and test coverage of at least 80% on changed lines.
- Pull requests should be smaller than 400 changed lines; larger changes need a design doc link.
- Reviewers should respond within 1 business day.

## 3. Deployment Windows

Production deployments are allowed Monday through Thursday between 9:00 AM and 3:00 PM Pacific Time. No deployments on Fridays, weekends, or company holidays, except hotfixes for SEV1 or SEV2 incidents. A company-wide code freeze is in effect from December 18 to January 4 every year.

## 4. Deployment Process

Deployments go through three environments: dev (automatic on merge), staging (automatic, with smoke tests), and production (manual promotion by the change owner). Production deployments use a canary release: 10% of traffic for 30 minutes, then 50% for 30 minutes, then 100%. The deployer must watch the error-rate dashboard during the canary and roll back if the error rate rises above 1%.

## 5. Database Migrations

Schema migrations must be backward compatible with the previous application version and reviewed by a member of the Data Platform team. Destructive migrations (dropping columns or tables) must be done in a separate release at least one week after the code stops using them.
