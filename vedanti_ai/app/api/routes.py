"""Contract-aligned AI service routes."""

from __future__ import annotations

import json
from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Path, Request
from sse_starlette.sse import EventSourceResponse

from app.models import AnswerRequest, ReindexRequest, SearchRequest
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

    @router.post("/internal/v1/documents/{documentId}/reindex", status_code=202)
    async def reindex(
        document_id: Annotated[UUID, Path(alias="documentId")],
        request: ReindexRequest = ...,
        http_request: Request = ...,
    ):
        trace_id = getattr(http_request.state, "request_id", "unknown")
        try:
            service.reindex(document_id, request.version_id, trace_id)
        except LookupError as exc:
            raise HTTPException(status_code=404, detail=str(exc)) from exc
        except RuntimeError as exc:
            raise HTTPException(status_code=503, detail=str(exc)) from exc
        except Exception as exc:
            raise HTTPException(status_code=503, detail="REINDEX_QUEUE_UNAVAILABLE") from exc
        return {"status": "REQUEUED", "documentId": str(document_id), "versionId": str(request.version_id)}

    return router
