import { ApiError, type ApiErrorBody } from '@/lib/api/errors';
import type { ChatRequest, Citation } from '@/lib/api/types';
import { emitUnauthorized, getToken } from '@/lib/auth/token';
import { config } from '@/lib/config';
import { readSse, type SseMessage } from './sse';

/** The events of contracts/streaming.md. SSE is not described in OpenAPI, so these are written by hand. */
export type StreamEvent =
  | { type: 'meta'; queryId: string; conversationId: string; messageId: string }
  | { type: 'token'; text: string }
  | { type: 'citations'; citations: Citation[] }
  | { type: 'done'; answered: boolean; confidence: number; latencyMs: number }
  | { type: 'error'; code: string; message: string; traceId?: string };

const BAD_EVENT: StreamEvent = {
  type: 'error',
  code: 'BAD_STREAM',
  message: 'The assistant sent an answer the portal could not read. Please try again.',
};

/** Maps one raw SSE message to a typed event. Unknown event names are ignored (forward compatible). */
export function toStreamEvent(message: SseMessage): StreamEvent | null {
  const known = ['meta', 'token', 'citations', 'done', 'error'];
  if (!known.includes(message.event)) return null;
  let data: Record<string, unknown>;
  try {
    const parsed: unknown = JSON.parse(message.data);
    if (!parsed || typeof parsed !== 'object') return BAD_EVENT;
    data = parsed as Record<string, unknown>;
  } catch {
    return BAD_EVENT;
  }
  switch (message.event) {
    case 'meta':
      return {
        type: 'meta',
        queryId: String(data.queryId ?? ''),
        conversationId: String(data.conversationId ?? ''),
        messageId: String(data.messageId ?? ''),
      };
    case 'token':
      return { type: 'token', text: typeof data.text === 'string' ? data.text : '' };
    case 'citations':
      return { type: 'citations', citations: Array.isArray(data.citations) ? (data.citations as Citation[]) : [] };
    case 'done':
      return {
        type: 'done',
        answered: data.answered === true,
        confidence: Number(data.confidence ?? 0),
        latencyMs: Number(data.latencyMs ?? 0),
      };
    default:
      return {
        type: 'error',
        code: typeof data.code === 'string' ? data.code : 'STREAM_ERROR',
        message: typeof data.message === 'string' && data.message ? data.message : BAD_EVENT.message,
        traceId: typeof data.traceId === 'string' ? data.traceId : undefined,
      };
  }
}

interface StreamOptions {
  signal?: AbortSignal;
  onEvent: (event: StreamEvent) => void;
}

/**
 * POST /api/v1/chat/stream and report each event as it arrives.
 * Resolves after `done` or `error`. Rejects with ApiError when the request itself fails
 * (HTTP error, no connection, or the connection closed before `done`), and with an
 * AbortError when `signal` is aborted (the Stop button).
 */
export async function streamChat(body: ChatRequest, { signal, onEvent }: StreamOptions): Promise<void> {
  const token = getToken();
  let response: Response;
  try {
    response = await fetch(`${config.apiBaseUrl}/api/v1/chat/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify(body),
      signal,
    });
  } catch (err) {
    if (isAbort(err)) throw err;
    throw new ApiError(0, undefined);
  }

  if (!response.ok) {
    if (response.status === 401) emitUnauthorized();
    let errorBody: Partial<ApiErrorBody> | undefined;
    try {
      errorBody = (await response.json()) as Partial<ApiErrorBody>;
    } catch {
      errorBody = undefined;
    }
    throw new ApiError(response.status, errorBody);
  }
  if (!response.body) throw new ApiError(502, undefined, 'The server did not send a stream.');

  try {
    for await (const message of readSse(response.body)) {
      const event = toStreamEvent(message);
      if (!event) continue;
      onEvent(event);
      if (event.type === 'done' || event.type === 'error') return;
    }
  } catch (err) {
    if (isAbort(err)) throw err;
    throw new ApiError(0, undefined, 'The connection was lost before the answer finished.');
  }
  throw new ApiError(0, undefined, 'The connection was closed before the answer finished.');
}

export function isAbort(err: unknown): boolean {
  return err instanceof DOMException ? err.name === 'AbortError' : err instanceof Error && err.name === 'AbortError';
}
