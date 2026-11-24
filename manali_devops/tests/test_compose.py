"""docker-compose.yml must match contracts/ports-and-services.md and the Dockerfile templates."""
from conftest import ROOT

APP_SERVICES = {"core-api", "audit-service", "ai-service", "ai-worker", "web"}


def test_expected_services(compose):
    assert APP_SERVICES | {"postgres", "localstack"} <= set(compose["services"])


def test_ports_match_contract(compose, contract_ports):
    for name, port in contract_ports.items():
        if name not in compose["services"]:
            continue
        published = [p.split(":")[0] for p in compose["services"][name].get("ports", [])]
        assert str(port) in published, f"{name} must publish {port} (contracts/ports-and-services.md)"


def test_apps_are_behind_profile(compose):
    for name in APP_SERVICES:
        assert compose["services"][name].get("profiles") == ["apps"], name


def test_apps_build_from_devops_templates(compose):
    for name in APP_SERVICES:
        build = compose["services"][name]["build"]
        dockerfile = (ROOT / build["context"] / build["dockerfile"]).resolve()
        assert dockerfile.is_file(), f"{name}: {dockerfile} missing"
        assert "manali_devops/docker" in str(dockerfile), f"{name} must use a template from manali_devops/docker"


def test_java_services_use_repo_root_context(compose):
    for name in ("core-api", "audit-service"):
        assert compose["services"][name]["build"]["context"] == ".", "needs contracts/database/migrations"
        assert compose["services"][name]["build"]["args"]["SERVICE"] == name


def test_worker_reuses_ai_image(compose):
    worker, api = compose["services"]["ai-worker"], compose["services"]["ai-service"]
    assert worker["image"] == api["image"]
    assert worker["command"] == ["python", "-m", "app.ingestion.worker"]


def test_containers_talk_via_service_names(compose):
    env = compose["services"]["core-api"]["environment"]
    assert env["POSTGRES_HOST"] == "postgres"
    assert env["AWS_ENDPOINT_URL"] == "http://localstack:4566"
    assert env["AI_SERVICE_URL"] == "http://ai-service:8000"


def test_third_party_images_pinned(compose):
    for name, svc in compose["services"].items():
        image = svc.get("image", "")
        if image.startswith("ekm/"):
            continue
        assert ":" in image and not image.endswith(":latest"), f"{name}: pin the image tag ({image})"


def test_infra_has_healthchecks(compose):
    for name in ("postgres", "localstack"):
        assert "healthcheck" in compose["services"][name]
