# Dockerfile templates (owner: Manali)

All service images are built from these templates, so members don't need to write Dockerfiles and every image follows
the same rules: multi-stage, pinned base image, **non-root user**, `HEALTHCHECK` on the contract health path, JSON logs
to stdout, config only via env vars from `.env.example`.

| Template | Services | Build context | What the owner must provide |
|---|---|---|---|
| [`java-service.Dockerfile`](java-service.Dockerfile) | core-api (`SERVICE=core-api PORT=8080`), audit-service (`SERVICE=audit-service PORT=8081`) | repo root | Maven multi-module project in `ayushi_backend/` (`mvn package -pl <service> -am` → one `<service>/target/*.jar`), `/actuator/health`; core-api runs Flyway from `classpath:db/migration` |
| [`python-service.Dockerfile`](python-service.Dockerfile) | ai-service (API), ai-worker (`command: python -m app.ingestion.worker`) | `vedanti_ai/` | `pyproject.toml` (or `requirements.txt`), `app.main:app`, `GET /health`, `app/ingestion/worker.py` |
| [`node-web.Dockerfile`](node-web.Dockerfile) | web | `changhyun_frontend/` | `output: 'standalone'` in `next.config.*`, `build` script, `GET /api/health` |

Each template has a `<name>.Dockerfile.dockerignore` next to it (BuildKit picks it up automatically), so no
`.dockerignore` is needed in members' folders.

The templates are tested against tiny fixture apps in [`../tests/fixtures/`](../tests/fixtures/) — `make test-docker`
builds each image, starts it and waits for the health check. CI runs the same test.

Build everything that exists with compose: `make up-apps`. Build one image by hand: see the header of each template.
