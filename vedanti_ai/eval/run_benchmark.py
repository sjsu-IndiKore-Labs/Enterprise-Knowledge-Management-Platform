"""Benchmark chunking choices against the 40-question golden set.

Run from the repository root with::

    python vedanti_ai/eval/run_benchmark.py

The benchmark uses fake embeddings, so it is repeatable and needs no API key.
It measures source Recall@k, not LLM answer quality.
"""

from __future__ import annotations

import argparse
import csv
import json
import sys
from pathlib import Path
from uuid import UUID, uuid5

SERVICE_DIR = Path(__file__).resolve().parents[1]
REPO_ROOT = SERVICE_DIR.parent
sys.path.insert(0, str(SERVICE_DIR))

from app.config import Settings
from app.ingestion.chunker import chunk_segments
from app.ingestion.extract import ExtractedSegment, extract_document
from app.models import DepartmentCode, DocumentCategory, SearchRequest
from app.rag.service import RagService

NAMESPACE = UUID("5f60be0c-a0d1-4e7f-97c5-8a9ed0b42e72")
STRATEGIES = ("fixed_500_overlap_80", "section_aware", "page_aware_recursive")
TOP_K_VALUES = (4, 6, 8)


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


def strategy_segments(segments: list[ExtractedSegment], strategy: str) -> list[ExtractedSegment]:
    if strategy == "fixed_500_overlap_80":
        return segments
    if strategy == "section_aware":
        grouped: list[ExtractedSegment] = []
        for segment in segments:
            if grouped and grouped[-1].section_title == segment.section_title:
                previous = grouped[-1]
                grouped[-1] = ExtractedSegment(
                    text=f"{previous.text}\n{segment.text}",
                    page_number=previous.page_number,
                    section_title=previous.section_title,
                )
            else:
                grouped.append(segment)
        return grouped
    if strategy == "page_aware_recursive":
        result: list[ExtractedSegment] = []
        for segment in segments:
            words = segment.text.split()
            for start in range(0, len(words), 350):
                result.append(ExtractedSegment(
                    text=" ".join(words[start : start + 350]),
                    page_number=segment.page_number,
                    section_title=segment.section_title,
                ))
        return result
    raise ValueError(f"unknown strategy: {strategy}")


def build_service(root: Path, rows: list[dict[str, str]], strategy: str) -> tuple[RagService, dict[str, str]]:
    service = RagService(Settings(ai_provider="fake"))
    titles: dict[str, str] = {}
    for row in rows:
        doc_id = row["docId"]
        version = int(row["version"])
        titles[doc_id] = row["title"]
        content_type = "application/pdf" if row["format"] == "pdf" else "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        segments = extract_document(root / "resources/corpus/files" / row["file"], content_type)
        chunks = chunk_segments(
            strategy_segments(segments, strategy),
            document_id=uuid5(NAMESPACE, doc_id),
            version_id=uuid5(NAMESPACE, f"{doc_id}:v{version}"),
            version=version,
            department=DepartmentCode(row["department"]),
            category=DocumentCategory(row["category"]),
            document_title=row["title"],
            chunk_size=500,
            overlap=80,
        )
        previous = uuid5(NAMESPACE, f"{doc_id}:v{version - 1}") if version > 1 else None
        service.index_chunks(chunks, previous)
    return service, titles


def run(root: Path) -> dict:
    rows = load_manifest(root)
    people = load_users(root)
    questions = [json.loads(line) for line in (root / "resources/eval/golden_qa.jsonl").read_text().splitlines()]
    results = []
    for strategy in STRATEGIES:
        service, titles = build_service(root, rows, strategy)
        for top_k in TOP_K_VALUES:
            matched = 0
            expected_total = 0
            for question in questions:
                expected_sources = question.get("expectedSources", [])
                expected_total += len(expected_sources)
                response = service.search(SearchRequest(
                    query=question["question"],
                    allowedDepartments=allowed_departments(people[question["askedBy"]]),
                    topK=top_k,
                ))
                for source in expected_sources:
                    if any(
                        result.document_title == titles[source["docId"]]
                        and ("page" not in source or result.page_number == source["page"])
                        for result in response.results
                    ):
                        matched += 1
            results.append({
                "strategy": strategy,
                "topK": top_k,
                "recallAtK": round(matched / expected_total, 4) if expected_total else 0,
                "matchedSources": matched,
                "expectedSources": expected_total,
            })
    return {"provider": "fake", "results": results}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=SERVICE_DIR / "eval/benchmarks/latest.json")
    args = parser.parse_args()
    report = run(REPO_ROOT)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
