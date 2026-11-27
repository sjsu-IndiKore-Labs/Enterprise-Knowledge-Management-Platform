"""Grounded answer generation providers."""

from __future__ import annotations

import re
from dataclasses import dataclass

from app.models import SearchResult
from app.rag.guardrails import NOT_FOUND_ANSWER, keep_valid_citations
from app.rag.prompt import build_prompt
from app.rag.query import canonical_term, normalize_query


@dataclass(frozen=True)
class GeneratedAnswer:
    text: str
    model: str
    prompt_tokens: int
    completion_tokens: int


class FakeLLM:
    model = "fake"

    def generate(self, query: str, results: list[SearchResult]) -> GeneratedAnswer:
        if not results:
            return GeneratedAnswer(NOT_FOUND_ANSWER, self.model, 0, 0)
        first = results[0]
        query_terms = {
            canonical_term(term)
            for term in re.findall(r"[a-z0-9]+", normalize_query(query).lower())
            if len(term) > 2 and canonical_term(term) not in {"what", "are", "the", "for", "how", "is", "a", "an"}
        }
        sentences = [sentence.strip() for sentence in re.split(r"(?<=[.!?])\s+", first.content) if sentence.strip()]
        ranked = sorted(
            sentences,
            key=lambda sentence: len(
                query_terms & {canonical_term(term) for term in re.findall(r"[a-z0-9]+", sentence.lower())}
            ),
            reverse=True,
        )
        relevant = [
            sentence
            for sentence in ranked
            if query_terms & {canonical_term(term) for term in re.findall(r"[a-z0-9]+", sentence.lower())}
        ]
        if not relevant:
            return GeneratedAnswer(NOT_FOUND_ANSWER, self.model, len(query.split()), 0)
        selected = " ".join(relevant[:2]).rstrip(".")
        text = keep_valid_citations(f"{selected}. [1]", len(results))
        return GeneratedAnswer(text, self.model, len(query.split()), len(text.split()))


class OpenAILLM:
    def __init__(self, api_key: str, model: str):
        from openai import OpenAI

        self.client = OpenAI(api_key=api_key)
        self.model = model

    def generate(self, query: str, results: list[SearchResult]) -> GeneratedAnswer:
        response = self.client.chat.completions.create(
            model=self.model,
            temperature=0,
            messages=[
                {"role": "system", "content": build_prompt(query, results)},
                {"role": "user", "content": query},
            ],
        )
        text = response.choices[0].message.content or NOT_FOUND_ANSWER
        usage = response.usage
        return GeneratedAnswer(
            keep_valid_citations(text, len(results)),
            self.model,
            getattr(usage, "prompt_tokens", 0),
            getattr(usage, "completion_tokens", 0),
        )
