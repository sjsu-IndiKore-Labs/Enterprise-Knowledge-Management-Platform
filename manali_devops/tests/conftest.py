import re
import shutil
from pathlib import Path

import pytest
import yaml

ROOT = Path(__file__).resolve().parents[2]


@pytest.fixture(scope="session")
def root() -> Path:
    return ROOT


@pytest.fixture(scope="session")
def env_example() -> dict:
    """KEY -> value from .env.example (inline comments stripped). Commented `# KEY=` lines map to None."""
    out = {}
    for line in (ROOT / ".env.example").read_text().splitlines():
        m = re.match(r"^(#\s*)?([A-Z][A-Z0-9_]*)=(.*)$", line)
        if m:
            value = re.split(r"\s+#", m.group(3), maxsplit=1)[0].strip()
            out[m.group(2)] = None if m.group(1) else value
    return out


@pytest.fixture(scope="session")
def compose() -> dict:
    return yaml.safe_load((ROOT / "docker-compose.yml").read_text())


@pytest.fixture(scope="session")
def contract_ports() -> dict:
    """service -> port from the table in contracts/ports-and-services.md"""
    ports = {}
    for line in (ROOT / "contracts" / "ports-and-services.md").read_text().splitlines():
        m = re.match(r"^\| `([a-z-]+)` \|[^|]*\|[^|]*\| (\d+) \|", line)
        if m:
            ports[m.group(1)] = int(m.group(2))
    return ports


def has(tool: str) -> bool:
    return shutil.which(tool) is not None
