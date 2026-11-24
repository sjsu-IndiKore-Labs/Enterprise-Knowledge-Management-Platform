import { describe, expect, it } from 'vitest';
import { hidePartialMarker, parseInline, parseMarkdown, type Inline } from './markdown';

const kinds = (nodes: Inline[]) => nodes.map((n) => n.t);

describe('parseInline', () => {
  it('finds citation markers in plain text', () => {
    expect(parseInline('Carry over 5 days [1] and sick leave does not [2].')).toEqual([
      { t: 'text', text: 'Carry over 5 days ' },
      { t: 'cite', n: 1 },
      { t: 'text', text: ' and sick leave does not ' },
      { t: 'cite', n: 2 },
      { t: 'text', text: '.' },
    ]);
  });

  it('handles bold with a citation inside, italic and inline code', () => {
    const nodes = parseInline('**Max 5 days [1]** is *the cap*, set by `PTO_CAP`.');
    expect(kinds(nodes)).toEqual(['strong', 'text', 'em', 'text', 'code', 'text']);
    expect(nodes[0]).toEqual({ t: 'strong', children: [{ t: 'text', text: 'Max 5 days ' }, { t: 'cite', n: 1 }] });
    expect(nodes[4]).toEqual({ t: 'code', text: 'PTO_CAP' });
  });

  it('does not treat snake_case words or list-like asterisks as emphasis', () => {
    expect(parseInline('Set max_upload_mb and other_value now')).toEqual([{ t: 'text', text: 'Set max_upload_mb and other_value now' }]);
    expect(kinds(parseInline('2 * 3 * 4'))).toEqual(['text']);
  });

  it('turns http(s) and mailto links into links', () => {
    expect(parseInline('See [the policy](https://example.com/p?a=1).')[1]).toEqual({
      t: 'link',
      href: 'https://example.com/p?a=1',
      children: [{ t: 'text', text: 'the policy' }],
    });
    expect(parseInline('[mail](mailto:hr@example.com)')[0]).toMatchObject({ t: 'link', href: 'mailto:hr@example.com' });
  });

  it('keeps links with other schemes as plain text (no javascript: or data: links)', () => {
    for (const bad of ['[x](javascript:alert(1))', '[x](data:text/html;base64,AAAA)', '[x](//evil.example)']) {
      expect(parseInline(bad).some((n) => n.t === 'link')).toBe(false);
    }
  });

  it('keeps raw HTML as text', () => {
    expect(parseInline('<img src=x onerror=alert(1)> <b>hi</b>')).toEqual([{ t: 'text', text: '<img src=x onerror=alert(1)> <b>hi</b>' }]);
  });

  it('does not read a link as a citation: [1](https://x.example) is a link', () => {
    expect(parseInline('[1](https://x.example)')[0]).toMatchObject({ t: 'link' });
  });
});

describe('parseMarkdown', () => {
  it('splits paragraphs on blank lines and joins soft line breaks', () => {
    const blocks = parseMarkdown('First line\nsecond line\n\nNext paragraph');
    expect(blocks).toHaveLength(2);
    expect(blocks[0]).toEqual({ t: 'p', children: [{ t: 'text', text: 'First line second line' }] });
  });

  it('parses headings, bullet lists and numbered lists', () => {
    const blocks = parseMarkdown('## Steps\n\n1. Open the form\n2. Submit it\n\n- one [1]\n- two');
    expect(blocks.map((b) => b.t)).toEqual(['h', 'ol', 'ul']);
    expect(blocks[1]).toMatchObject({ t: 'ol', start: 1 });
    expect((blocks[1] as { items: unknown[] }).items).toHaveLength(2);
    expect((blocks[2] as { items: Inline[][] }).items[0]).toEqual([{ t: 'text', text: 'one ' }, { t: 'cite', n: 1 }]);
  });

  it('ends a paragraph where a list starts', () => {
    expect(parseMarkdown('Related details\n- a\n- b').map((b) => b.t)).toEqual(['p', 'ul']);
  });

  it('keeps code fences literal, also while the closing fence has not arrived yet', () => {
    expect(parseMarkdown('```sh\nmake up\n```\nDone')).toEqual([
      { t: 'code', text: 'make up', lang: 'sh' },
      { t: 'p', children: [{ t: 'text', text: 'Done' }] },
    ]);
    expect(parseMarkdown('```\n**not bold** [1]')[0]).toEqual({ t: 'code', text: '**not bold** [1]', lang: '' });
  });

  it('parses a simple table with inline content in cells', () => {
    const [table] = parseMarkdown('| Meal | Limit |\n|---|---|\n| Lunch | **$25** [1] |\n| Dinner | $50 |');
    expect(table.t).toBe('table');
    const t = table as { head: Inline[][]; rows: Inline[][][] };
    expect(t.head).toHaveLength(2);
    expect(t.rows).toHaveLength(2);
    expect(kinds(t.rows[0][1])).toEqual(['strong', 'text', 'cite']);
  });

  it('turns quotes into quote blocks', () => {
    expect(parseMarkdown('> Policy text\n> continues')[0]).toEqual({ t: 'quote', children: [{ t: 'text', text: 'Policy text continues' }] });
  });

  it('returns nothing for empty text and survives half-written markdown', () => {
    expect(parseMarkdown('')).toEqual([]);
    expect(() => parseMarkdown('**bold never closed [1')).not.toThrow();
    expect(() => parseMarkdown('| a | b |\n|--')).not.toThrow();
  });
});

describe('hidePartialMarker', () => {
  it('hides a marker that is still being written', () => {
    expect(hidePartialMarker('Up to 5 days [')).toBe('Up to 5 days ');
    expect(hidePartialMarker('Up to 5 days [1')).toBe('Up to 5 days ');
    expect(hidePartialMarker('Up to 5 days [1]')).toBe('Up to 5 days [1]');
    expect(hidePartialMarker('a [12')).toBe('a ');
  });
});
