# syntax=docker/dockerfile:1.7
# =============================================================================
# Python FastAPI service (ai-service API + ai-worker). Owner: Manali.
# Build context: vedanti_ai/
#
#   docker build -f manali_devops/docker/python-service.Dockerfile -t ekm-ai-service vedanti_ai
#
# Expects (Vedanti): pyproject.toml (or requirements.txt) at the context root, package `app`,
# ASGI app at app.main:app with GET /health. The worker runs from the SAME image with
#   command: ["python", "-m", "app.ingestion.worker"]
# =============================================================================
ARG PYTHON_IMAGE=python:3.11-slim

FROM ${PYTHON_IMAGE} AS build
ENV PIP_NO_CACHE_DIR=1 PIP_DISABLE_PIP_VERSION_CHECK=1
RUN python -m venv /venv
ENV PATH=/venv/bin:$PATH
WORKDIR /src
# Dependencies first (better layer caching), then the source.
COPY pyproject.toml* requirements*.txt ./
RUN if [ -f requirements.txt ]; then pip install -r requirements.txt; fi
COPY . .
RUN if [ -f pyproject.toml ]; then pip install .; fi

FROM ${PYTHON_IMAGE}
ARG PORT=8000
LABEL org.opencontainers.image.title="ekm-ai-service" \
      org.opencontainers.image.source="https://github.com/sjsu-IndiKore-Labs/Enterprise-Knowledge-Management-Platform"
RUN useradd --create-home --uid 10001 app
COPY --from=build /venv /venv
WORKDIR /app
COPY --chown=app:app . /app
ENV PATH=/venv/bin:$PATH \
    PYTHONPATH=/app \
    PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PORT=${PORT} \
    APP_MODULE=app.main:app \
    UVICORN_WORKERS=2
USER app
EXPOSE ${PORT}
HEALTHCHECK --interval=15s --timeout=3s --start-period=30s --retries=5 \
  CMD python -c "import os,urllib.request; urllib.request.urlopen(f'http://127.0.0.1:{os.environ[\"PORT\"]}/health', timeout=2)" || exit 1
# --http h11: core-api's java.net.http.HttpClient offers an h2c upgrade on plain HTTP; uvicorn's default
# httptools parser drops the POST body on that request (core-api then sees 400/422). h11 ignores the upgrade.
CMD ["sh", "-c", "exec uvicorn \"$APP_MODULE\" --host 0.0.0.0 --port \"$PORT\" --workers \"$UVICORN_WORKERS\" --proxy-headers --http h11"]
