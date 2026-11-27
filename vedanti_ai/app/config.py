from functools import lru_cache

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    app_env: str = "local"
    log_level: str = "INFO"
    log_format: str = "text"
    postgresql_host: str = Field("localhost", validation_alias="POSTGRES_HOST")
    postgresql_port: int = Field(5432, validation_alias="POSTGRES_PORT")
    postgresql_db: str = Field("ekm", validation_alias="POSTGRES_DB")
    postgresql_user: str = Field("ekm", validation_alias="POSTGRES_USER")
    postgresql_password: str = Field("ekm_local_password", validation_alias="POSTGRES_PASSWORD")
    postgresql_sslmode: str = Field("prefer", validation_alias="POSTGRES_SSLMODE")
    aws_region: str = "us-west-2"
    aws_endpoint_url: str | None = "http://localhost:4566"
    aws_access_key_id: str = "test"
    aws_secret_access_key: str = "test"
    s3_documents_bucket: str = "ekm-documents-local"
    sqs_document_ingest_queue: str = "ekm-document-ingest"
    sqs_document_status_queue: str = "ekm-document-status"
    ai_service_port: int = 8000
    internal_service_token: str = "local-internal-token-change-me"
    ai_provider: str = "fake"
    openai_api_key: str | None = None
    openai_chat_model: str = "gpt-4o-mini"
    openai_embedding_model: str = "text-embedding-3-small"
    embedding_dimensions: int = 1536
    chunk_size_tokens: int = 500
    chunk_overlap_tokens: int = 80
    rag_top_k: int = 6
    rag_min_similarity: float = 0.35
    rag_hybrid_semantic_weight: float = 0.7
    embedding_cache_ttl_seconds: int = 3600

    model_config = SettingsConfigDict(
        env_file=".env",
        extra="ignore",
        case_sensitive=False,
        populate_by_name=True,
    )


@lru_cache
def get_settings() -> Settings:
    return Settings()
