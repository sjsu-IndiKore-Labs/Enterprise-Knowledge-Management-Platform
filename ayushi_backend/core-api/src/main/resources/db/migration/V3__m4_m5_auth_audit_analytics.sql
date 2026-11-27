-- =============================================================================
-- V3__m4_m5_auth_audit_analytics.sql
-- Milestone 4 & 5 tables (audit_logs, query_logs, knowledge_gaps) and seed users
-- from resources/seed/users.json (password: Password123!)
-- =============================================================================

-- 1. Seed all 12 dev users from resources/seed/users.json
INSERT INTO users (id, email, full_name, password_hash, role, department_code, active) VALUES
    ('11111111-1111-4111-8111-000000000001', 'alice.admin@helios.example.com',   'Alice Admin',   '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'ADMIN',    'GENERAL',     TRUE),
    ('11111111-1111-4111-8111-000000000002', 'harper.hrmgr@helios.example.com',  'Harper Hughes', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'MANAGER',  'HR',          TRUE),
    ('11111111-1111-4111-8111-000000000003', 'hannah.hr@helios.example.com',     'Hannah Reyes',  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'EMPLOYEE', 'HR',          TRUE),
    ('11111111-1111-4111-8111-000000000004', 'ethan.engmgr@helios.example.com',  'Ethan Park',    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'MANAGER',  'ENGINEERING', TRUE),
    ('11111111-1111-4111-8111-000000000005', 'eric.eng@helios.example.com',      'Eric Nguyen',   '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'EMPLOYEE', 'ENGINEERING', TRUE),
    ('11111111-1111-4111-8111-000000000006', 'frank.finmgr@helios.example.com',  'Frank Ortiz',   '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'MANAGER',  'FINANCE',     TRUE),
    ('11111111-1111-4111-8111-000000000007', 'fiona.fin@helios.example.com',     'Fiona Brooks',  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'EMPLOYEE', 'FINANCE',     TRUE),
    ('11111111-1111-4111-8111-000000000008', 'lena.legalmgr@helios.example.com', 'Lena Schmidt',  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'MANAGER',  'LEGAL',       TRUE),
    ('11111111-1111-4111-8111-000000000009', 'leo.legal@helios.example.com',     'Leo Martins',   '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'EMPLOYEE', 'LEGAL',       TRUE),
    ('11111111-1111-4111-8111-000000000010', 'mark.mktmgr@helios.example.com',   'Mark Olsen',    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'MANAGER',  'MARKETING',   TRUE),
    ('11111111-1111-4111-8111-000000000011', 'maya.mkt@helios.example.com',      'Maya Chen',     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'EMPLOYEE', 'MARKETING',   TRUE),
    ('11111111-1111-4111-8111-000000000012', 'ivan.inactive@helios.example.com', 'Ivan Idle',     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'EMPLOYEE', 'MARKETING',   FALSE)
ON CONFLICT (email) DO NOTHING;

-- 2. Audit Logs Table
CREATE TABLE IF NOT EXISTS audit_logs (
    id               BIGSERIAL    PRIMARY KEY,
    event_id         UUID         NOT NULL UNIQUE,
    occurred_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
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
    metadata         TEXT         NOT NULL DEFAULT '{}'
);
CREATE INDEX IF NOT EXISTS idx_audit_occurred ON audit_logs (occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_action ON audit_logs (action, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_actor ON audit_logs (actor_user_id, occurred_at DESC);

-- 3. Query Logs Table
CREATE TABLE IF NOT EXISTS query_logs (
    id                  UUID         PRIMARY KEY,
    user_id             UUID,
    department_code     VARCHAR(32),
    conversation_id     UUID,
    query_text          TEXT         NOT NULL,
    normalized_query    TEXT         NOT NULL,
    allowed_departments VARCHAR(255) NOT NULL DEFAULT '',
    answered            BOOLEAN      NOT NULL,
    confidence          DOUBLE PRECISION,
    latency_ms          INTEGER,
    cited_document_ids  TEXT         NOT NULL DEFAULT '',
    model               VARCHAR(64),
    prompt_tokens       INTEGER,
    completion_tokens   INTEGER,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_query_logs_created ON query_logs (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_query_logs_normalized ON query_logs (normalized_query);

-- 4. Knowledge Gaps Table
CREATE TABLE IF NOT EXISTS knowledge_gaps (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    normalized_query TEXT         NOT NULL,
    sample_query     TEXT         NOT NULL,
    department_code  VARCHAR(32)  NOT NULL,
    occurrences      INTEGER      NOT NULL DEFAULT 1,
    status           VARCHAR(16)  NOT NULL DEFAULT 'OPEN',
    topic_label      VARCHAR(200),
    notes            VARCHAR(2000),
    first_seen_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_seen_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    resolved_by      UUID,
    resolved_at      TIMESTAMPTZ,
    UNIQUE (normalized_query, department_code)
);
CREATE INDEX IF NOT EXISTS idx_gaps_status ON knowledge_gaps (status, last_seen_at DESC);

