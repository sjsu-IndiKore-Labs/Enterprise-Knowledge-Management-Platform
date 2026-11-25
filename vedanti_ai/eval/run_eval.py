"""Run the 40-question RAG evaluation set against a local fake-provider index.

Usage from the repository root:
    python vedanti_ai/eval/run_eval.py

This is intentionally offline by default. It validates authorization, refusal,
source selection, and response shape; answer-quality metrics improve when the
real embedding and LLM providers are configured.
"""

from __future__ import annotations

import argparse
import csv
import json
import sys
import time
from pathlib import Path
from statistics import quantiles
from uuid import UUID, uuid5

SERVICE_DIR = Path(__file__).resolve().parents[1]
REPO_ROOT = SERVICE_DIR.parent
sys.path.insert(0, str(SERVICE_DIR))

from app.config import Settings
from app.ingestion.chunker import chunk_segments
from app.ingestion.extract import extract_document
from app.models import AnswerRequest, DepartmentCode, DocumentCategory, SearchRequest
from app.rag.service import RagService

NAMESPACE = UUID("5f60be0c-a0d1-4e7f-97c5-8a9ed0b42e72")


def load_manifest(root: Path) -> list[dict[str, str]]:
    with (root / "resources/corpus/manifest.csv").open(newline="") as file:
        return list(csv.DictReader(file))


def load_users(root: Path) -> dict[str, dict]:
    data = json.loads((root / "resources/seed/users.json").read_text())
    return {user["email"]: user for user in data["users"]}


def allowed_departments(user: dict) -> list[DepartmentCode]:
    if user["role"] == "ADMIN":
        return list(DepartmentCode)
    return [DepartmentCode(user["department"]), DepartmentCode.GENERAL]


def index_corpus(service: RagService, root: Path, manifest: list[dict[str, str]]) -> dict[str, str]:
    titles: dict[str, str] = {}
    for row in manifest:
        doc_id = row["docId"]
        version = int(row["version"])
        title = row["title"]
        titles[doc_id] = title
        document_id = uuid5(NAMESPACE, doc_id)
        version_id = uuid5(NAMESPACE, f"{doc_id}:v{version}")
        previous = uuid5(NAMESPACE, f"{doc_id}:v{version - 1}") if version > 1 else None
        path = root / "resources/corpus/files" / row["file"]
        segments = extract_document(path, "application/pdf" if row["format"] == "pdf" else "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
        chunks = chunk_segments(
            segments,
            document_id=document_id,
            version_id=version_id,
            version=version,
            department=DepartmentCode(row["department"]),
            category=DocumentCategory(row["category"]),
            document_title=title,
            chunk_size=500,
            overlap=80,
        )
        service.index_chunks(chunks, previous)
    return titles


def source_matches(citation, expected: dict, title: str) -> bool:
    return (
        citation.document_title == title
        and ("version" not in expected or citation.version == expected["version"])
        and ("page" not in expected or citation.page_number == expected["page"])
    )


def run(root: Path) -> dict:
    settings = Settings(ai_provider="fake", rag_min_similarity=0.35)
    service = RagService(settings)
    manifest = load_manifest(root)
    users = load_users(root)
    titles = index_corpus(service, root, manifest)
    questions = [json.loads(line) for line in (root / "resources/eval/golden_qa.jsonl").read_text().splitlines()]
    results = []
    latencies = []

    for question in questions:
        user = users[question["askedBy"]]
        departments = allowed_departments(user)
        started = time.monotonic()
        answer = service.answer(
            AnswerRequest(
                queryId=uuid5(NAMESPACE, question["id"]),
                query=question["question"],
                allowedDepartments=departments,
                history=question.get("history", []),
            )
        )
        search = service.search(
            SearchRequest(query=question["question"], allowedDepartments=departments, topK=settings.rag_top_k)
        )
        latency_ms = int((time.monotonic() - started) * 1000)
        latencies.append(latency_ms)
        answer_lower = answer.answer.lower()
        expected_answered = question["expectedAnswered"]
        expected_text_ok = all(value.lower() in answer_lower for value in question.get("expectedAnswerContains", []))
        forbidden_text_hit = any(value.lower() in answer_lower for value in question.get("forbiddenAnswerContains", []))
        expected_sources = question.get("expectedSources", [])
        citation_ok = all(
            any(source_matches(citation, expected, titles[expected["docId"]]) for citation in answer.citations)
            for expected in expected_sources
        ) if expected_sources else True
        forbidden_doc_hit = any(
            any(citation.document_title == titles[doc_id] for citation in answer.citations)
            for doc_id in question.get("forbiddenDocIds", [])
        )
        results.append({
            "id": question["id"],
            "type": question["type"],
            "answered": answer.answered,
            "expectedAnswered": expected_answered,
            "answeredCorrectly": answer.answered == expected_answered,
            "expectedTextPresent": expected_text_ok,
            "citationSourcesCorrect": citation_ok,
            "forbiddenTextHit": forbidden_text_hit,
            "forbiddenDocumentHit": forbidden_doc_hit,
            "answer": answer.answer,
            "citations": [citation.model_dump(by_alias=True, exclude_none=True, mode="json") for citation in answer.citations],
            "searchResultCount": len(search.results),
            "latencyMs": latency_ms,
        })

    total = len(results)
    refused = [item for item in results if not item["expectedAnswered"]]
    answered = [item for item in results if item["expectedAnswered"]]
    p95 = quantiles(latencies, n=20, method="inclusive")[18] if len(latencies) >= 2 else latencies[0]
    summary = {
        "totalQuestions": total,
        "answeredAccuracy": sum(item["answeredCorrectly"] for item in results) / total,
        "answerTextAccuracy": sum(item["expectedTextPresent"] for item in answered) / len(answered),
        "citationAccuracy": sum(item["citationSourcesCorrect"] for item in answered) / len(answered),
        "refusalAccuracy": sum(item["answeredCorrectly"] for item in refused) / len(refused),
        "departmentLeakage": sum(item["forbiddenTextHit"] or item["forbiddenDocumentHit"] for item in results),
        "p95LatencyMs": p95,
        "provider": settings.ai_provider,
        "note": "Fake embeddings and FakeLLM validate pipeline behavior, not production answer quality.",
    }
    return {"summary": summary, "results": results}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=SERVICE_DIR / "eval/results/latest.json")
    args = parser.parse_args()
    report = run(REPO_ROOT)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report["summary"], indent=2))


if __name__ == "__main__":
    main()
