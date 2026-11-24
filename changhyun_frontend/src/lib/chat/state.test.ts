import { describe, expect, it } from 'vitest';
import type { Citation } from '@/lib/api/types';
import { applyEvent, canRate, fromMessage } from './state';
import { pendingAnswer } from './state';
import { toStreamEvent, type StreamEvent } from './stream';

const cite: Citation = {
  citationNumber: 1,
  documentId: 'd1',
  documentTitle: 'PTO and Leave Policy',
  version: 1,
  pageNumber: 1,
  chunkId: 'c1',
  snippet: 'Employees may carry over…',
  score: 0.83,
};

const replay = (events: StreamEvent[]) => events.reduce(applyEvent, pendingAnswer());

describe('applyEvent', () => {
  it('builds an answer from meta, tokens, citations and done', () => {
    const turn = replay([
      { type: 'meta', queryId: 'q', conversationId: 'c', messageId: 'm1' },
      { type: 'token', text: 'Up to 5 days ' },
      { type: 'token', text: 'carry over [1].' },
      { type: 'citations', citations: [cite] },
      { type: 'done', answered: true, confidence: 0.8, latencyMs: 12 },
    ]);
    expect(turn).toMatchObject({ content: 'Up to 5 days carry over [1].', status: 'done', answered: true, messageId: 'm1' });
    expect(turn.citations).toEqual([cite]);
    expect(canRate(turn)).toBe(true);
  });

  it('stays streaming and cannot be rated until done', () => {
    const turn = replay([{ type: 'meta', queryId: 'q', conversationId: 'c', messageId: 'm1' }, { type: 'token', text: 'Hi' }]);
    expect(turn.status).toBe('streaming');
    expect(canRate(turn)).toBe(false);
  });

  it('keeps the partial text and shows the message when the stream errors', () => {
    const turn = replay([
      { type: 'token', text: 'Partial ' },
      { type: 'error', code: 'UPSTREAM_UNAVAILABLE', message: 'The assistant stopped unexpectedly.' },
    ]);
    expect(turn).toMatchObject({ status: 'error', content: 'Partial ', error: 'The assistant stopped unexpectedly.' });
    expect(canRate(turn)).toBe(false);
  });

  it('marks "not found" answers with answered=false', () => {
    const turn = replay([{ type: 'token', text: "I couldn't find this." }, { type: 'citations', citations: [] }, { type: 'done', answered: false, confidence: 0.1, latencyMs: 5 }]);
    expect(turn.answered).toBe(false);
  });
});

describe('toStreamEvent', () => {
  it('parses the events of contracts/streaming.md', () => {
    expect(toStreamEvent({ event: 'meta', data: '{"queryId":"q","conversationId":"c","messageId":"m"}' })).toEqual({ type: 'meta', queryId: 'q', conversationId: 'c', messageId: 'm' });
    expect(toStreamEvent({ event: 'token', data: '{"text":"Hi "}' })).toEqual({ type: 'token', text: 'Hi ' });
    expect(toStreamEvent({ event: 'citations', data: JSON.stringify({ citations: [cite] }) })).toEqual({ type: 'citations', citations: [cite] });
    expect(toStreamEvent({ event: 'done', data: '{"answered":false,"confidence":0.1,"latencyMs":9}' })).toEqual({ type: 'done', answered: false, confidence: 0.1, latencyMs: 9 });
    expect(toStreamEvent({ event: 'error', data: '{"code":"X","message":"Boom","traceId":"t"}' })).toEqual({ type: 'error', code: 'X', message: 'Boom', traceId: 't' });
  });

  it('ignores unknown events and turns unreadable data into an error event', () => {
    expect(toStreamEvent({ event: 'ping', data: '{}' })).toBeNull();
    expect(toStreamEvent({ event: 'token', data: '{"text":' })).toMatchObject({ type: 'error', code: 'BAD_STREAM' });
    expect(toStreamEvent({ event: 'token', data: '42' })).toMatchObject({ type: 'error', code: 'BAD_STREAM' });
  });
});

describe('fromMessage', () => {
  it('maps stored messages, including feedback and missing citations', () => {
    const a = fromMessage({ id: 'm1', role: 'ASSISTANT', content: 'Answer [1]', citations: [cite], answered: true, feedback: 'UP', createdAt: '2026-11-14T10:00:00Z' });
    expect(a).toMatchObject({ key: 'm1', messageId: 'm1', feedback: 'UP', status: 'done', answered: true });
    const u = fromMessage({ id: 'u1', role: 'USER', content: 'Question?', createdAt: '2026-11-14T10:00:00Z' });
    expect(u).toMatchObject({ messageId: null, citations: [], answered: null, feedback: null });
  });
});
