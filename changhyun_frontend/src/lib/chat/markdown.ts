// A small, safe Markdown subset for assistant answers: paragraphs, headings, bold, italic, inline code,
// code blocks, bullet and numbered lists, quotes, simple tables, http(s)/mailto links, and [n] citation markers.
//
// It produces a plain data tree (no HTML strings), and the React renderer turns that tree into elements.
// Raw HTML in the answer is just text, and links with other schemes (javascript:, data:) stay text,
// so nothing the model writes can run script in the portal.

export type Inline =
  | { t: 'text'; text: string }
  | { t: 'code'; text: string }
  | { t: 'strong'; children: Inline[] }
  | { t: 'em'; children: Inline[] }
  | { t: 'link'; href: string; children: Inline[] }
  | { t: 'cite'; n: number };

export type Block =
  | { t: 'p'; children: Inline[] }
  | { t: 'h'; level: 1 | 2 | 3 | 4 | 5 | 6; children: Inline[] }
  | { t: 'ul'; items: Inline[][] }
  | { t: 'ol'; start: number; items: Inline[][] }
  | { t: 'code'; text: string; lang: string }
  | { t: 'quote'; children: Inline[] }
  | { t: 'table'; head: Inline[][]; rows: Inline[][][] };

// Order matters: code first (its content is literal), then links before [n] so "[1](x)" is a link.
const INLINE =
  /(`[^`\n]+`)|(\[([^\]\n]+)\]\(((?:https?:\/\/|mailto:)[^\s)]+)\))|(\[(\d{1,3})\])|(\*\*([^*\n]+?)\*\*)|(\*([^*\s][^*\n]*?)\*)|((?<![A-Za-z0-9])_([^_\s][^_\n]*?)_(?![A-Za-z0-9]))/g;

export function parseInline(text: string): Inline[] {
  const out: Inline[] = [];
  let last = 0;
  for (const m of text.matchAll(INLINE)) {
    const index = m.index ?? 0;
    if (index > last) out.push({ t: 'text', text: text.slice(last, index) });
    if (m[1]) out.push({ t: 'code', text: m[1].slice(1, -1) });
    else if (m[2]) out.push({ t: 'link', href: m[4], children: parseInline(m[3]) });
    else if (m[5]) out.push({ t: 'cite', n: Number(m[6]) });
    else if (m[7]) out.push({ t: 'strong', children: parseInline(m[8]) });
    else if (m[9]) out.push({ t: 'em', children: parseInline(m[10]) });
    else if (m[11]) out.push({ t: 'em', children: parseInline(m[12]) });
    last = index + m[0].length;
  }
  if (last < text.length) out.push({ t: 'text', text: text.slice(last) });
  return out;
}

const FENCE = /^\s*```\s*([\w+-]*)\s*$/;
const HEADING = /^(#{1,6})\s+(.*?)\s*#*\s*$/;
const UL = /^\s*[-*+]\s+(.*)$/;
const OL = /^\s*(\d{1,3})[.)]\s+(.*)$/;
const QUOTE = /^\s*>\s?(.*)$/;
const TABLE_SEP = /^\s*\|?\s*:?-{2,}:?\s*(\|\s*:?-{2,}:?\s*)*\|?\s*$/;

const isBlockStart = (line: string, next: string | undefined): boolean =>
  FENCE.test(line) || HEADING.test(line) || UL.test(line) || OL.test(line) || QUOTE.test(line) || isTableStart(line, next);

function isTableStart(line: string, next: string | undefined): boolean {
  return line.includes('|') && next !== undefined && next.includes('-') && TABLE_SEP.test(next);
}

function splitRow(line: string): string[] {
  return line
    .trim()
    .replace(/^\|/, '')
    .replace(/\|$/, '')
    .split('|')
    .map((c) => c.trim());
}

/** An unclosed code fence (a streamed answer that is still arriving) is shown as code so far. */
export function parseMarkdown(source: string): Block[] {
  const lines = source.replace(/\r\n?/g, '\n').split('\n');
  const blocks: Block[] = [];
  let i = 0;
  while (i < lines.length) {
    const line = lines[i];
    if (line.trim() === '') {
      i++;
      continue;
    }

    const fence = FENCE.exec(line);
    if (fence) {
      const body: string[] = [];
      i++;
      while (i < lines.length && !FENCE.test(lines[i])) body.push(lines[i++]);
      i++; // closing fence (or the end of text)
      blocks.push({ t: 'code', text: body.join('\n'), lang: fence[1] });
      continue;
    }

    const h = HEADING.exec(line);
    if (h) {
      blocks.push({ t: 'h', level: h[1].length as 1 | 2 | 3 | 4 | 5 | 6, children: parseInline(h[2]) });
      i++;
      continue;
    }

    if (isTableStart(line, lines[i + 1])) {
      const head = splitRow(line).map(parseInline);
      i += 2;
      const rows: Inline[][][] = [];
      while (i < lines.length && lines[i].includes('|') && lines[i].trim() !== '') rows.push(splitRow(lines[i++]).map(parseInline));
      blocks.push({ t: 'table', head, rows });
      continue;
    }

    if (UL.test(line)) {
      const items: Inline[][] = [];
      while (i < lines.length && UL.test(lines[i])) items.push(parseInline((UL.exec(lines[i++]) as RegExpExecArray)[1]));
      blocks.push({ t: 'ul', items });
      continue;
    }

    const ol = OL.exec(line);
    if (ol) {
      const items: Inline[][] = [];
      const start = Number(ol[1]);
      while (i < lines.length && OL.test(lines[i])) items.push(parseInline((OL.exec(lines[i++]) as RegExpExecArray)[2]));
      blocks.push({ t: 'ol', start, items });
      continue;
    }

    if (QUOTE.test(line)) {
      const parts: string[] = [];
      while (i < lines.length && QUOTE.test(lines[i])) parts.push((QUOTE.exec(lines[i++]) as RegExpExecArray)[1]);
      blocks.push({ t: 'quote', children: parseInline(parts.join(' ')) });
      continue;
    }

    const para: string[] = [line.trim()];
    i++;
    while (i < lines.length && lines[i].trim() !== '' && !isBlockStart(lines[i], lines[i + 1])) para.push(lines[i++].trim());
    blocks.push({ t: 'p', children: parseInline(para.join(' ')) });
  }
  return blocks;
}

/** While streaming, hide a half-written marker such as "[" or "[1" at the end of the text. */
export const hidePartialMarker = (text: string): string => text.replace(/\[\d{0,3}$/, '');
