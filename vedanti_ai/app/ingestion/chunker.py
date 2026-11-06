"""Deterministic token-aware chunking that preserves source metadata."""

from __future__ import annotations

from dataclasses import dataclass
from uuid import UUID

from app.ingestion.extract import ExtractedSegment
from app.models import DepartmentCode, DocumentCategory


@dataclass(frozen=True)
class Chunk:
    document_id: UUID
    version_id: UUID
    version: int
    department: DepartmentCode
    category: DocumentCategory
    document_title: str
    chunk_index: int
    content: str
    token_count: int
    page_number: int | None
    section_title: str | None


def _words(text: str) -> list[str]:
    return text.split()


def chunk_segments(
    segments: list[ExtractedSegment],
    *,
    document_id: UUID,
    version_id: UUID,
    version: int,
    department: DepartmentCode,
    category: DocumentCategory,
    document_title: str,
    chunk_size: int = 500,
    overlap: int = 80,
) -> list[Chunk]:
    if chunk_size <= 0 or overlap < 0 or overlap >= chunk_size:
        raise ValueError("chunk_size must be positive and overlap must be smaller than chunk_size")

    chunks: list[Chunk] = []
    index = 0
    for segment in segments:
        words = _words(segment.text)
        start = 0
        while start < len(words):
            end = min(start + chunk_size, len(words))
            content = " ".join(words[start:end]).strip()
            if content:
                chunks.append(
                    Chunk(
                        document_id=document_id,
                        version_id=version_id,
                        version=version,
                        department=department,
                        category=category,
                        document_title=document_title,
                        chunk_index=index,
                        content=content,
                        token_count=end - start,
                        page_number=segment.page_number,
                        section_title=segment.section_title,
                    )
                )
                index += 1
            if end == len(words):
                break
            start = end - overlap
    if not chunks:
        raise ValueError("NO_EXTRACTABLE_TEXT")
    return chunks
