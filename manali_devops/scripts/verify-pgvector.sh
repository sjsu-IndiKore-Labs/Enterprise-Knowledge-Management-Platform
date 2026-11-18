#!/usr/bin/env bash
set -euo pipefail

# Read-only verification for the PostgreSQL + pgvector contract.
# The database must have been initialized by core-api/Flyway V1 before this runs.

command -v psql >/dev/null 2>&1 || {
  echo "psql is required to verify PostgreSQL; install the PostgreSQL client first." >&2
  exit 1
}

db_host="${POSTGRES_HOST:-localhost}"
db_port="${POSTGRES_PORT:-5432}"
db_name="${POSTGRES_DB:-ekm}"
db_user="${POSTGRES_USER:-ekm}"
db_password="${POSTGRES_PASSWORD:-}"

psql_args=(
  --no-password
  --host "$db_host"
  --port "$db_port"
  --username "$db_user"
  --dbname "$db_name"
)

run_query() {
  local sql="$1"

  if [[ -n "$db_password" ]]; then
    PGPASSWORD="$db_password" psql "${psql_args[@]}" -v ON_ERROR_STOP=1 -Atqc "$sql"
  else
    psql "${psql_args[@]}" -v ON_ERROR_STOP=1 -Atqc "$sql"
  fi
}

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

run_query 'SELECT 1;' >/dev/null || fail "cannot connect to ${db_host}:${db_port}/${db_name} as ${db_user}"

flyway_status="$(run_query "
  SELECT CASE
    WHEN to_regclass('public.flyway_schema_history') IS NULL THEN 'MISSING'
    WHEN EXISTS (
      SELECT 1
      FROM public.flyway_schema_history
      WHERE version = '1' AND success
    ) THEN 'OK'
    ELSE 'FAILED'
  END;
")"
[[ "$flyway_status" == "OK" ]] || fail "Flyway V1 is not recorded as successful (status: ${flyway_status})"

vector_version="$(run_query "
  SELECT COALESCE(
    (SELECT extversion FROM pg_extension WHERE extname = 'vector'),
    ''
  );
")"
[[ -n "$vector_version" ]] || fail "the vector extension is not installed"

embedding_type="$(run_query "
  SELECT COALESCE(
    (
      SELECT format_type(a.atttypid, a.atttypmod)
      FROM pg_attribute AS a
      JOIN pg_class AS c ON c.oid = a.attrelid
      JOIN pg_namespace AS n ON n.oid = c.relnamespace
      WHERE n.nspname = 'public'
        AND c.relname = 'document_chunks'
        AND a.attname = 'embedding'
        AND NOT a.attisdropped
    ),
    ''
  );
")"
[[ "$embedding_type" == "vector(1536)" ]] || fail "document_chunks.embedding is ${embedding_type:-missing}; expected vector(1536)"

index_status="$(run_query "
  SELECT CASE
    WHEN EXISTS (
      SELECT 1
      FROM pg_index AS i
      JOIN pg_class AS idx ON idx.oid = i.indexrelid
      JOIN pg_class AS tbl ON tbl.oid = i.indrelid
      JOIN pg_namespace AS n ON n.oid = tbl.relnamespace
      WHERE n.nspname = 'public'
        AND tbl.relname = 'document_chunks'
        AND idx.relname = 'idx_chunks_embedding_hnsw'
        AND i.indisvalid
        AND pg_get_indexdef(i.indexrelid) LIKE '%vector_cosine_ops%'
    ) THEN 'OK'
    ELSE 'MISSING'
  END;
")"
[[ "$index_status" == "OK" ]] || fail "the valid HNSW cosine index idx_chunks_embedding_hnsw is missing"

echo "pgvector verification passed"
echo "  database: ${db_host}:${db_port}/${db_name}"
echo "  Flyway V1: ${flyway_status}"
echo "  vector extension: ${vector_version}"
echo "  document_chunks.embedding: ${embedding_type}"
echo "  HNSW cosine index: ${index_status}"
