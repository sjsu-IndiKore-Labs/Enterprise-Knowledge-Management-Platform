"""Embedding providers with an offline deterministic implementation."""

from __future__ import annotations

import hashlib
import math
import time
from collections import OrderedDict
from time import monotonic


class FakeEmbeddings:
    def __init__(self, dimensions: int = 1536):
        self.dimensions = dimensions

    def embed(self, texts: list[str]) -> list[list[float]]:
        vectors = []
        for text in texts:
            values = [0.0] * self.dimensions
            tokens = text.lower().split()
            for token in tokens:
                digest = hashlib.sha256(token.strip(".,:;!?()[]{}\"").encode()).digest()
                index = int.from_bytes(digest[:4], "big") % self.dimensions
                values[index] += 1.0 if digest[4] % 2 else -1.0
            norm = math.sqrt(sum(value * value for value in values)) or 1.0
            vectors.append([value / norm for value in values])
        return vectors


class CachedEmbeddings:
    def __init__(self, provider: FakeEmbeddings, ttl_seconds: int = 3600, max_items: int = 2048):
        self.provider = provider
        self.ttl_seconds = ttl_seconds
        self.max_items = max_items
        self._cache: OrderedDict[str, tuple[float, list[float]]] = OrderedDict()

    def embed(self, texts: list[str]) -> list[list[float]]:
        now = monotonic()
        result: list[list[float] | None] = []
        missing: list[str] = []
        for text in texts:
            cached = self._cache.get(text)
            if cached and now - cached[0] < self.ttl_seconds:
                self._cache.move_to_end(text)
                result.append(cached[1])
            else:
                missing.append(text)
                result.append(None)
        if missing:
            for text, vector in zip(missing, self.provider.embed(missing)):
                self._cache[text] = (now, vector)
                self._cache.move_to_end(text)
            for index, value in enumerate(result):
                if value is None:
                    result[index] = self._cache[texts[index]][1]
        while len(self._cache) > self.max_items:
            self._cache.popitem(last=False)
        return [vector for vector in result if vector is not None]


class OpenAIEmbeddings:
    def __init__(self, api_key: str, model: str, dimensions: int):
        from openai import OpenAI

        self.client = OpenAI(api_key=api_key)
        self.model = model
        self.dimensions = dimensions
        self.last_token_count = 0

    def embed(self, texts: list[str]) -> list[list[float]]:
        vectors: list[list[float]] = []
        for start in range(0, len(texts), 100):
            batch = texts[start : start + 100]
            response = None
            last_error = None
            for attempt in range(3):
                try:
                    response = self.client.embeddings.create(
                        model=self.model,
                        input=batch,
                        dimensions=self.dimensions,
                    )
                    break
                except Exception as exc:  # noqa: BLE001 - retry transient provider failures.
                    last_error = exc
                    if attempt < 2:
                        time.sleep(2**attempt)
            if response is None:
                raise RuntimeError("EMBEDDING_FAILED") from last_error
            vectors.extend(item.embedding for item in response.data)
            self.last_token_count += sum(getattr(response.usage, "prompt_tokens", 0) for _ in [0]) if response.usage else 0
        return vectors


def count_tokens(text: str, model: str = "text-embedding-3-small") -> int:
    """Use the model tokenizer when installed; fall back to a safe estimate."""
    try:
        import tiktoken

        encoding = tiktoken.encoding_for_model(model)
        return len(encoding.encode(text))
    except Exception:  # noqa: BLE001 - token counting must not block ingestion.
        return len(text.split())
