"""Safety checks applied before and after answer generation."""

from __future__ import annotations

import re

NOT_FOUND_ANSWER = "I couldn't find this in the documents you have access to."

_INJECTION_PATTERNS = (
    r"ignore\s+(all|any|the)\s+(previous|prior|above)\s+instructions",
    r"reveal\s+(the|all)\s+(system|hidden|private|confidential)",
    r"show\s+me\s+documents?\s+from\s+another\s+department",
)


def looks_like_prompt_injection(text: str) -> bool:
    return any(re.search(pattern, text, flags=re.IGNORECASE) for pattern in _INJECTION_PATTERNS)


def keep_valid_citations(answer: str, citation_count: int) -> str:
    def replace(match: re.Match[str]) -> str:
        number = int(match.group(1))
        return match.group(0) if 1 <= number <= citation_count else ""

    return re.sub(r"\[(\d+)\]", replace, answer)


def referenced_citations(answer: str, citation_count: int) -> list[int]:
    """Return only citation indexes actually referenced by the answer."""
    return sorted({
        number
        for number in (int(value) for value in re.findall(r"\[(\d+)\]", answer))
        if 1 <= number <= citation_count
    })
