import { describe, expect, it } from 'vitest';
import { ApiError, toMessage, unwrap, unwrapEmpty } from './errors';

const res = (status: number) => new Response(null, { status });

describe('unwrap', () => {
  it('returns data on success', () => {
    expect(unwrap({ data: { ok: 1 }, response: res(200) })).toEqual({ ok: 1 });
  });

  it('throws ApiError carrying code, message and traceId from the shared error body', () => {
    try {
      unwrap({
        error: { code: 'DOCUMENT_NOT_FOUND', message: 'No such document', traceId: 't-1' },
        response: res(404),
      });
      throw new Error('should have thrown');
    } catch (e) {
      expect(e).toBeInstanceOf(ApiError);
      const err = e as ApiError;
      expect(err.status).toBe(404);
      expect(err.code).toBe('DOCUMENT_NOT_FOUND');
      expect(err.message).toBe('No such document');
      expect(err.traceId).toBe('t-1');
    }
  });

  it('falls back to a friendly default when the body is missing', () => {
    expect(() => unwrap({ error: undefined, response: res(502) })).toThrow(/unavailable/);
  });
});

describe('unwrapEmpty / toMessage', () => {
  it('accepts 204', () => {
    expect(() => unwrapEmpty({ response: res(204) })).not.toThrow();
  });
  it('maps unknown errors to a generic message', () => {
    expect(toMessage('boom')).toMatch(/Something went wrong/);
    expect(toMessage(new ApiError(0, undefined))).toMatch(/Cannot reach the server/);
  });
});
