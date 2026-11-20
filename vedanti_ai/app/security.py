"""Internal service authentication and request correlation."""

from __future__ import annotations

from uuid import uuid4

from fastapi import Header, HTTPException, Request

from app.config import get_settings


async def require_internal_token(x_internal_token: str | None = Header(default=None)) -> None:
    settings = get_settings()
    if not x_internal_token or x_internal_token != settings.internal_service_token:
        raise HTTPException(status_code=401, detail="Missing or invalid X-Internal-Token")


async def request_id(request: Request, x_request_id: str | None = Header(default=None)) -> str:
    value = x_request_id or str(uuid4())
    request.state.request_id = value
    return value
