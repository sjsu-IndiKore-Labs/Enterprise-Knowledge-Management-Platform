# Backend Integration & Contract Hand-Off (Milestones 1 & 2)

**Author:** Ayushi (Team Leader, Backend & Security Lead)  
**Date:** October 28, 2026  
**Target Audience:** Kevin (Frontend), Vedanti (AI/RAG), Manali (DevOps/Cloud)  
**Git Branch:** `ayushi/maven-backend-setup`  
**Pull Request:** [PR # on GitHub (ayushi/maven-backend-setup into develop)](https://github.com/sjsu-IndiKore-Labs/Enterprise-Knowledge-Management-Platform/pull/new/ayushi/maven-backend-setup)  

---

## Executive Summary

The **Milestone 1 backend architecture** and the **Milestone 2 document ingestion contracts** are officially built, tested, and pushed to GitHub.

- **Build System:** Migrated from Gradle to standard **Apache Maven multi-module (Java 21 / Spring Boot 3.4.1)**.
- **Database:** PostgreSQL initialized with automated **Flyway migrations** (`V1__init_schema.sql`).
- **Interactive Documentation:** Live Swagger UI / OpenAPI 3.0 spec is up and serving live responses.

---

## 1. Frontend Integration Guide (for Kevin / Changhyun)

### 1.1 Service Endpoints & Swagger UI
- **Swagger UI Interactive Console:** `http://localhost:8082/swagger-ui.html` *(or `http://localhost:8080/swagger-ui.html` when running via Docker)*
- **OpenAPI 3.0 JSON Contract:** `http://localhost:8082/api-docs`
- **Frontend Base URL Config:** Set `NEXT_PUBLIC_API_BASE_URL=http://localhost:8082` in `changhyun_frontend/.env.local`.

---

### 1.2 Endpoints Ready for UI Consumption

#### A. Upload Document (`POST /api/v1/documents/upload`)
- **Content-Type:** `multipart/form-data`
- **Form Parameters:**
  - `file`: The binary document file (`.pdf` or `.docx`, max size 50MB).
  - `title`: String (e.g. `"2026 Employee Health & Benefits Guide"`).
  - `department`: Enum: `HR`, `ENGINEERING`, `FINANCE`, `LEGAL`, `OPERATIONS`, `GENERAL`.
  - `classification`: Enum: `PUBLIC`, `INTERNAL`, `CONFIDENTIAL`, `RESTRICTED`.
- **Response:** `202 Accepted`
  ```json
  {
    "documentId": "1a1e0b04-f651-40ea-a05b-6a5df9b64627",
    "jobId": "ff7ba59f-d3e9-4e7a-bfb6-6819a5445214",
    "title": "2026 Employee Health & Benefits Guide",
    "fileName": "Employee_Guide_2026.pdf",
    "status": "PENDING",
    "message": "Document uploaded successfully and queued for background indexing."
  }
  ```

#### B. Polling Upload & Indexing Status (`GET /api/v1/documents/{id}/status`)
Use this endpoint to drive the live progress bar in your drag-and-drop UI:
- **Response:** `200 OK`
  ```json
  {
    "documentId": "1a1e0b04-f651-40ea-a05b-6a5df9b64627",
    "jobId": "ff7ba59f-d3e9-4e7a-bfb6-6819a5445214",
    "status": "INDEXED",
    "progressPercent": 100,
    "errorMessage": null,
    "updatedAt": "2026-10-28T20:47:36.935Z"
  }
  ```
- **Status Lifecycle:** `PENDING` ➔ `PROCESSING` ➔ `INDEXED` (or `FAILED` with error message).

#### C. List All Enterprise Documents (`GET /api/v1/documents`)
- **Response:** `200 OK`
  ```json
  [
    {
      "id": "1a1e0b04-f651-40ea-a05b-6a5df9b64627",
      "title": "2026 Employee Health & Benefits Guide",
      "fileName": "Employee_Guide_2026.pdf",
      "fileType": "application/pdf",
      "fileSize": 1048576,
      "department": "HR",
      "classification": "INTERNAL",
      "version": 1,
      "status": "INDEXED",
      "createdAt": "2026-10-28T20:45:00.000Z"
    }
  ]
  ```

#### D. Hybrid Search & Citation Retrieval (`POST /api/v1/search`)
Use this contract to render search results and citation preview cards:
- **Request:**
  ```json
  {
    "query": "What is the parental leave policy?",
    "authorizedDepartments": ["HR", "GENERAL"],
    "topK": 5,
    "minScore": 0.72
  }
  ```
- **Response:** `200 OK`
  ```json
  {
    "query": "What is the parental leave policy?",
    "totalResults": 2,
    "results": [
      {
        "chunkId": "ff7ba59f-d3e9-4e7a-bfb6-6819a5445214",
        "documentId": "1a1e0b04-f651-40ea-a05b-6a5df9b64627",
        "documentTitle": "Enterprise Standard Operating Procedures & Policies.pdf",
        "fileName": "Enterprise_SOP_2026.pdf",
        "pageNumber": 12,
        "snippet": "Eligible employees may take up to 16 consecutive weeks of fully paid parental leave following the birth, adoption, or foster placement of a child.",
        "similarityScore": 0.925,
        "department": "GENERAL"
      }
    ]
  }
  ```

---

## 2. AI & RAG Pipeline Guide (for Vedanti)

### 2.1 Asynchronous Ingestion Hand-Off (Milestone 2)
When an employee uploads a document, `core-api` stores the raw file in S3 and produces an event to the SQS queue:
- **SQS Queue Name:** `ekm-document-ingest`
- **S3 Bucket:** `ekm-documents-local` (or configured AWS S3 bucket)
- **Message Payload (`DOCUMENT_INGEST_REQUESTED`):**
  ```json
  {
    "eventId": "uuid-v4",
    "eventType": "DOCUMENT_INGEST_REQUESTED",
    "timestamp": "2026-10-28T20:47:36Z",
    "jobId": "ff7ba59f-d3e9-4e7a-bfb6-6819a5445214",
    "documentId": "1a1e0b04-f651-40ea-a05b-6a5df9b64627",
    "s3Key": "documents/HR/1a1e0b04-f651-40ea-a05b-6a5df9b64627/v1/Employee_Guide_2026.pdf",
    "department": "HR",
    "fileType": "PDF"
  }
  ```

### 2.2 Ingestion Status Callback (SQS `ekm-document-status`)
Once your Python ingestion worker completes chunking and embedding, emit status updates:
```json
{
  "eventId": "uuid-v4",
  "eventType": "DOCUMENT_INGEST_STATUS",
  "timestamp": "2026-10-28T20:50:00Z",
  "jobId": "ff7ba59f-d3e9-4e7a-bfb6-6819a5445214",
  "documentId": "1a1e0b04-f651-40ea-a05b-6a5df9b64627",
  "status": "INDEXED",
  "progressPercent": 100,
  "chunkCount": 38,
  "pageCount": 16
}
```

### 2.3 Milestone 3 `pgvector` Schema Preview
In Milestone 3, Flyway migration `V2__chunks_and_vector.sql` will create:
```sql
CREATE TABLE document_chunks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    chunk_index INT NOT NULL,
    page_number INT,
    chunk_text TEXT NOT NULL,
    department VARCHAR(64) NOT NULL,
    tsv_content tsvector GENERATED ALWAYS AS (to_tsvector('english', chunk_text)) STORED,
    embedding vector(1536) NOT NULL
);

CREATE INDEX idx_chunks_embedding_hnsw 
ON document_chunks 
USING hnsw (embedding vector_cosine_ops) 
WITH (m = 16, ef_construction = 64);
```

---

## 3. DevOps & Cloud Infrastructure Guide (for Manali)

### 3.1 Maven Architecture Transition
All backend services are now structured as a standard **Maven multi-module** project under [`ayushi_backend/`](file:///c:/Users/ayush/Desktop/Enterprize%20project/ayushi_backend):
- `ayushi_backend/pom.xml` — Parent POM managing dependency versions and plugins.
- `ayushi_backend/common` — Shared library (Error models, cross-service DTOs).
- `ayushi_backend/core-api` — Port `8080` (or `8082` locally).
- `ayushi_backend/audit-service` — Port `8081`.

### 3.2 CI/CD & Build Commands
If your Jenkins or GitHub Actions pipeline currently runs Gradle, update the backend step to:
```bash
# Build and package all services (skipping tests during packaging):
cd ayushi_backend
./mvnw clean package -DskipTests

# Run unit tests:
./mvnw test
```

### 3.3 Multi-Stage Dockerfiles
Two production-ready multi-stage Dockerfiles are available:
1. [`ayushi_backend/core-api/Dockerfile`](file:///c:/Users/ayush/Desktop/Enterprize%20project/ayushi_backend/core-api/Dockerfile)
2. [`ayushi_backend/audit-service/Dockerfile`](file:///c:/Users/ayush/Desktop/Enterprize%20project/ayushi_backend/audit-service/Dockerfile)

They adhere strictly to root context builds (`context: .`) matching your [`docker-compose.yml`](file:///c:/Users/ayush/Desktop/Enterprize%20project/docker-compose.yml) specifications.

---

## 4. Local Run Instructions

### Prerequisites
- Java 21 or Java 25 installed
- PostgreSQL 16+ running on port `5432` with database `knowledge_hub`

### Launch `core-api`
From PowerShell in the project root:
```powershell
cd ayushi_backend
.\mvnw.cmd -f core-api/pom.xml spring-boot:run
```
Visit: **`http://localhost:8082/swagger-ui.html`**
