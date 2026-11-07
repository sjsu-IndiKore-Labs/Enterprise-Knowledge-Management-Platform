-- =============================================================================
-- V2__contract_schema_m1_m3.sql
-- Aligns core-api schema with contracts/database/migrations/V1__init_schema.sql
-- for Milestones 1, 2, and 3 while preserving V1 compatibility.
-- =============================================================================

-- 1. Reference Data: Departments
CREATE TABLE IF NOT EXISTS departments (
    code VARCHAR(32) PRIMARY KEY,
    name VARCHAR(100) NOT NULL
);

INSERT INTO departments (code, name) VALUES
    ('GENERAL',     'Company-wide'),
    ('HR',          'Human Resources'),
    ('ENGINEERING', 'Engineering'),
    ('FINANCE',     'Finance'),
    ('LEGAL',       'Legal'),
    ('MARKETING',   'Marketing'),
    ('OPERATIONS',  'Operations')
ON CONFLICT (code) DO NOTHING;

-- 2. Users Table
CREATE TABLE IF NOT EXISTS users (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) NOT NULL UNIQUE,
    full_name       VARCHAR(200) NOT NULL,
    password_hash   VARCHAR(100) NOT NULL,
    role            VARCHAR(16)  NOT NULL,
    department_code VARCHAR(32)  NOT NULL REFERENCES departments(code),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Seed a default system admin user for unauthenticated/dev uploads before Milestone 4 login
INSERT INTO users (id, email, full_name, password_hash, role, department_code, active)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'admin@helios.example.com',
    'System Admin',
    '$2a$10$7EqJtq98hPqEX7fNZaFWoOHi6M1T5kS9nF5R2G1d.e5pX6v1a2b3c',
    'ADMIN',
    'GENERAL',
    TRUE
) ON CONFLICT (email) DO NOTHING;

-- 3. Align documents table with contract columns
ALTER TABLE documents ADD COLUMN IF NOT EXISTS department_code VARCHAR(32);
ALTER TABLE documents ADD COLUMN IF NOT EXISTS category VARCHAR(16) NOT NULL DEFAULT 'OTHER';
ALTER TABLE documents ADD COLUMN IF NOT EXISTS author VARCHAR(120);
ALTER TABLE documents ADD COLUMN IF NOT EXISTS tags VARCHAR(1000) NOT NULL DEFAULT '';
ALTER TABLE documents ADD COLUMN IF NOT EXISTS current_version INT NOT NULL DEFAULT 1;
ALTER TABLE documents ADD COLUMN IF NOT EXISTS page_count INT;
ALTER TABLE documents ADD COLUMN IF NOT EXISTS created_by UUID DEFAULT '00000000-0000-0000-0000-000000000001';
ALTER TABLE documents ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

UPDATE documents SET department_code = department WHERE department_code IS NULL AND department IS NOT NULL;
UPDATE documents SET department_code = 'GENERAL' WHERE department_code IS NULL;

-- Make legacy columns have safe defaults so contract-only inserts also succeed
ALTER TABLE documents ALTER COLUMN file_name SET DEFAULT 'document.pdf';
ALTER TABLE documents ALTER COLUMN file_type SET DEFAULT 'application/pdf';
ALTER TABLE documents ALTER COLUMN file_size SET DEFAULT 0;
ALTER TABLE documents ALTER COLUMN classification SET DEFAULT 'INTERNAL';

-- 4. Document Versions Table
CREATE TABLE IF NOT EXISTS document_versions (
    id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id       UUID          NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    version           INTEGER       NOT NULL,
    s3_bucket         VARCHAR(255)  NOT NULL,
    s3_key            VARCHAR(1024) NOT NULL,
    original_filename VARCHAR(255)  NOT NULL,
    content_type      VARCHAR(255)  NOT NULL,
    size_bytes        BIGINT        NOT NULL,
    checksum_sha256   VARCHAR(64),
    status            VARCHAR(32)   NOT NULL DEFAULT 'QUEUED',
    page_count        INTEGER,
    chunk_count       INTEGER,
    change_note       VARCHAR(500),
    error_code        VARCHAR(64),
    error_message     VARCHAR(1000),
    uploaded_by       UUID          NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001',
    uploaded_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    indexed_at        TIMESTAMPTZ,
    UNIQUE (document_id, version)
);

CREATE INDEX IF NOT EXISTS idx_doc_versions_doc_id ON document_versions(document_id);

-- 5. Document Chunks Table (read by core-api for citation previews; written by ai-service)
CREATE TABLE IF NOT EXISTS document_chunks (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id      UUID         NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    version_id       UUID         REFERENCES document_versions(id) ON DELETE CASCADE,
    version          INTEGER      NOT NULL DEFAULT 1,
    department_code  VARCHAR(32)  NOT NULL,
    category         VARCHAR(16)  NOT NULL DEFAULT 'OTHER',
    document_title   VARCHAR(200) NOT NULL,
    chunk_index      INTEGER      NOT NULL,
    page_number      INTEGER,
    section_title    VARCHAR(300),
    content          TEXT         NOT NULL,
    token_count      INTEGER      NOT NULL DEFAULT 0,
    is_current       BOOLEAN      NOT NULL DEFAULT TRUE,
    is_deleted       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_chunks_filter ON document_chunks (department_code, is_current, is_deleted);
CREATE INDEX IF NOT EXISTS idx_chunks_document ON document_chunks (document_id);

-- 6. Conversations & Chat Messages Tables (Milestone 3)
CREATE TABLE IF NOT EXISTS conversations (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL,
    title       VARCHAR(200) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_conversations_user ON conversations (user_id, updated_at DESC);

CREATE TABLE IF NOT EXISTS chat_messages (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id  UUID          NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    role             VARCHAR(16)   NOT NULL,
    content          TEXT          NOT NULL,
    citations        TEXT          NOT NULL DEFAULT '[]',
    query_id         UUID,
    answered         BOOLEAN,
    feedback         VARCHAR(8),
    feedback_comment VARCHAR(1000),
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_messages_conversation ON chat_messages (conversation_id, created_at);

