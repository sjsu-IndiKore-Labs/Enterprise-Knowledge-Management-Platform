---
from: manali
to: ayushi
status: open
priority: normal
created: 2026-10-19
related: manali_devops/docker/java-service.Dockerfile, contracts/ports-and-services.md
---

# Can core-api / audit-service follow these container conventions?

## Context
Dockerfiles for both Spring services are provided by DevOps (`manali_devops/docker/java-service.Dockerfile`),
tested against a fixture Gradle project. In AWS, RDS only accepts TLS connections.

## Question
Please confirm you can do all of these (or tell me what to change in the template):
1. Gradle multi-project in `ayushi_backend/` (`settings.gradle.kts` with `core-api`, `audit-service`), `:<service>:bootJar` builds the runnable jar.
2. Server port from env: `server.port: ${PORT:8080}` (core-api) / `${PORT:8081}` (audit-service).
3. Flyway: `spring.flyway.locations: filesystem:${FLYWAY_LOCATION:../contracts/database/migrations}`; the image has migrations at `/contracts/database/migrations`. audit-service: `spring.flyway.enabled: false`.
4. JDBC URL: `jdbc:postgresql://${POSTGRES_HOST}:${POSTGRES_PORT}/${POSTGRES_DB}?sslmode=${POSTGRES_SSLMODE:disable}` (new env var, `require` in AWS).
5. AWS SDK: when `AWS_ENDPOINT_URL` is empty, use the default endpoints and credentials chain (ECS task role), no static keys.
6. Actuator: expose `health` only; `/actuator/health` returns 200 without auth.

## Proposed answer
Yes to all six. Note: `FLYWAY_LOCATION` would be a new env var. Alternatively use a Spring profile `docker` that sets the path.

## Answer
