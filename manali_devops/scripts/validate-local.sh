#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

command -v docker >/dev/null 2>&1 || {
  echo "docker is required; start Docker Desktop or install Docker" >&2
  exit 1
}

docker compose config --quiet

required_files=(
  docker-compose.yml
  Makefile
  .env.example
  contracts/ports-and-services.md
  manali_devops/localstack/init-aws.sh
)

for file in "${required_files[@]}"; do
  test -f "$file" || {
    echo "missing required file: $file" >&2
    exit 1
  }
done

echo "Local Compose configuration is valid."
