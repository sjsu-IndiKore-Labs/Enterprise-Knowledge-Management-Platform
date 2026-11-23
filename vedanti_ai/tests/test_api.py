from uuid import uuid4

from fastapi.testclient import TestClient

from app.config import get_settings
from app.main import app


def test_internal_routes_require_token():
    client = TestClient(app)
    response = client.post("/internal/v1/search", json={"query": "test", "allowedDepartments": ["GENERAL"]})
    assert response.status_code == 401
    assert response.json()["code"] == "UNAUTHENTICATED"


def test_health_reports_local_store():
    client = TestClient(app)
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json()["status"] == "UP"
    assert response.json()["database"] == "UP"


def test_authorized_search_and_stream_routes_have_contract_shape():
    client = TestClient(app)
    headers = {"X-Internal-Token": get_settings().internal_service_token}
    search = client.post(
        "/internal/v1/search",
        headers=headers,
        json={"query": "test", "allowedDepartments": ["GENERAL"]},
    )
    assert search.status_code == 200
    assert {"query", "mode", "results", "latencyMs"} <= search.json().keys()

    stream = client.post(
        "/internal/v1/answer/stream",
        headers=headers,
        json={"queryId": str(uuid4()), "query": "test", "allowedDepartments": ["GENERAL"]},
    )
    assert stream.status_code == 200
    assert "event: citations" in stream.text
    assert "event: done" in stream.text
