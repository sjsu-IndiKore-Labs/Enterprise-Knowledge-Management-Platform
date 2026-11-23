"""SQS long-poll worker for DOCUMENT_INGEST_REQUESTED events."""

from __future__ import annotations

import json
import tempfile
from pathlib import Path

import boto3

from app.config import get_settings
from app.db import create_store
from app.ingestion.pipeline import IngestionPipeline
from app.models import IngestEvent
from app.rag.service import RagService


def _client_kwargs(settings):
    kwargs = {"region_name": settings.aws_region}
    if settings.aws_endpoint_url:
        kwargs["endpoint_url"] = settings.aws_endpoint_url
        kwargs["aws_access_key_id"] = settings.aws_access_key_id
        kwargs["aws_secret_access_key"] = settings.aws_secret_access_key
    return kwargs


def run_worker() -> None:
    settings = get_settings()
    sqs = boto3.client("sqs", **_client_kwargs(settings))
    s3 = boto3.client("s3", **_client_kwargs(settings))
    ingest_url = sqs.get_queue_url(QueueName=settings.sqs_document_ingest_queue)["QueueUrl"]
    status_url = sqs.get_queue_url(QueueName=settings.sqs_document_status_queue)["QueueUrl"]
    pipeline = IngestionPipeline(settings, RagService(settings, create_store(settings)))

    while True:
        response = sqs.receive_message(
            QueueUrl=ingest_url,
            MaxNumberOfMessages=1,
            WaitTimeSeconds=20,
            VisibilityTimeout=300,
        )
        for message in response.get("Messages", []):
            event = None
            try:
                event = IngestEvent.model_validate(json.loads(message["Body"]))
                processing = pipeline._status(event, "PROCESSING")
                sqs.send_message(QueueUrl=status_url, MessageBody=processing.model_dump_json(by_alias=True))
                with tempfile.TemporaryDirectory() as directory:
                    path = Path(directory) / event.original_filename
                    s3.download_file(event.s3_bucket, event.s3_key, str(path))
                    result = pipeline.process_file(event, path)
                sqs.send_message(QueueUrl=status_url, MessageBody=result.model_dump_json(by_alias=True))
                sqs.delete_message(QueueUrl=ingest_url, ReceiptHandle=message["ReceiptHandle"])
            except Exception as exc:  # noqa: BLE001 - leave message for SQS retry/DLQ handling.
                if event is not None:
                    error_code = "S3_READ_FAILED" if "download" in str(exc).lower() else "INTERNAL_ERROR"
                    failed = pipeline._status(event, "FAILED", error_code=error_code, error_message=str(exc)[:1000])
                    sqs.send_message(QueueUrl=status_url, MessageBody=failed.model_dump_json(by_alias=True))
                # Leave the message visible for SQS retry/DLQ policy.
                print(json.dumps({"level": "error", "message": "ingestion_failed", "error": str(exc)[:1000]}))


if __name__ == "__main__":
    run_worker()
