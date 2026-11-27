# Multi-stage Dockerfile for core-api
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY ayushi_backend/pom.xml ./ayushi_backend/
COPY ayushi_backend/common/pom.xml ./ayushi_backend/common/
COPY ayushi_backend/core-api/pom.xml ./ayushi_backend/core-api/
COPY ayushi_backend/audit-service/pom.xml ./ayushi_backend/audit-service/
COPY ayushi_backend/common/src ./ayushi_backend/common/src
COPY ayushi_backend/core-api/src ./ayushi_backend/core-api/src
COPY contracts/database/migrations ./contracts/database/migrations

WORKDIR /app/ayushi_backend
RUN mvn clean package -pl core-api -am -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S ekm && adduser -S ekm -G ekm
COPY --from=build /app/ayushi_backend/core-api/target/*.jar app.jar
COPY contracts/database/migrations /contracts/database/migrations
RUN chown -R ekm:ekm /app /contracts
USER ekm

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
