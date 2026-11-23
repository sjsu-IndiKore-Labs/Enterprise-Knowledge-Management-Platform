"""Contract-aligned AI service routes."""

from __future__ import annotations

import json

from fastapi import APIRouter, Depends
from sse_starlette.sse import EventSourceResponse

from app.models import AnswerRequest, SearchRequest
from app.rag.service import RagService
from app.security import require_internal_token


def create_router(service: RagService) -> APIRouter:
    router = APIRouter(dependencies=[Depends(require_internal_token)])

    @router.post("/internal/v1/search")
    async def search(request: SearchRequest):
        return service.search(request).model_dump(by_alias=True, exclude_none=True)

    @router.post("/internal/v1/answer")
    async def answer(request: AnswerRequest):
        return service.answer(request).model_dump(by_alias=True, exclude_none=True)

    @router.post("/internal/v1/answer/stream")
    async def answer_stream(request: AnswerRequest):
        response = service.answer(request)

        async def events():
            if response.answer:
                words = response.answer.split(" ")
                for index, word in enumerate(words):
                    yield {"event": "token", "data": json.dumps({"text": word + (" " if index < len(words) - 1 else "")})}
            yield {
                "event": "citations",
                "data": json.dumps({"citations": [citation.model_dump(by_alias=True, exclude_none=True) for citation in response.citations]}),
            }
            yield {
                "event": "done",
                "data": json.dumps({
                    "answered": response.answered,
                    "confidence": response.confidence,
                    "latencyMs": response.latency_ms,
                }),
            }

        return EventSourceResponse(events())

    return router
