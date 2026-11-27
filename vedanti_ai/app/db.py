"""Chunk persistence adapters.

The in-memory store makes local fake-provider development deterministic. The
PostgreSQL adapter contains the production retrieval query and never performs
authorization filtering after retrieval.
"""

from __future__ import annotations

import math
import re
from dataclasses import dataclass
from datetime import UTC, datetime
from uuid import UUID

from app.ingestion.chunker import Chunk
from app.models import DepartmentCode, DocumentCategory, IngestEvent, SearchMode
from app.rag.query import canonical_term, normalize_query


@dataclass(frozen=True)
class StoredChunk:
    chunk: Chunk
    embedding: list[float]
    is_current: bool = True
    is_deleted: bool = False


def _cosine(left: list[float], right: list[float]) -> float:
    numerator = sum(a * b for a, b in zip(left, right))
    left_norm = math.sqrt(sum(value * value for value in left))
    right_norm = math.sqrt(sum(value * value for value in right))
    if not left_norm or not right_norm:
        return 0.0
    return max(0.0, min(1.0, numerator / (left_norm * right_norm)))


def _keyword_score(query: str, content: str) -> float:
    stop_words = {
        "a", "about", "am", "an", "and", "are", "can", "do", "does", "for", "how",
        "i", "in", "is", "it", "me", "of", "on", "or", "the", "this", "to", "was",
        "what", "when", "where", "which", "who", "why", "will", "with", "you",
    }
    query_terms = {
        canonical_term(term)
        for term in re.findall(r"[a-z0-9]+", normalize_query(query).lower())
        if len(term) > 1 and canonical_term(term) not in stop_words
    }
    content_terms = {
        canonical_term(term) for term in re.findall(r"[a-z0-9]+", content.lower())
    }
    if not query_terms:
        return 0.0
    return len(query_terms & content_terms) / len(query_terms)


class InMemoryChunkStore:
    def __init__(self) -> None:
        self._chunks: dict[UUID, StoredChunk] = {}

    def health(self) -> str:
        return "UP"

    def get_ingest_event(self, document_id: UUID, version_id: UUID, trace_id: str) -> IngestEvent:
        raise RuntimeError("REINDEX_REQUIRES_DATABASE")

    def replace_version(self, version_id: UUID, chunks: list[Chunk], embeddings: list[list[float]], previous_version_id: UUID | None = None) -> None:
        self.mark_previous_version_not_current(previous_version_id)
        for chunk_id, stored in list(self._chunks.items()):
            if stored.chunk.version_id == version_id:
                del self._chunks[chunk_id]
        for chunk, embedding in zip(chunks, embeddings):
            # Stable IDs are useful for repeatable local tests and are replaced by
            # database-generated UUIDs in the PostgreSQL adapter.
            import uuid

            chunk_id = uuid.uuid5(uuid.NAMESPACE_URL, f"{chunk.version_id}:{chunk.chunk_index}")
            self._chunks[chunk_id] = StoredChunk(chunk, embedding)

    def mark_previous_version_not_current(self, previous_version_id: UUID | None) -> None:
        if previous_version_id is None:
            return
        for chunk_id, stored in self._chunks.items():
            if stored.chunk.version_id == previous_version_id:
                self._chunks[chunk_id] = StoredChunk(stored.chunk, stored.embedding, False, stored.is_deleted)

    def search(
        self,
        query: str,
        query_embedding: list[float],
        allowed_departments: list[DepartmentCode],
        mode: SearchMode,
        top_k: int,
        categories: list[DocumentCategory] | None = None,
        semantic_weight: float = 0.7,
    ) -> list[tuple[UUID, StoredChunk, float, float, float]]:
        query = normalize_query(query)
        allowed = set(allowed_departments)
        candidates = []
        for chunk_id, stored in self._chunks.items():
            chunk = stored.chunk
            if stored.is_deleted or not stored.is_current or chunk.department not in allowed:
                continue
            if categories and chunk.category not in set(categories):
                continue
            semantic = _cosine(query_embedding, stored.embedding)
            keyword = _keyword_score(query, chunk.content)
            if mode == SearchMode.SEMANTIC:
                score = semantic
            elif mode == SearchMode.KEYWORD:
                score = keyword
            else:
                score = semantic_weight * semantic + (1 - semantic_weight) * keyword
            candidates.append((chunk_id, stored, score, semantic, keyword))
        candidates.sort(key=lambda item: item[2], reverse=True)
        return candidates[:top_k]


