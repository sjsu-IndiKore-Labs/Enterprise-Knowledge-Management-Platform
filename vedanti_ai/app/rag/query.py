"""Small, provider-independent query normalization helpers."""

from __future__ import annotations

import re

_TYPO_REPLACEMENTS = {
    "wrking": "working",
    "workng": "working",
}


def normalize_query(query: str) -> str:
    """Make common shorthand/typos searchable without changing user-visible text."""
    words = query.split()
    normalized: list[str] = []
    for word in words:
        punctuation_left = re.match(r"^\W*", word).group(0)
        punctuation_right = re.search(r"\W*$", word).group(0)
        core = word[len(punctuation_left) : len(word) - len(punctuation_right) or None]
        replacement = _TYPO_REPLACEMENTS.get(core.lower(), core)
        normalized.append(f"{punctuation_left}{replacement}{punctuation_right}")

    text = " ".join(normalized)
    # Treat hyphenated phrases like ordinary speech: "on-call" and "on call".
    text = re.sub(r"(?<=\w)[-‐‑‒–—](?=\w)", " ", text)
    # In this context, "working hr" means "working hours". Do not rewrite
    # standalone HR, which commonly means Human Resources.
    text = re.sub(r"\bworking\s+hr\b", "working hours", text, flags=re.IGNORECASE)
    text = re.sub(r"\bwork\s+hr\b", "work hours", text, flags=re.IGNORECASE)
    return text


def canonical_term(term: str) -> str:
    """Normalize simple singular/plural forms for keyword matching."""
    value = term.lower().strip(".,:;!?()[]{}\"")
    if len(value) > 4 and value.endswith("ies"):
        return value[:-3] + "y"
    if len(value) > 4 and value.endswith("s"):
        return value[:-1]
    return value
