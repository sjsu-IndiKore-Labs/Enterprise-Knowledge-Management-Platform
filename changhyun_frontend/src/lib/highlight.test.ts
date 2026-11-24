import { describe, expect, it } from 'vitest';
import { excerpt, queryTerms, splitHighlight } from './highlight';

describe('excerpt', () => {
  it('returns short text unchanged', () => {
    expect(excerpt('short text', 'text')).toBe('short text');
  });
  it('centers long text on the first match and adds ellipses', () => {
    const text = `${'a '.repeat(300)}needle${' b'.repeat(300)}`;
    const out = excerpt(text, 'needle', 100);
    expect(out).toContain('needle');
    expect(out.startsWith('…')).toBe(true);
    expect(out.endsWith('…')).toBe(true);
    expect(out.length).toBeLessThanOrEqual(104);
  });
  it('starts at the beginning when there is no match', () => {
    const out = excerpt('x'.repeat(500), 'zzz', 50);
    expect(out.startsWith('…')).toBe(false);
    expect(out.endsWith('…')).toBe(true);
  });
});

describe('queryTerms', () => {
  it('drops short and duplicate terms and surrounding punctuation', () => {
    expect(queryTerms('PTO, a  pto "carry-over"?')).toEqual(['PTO', 'carry-over']);
  });
});

describe('splitHighlight', () => {
  it('marks matches case-insensitively and keeps the original casing', () => {
    expect(splitHighlight('Annual Leave policy', 'leave')).toEqual([
      { text: 'Annual ', match: false },
      { text: 'Leave', match: true },
      { text: ' policy', match: false },
    ]);
  });

  it('handles a match at the very start and end', () => {
    expect(splitHighlight('pto and pto', 'pto')).toEqual([
      { text: 'pto', match: true },
      { text: ' and ', match: false },
      { text: 'pto', match: true },
    ]);
  });

  it('prefers the longest term', () => {
    const segs = splitHighlight('benefits', 'benefit benefits');
    expect(segs).toEqual([{ text: 'benefits', match: true }]);
  });

  it('escapes regex characters in the query', () => {
    // "." must be literal: it may not match the "x" in "v5x0".
    expect(splitHighlight('v5x0 and v5.0', '5.0')).toEqual([
      { text: 'v5x0 and v', match: false },
      { text: '5.0', match: true },
    ]);
  });

  it('returns the text unchanged when there is nothing to highlight', () => {
    expect(splitHighlight('hello', '  ')).toEqual([{ text: 'hello', match: false }]);
    expect(splitHighlight('', 'x1')).toEqual([]);
  });
});
