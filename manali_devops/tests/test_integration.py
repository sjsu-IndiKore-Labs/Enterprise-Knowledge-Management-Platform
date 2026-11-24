"""Live checks against the local stack: `make up && make migrate-raw seed`, then `pytest -m integration`."""
import json
import uuid

import pytest

from conftest import ROOT

pytestmark = pytest.mark.integration

boto3 = pytest.importorskip("boto3")
psycopg = pytest.importorskip("psycopg")


@pytest.fixture(scope="module")
def env(env_example):
    return env_example


@pytest.fixture(scope="module")
def aws(env):
    kw = dict(endpoint_url="http://localhost:4566", region_name=env["AWS_REGION"],
              aws_access_key_id="test", aws_secret_access_key="test")
    try:
        sqs = boto3.client("sqs", **kw)
        sqs.list_queues()
    except Exception as exc:  # noqa: BLE001
        pytest.skip(f"LocalStack not reachable: {exc}")
    return {"sqs": sqs, "s3": boto3.client("s3", **kw)}


@pytest.fixture(scope="module")
def db(env):
    try:
        conn = psycopg.connect(host="localhost", port=env["POSTGRES_PORT"], dbname=env["POSTGRES_DB"],
                               user=env["POSTGRES_USER"], password=env["POSTGRES_PASSWORD"], connect_timeout=3)
    except Exception as exc:  # noqa: BLE001
        pytest.skip(f"Postgres not reachable: {exc}")
    conn.autocommit = True
    yield conn
    conn.close()


def test_bucket_exists(aws, env):
    names = [b["Name"] for b in aws["s3"].list_buckets()["Buckets"]]
    assert env["S3_DOCUMENTS_BUCKET"] in names


@pytest.mark.parametrize("key", ["SQS_DOCUMENT_INGEST_QUEUE", "SQS_DOCUMENT_STATUS_QUEUE", "SQS_AUDIT_EVENTS_QUEUE"])
def test_queue_with_dlq(aws, env, key):
    sqs, name = aws["sqs"], env[key]
    url = sqs.get_queue_url(QueueName=name)["QueueUrl"]
    attrs = sqs.get_queue_attributes(QueueUrl=url, AttributeNames=["All"])["Attributes"]
    redrive = json.loads(attrs["RedrivePolicy"])
    assert int(redrive["maxReceiveCount"]) == 3
    assert redrive["deadLetterTargetArn"].endswith(f"{name}-dlq")
    if key == "SQS_DOCUMENT_INGEST_QUEUE":
        assert attrs["VisibilityTimeout"] == "300"


def test_ingest_message_roundtrip(aws, env):
    sqs = aws["sqs"]
    url = sqs.get_queue_url(QueueName=env["SQS_DOCUMENT_INGEST_QUEUE"])["QueueUrl"]
    body = json.loads((ROOT / "contracts/events/examples/document-ingest-requested.json").read_text())
    body["eventId"] = str(uuid.uuid4())
    sqs.send_message(QueueUrl=url, MessageBody=json.dumps(body))
    for _ in range(5):
        msgs = sqs.receive_message(QueueUrl=url, MaxNumberOfMessages=10, WaitTimeSeconds=2).get("Messages", [])
        for m in msgs:
            sqs.delete_message(QueueUrl=url, ReceiptHandle=m["ReceiptHandle"])
            if json.loads(m["Body"])["eventId"] == body["eventId"]:
                return
    pytest.fail("message not received back from the ingest queue")


def test_presigned_upload_download(aws, env):
    s3, bucket = aws["s3"], env["S3_DOCUMENTS_BUCKET"]
    key = f"documents/HR/{uuid.uuid4()}/v1/test.pdf"
    s3.put_object(Bucket=bucket, Key=key, Body=b"%PDF-1.7 test")
    assert s3.get_object(Bucket=bucket, Key=key)["Body"].read().startswith(b"%PDF")
    s3.delete_object(Bucket=bucket, Key=key)


def test_pgvector_and_schema(db):
    with db.cursor() as cur:
        cur.execute("SELECT extversion FROM pg_extension WHERE extname = 'vector'")
        assert cur.fetchone(), "pgvector extension missing"
        cur.execute("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'")
        tables = {r[0] for r in cur.fetchall()}
    assert {"departments", "users", "documents", "document_versions", "document_chunks",
            "conversations", "chat_messages", "audit_logs", "query_logs", "knowledge_gaps"} <= tables


def test_seed_users(db):
    with db.cursor() as cur:
        cur.execute("SELECT count(*), count(*) FILTER (WHERE role = 'ADMIN') FROM users")
        total, admins = cur.fetchone()
    assert total >= 12 and admins >= 1


def test_department_prefilter_query(db):
    """The retrieval query shape from contracts/database/README.md never returns other departments' chunks."""
    with db.cursor() as cur:
        cur.execute("BEGIN")
        try:
            cur.execute("SELECT id FROM users WHERE role = 'ADMIN' LIMIT 1")
            admin = cur.fetchone()[0]
            vec = "[" + ",".join(["0.01"] * 1536) + "]"
            for dept in ("HR", "ENGINEERING"):
                cur.execute("""INSERT INTO documents (title, department_code, category, created_by)
                               VALUES (%s, %s, 'POLICY', %s) RETURNING id""", (f"t-{dept}", dept, admin))
                doc = cur.fetchone()[0]
                cur.execute("""INSERT INTO document_versions (document_id, version, s3_bucket, s3_key, original_filename,
                               content_type, size_bytes, uploaded_by) VALUES (%s, 1, 'b', 'k', 'f.pdf', 'application/pdf', 1, %s)
                               RETURNING id""", (doc, admin))
                ver = cur.fetchone()[0]
                cur.execute("""INSERT INTO document_chunks (document_id, version_id, version, department_code, category,
                               document_title, chunk_index, page_number, content, token_count, embedding)
                               VALUES (%s, %s, 1, %s, 'POLICY', %s, 0, 1, %s, 5, %s::vector)""",
                            (doc, ver, dept, f"t-{dept}", f"secret {dept} policy text", vec))
            cur.execute("""SELECT department_code, 1 - (embedding <=> %s::vector) AS score FROM document_chunks
                           WHERE department_code = ANY(%s) AND is_current AND NOT is_deleted
                           ORDER BY embedding <=> %s::vector LIMIT 10""", (vec, ["ENGINEERING", "GENERAL"], vec))
            rows = cur.fetchall()
            assert rows and {r[0] for r in rows} == {"ENGINEERING"}
            cur.execute("""SELECT count(*) FROM document_chunks WHERE content_tsv @@ websearch_to_tsquery('english', 'secret policy')
                           AND department_code = ANY(%s)""", (["ENGINEERING"],))
            assert cur.fetchone()[0] == 1
        finally:
            cur.execute("ROLLBACK")
