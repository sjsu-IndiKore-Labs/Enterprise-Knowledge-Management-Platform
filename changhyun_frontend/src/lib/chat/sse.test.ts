// @vitest-environment node
import { describe, expect, it } from 'vitest';
import { readSse, SseParser } from './sse';

const SAMPLE =
  'event: meta\ndata: {"queryId":"q"}\n\n' +
  ': keep-alive\n\n' +
  'event: token\ndata: {"text":"Hello "}\n\n' +
  'event: done\ndata: {"answered":true}\n\n';

describe('SseParser', () => {
  it('parses complete events and skips comment lines', () => {
    const out = new SseParser().push(SAMPLE);
    expect(out.map((m) => m.event)).toEqual(['meta', 'token', 'done']);
    expect(JSON.parse(out[1].data)).toEqual({ text: 'Hello ' });
  });

  it('gives the same result for every possible split of the stream', () => {
    const whole = new SseParser().push(SAMPLE);
    for (let cut = 1; cut < SAMPLE.length; cut++) {
      const p = new SseParser();
      const got = [...p.push(SAMPLE.slice(0, cut)), ...p.push(SAMPLE.slice(cut))];
      expect(got).toEqual(whole);
    }
  });

  it('works one character at a time', () => {
    const p = new SseParser();
    const got = [...SAMPLE].flatMap((ch) => p.push(ch));
    expect(got).toHaveLength(3);
  });

  it('accepts \\r\\n and \\r line ends, including a \\r\\n split between chunks', () => {
    const crlf = new SseParser().push('event: token\r\ndata: {"text":"a"}\r\n\r\n');
    expect(crlf).toEqual([{ event: 'token', data: '{"text":"a"}' }]);
    // a lone "\r" at the very end is held back (it could be half of "\r\n"), so more text follows here
    const cr = new SseParser().push('event: token\rdata: x\r\revent: next');
    expect(cr).toEqual([{ event: 'token', data: 'x' }]);
    const p = new SseParser();
    expect([...p.push('event: token\r'), ...p.push('\ndata: y\r'), ...p.push('\n\r'), ...p.push('\n')]).toEqual([
      { event: 'token', data: 'y' },
    ]);
  });

  it('joins several data lines with a newline and defaults the event name to "message"', () => {
    expect(new SseParser().push('data: one\ndata: two\n\n')).toEqual([{ event: 'message', data: 'one\ntwo' }]);
  });

  it('keeps spaces after the first one and tolerates "data:" without a space', () => {
    expect(new SseParser().push('data:  indented\n\n')[0].data).toBe(' indented');
    expect(new SseParser().push('data:tight\n\n')[0].data).toBe('tight');
  });

  it('drops an event that never got its blank line, and events without data', () => {
    const p = new SseParser();
    expect(p.push('event: token\ndata: {"text":"cut')).toEqual([]);
    expect(new SseParser().push('event: ping\n\n')).toEqual([]);
  });

  it('does not leak the event name into the next event', () => {
    const out = new SseParser().push('event: token\ndata: 1\n\ndata: 2\n\n');
    expect(out.map((m) => m.event)).toEqual(['token', 'message']);
  });
});

describe('readSse', () => {
  const bodyOf = (parts: Uint8Array[]) =>
    new ReadableStream<Uint8Array>({
      start(controller) {
        for (const part of parts) controller.enqueue(part);
        controller.close();
      },
    });

  it('decodes multi-byte characters that are split across chunks', async () => {
    const bytes = new TextEncoder().encode('event: token\ndata: {"text":"안녕"}\n\n');
    const cut = bytes.length - 6; // inside the second Korean character
    const got: string[] = [];
    for await (const m of readSse(bodyOf([bytes.slice(0, cut), bytes.slice(cut)]))) got.push(m.data);
    expect(got).toEqual(['{"text":"안녕"}']);
  });

  it('yields events in order until the stream closes', async () => {
    const enc = new TextEncoder();
    const names: string[] = [];
    for await (const m of readSse(bodyOf([enc.encode(SAMPLE.slice(0, 40)), enc.encode(SAMPLE.slice(40))]))) names.push(m.event);
    expect(names).toEqual(['meta', 'token', 'done']);
  });
});
