-- =============================================================================
-- V1__init_schema.sql — EKM database schema v1
-- Owner: Ayushi. Applied by Flyway from core-api (spring.flyway.locations points here).
-- NEVER edit a migration that has been merged; add V2__..., V3__... instead.
-- Table ownership (who WRITES): see contracts/database/README.md
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pgcrypto;   -- gen_random_uuid()

-- ---------------------------------------------------------------------------
-- Reference data
-- ---------------------------------------------------------------------------
CREATE TABLE departments (
    code        VARCHAR(32)  PRIMARY KEY,
    name        VARCHAR(100) NOT NULL
);

INSERT INTO departments (code, name) VALUES
    ('GENERAL',     'Company-wide'),
    ('HR',          'Human Resources'),
    ('ENGINEERING', 'Engineering'),
    ('FINANCE',     'Finance'),
    ('LEGAL',       'Legal'),
    ('MARKETING',   'Marketing');

-- ---------------------------------------------------------------------------
-- Users (core-api)
-- ---------------------------------------------------------------------------
CREATE TABLE users (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) NOT NULL UNIQUE,
    full_name       VARCHAR(200) NOT NULL,
    password_hash   VARCHAR(100) NOT NULL,
    role            VARCHAR(16)  NOT NULL CHECK (role IN ('EMPLOYEE', 'MANAGER', 'ADMIN')),
    department_code VARCHAR(32)  NOT NULL REFERENCES departments(code),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- Documents & versions (core-api writes; ai-service reads documents for titles)
-- ---------------------------------------------------------------------------
CREATE TABLE documents (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    title            VARCHAR(200) NOT NULL,
    department_code  VARCHAR(32)  NOT NULL REFERENCES departments(code),
    category         VARCHAR(16)  NOT NULL CHECK (category IN ('POLICY', 'SOP', 'MANUAL', 'GUIDE', 'REPORT', 'OTHER')),
    author           VARCHAR(120),
    tags             TEXT[]       NOT NULL DEFAULT '{}',
    current_version  INTEGER      NOT NULL DEFAULT 1,
    status           VARCHAR(16)  NOT NULL DEFAULT 'UPLOADED'
                     CHECK (status IN ('UPLOADED', 'QUEUED', 'PROCESSING', 'INDEXED', 'FAILED')),
    created_by       UUID         NOT NULL REFERENCES users(id),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at       TIMESTAMPTZ                                  -- soft delete
);
CREATE INDEX idx_documents_department ON documents (department_code) WHERE deleted_at IS NULL;
CREATE INDEX idx_documents_tags ON documents USING GIN (tags);

CREATE TABLE document_versions (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id       UUID         NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    version           INTEGER      NOT NULL CHECK (version >= 1),
    s3_bucket         VARCHAR(255) NOT NULL,
    s3_key            VARCHAR(1024) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type      VARCHAR(255) NOT NULL,
    size_bytes        BIGINT       NOT NULL,
    checksum_sha256   CHAR(64),
    status            VARCHAR(16)  NOT NULL DEFAULT 'UPLOADED'
                      CHECK (status IN ('UPLOADED', 'QUEUED', 'PROCESSING', 'INDEXED', 'FAILED')),
    page_count        INTEGER,
    chunk_count       INTEGER,
    change_note       VARCHAR(500),
    error_code        VARCHAR(64),
    error_message     VARCHAR(1000),
    uploaded_by       UUID         NOT NULL REFERENCES users(id),
    uploaded_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    indexed_at        TIMESTAMPTZ,
    UNIQUE (document_id, version)
);

-- ---------------------------------------------------------------------------
-- Chunks + embeddings (ai-service writes; core-api reads for citation preview)
-- department_code / document_title are denormalized so retrieval is a single-table,
-- pre-filtered query: WHERE department_code = ANY(:allowed) AND is_current AND NOT is_deleted
-- ---------------------------------------------------------------------------
CREATE TABLE document_chunks (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id      UUID         NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    version_id       UUID         NOT NULL REFERENCES document_versions(id) ON DELETE CASCADE,
    version          INTEGER      NOT NULL,
    department_code  VARCHAR(32)  NOT NULL REFERENCES departments(code),
    category         VARCHAR(16)  NOT NULL,
    document_title   VARCHAR(200) NOT NULL,
    chunk_index      INTEGER      NOT NULL,
    page_number      INTEGER,                                     -- 1-based; NULL for DOCX
    section_title    VARCHAR(300),
    content          TEXT         NOT NULL,
    token_count      INTEGER      NOT NULL,
    embedding        vector(1536) NOT NULL,                       -- text-embedding-3-small; must equal EMBEDDING_DIMENSIONS
    content_tsv      tsvector     GENERATED ALWAYS AS (to_tsvector('english', coalesce(section_title, '') || ' ' || content)) STORED,
    is_current       BOOLEAN      NOT NULL DEFAULT TRUE,          -- false once a newer version is INDEXED
    is_deleted       BOOLEAN      NOT NULL DEFAULT FALSE,         -- mirrors documents.deleted_at (set by core-api on delete)
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (version_id, chunk_index)
);
CREATE INDEX idx_chunks_embedding_hnsw ON document_chunks USING hnsw (embedding vector_cosine_ops);
CREATE INDEX idx_chunks_tsv ON document_chunks USING GIN (content_tsv);
CREATE INDEX idx_chunks_filter ON document_chunks (department_code, is_current, is_deleted);
CREATE INDEX idx_chunks_document ON document_chunks (document_id);

