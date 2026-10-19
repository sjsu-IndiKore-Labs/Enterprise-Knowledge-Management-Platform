# ayushi_backend/ — Backend & Security (owner: Ayushi)

Two Spring Boot 3 / Java 21 services. Build tool: **Gradle (Kotlin DSL)** multi-project.

```
ayushi_backend/
  settings.gradle.kts            # include("core-api", "audit-service", "common")
  build.gradle.kts
  gradlew, gradle/
  common/                        # shared DTOs generated from OpenAPI, error body, JSON config, SQS helpers
  core-api/                      # :8080 auth, users, documents, search+chat proxy, conversations, analytics
    src/main/java/com/helios/ekm/coreapi/
      auth/  user/  document/  search/  chat/  analytics/  messaging/  config/  common/
    src/main/resources/application.yml, application-local.yml
    src/test/java/...            # unit + Testcontainers (postgres pgvector, localstack)
    Dockerfile
  audit-service/                 # :8081 consumes ekm-audit-events -> audit_logs, query_logs, knowledge_gaps
    src/main/java/com/helios/ekm/audit/
    Dockerfile
```

Package root: `com.helios.ekm`. DB migrations are **not** here — they live in
[`../contracts/database/migrations`](../contracts/database/migrations) (Flyway location configured to that folder).

## Run
```bash
make up && make seed                                   # from repo root: postgres + localstack + dev users
cd ayushi_backend && ./gradlew :core-api:bootRun --args='--spring.profiles.active=local'
./gradlew :audit-service:bootRun --args='--spring.profiles.active=local'
```
Swagger UI (springdoc): http://localhost:8080/swagger-ui.html — must match `contracts/openapi/core-api.yaml`.
