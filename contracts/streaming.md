# Streaming chat answers (Server-Sent Events)

Endpoint: `POST /api/v1/chat/stream` on core-api (body = `ChatRequest`, see core-api.yaml),
response `Content-Type: text/event-stream`. ai-service exposes the **same event format** on
`POST /internal/v1/answer/stream`, so core-api can relay events almost unchanged.

> The browser must use `fetch()` + `ReadableStream` (not `EventSource`), because we POST a body and send the Authorization header.

## Events (in order)

```
event: meta
data: {"queryId":"…","conversationId":"…","messageId":"…"}

event: token
data: {"text":"According to the PTO policy, "}

event: token
data: {"text":"employees accrue 1.67 days per month [1]."}

event: citations
data: {"citations":[{"citationNumber":1,"documentId":"…","documentTitle":"PTO and Leave Policy","version":1,"pageNumber":1,"chunkId":"…","snippet":"Full-time employees accrue…","score":0.83}]}

event: done
data: {"answered":true,"confidence":0.83,"latencyMs":2140}
```

| Event | When | Data |
|---|---|---|
| `meta` | first; core-api only (ai-service skips it) | `queryId`, `conversationId`, `messageId` |
| `token` | 0..n times | `text` (append to the answer) |
| `citations` | once, after the last token | `citations: Citation[]` (may be empty) |
| `done` | last on success | `answered` (false → knowledge gap), `confidence`, `latencyMs` |
| `error` | instead of `done` on failure | standard error body (`code`, `message`, `traceId`) |

Rules:
- Citation markers in the text are `[n]` where `n` = `citationNumber`. The UI turns them into clickable badges.
- If no chunk passes `RAG_MIN_SIMILARITY`, the answer is exactly:
  `I couldn't find this in the documents you have access to.` with `answered:false` and empty citations.
- Each `data:` is a single line of JSON. Events are separated by a blank line. core-api sends a `: keep-alive` comment every 15 s.
