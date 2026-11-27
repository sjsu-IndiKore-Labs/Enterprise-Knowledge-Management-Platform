import json
from pathlib import Path
from unittest.mock import Mock
from uuid import uuid4

import pytest
from app.config import Settings
from app.ingestion import worker
from app.ingestion.chunker import Chunk
from app.models import AnswerRequest, DepartmentCode, DocumentCategory
from app.rag.generator import GeneratedAnswer
from app.rag.guardrails import NOT_FOUND_ANSWER
from app.rag.service import RagService


def test_shared_database_environment(monkeypatch):
    for key, value in {
        "POSTGRES_HOST": "db.example", "POSTGRES_PORT": "5433",
        "POSTGRES_DB": "integration", "POSTGRES_USER": "worker",
        "POSTGRES_PASSWORD": "test password", "POSTGRES_SSLMODE": "require",
    }.items():
        monkeypatch.setenv(key, value)
    s = Settings(_env_file=None)
    assert (s.postgresql_host, s.postgresql_port, s.postgresql_db) == ("db.example", 5433, "integration")
    assert (s.postgresql_user, s.postgresql_password, s.postgresql_sslmode) == ("worker", "test password", "require")


@pytest.mark.parametrize("text", [NOT_FOUND_ANSWER, "Unsupported claim without a citation", "Claim [99]"])
def test_generated_refusal_or_missing_valid_source_fails_closed(text):
    service = RagService(Settings(ai_provider="fake", rag_min_similarity=0))
    service.index_chunks([Chunk(uuid4(), uuid4(), 1, DepartmentCode.GENERAL,
        DocumentCategory.POLICY, "Leave", 0, "Paid leave is available.", 4, 1, None)])
    service.generator = Mock()
    service.generator.generate.return_value = GeneratedAnswer(text, "test", 1, 1)
    answer = service.answer(AnswerRequest(queryId=uuid4(), query="paid leave", allowedDepartments=["GENERAL"]))
    service.generator.generate.assert_called_once()
    assert not answer.answered
    assert answer.answer == NOT_FOUND_ANSWER
    assert answer.citations == []


@pytest.mark.parametrize("status,acknowledged", [("INDEXED", True), ("FAILED", False)])
def test_worker_acknowledges_only_indexed_messages(monkeypatch, status, acknowledged):
    root = Path(__file__).resolve().parents[2]
    body = (root / "contracts/events/examples/document-ingest-requested.json").read_text()
    sqs, s3 = Mock(), Mock()
    sqs.get_queue_url.return_value = {"QueueUrl": "test-queue"}
    sqs.receive_message.side_effect = [{"Messages": [{"Body": body, "ReceiptHandle": "receipt"}]}, KeyboardInterrupt]
    pipeline = Mock()
    pipeline.process_file.return_value.status = status
    pipeline.process_file.return_value.model_dump_json.return_value = json.dumps({"status": status})
    pipeline._status.return_value.model_dump_json.return_value = '{"status":"PROCESSING"}'
    monkeypatch.setattr(worker.boto3, "client", lambda name, **kwargs: sqs if name == "sqs" else s3)
    monkeypatch.setattr(worker, "get_settings", lambda: Settings(_env_file=None))
    monkeypatch.setattr(worker, "IngestionPipeline", lambda *args: pipeline)
    with pytest.raises(KeyboardInterrupt):
        worker.run_worker()
    s3.download_file.assert_called_once()
    assert sqs.delete_message.called is acknowledged
    assert sqs.send_message.call_count == 2


def test_ecs_client_does_not_override_runtime_credentials():
    settings = Settings(_env_file=None, aws_endpoint_url="", aws_region="us-west-2")
    assert worker._client_kwargs(settings) == {"region_name": "us-west-2"}


def test_on_call_stipend_is_found_with_hyphen_or_space_wording():
    from scripts.local_worker_chat import ingest_corpus

    service = ingest_corpus(Path(__file__).resolve().parents[2], DepartmentCode.ENGINEERING)
    for query in ("on call stipend", "on-call stipend", "What is the on call stipend?"):
        response = service.answer(AnswerRequest(
            queryId=uuid4(), query=query,
            allowedDepartments=[DepartmentCode.ENGINEERING, DepartmentCode.GENERAL],
        ))
        assert response.answered
        assert "$300 per week as primary" in response.answer
        assert "$150 per week as secondary" in response.answer
        assert [citation.document_title for citation in response.citations] == ["Engineering On-Call Policy"]