-- ---------------------------------------------------------------------------
-- Conversations (core-api)
-- ---------------------------------------------------------------------------
CREATE TABLE conversations (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title       VARCHAR(200) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_conversations_user ON conversations (user_id, updated_at DESC);

CREATE TABLE chat_messages (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id  UUID         NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    role             VARCHAR(16)  NOT NULL CHECK (role IN ('USER', 'ASSISTANT')),
    content          TEXT         NOT NULL,
    citations        JSONB        NOT NULL DEFAULT '[]'::jsonb,   -- Citation[] exactly as in the API contract
    query_id         UUID,                                        -- links ASSISTANT message to query_logs.id
    answered         BOOLEAN,
    feedback         VARCHAR(8)   CHECK (feedback IN ('UP', 'DOWN')),
    feedback_comment VARCHAR(1000),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_messages_conversation ON chat_messages (conversation_id, created_at);

-- ---------------------------------------------------------------------------
-- Audit, query logs, knowledge gaps (audit-service writes; core-api reads for analytics)
-- ---------------------------------------------------------------------------
CREATE TABLE audit_logs (
    id               BIGSERIAL    PRIMARY KEY,
    event_id         UUID         NOT NULL UNIQUE,                -- idempotency key from the SQS message
    occurred_at      TIMESTAMPTZ  NOT NULL,
    action           VARCHAR(48)  NOT NULL,
    actor_user_id    UUID,
    actor_email      VARCHAR(255),
    actor_role       VARCHAR(16),
    actor_department VARCHAR(32),
    resource_type    VARCHAR(32),
    resource_id      VARCHAR(64),
    ip_address       VARCHAR(64),
    user_agent       VARCHAR(512),
    trace_id         VARCHAR(64),
    metadata         JSONB        NOT NULL DEFAULT '{}'::jsonb
);
CREATE INDEX idx_audit_occurred ON audit_logs (occurred_at DESC);
CREATE INDEX idx_audit_action ON audit_logs (action, occurred_at DESC);
CREATE INDEX idx_audit_actor ON audit_logs (actor_user_id, occurred_at DESC);

CREATE TABLE query_logs (
    id                  UUID         PRIMARY KEY,                 -- = queryId generated by core-api
    user_id             UUID         REFERENCES users(id) ON DELETE SET NULL,
    department_code     VARCHAR(32),                              -- asker's department
    conversation_id     UUID,
    query_text          TEXT         NOT NULL,
    normalized_query    TEXT         NOT NULL,                    -- lower(trim(collapse spaces))
    allowed_departments TEXT[]       NOT NULL DEFAULT '{}',
    answered            BOOLEAN      NOT NULL,
    confidence          REAL,
    latency_ms          INTEGER,
    cited_document_ids  UUID[]       NOT NULL DEFAULT '{}',
    model               VARCHAR(64),
    prompt_tokens       INTEGER,
    completion_tokens   INTEGER,
    created_at          TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_query_logs_created ON query_logs (created_at DESC);
CREATE INDEX idx_query_logs_normalized ON query_logs (normalized_query);

CREATE TABLE knowledge_gaps (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    normalized_query TEXT         NOT NULL,
    sample_query     TEXT         NOT NULL,
    department_code  VARCHAR(32)  NOT NULL REFERENCES departments(code),
    occurrences      INTEGER      NOT NULL DEFAULT 1,
    status           VARCHAR(16)  NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED', 'DISMISSED')),
    topic_label      VARCHAR(200),                                -- optional, from ai-service clustering
    notes            VARCHAR(2000),
    first_seen_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_seen_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    resolved_by      UUID         REFERENCES users(id),
    resolved_at      TIMESTAMPTZ,
    UNIQUE (normalized_query, department_code)
);
CREATE INDEX idx_gaps_status ON knowledge_gaps (status, last_seen_at DESC);
