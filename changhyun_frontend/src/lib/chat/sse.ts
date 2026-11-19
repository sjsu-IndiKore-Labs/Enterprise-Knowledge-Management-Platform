// Server-Sent Events parser for fetch() response bodies (contracts/streaming.md).
// We cannot use EventSource: the request is a POST with a JSON body and an Authorization header.

export interface SseMessage {
  event: string;
  data: string;
}

/**
 * Incremental parser: feed it text as it arrives, get back the complete messages.
 * Handles chunks that split a line or an event anywhere, \n, \r\n and \r line ends,
 * `:` comment lines (keep-alive), and ignores `id:` / `retry:`.
 * An event is only delivered once its terminating blank line arrived; a half-sent event is dropped.
 */
export class SseParser {
  private buffer = '';
  private event = '';
  private data: string[] = [];

  push(chunk: string): SseMessage[] {
    this.buffer += chunk;
    const out: SseMessage[] = [];
    for (;;) {
      // A lone "\r" at the very end may be the first half of "\r\n": wait for the next chunk.
      const m = /\r\n|\n|\r(?!$)/.exec(this.buffer);
      if (!m) break;
      const line = this.buffer.slice(0, m.index);
      this.buffer = this.buffer.slice(m.index + m[0].length);
      const message = this.line(line);
      if (message) out.push(message);
    }
    return out;
  }

  private line(line: string): SseMessage | null {
    if (line === '') {
      const hasData = this.data.length > 0;
      const message = hasData ? { event: this.event || 'message', data: this.data.join('\n') } : null;
      this.event = '';
      this.data = [];
      return message;
    }
    if (line.startsWith(':')) return null; // comment
    const colon = line.indexOf(':');
    const field = colon === -1 ? line : line.slice(0, colon);
    let value = colon === -1 ? '' : line.slice(colon + 1);
    if (value.startsWith(' ')) value = value.slice(1);
    if (field === 'event') this.event = value;
    else if (field === 'data') this.data.push(value);
    return null;
  }
}

/** Yields the messages of an SSE response body until the server closes it. */
export async function* readSse(body: ReadableStream<Uint8Array>): AsyncGenerator<SseMessage> {
  const reader = body.getReader();
  const decoder = new TextDecoder();
  const parser = new SseParser();
  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      for (const message of parser.push(decoder.decode(value, { stream: true }))) yield message;
    }
  } finally {
    try {
      reader.releaseLock();
    } catch {
      // a read was still pending (aborted stream): nothing left to release
    }
  }
}
