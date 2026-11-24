# syntax=docker/dockerfile:1.7
# =============================================================================
# Spring Boot services (core-api, audit-service). Owner: Manali.
# Build context: REPO ROOT (the image needs contracts/database/migrations for Flyway).
#
#   docker build -f manali_devops/docker/java-service.Dockerfile \
#     --build-arg SERVICE=core-api --build-arg PORT=8080 -t ekm-core-api .
#
# Expects (Ayushi): a Gradle multi-project in $BACKEND_DIR where `:<SERVICE>:bootJar`
# produces <SERVICE>/build/libs/<name>.jar, and GET /actuator/health on $PORT.
# Flyway location inside the image: filesystem:/contracts/database/migrations
# =============================================================================
ARG GRADLE_IMAGE=gradle:8.10.2-jdk21
ARG RUNTIME_IMAGE=eclipse-temurin:21-jre-alpine

FROM ${GRADLE_IMAGE} AS build
ARG BACKEND_DIR=ayushi_backend
ARG SERVICE=core-api
WORKDIR /src
COPY ${BACKEND_DIR}/ ./
# Use the project's Gradle wrapper when present, otherwise the image's Gradle.
RUN --mount=type=cache,target=/home/gradle/.gradle/caches \
    GRADLE="gradle"; if [ -f ./gradlew ]; then chmod +x ./gradlew && GRADLE="./gradlew"; fi; \
    $GRADLE --no-daemon -q ":${SERVICE}:bootJar" -x test \
 && for f in "${SERVICE}"/build/libs/*.jar; do case "$f" in *-plain.jar) ;; *) cp "$f" /app.jar ;; esac; done \
 && test -f /app.jar

FROM ${RUNTIME_IMAGE}
ARG SERVICE=core-api
ARG PORT=8080
LABEL org.opencontainers.image.title="ekm-${SERVICE}" \
      org.opencontainers.image.source="https://github.com/sjsu-IndiKore-Labs/Enterprise-Knowledge-Management-Platform"
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app
COPY --from=build --chown=app:app /app.jar /app/app.jar
COPY --chown=app:app contracts/database/migrations /contracts/database/migrations
ENV PORT=${PORT} \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"
USER app
EXPOSE ${PORT}
HEALTHCHECK --interval=15s --timeout=3s --start-period=90s --retries=5 \
  CMD wget -qO- "http://127.0.0.1:${PORT}/actuator/health" >/dev/null || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
