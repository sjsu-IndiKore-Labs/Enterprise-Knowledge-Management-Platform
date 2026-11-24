"""Guardrails for GitHub Actions workflows, the Jenkinsfile and deployment scripts."""
import json
import os
import re
import stat
import subprocess
from pathlib import Path

import pytest
import yaml

from conftest import ROOT, has

WORKFLOWS = sorted((ROOT / ".github" / "workflows").glob("*.yml"))
SCRIPTS = ROOT / "manali_devops" / "scripts"


@pytest.mark.parametrize("path", WORKFLOWS, ids=lambda p: p.name)
def test_workflow_parses_and_pins_actions(path):
    data = yaml.safe_load(path.read_text())
    assert "jobs" in data
    for ref in re.findall(r"uses:\s*([^\s]+)", path.read_text()):
        assert "@" in ref, f"{path.name}: action {ref} must be pinned to a version"
        assert not ref.endswith(("@main", "@master")), f"{path.name}: {ref} pinned to a branch"


@pytest.mark.parametrize("path", WORKFLOWS, ids=lambda p: p.name)
def test_no_long_lived_aws_keys(path):
    text = path.read_text()
    assert "AWS_SECRET_ACCESS_KEY" not in text and "aws-secret-access-key" not in text, "use OIDC (role-to-assume)"


def test_cd_uses_oidc_and_staging_environment():
    cd = yaml.safe_load((ROOT / ".github" / "workflows" / "cd-staging.yml").read_text())
    assert cd["permissions"]["id-token"] == "write"
    assert cd["jobs"]["deploy"]["environment"]["name"] == "staging"
    assert cd["concurrency"]["cancel-in-progress"] is False, "never cancel a deployment half-way"


def test_cd_image_platform_matches_terraform_default():
    cd = (ROOT / ".github" / "workflows" / "cd-staging.yml").read_text()
    tf = (ROOT / "manali_devops" / "infra" / "terraform" / "variables.tf").read_text()
    assert "PLATFORM: linux/amd64" in cd and 'default     = "X86_64"' in tf


@pytest.mark.parametrize("script", sorted(SCRIPTS.glob("*.sh")) + [ROOT / "manali_devops/localstack/init-aws.sh",
                                                                  ROOT / "manali_devops/tests/docker/test_images.sh"],
                         ids=lambda p: p.name)
def test_scripts_are_executable_and_strict(script):
    assert script.stat().st_mode & stat.S_IXUSR, f"{script.name} must be executable"
    assert "set -euo pipefail" in script.read_text()


def test_jenkinsfile_uses_shared_scripts():
    text = (ROOT / "manali_devops" / "jenkins" / "Jenkinsfile").read_text()
    assert "manali_devops/scripts/build-push.sh" in text and "manali_devops/scripts/ecs-deploy.sh" in text


@pytest.mark.skipif(not has("jq"), reason="jq not installed")
def test_ecs_deploy_swaps_image_only(tmp_path: Path):
    """Run ecs-deploy.sh in DRY_RUN mode against a fake `aws` CLI."""
    task_def = {
        "taskDefinitionArn": "arn:aws:ecs:us-west-2:123456789012:task-definition/ekm-staging-core-api:7",
        "family": "ekm-staging-core-api", "revision": 7, "status": "ACTIVE",
        "requiresAttributes": [], "compatibilities": ["FARGATE"], "registeredAt": "x", "registeredBy": "y",
        "containerDefinitions": [{"name": "core-api", "image": "old:1", "environment": [{"name": "A", "value": "1"}]}],
    }
    fake = tmp_path / "aws"
    fake.write_text("#!/bin/sh\necho '" + json.dumps(task_def) + "'\n")
    fake.chmod(0o755)
    env = {**os.environ, "PATH": f"{tmp_path}:{os.environ['PATH']}", "DRY_RUN": "1",
           "ECR_REGISTRY": "123456789012.dkr.ecr.us-west-2.amazonaws.com"}
    out = subprocess.run([str(SCRIPTS / "ecs-deploy.sh"), "staging", "abc123", "core-api", "ai-worker"],
                         env=env, capture_output=True, text=True, check=True).stdout
    assert "123456789012.dkr.ecr.us-west-2.amazonaws.com/ekm-staging/core-api:abc123" in out
    # ai-worker deploys the ai-service image
    assert "ekm-staging/ai-service:abc123" in out
