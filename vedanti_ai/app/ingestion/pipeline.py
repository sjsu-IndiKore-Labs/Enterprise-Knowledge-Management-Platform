"""S3 document ingestion pipeline used by the worker and local tests."""

from __future__ import annotations

from datetime import UTC, datetime
from pathlib import Path
from uuid import uuid4

from app.config import Settings
from app.ingestion.chunker import chunk_segments
from app.ingestion.extract import extract_document
from app.models import IngestEvent, IngestStatus
from app.rag.service import RagService


class IngestionPipeline:
    def __init__(self, settings: Settings, rag_service: RagService):
        self.settings = settings
        self.rag_service = rag_service

    def process_file(self, event: IngestEvent, path: str | Path) -> IngestStatus:
        started = datetime.now(UTC)
        try:
            segments = extract_document(path, event.content_type)
            chunks = chunk_segments(
                segments,
                document_id=event.document_id,
                version_id=event.version_id,
                version=event.version,
                department=event.department,
                category=event.category,
                document_title=event.title,
                chunk_size=self.settings.chunk_size_tokens,
                overlap=self.settings.chunk_overlap_tokens,
            )
            self.rag_service.index_chunks(chunks, event.previous_version_id)
            elapsed = int((datetime.now(UTC) - started).total_seconds() * 1000)
            return self._status(event, "INDEXED", page_count=len({c.page_number for c in chunks if c.page_number}), chunk_count=len(chunks), processing_ms=elapsed)
        except ValueError as exc:
            error_code = str(exc) if str(exc) in {
                "UNSUPPORTED_FORMAT", "CORRUPT_FILE", "NO_EXTRACTABLE_TEXT"
            } else "INTERNAL_ERROR"
            return self._status(event, "FAILED", error_code=error_code, error_message=str(exc)[:1000])
        except Exception as exc:  # noqa: BLE001 - convert unexpected parser/provider errors to contract status.
            return self._status(event, "FAILED", error_code="INTERNAL_ERROR", error_message=str(exc)[:1000])

    @staticmethod
    def _status(event: IngestEvent, status: str, **kwargs) -> IngestStatus:
        return IngestStatus(
            eventId=uuid4(),
            eventType="DOCUMENT_INGEST_STATUS",
            occurredAt=datetime.now(UTC).isoformat(),
            traceId=event.trace_id,
            documentId=event.document_id,
            versionId=event.version_id,
            status=status,
            **kwargs,
        )
