# Multi-stage Dockerfile for audit-service
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY ayushi_backend/pom.xml ./ayushi_backend/
COPY ayushi_backend/common/pom.xml ./ayushi_backend/common/
COPY ayushi_backend/core-api/pom.xml ./ayushi_backend/core-api/
COPY ayushi_backend/audit-service/pom.xml ./ayushi_backend/audit-service/
COPY ayushi_backend/common/src ./ayushi_backend/common/src
COPY ayushi_backend/audit-service/src ./ayushi_backend/audit-service/

WORKDIR /app/ayushi_backend
RUN mvn clean package -pl audit-service -am -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S ekm && adduser -S ekm -G ekm
COPY --from=build /app/ayushi_backend/audit-service/target/*.jar app.jar
RUN chown -R ekm:ekm /app
USER ekm

EXPOSE 8081
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8081/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
