"""Static rules for the Dockerfile templates (the build/run test is tests/docker/test_images.sh)."""
import re

import pytest

from conftest import ROOT

DOCKER = ROOT / "manali_devops" / "docker"
TEMPLATES = {
    "java-service.Dockerfile": "/actuator/health",
    "python-service.Dockerfile": "/health",
    "node-web.Dockerfile": "/api/health",
}


@pytest.mark.parametrize("name", TEMPLATES)
def test_base_images_pinned(name):
    text = (DOCKER / name).read_text()
    for image in re.findall(r"^ARG \w+_IMAGE=(\S+)$", text, re.M):
        assert ":" in image and not image.endswith(":latest"), f"{name}: {image} not pinned"
    for image in re.findall(r"^FROM (\S+)", text, re.M):
        assert image.startswith("${") or ":" in image, f"{name}: FROM {image} not pinned"


@pytest.mark.parametrize("name", TEMPLATES)
def test_multi_stage(name):
    assert len(re.findall(r"^FROM ", (DOCKER / name).read_text(), re.M)) >= 2


@pytest.mark.parametrize("name", TEMPLATES)
def test_final_stage_runs_as_non_root(name):
    final = (DOCKER / name).read_text().split("\nFROM ")[-1]
    users = re.findall(r"^USER (\S+)", final, re.M)
    assert users and users[-1] not in ("root", "0"), f"{name}: final stage must switch to a non-root USER"


@pytest.mark.parametrize("name,path", TEMPLATES.items())
def test_healthcheck_uses_contract_path(name, path):
    text = (DOCKER / name).read_text()
    assert "HEALTHCHECK" in text and path in text, f"{name}: HEALTHCHECK must hit {path}"


@pytest.mark.parametrize("name", TEMPLATES)
def test_has_dockerignore(name):
    assert (DOCKER / f"{name}.dockerignore").is_file()


def test_java_image_ships_migrations():
    assert "COPY --chown=app:app contracts/database/migrations /contracts/database/migrations" in (
        DOCKER / "java-service.Dockerfile").read_text()


def test_no_secrets_baked_into_images():
    for name in TEMPLATES:
        text = (DOCKER / name).read_text()
        for secret in ("OPENAI_API_KEY", "JWT_SECRET", "POSTGRES_PASSWORD", "INTERNAL_SERVICE_TOKEN", "AWS_SECRET_ACCESS_KEY"):
            assert secret not in text, f"{name} must not reference {secret}"
