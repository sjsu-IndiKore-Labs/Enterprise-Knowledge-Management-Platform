# Service card: ai-service

**Service name:** `ai-service`  
**Aliases:** AI service, RAG API, retrieval service, answer service  
**Owner:** Vedanti  
**Technology:** Python 3.11, FastAPI  
**Port:** `8000`  
**Health check:** `GET /health`

## Responsibility

`ai-service` provides retrieval and answer-generation capabilities. It performs hybrid retrieval over PostgreSQL/pgvector, generates answers using the configured AI provider, applies RAG guardrails, and returns citations from indexed documents.

## Dependencies

- Receives internal requests from `core-api` through `AI_SERVICE_URL`.
- Reads indexed chunks and embeddings from PostgreSQL/pgvector.
- May read source objects from S3 when the retrieval or processing flow requires document bytes.
- Uses the configured embedding and chat provider. `AI_PROVIDER=fake` supports local development without an OpenAI key.
- Uses the internal service token for service-to-service authentication.

## S3 permissions in AWS

The ECS task role grants `s3:GetObject` for `documents/*` and `kms:Decrypt` for the documents KMS key. The service should use the `S3_DOCUMENTS_BUCKET` environment variable and retrieve objects by the key provided by the application workflow. It should not require bucket-wide listing permission.

## Retrieval and answer behavior

- Department filtering must happen before retrieval using the caller's authorized context.
- Answers should be based only on retrieved, authorized document chunks.
- Responses should include document and page/section citations where available.
- If the similarity threshold is not met, return the contract's not-found behavior instead of inventing an answer.
- Do not log document text, JWTs, API keys, or passwords.

## Deployment

- ECS service name: `ekm-staging-ai-service`.
- Container port: `8000`.
- The same Python image is reused by `ai-worker` with a different command.
- ECS Service Connect exposes the internal name `ai-service` to `core-api`.

## Troubleshooting

- `403` reading a document: verify the object is under `documents/*`, the task role is attached, and the KMS key policy permits decrypt.
- No search results: inspect indexed chunks, department filtering, embedding dimensions, and similarity threshold.
- Provider errors: verify `AI_PROVIDER`, model configuration, and the Secrets Manager OpenAI key.

## Source references

- `../../../contracts/ports-and-services.md`
- `../../../contracts/openapi/ai-service.yaml`
- `../../../../vedanti_ai/README.md`
- `../../../../vedanti_ai/TODO.md`
- `../../infra/terraform/ecs.tf`
- `../../infra/terraform/iam.tf`
