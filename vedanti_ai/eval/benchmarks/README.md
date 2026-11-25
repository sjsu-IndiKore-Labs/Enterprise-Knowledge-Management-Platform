# Chunking benchmark

Run from the repository root:

```bash
python vedanti_ai/eval/run_benchmark.py
```

The runner compares fixed, section-aware, and page-aware chunking at `topK`
4, 6, and 8 against the golden set. It uses deterministic fake embeddings and
writes generated JSON to `vedanti_ai/eval/benchmarks/latest.json` by default.
Generated results are local artifacts; rerun the final comparison with the
configured OpenAI embedding model before the report is finalized.

The current offline baseline selected for development is fixed/page-aware
chunking with `topK=6` (Recall@6: `0.9677`).