class PostgresChunkStore:
    """Production adapter; migrations remain owned and applied by core-api."""

    def __init__(self, connection_pool):
        self.pool = connection_pool

    def health(self) -> str:
        try:
            with self.pool.connection() as connection, connection.cursor() as cursor:
                cursor.execute("SELECT 1")
            return "UP"
        except Exception:  # noqa: BLE001 - health must return a response when DB is unavailable.
            return "DOWN"

    def get_ingest_event(self, document_id: UUID, version_id: UUID, trace_id: str) -> IngestEvent | None:
        sql = """
        SELECT d.title, d.department_code, d.category,
               v.id, v.version, v.s3_bucket, v.s3_key, v.content_type,
               v.original_filename, v.size_bytes, v.uploaded_by,
               previous.id
        FROM documents AS d
        JOIN document_versions AS v ON v.document_id = d.id
        LEFT JOIN document_versions AS previous
          ON previous.document_id = v.document_id
         AND previous.version = v.version - 1
        WHERE d.id = %s AND v.id = %s AND d.deleted_at IS NULL
        """
        with self.pool.connection() as connection, connection.cursor() as cursor:
            cursor.execute(sql, (document_id, version_id))
            row = cursor.fetchone()
        if row is None:
            return None
        title, department, category, version_id, version, bucket, key, content_type, filename, size_bytes, uploaded_by, previous_id = row
        return IngestEvent(
            eventId=__import__("uuid").uuid4(),
            eventType="DOCUMENT_INGEST_REQUESTED",
            occurredAt=datetime.now(UTC).isoformat(),
            traceId=trace_id,
            documentId=document_id,
            versionId=version_id,
            version=version,
            previousVersionId=previous_id,
            s3Bucket=bucket,
            s3Key=key,
            contentType=content_type,
            originalFilename=filename,
            sizeBytes=size_bytes,
            title=title,
            department=DepartmentCode(department),
            category=DocumentCategory(category),
            uploadedBy=uploaded_by,
        )

    def replace_version(self, version_id: UUID, chunks: list[Chunk], embeddings: list[list[float]], previous_version_id: UUID | None = None) -> None:
        insert_sql = """
        INSERT INTO document_chunks
          (document_id, version_id, version, department_code, category, document_title,
           chunk_index, page_number, section_title, content, token_count, embedding)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s::vector)
        """
        with self.pool.connection() as connection, connection.cursor() as cursor:
            if previous_version_id is not None:
                cursor.execute(
                    "UPDATE document_chunks SET is_current = FALSE WHERE version_id = %s",
                    (previous_version_id,),
                )
            cursor.execute("DELETE FROM document_chunks WHERE version_id = %s", (version_id,))
            for chunk, embedding in zip(chunks, embeddings):
                vector = "[" + ",".join(str(value) for value in embedding) + "]"
                cursor.execute(
                    insert_sql,
                    (
                        chunk.document_id,
                        chunk.version_id,
                        chunk.version,
                        chunk.department,
                        chunk.category,
                        chunk.document_title,
                        chunk.chunk_index,
                        chunk.page_number,
                        chunk.section_title,
                        chunk.content,
                        chunk.token_count,
                        vector,
                    ),
                )

    def search(
        self,
        query: str,
        query_embedding: list[float],
        allowed_departments: list[DepartmentCode],
        mode: SearchMode,
        top_k: int,
        categories: list[DocumentCategory] | None = None,
        semantic_weight: float = 0.7,
    ):
        score_expression = {
            SearchMode.SEMANTIC: "semantic_score",
            SearchMode.KEYWORD: "keyword_score",
            SearchMode.HYBRID: f"{semantic_weight} * semantic_score + {1 - semantic_weight} * keyword_score",
        }[mode]
        category_clause = "AND category = ANY(%s)" if categories else ""
        sql = f"""
        WITH candidates AS (
          SELECT id, document_id, document_title, department_code, category, version,
                 page_number, section_title, content,
                 1 - (embedding <=> %s::vector) AS semantic_score,
                 LEAST(1, COALESCE(ts_rank_cd(content_tsv, websearch_to_tsquery('english', %s)), 0)) AS keyword_score
          FROM document_chunks
          WHERE department_code = ANY(%s)
            AND is_current
            AND NOT is_deleted
            {category_clause}
        )
        SELECT id, document_id, document_title, department_code, category, version,
               page_number, section_title, content, semantic_score, keyword_score,
               {score_expression} AS score
        FROM candidates
        ORDER BY score DESC
        LIMIT %s
        """
        with self.pool.connection() as connection, connection.cursor() as cursor:
            vector = "[" + ",".join(str(value) for value in query_embedding) + "]"
            parameters = [vector, query, list(allowed_departments)]
            if categories:
                parameters.append(list(categories))
            parameters.append(top_k)
            cursor.execute(sql, parameters)
            rows = []
            import uuid

            for row in cursor.fetchall():
                chunk_id, document_id, title, department, category, version, page, section, content, semantic, keyword, score = row
                chunk = Chunk(
                    document_id=document_id,
                    version_id=uuid.UUID(int=0),
                    version=version,
                    department=DepartmentCode(department),
                    category=DocumentCategory(category),
                    document_title=title,
                    chunk_index=0,
                    content=content,
                    token_count=len(content.split()),
                    page_number=page,
                    section_title=section,
                )
                rows.append((chunk_id, StoredChunk(chunk, []), score, semantic, keyword))
            return rows


def create_store(settings):
    # Docker Compose overrides POSTGRES_HOST to `postgres`; local unit tests
    # keep the in-memory store so the service can run without infrastructure.
    if settings.postgresql_host != "postgres" and settings.app_env not in {"staging", "prod"}:
        return InMemoryChunkStore()
    from psycopg.conninfo import make_conninfo
    from psycopg_pool import ConnectionPool

    connection_string = make_conninfo(
        host=settings.postgresql_host, port=settings.postgresql_port,
        dbname=settings.postgresql_db, user=settings.postgresql_user,
        password=settings.postgresql_password, sslmode=settings.postgresql_sslmode,
    )
    return PostgresChunkStore(ConnectionPool(conninfo=connection_string, open=True))
