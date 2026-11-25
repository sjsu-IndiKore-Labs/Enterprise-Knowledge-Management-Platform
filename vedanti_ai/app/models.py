"""Contract-shaped models for the internal AI service."""

from __future__ import annotations

from enum import StrEnum
from typing import Any
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field


class DepartmentCode(StrEnum):
    GENERAL = "GENERAL"
    HR = "HR"
    ENGINEERING = "ENGINEERING"
    FINANCE = "FINANCE"
    LEGAL = "LEGAL"
    MARKETING = "MARKETING"


class DocumentCategory(StrEnum):
    POLICY = "POLICY"
    SOP = "SOP"
    MANUAL = "MANUAL"
    GUIDE = "GUIDE"
    REPORT = "REPORT"
    OTHER = "OTHER"


class SearchMode(StrEnum):
    HYBRID = "HYBRID"
    SEMANTIC = "SEMANTIC"
    KEYWORD = "KEYWORD"


class ContractModel(BaseModel):
    model_config = ConfigDict(populate_by_name=True, use_enum_values=True)


class SearchRequest(ContractModel):
    query: str = Field(min_length=1, max_length=1000)
    allowed_departments: list[DepartmentCode] = Field(alias="allowedDepartments", min_length=1)
    mode: SearchMode = SearchMode.HYBRID
    top_k: int = Field(10, alias="topK", ge=1, le=50)
    categories: list[DocumentCategory] | None = None


class HistoryMessage(ContractModel):
    role: str
    content: str


class AnswerRequest(ContractModel):
    query_id: UUID = Field(alias="queryId")
    query: str = Field(min_length=1, max_length=2000)
    allowed_departments: list[DepartmentCode] = Field(alias="allowedDepartments", min_length=1)
    history: list[HistoryMessage] = Field(default_factory=list, max_length=6)
    top_k: int = Field(6, alias="topK", ge=1, le=20)


class SearchResult(ContractModel):
    chunk_id: UUID = Field(alias="chunkId")
    document_id: UUID = Field(alias="documentId")
    document_title: str = Field(alias="documentTitle")
    department: DepartmentCode
    version: int
    content: str
    score: float
    category: DocumentCategory | None = None
    page_number: int | None = Field(None, alias="pageNumber")
    section_title: str | None = Field(None, alias="sectionTitle")
    semantic_score: float | None = Field(None, alias="semanticScore")
    keyword_score: float | None = Field(None, alias="keywordScore")


class SearchResponse(ContractModel):
    query: str
    mode: SearchMode
    results: list[SearchResult]
    latency_ms: int = Field(alias="latencyMs")


class Citation(ContractModel):
    citation_number: int = Field(alias="citationNumber", ge=1)
    document_id: UUID = Field(alias="documentId")
    document_title: str = Field(alias="documentTitle")
    version: int
    chunk_id: UUID = Field(alias="chunkId")
    snippet: str
    score: float
    department: DepartmentCode | None = None
    page_number: int | None = Field(None, alias="pageNumber")
    section_title: str | None = Field(None, alias="sectionTitle")


class Usage(ContractModel):
    model: str
    prompt_tokens: int = Field(alias="promptTokens")
    completion_tokens: int = Field(alias="completionTokens")
    embedding_tokens: int | None = Field(None, alias="embeddingTokens")


class AnswerResponse(ContractModel):
    query_id: UUID = Field(alias="queryId")
    answer: str
    answered: bool
    confidence: float
    citations: list[Citation]
    usage: Usage | None = None
    latency_ms: int = Field(alias="latencyMs")


class ErrorResponse(ContractModel):
    code: str
    message: str
    trace_id: str = Field(alias="traceId")
    timestamp: str
    details: dict[str, Any] | None = None


class IngestEvent(ContractModel):
    event_id: UUID = Field(alias="eventId")
    event_type: str = Field(alias="eventType")
    occurred_at: str = Field(alias="occurredAt")
    trace_id: str = Field(alias="traceId")
    document_id: UUID = Field(alias="documentId")
    version_id: UUID = Field(alias="versionId")
    version: int
    s3_bucket: str = Field(alias="s3Bucket")
    s3_key: str = Field(alias="s3Key")
    content_type: str = Field(alias="contentType")
    original_filename: str = Field(alias="originalFilename")
    title: str
    department: DepartmentCode
    category: DocumentCategory
    uploaded_by: UUID = Field(alias="uploadedBy")
    previous_version_id: UUID | None = Field(None, alias="previousVersionId")
    size_bytes: int | None = Field(None, alias="sizeBytes")
    tags: list[str] = Field(default_factory=list)
    author: str | None = None


class IngestStatus(ContractModel):
    event_id: UUID = Field(alias="eventId")
    event_type: str = Field(alias="eventType")
    occurred_at: str = Field(alias="occurredAt")
    trace_id: str = Field(alias="traceId")
    document_id: UUID = Field(alias="documentId")
    version_id: UUID = Field(alias="versionId")
    status: str
    page_count: int | None = Field(None, alias="pageCount")
    chunk_count: int | None = Field(None, alias="chunkCount")
    embedding_tokens: int | None = Field(None, alias="embeddingTokens")
    processing_ms: int | None = Field(None, alias="processingMs")
    error_code: str | None = Field(None, alias="errorCode")
    error_message: str | None = Field(None, alias="errorMessage")
