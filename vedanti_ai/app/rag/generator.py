"""Grounded answer generation providers."""

from __future__ import annotations

from dataclasses import dataclass

from app.models import SearchResult
from app.rag.guardrails import NOT_FOUND_ANSWER, keep_valid_citations
from app.rag.prompt import build_prompt


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
        sentence = first.content.split(". ", 1)[0].strip().rstrip(".")
        text = keep_valid_citations(f"{sentence}. [1]", len(results))
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
