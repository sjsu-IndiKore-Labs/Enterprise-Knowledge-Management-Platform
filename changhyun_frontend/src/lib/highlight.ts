export interface Segment {
  text: string;
  match: boolean;
}

const escapeRegExp = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

/** Query terms worth highlighting: 2+ characters, de-duplicated, case-insensitive. */
export function queryTerms(query: string): string[] {
  const seen = new Set<string>();
  return query
    .split(/\s+/)
    .map((t) => t.replace(/^[^\p{L}\p{N}]+|[^\p{L}\p{N}]+$/gu, ''))
    .filter((t) => {
      const key = t.toLowerCase();
      if (t.length < 2 || seen.has(key)) return false;
      seen.add(key);
      return true;
    });
}

/** Shortens long text to about `max` characters, centered on the first query match when there is one. */
export function excerpt(text: string, query: string, max = 300): string {
  if (text.length <= max) return text;
  const lower = text.toLowerCase();
  const hit = queryTerms(query)
    .map((t) => lower.indexOf(t.toLowerCase()))
    .filter((i) => i >= 0)
    .sort((a, b) => a - b)[0];
  const start = hit === undefined ? 0 : Math.max(0, Math.min(hit - Math.floor(max / 3), text.length - max));
  const end = Math.min(text.length, start + max);
  return `${start > 0 ? '…' : ''}${text.slice(start, end).trim()}${end < text.length ? '…' : ''}`;
}

/** Splits text into plain and matching segments so it can be rendered as <mark> without innerHTML. */
export function splitHighlight(text: string, query: string): Segment[] {
  const terms = queryTerms(query);
  if (!text) return [];
  if (terms.length === 0) return [{ text, match: false }];
  // Longest first so "benefits" wins over "benefit".
  const pattern = new RegExp(
    `(${terms
      .sort((a, b) => b.length - a.length)
      .map(escapeRegExp)
      .join('|')})`,
    'gi',
  );
  // split() with one capture group puts the matches at odd indexes; map before dropping empties.
  return text
    .split(pattern)
    .map((part, i) => ({ text: part, match: i % 2 === 1 }))
    .filter((seg) => seg.text !== '');
}
