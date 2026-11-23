"""Application service joining retrieval, generation, citations, and metrics."""

from __future__ import annotations

from time import monotonic
from uuid import UUID

from app.config import Settings
from app.db import InMemoryChunkStore
from app.models import (
    AnswerRequest,
    AnswerResponse,
    Citation,
    SearchRequest,
    SearchResponse,
    SearchResult,
    Usage,
)
from app.rag.embeddings import CachedEmbeddings, FakeEmbeddings, OpenAIEmbeddings
from app.rag.generator import FakeLLM, OpenAILLM
from app.rag.guardrails import NOT_FOUND_ANSWER, looks_like_prompt_injection


class RagService:
    def __init__(self, settings: Settings, store: InMemoryChunkStore | None = None):
        self.settings = settings
        self.store = store or InMemoryChunkStore()
        fake = FakeEmbeddings(settings.embedding_dimensions)
        if settings.ai_provider == "openai" and settings.openai_api_key:
            self.embeddings = CachedEmbeddings(
                OpenAIEmbeddings(settings.openai_api_key, settings.openai_embedding_model, settings.embedding_dimensions),
                settings.embedding_cache_ttl_seconds,
            )
            self.generator = OpenAILLM(settings.openai_api_key, settings.openai_chat_model)
        else:
            self.embeddings = CachedEmbeddings(fake, settings.embedding_cache_ttl_seconds)
            self.generator = FakeLLM()

    def index_chunks(self, chunks, previous_version_id: UUID | None = None) -> int:
        embeddings = self.embeddings.embed([chunk.content for chunk in chunks])
        self.store.replace_version(chunks[0].version_id, chunks, embeddings, previous_version_id)
        return len(chunks)

    @staticmethod
    def rewrite_query(query: str, history) -> str:
        """Resolve simple follow-ups without sending conversation text to retrieval blindly."""
        if not history:
            return query
        follow_up_markers = ("it", "that", "this", "they", "them", "also", "what about", "how about", "how much", "how many")
        lowered = query.lower()
        starts_as_follow_up = lowered.lstrip().startswith(("and ", "also ", "what about", "how about"))
        if not starts_as_follow_up and not any(marker in lowered.split() for marker in follow_up_markers[:6]) and not any(marker in lowered for marker in follow_up_markers[6:]):
            return query
        previous_user = next(
            (message.content for message in reversed(history) if message.role.upper() == "USER"),
            None,
        )
        if previous_user:
            return f"{previous_user} Follow-up question: {query}"
        return query

    def search(self, request: SearchRequest) -> SearchResponse:
        started = monotonic()
        query_embedding = self.embeddings.embed([request.query])[0]
        rows = self.store.search(
            request.query,
            query_embedding,
            request.allowed_departments,
            request.mode,
            request.top_k,
            request.categories,
            self.settings.rag_hybrid_semantic_weight,
        )
        results = [
            SearchResult(
                chunkId=chunk_id,
                documentId=stored.chunk.document_id,
                documentTitle=stored.chunk.document_title,
                department=stored.chunk.department,
                category=stored.chunk.category,
                version=stored.chunk.version,
                pageNumber=stored.chunk.page_number,
                sectionTitle=stored.chunk.section_title,
                content=stored.chunk.content,
                score=round(score, 6),
                semanticScore=round(semantic, 6),
                keywordScore=round(keyword, 6),
            )
            for chunk_id, stored, score, semantic, keyword in rows
        ]
        return SearchResponse(
            query=request.query,
            mode=request.mode,
            results=results,
            latencyMs=int((monotonic() - started) * 1000),
        )

    def answer(self, request: AnswerRequest) -> AnswerResponse:
        started = monotonic()
        if looks_like_prompt_injection(request.query):
            return AnswerResponse(
                queryId=request.query_id,
                answer=NOT_FOUND_ANSWER,
                answered=False,
                confidence=0,
                citations=[],
                latencyMs=int((monotonic() - started) * 1000),
            )
        retrieval_query = self.rewrite_query(request.query, request.history)
        search_response = self.search(
            SearchRequest(
                query=retrieval_query,
                allowedDepartments=request.allowed_departments,
                mode="HYBRID",
                topK=request.top_k,
            )
        )
        results = search_response.results
        confidence = results[0].score if results else 0.0
        if confidence < self.settings.rag_min_similarity:
            return AnswerResponse(
                queryId=request.query_id,
                answer=NOT_FOUND_ANSWER,
                answered=False,
                confidence=round(confidence, 6),
                citations=[],
                latencyMs=int((monotonic() - started) * 1000),
            )
        generated = self.generator.generate(request.query, results)
        citations = [
            Citation(
                citationNumber=index,
                documentId=result.document_id,
                documentTitle=result.document_title,
                department=result.department,
                version=result.version,
                pageNumber=result.page_number,
                sectionTitle=result.section_title,
                chunkId=result.chunk_id,
                snippet=result.content[:500],
                score=result.score,
            )
            for index, result in enumerate(results, start=1)
        ]
        return AnswerResponse(
            queryId=request.query_id,
            answer=generated.text,
            answered=True,
            confidence=round(confidence, 6),
            citations=citations,
            usage=Usage(
                model=generated.model,
                promptTokens=generated.prompt_tokens,
                completionTokens=generated.completion_tokens,
            ),
            latencyMs=int((monotonic() - started) * 1000),
        )
