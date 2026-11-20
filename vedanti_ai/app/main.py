from datetime import UTC, datetime

from fastapi import FastAPI, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.api.routes import create_router
from app.config import get_settings
from app.db import create_store
from app.rag.service import RagService

settings = get_settings()
rag_service = RagService(settings, create_store(settings))
app = FastAPI(
    title="Enterprise Knowledge Management AI Service",
    version="1.0.0",
)
app.include_router(create_router(rag_service))


def _error_response(request: Request, code: str, message: str, details=None, status_code: int = 500):
    return JSONResponse(
        status_code=status_code,
        content={
            "code": code,
            "message": message,
            "details": details,
            "traceId": getattr(request.state, "request_id", "unknown"),
            "timestamp": datetime.now(UTC).isoformat(),
        },
    )


@app.middleware("http")
async def add_request_id(request: Request, call_next):
    from uuid import uuid4

    request.state.request_id = request.headers.get("X-Request-Id", str(uuid4()))
    response = await call_next(request)
    response.headers["X-Request-Id"] = request.state.request_id
    return response


@app.exception_handler(HTTPException)
async def handle_http_exception(request: Request, exception: HTTPException):
    code = "UNAUTHENTICATED" if exception.status_code == 401 else "HTTP_ERROR"
    message = exception.detail if isinstance(exception.detail, str) else "Request failed"
    return _error_response(request, code, message, status_code=exception.status_code)


@app.exception_handler(RequestValidationError)
async def handle_validation_exception(request: Request, exception: RequestValidationError):
    return _error_response(request, "VALIDATION_ERROR", "Invalid request body", exception.errors(), 422)


@app.get("/health")
async def health() -> dict[str, str]:
    """Report whether the AI service process is alive."""
    return {
        "status": "DEGRADED",
        "provider": settings.ai_provider,
        "database": "DOWN",
        "queueConsumer": "STOPPED",
    }
