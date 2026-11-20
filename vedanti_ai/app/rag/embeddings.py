"""Embedding providers with an offline deterministic implementation."""

from __future__ import annotations

import hashlib
import math
from collections import OrderedDict
from time import monotonic


class FakeEmbeddings:
    def __init__(self, dimensions: int = 1536):
        self.dimensions = dimensions

    def embed(self, texts: list[str]) -> list[list[float]]:
        vectors = []
        for text in texts:
            values = []
            counter = 0
            while len(values) < self.dimensions:
                digest = hashlib.sha256(f"{text}\0{counter}".encode()).digest()
                values.extend((byte / 127.5) - 1.0 for byte in digest)
                counter += 1
            values = values[: self.dimensions]
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

    def embed(self, texts: list[str]) -> list[list[float]]:
        vectors: list[list[float]] = []
        for start in range(0, len(texts), 100):
            response = self.client.embeddings.create(
                model=self.model,
                input=texts[start : start + 100],
                dimensions=self.dimensions,
            )
            vectors.extend(item.embedding for item in response.data)
        return vectors
