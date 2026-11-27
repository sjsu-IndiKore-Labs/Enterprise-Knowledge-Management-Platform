"""Ingest the checked-in corpus through the real pipeline, then ask questions.

Run from the repository root:
    PYTHONPATH=vedanti_ai vedanti_ai/.venv/bin/python vedanti_ai/scripts/local_worker_chat.py

This deliberately replaces only the S3/SQS transport. It invokes the same
IngestionPipeline.process_file method as the worker, using local corpus files.
"""

from __future__ import annotations

import argparse
import csv
from datetime import UTC, datetime
from pathlib import Path
from uuid import NAMESPACE_URL, uuid4, uuid5

from app.config import Settings
from app.ingestion.pipeline import IngestionPipeline
from app.models import (
    AnswerRequest,
    DepartmentCode,
    DocumentCategory,
    HistoryMessage,
    IngestEvent,
)
from app.rag.service import RagService


def ingest_corpus(root: Path, department: DepartmentCode | None = None) -> RagService:
    settings = Settings(ai_provider="fake", rag_min_similarity=0.35)
    service = RagService(settings)
    pipeline = IngestionPipeline(settings, service)
    manifest_path = root / "resources/corpus/manifest.csv"
    files_root = root / "resources/corpus/files"

    with manifest_path.open(newline="") as manifest_file:
        documents = list(csv.DictReader(manifest_file))

    indexed = 0
    for row in documents:
        doc_id = uuid5(NAMESPACE_URL, row["docId"])
        version = int(row["version"])
        version_id = uuid5(NAMESPACE_URL, f'{row["docId"]}:v{version}')
        previous_id = uuid5(NAMESPACE_URL, f'{row["docId"]}:v{version - 1}') if version > 1 else None
        file_path = files_root / row["file"]
        content_type = (
            "application/pdf" if row["format"].lower() == "pdf"
            else "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        )
        event = IngestEvent(
            eventId=uuid4(), eventType="DOCUMENT_INGEST_REQUESTED",
            occurredAt=datetime.now(UTC).isoformat(), traceId=str(uuid4()),
            documentId=doc_id, versionId=version_id, version=version,
            previousVersionId=previous_id, s3Bucket="local-filesystem",
            s3Key=str(file_path), contentType=content_type,
            originalFilename=file_path.name, sizeBytes=file_path.stat().st_size,
            title=row["title"], department=DepartmentCode(row["department"]),
            category=DocumentCategory(row["category"]),
            uploadedBy=uuid5(NAMESPACE_URL, "local-corpus-uploader"),
            author=row.get("author"),
        )
        status = pipeline.process_file(event, file_path)
        if status.status != "INDEXED":
            raise RuntimeError(f'Could not ingest {file_path.name}: {status.error_code} {status.error_message}')
        indexed += 1
        print(f'Indexed: {row["title"]} (v{version}, {status.chunk_count} chunks)')

    print(f"\nReady: {indexed} document versions indexed using the worker's ingestion pipeline.")
    if department:
        print(f"Simulated employee access: {department.value} + GENERAL")
    else:
        print("Simulated access: all departments (local testing only)")
    return service


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--department", choices=[item.value for item in DepartmentCode],
                        help="simulate a department employee; omit to allow all departments locally")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    department = DepartmentCode(args.department) if args.department else None
    service = ingest_corpus(root, department)
    allowed = list(DepartmentCode) if department is None else [department, DepartmentCode.GENERAL]
    history: list[HistoryMessage] = []

    while True:
        try:
            question = input("\nAsk a question (or type exit): ").strip()
        except (EOFError, KeyboardInterrupt):
            print("\nExiting.")
            break
        if question.lower() == "exit":
            break
        if not question:
            continue
        answer = service.answer(AnswerRequest(
            queryId=uuid4(), query=question, allowedDepartments=allowed, history=history[-6:]
        ))
        print(f"\nAnswer: {answer.answer}")
        print("Citations:")
        for citation in answer.citations:
            location = citation.page_number or citation.section_title or "document section"
            print(f"- {citation.document_title}, {location}")
        history.extend((HistoryMessage(role="USER", content=question),
                        HistoryMessage(role="ASSISTANT", content=answer.answer)))


if __name__ == "__main__":
    main()
