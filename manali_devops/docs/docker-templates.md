# Docker templates

These are the approved container conventions for the project. Service owners keep the
Dockerfiles in their own folders; this document is the shared DevOps template.

## Java 21 / Spring Boot

- Build with `maven:3.9-eclipse-temurin-21`.
- Copy only the selected module and its shared module into the build stage.
- Run `mvn clean package -DskipTests`.
- Run the application on `eclipse-temurin:21-jre` as a non-root user.
- Expose the contract port and add an HTTP healthcheck for `/actuator/health`.

## Node 20 / Next.js

- Build with `node:20-alpine`.
- Enable standalone output in Next.js.
- Install dependencies with the repository's locked package manager.
- Copy only `.next/standalone` and `.next/static` into the runtime image.
- Run as a non-root user on port `3000`.
- Add a healthcheck for `/api/health`.

## Python 3.11 / FastAPI

- Build with `python:3.11-slim`.
- Install pinned production dependencies into the image.
- Run as a non-root user with the service's configured command.
- Expose port `8000`.
- Add a healthcheck for `/health`.

All images must avoid secrets in build arguments, use the ports and health paths in
`contracts/ports-and-services.md`, and emit JSON-friendly stdout logs.
