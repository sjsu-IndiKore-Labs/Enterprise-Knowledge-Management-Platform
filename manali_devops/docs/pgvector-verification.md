# pgvector verification

`verify-pgvector.sh` is a read-only check for the PostgreSQL and pgvector contract.
It confirms that core-api/Flyway has successfully applied V1 and that the database
contains the objects required by AI retrieval:

- the `vector` extension;
- `document_chunks.embedding` with type `vector(1536)`; and
- a valid HNSW cosine index named `idx_chunks_embedding_hnsw`.

The script uses the existing `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`,
`POSTGRES_USER`, and `POSTGRES_PASSWORD` variables from `.env.example`. Run it from
a host that can reach the database, such as an approved staging access path for the
private RDS instance:

```bash
bash manali_devops/scripts/verify-pgvector.sh
```

Do not use `make migrate-raw` as evidence for this task. The AWS verification is
complete only after core-api starts successfully, Flyway records V1 as successful,
and this script exits with `pgvector verification passed`.
