# ADR-0005: Browser talks only to core-api; SSE for streaming

Status: Proposed (2026-10-19)

## Decision
The web app calls only `core-api` (`/api/v1`). core-api relays chat streaming from ai-service using Server-Sent Events
(`contracts/streaming.md`). ai-service is never publicly reachable and trusts only `X-Internal-Token`.

## Consequences
One place for auth, RBAC, rate limiting, auditing. Slight extra latency for relaying tokens (negligible). SSE over POST requires
`fetch` streaming on the client (not `EventSource`).
