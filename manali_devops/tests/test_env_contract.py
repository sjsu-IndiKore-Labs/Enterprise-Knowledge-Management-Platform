"""Every env var used anywhere in the deployment must be declared in .env.example (contracts rule)."""
import re
import subprocess

import pytest

from conftest import ROOT, has

TF = ROOT / "manali_devops" / "infra" / "terraform"


def test_no_duplicate_keys():
    keys = re.findall(r"^#?\s*([A-Z][A-Z0-9_]*)=", (ROOT / ".env.example").read_text(), re.M)
    dupes = {k for k in keys if keys.count(k) > 1}
    assert not dupes, f"duplicate keys in .env.example: {dupes}"


def test_compose_variables_are_declared(env_example):
    text = (ROOT / "docker-compose.yml").read_text()
    used = set(re.findall(r"\$\{([A-Z][A-Z0-9_]*)", text))
    assert used, "expected ${VAR} references in docker-compose.yml"
    assert used <= env_example.keys(), f"undeclared in .env.example: {used - env_example.keys()}"


def test_compose_environment_overrides_are_declared(compose, env_example):
    for name, svc in compose["services"].items():
        env = svc.get("environment") or {}
        if name in ("postgres", "localstack"):
            continue  # image-specific variables
        assert set(env) <= env_example.keys(), f"{name}: {set(env) - env_example.keys()} not in .env.example"


def test_terraform_injected_variables_are_declared(env_example):
    text = (TF / "ecs.tf").read_text() + (TF / "variables.tf").read_text() + (TF / "secrets.tf").read_text()
    # map keys written as `  NAME = ...` (backend_env, app_config, generated_secrets) + the secret_names list
    names = set(re.findall(r"^\s+([A-Z][A-Z0-9_]{2,})\s+=", text, re.M))
    secret_list = re.search(r"secret_names\s+=\s+\[([^\]]*)\]", text)
    assert secret_list, "local.secret_names not found in secrets.tf"
    names |= set(re.findall(r'"([A-Z][A-Z0-9_]+)"', secret_list.group(1)))
    names -= {"PORT"}  # set per container by the image / task definition
    missing = names - env_example.keys()
    assert not missing, f"Terraform injects env vars missing from .env.example: {missing}"


def test_secrets_are_not_given_real_defaults(env_example):
    assert env_example["OPENAI_API_KEY"] == ""
    assert "change-me" in env_example["JWT_SECRET"]
    assert "change-me" in env_example["INTERNAL_SERVICE_TOKEN"]


def test_resource_names_consistent(env_example):
    init = (ROOT / "manali_devops" / "localstack" / "init-aws.sh").read_text()
    ports_md = (ROOT / "contracts" / "ports-and-services.md").read_text()
    for key in ("S3_DOCUMENTS_BUCKET", "SQS_DOCUMENT_INGEST_QUEUE", "SQS_DOCUMENT_STATUS_QUEUE", "SQS_AUDIT_EVENTS_QUEUE"):
        value = env_example[key]
        assert f"{key}:-{value}" in init, f"init-aws.sh default for {key} must be {value}"
        assert f"`{value}`" in ports_md, f"{value} not documented in contracts/ports-and-services.md"


@pytest.mark.skipif(not has("docker"), reason="docker not installed")
def test_compose_strips_inline_comments_from_env_file():
    """.env.example uses `KEY=value   # comment`; compose must not include the comment in the value."""
    out = subprocess.run(
        ["docker", "compose", "--env-file", ".env.example", "--profile", "apps", "config", "--format", "json"],
        cwd=ROOT, capture_output=True, text=True,
    )
    if out.returncode != 0:
        pytest.skip(f"docker compose unavailable: {out.stderr[:200]}")
    assert '"POSTGRES_DB": "ekm"' in out.stdout or '"POSTGRES_DB":"ekm"' in out.stdout
    assert "#" not in out.stdout.split('"S3_DOCUMENTS_BUCKET"')[1].split(",")[0]
