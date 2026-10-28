# ayushi_backend/ — Backend & Security (owner: Ayushi)

Two Spring Boot 3 / Java 21 services. Build tool: **Apache Maven** multi-module project.

```
ayushi_backend/
  pom.xml                        # Parent POM: modules ("common", "core-api", "audit-service")
  mvnw, mvnw.cmd, .mvn/          # Maven wrapper
  common/                        # Shared DTOs, events, error body, JSON config, SQS helpers
    pom.xml
  core-api/                      # :8080 auth, users, documents, search+chat proxy, conversations, analytics
    pom.xml
    src/main/java/com/helios/ekm/coreapi/
      auth/  user/  document/  search/  chat/  analytics/  messaging/  config/  common/
    src/main/resources/application.yml, application-local.yml
    src/test/java/...            # unit + Testcontainers (postgres pgvector, localstack)
    Dockerfile
  audit-service/                 # :8081 consumes ekm-audit-events -> audit_logs, query_logs, knowledge_gaps
    pom.xml
    src/main/java/com/helios/ekm/audit/
    src/main/resources/application.yml
    Dockerfile
```

Package root: `com.helios.ekm`. DB migrations are **not** here — they live in
[`../contracts/database/migrations`](../contracts/database/migrations) (Flyway location configured to `classpath:db/migration,filesystem:../contracts/database/migrations`).

## Build & Run

### 1. Build all modules
```bash
cd ayushi_backend
./mvnw clean compile
# or on Windows PowerShell:
.\mvnw.cmd clean compile
```

### 2. Run with Docker infrastructure
```bash
# from repo root: start postgres + localstack + dev users
make up && make seed

# Run core-api (:8080)
cd ayushi_backend
./mvnw -pl core-api spring-boot:run -Dspring-boot.run.profiles=local

# Run audit-service (:8081)
./mvnw -pl audit-service spring-boot:run -Dspring-boot.run.profiles=local
```

### 3. Run Tests
```bash
cd ayushi_backend
./mvnw test
```

### 4. Interactive Swagger UI
When `core-api` is running:
- **Swagger UI (springdoc):** http://localhost:8080/swagger-ui.html — strictly matches `contracts/openapi/core-api.yaml`.
- **OpenAPI JSON Spec:** http://localhost:8080/v3/api-docs
