import { splitHighlight } from '@/lib/highlight';

/** Renders text with query terms wrapped in <mark>. Built from segments, never innerHTML. */
export function Highlighted({ text, query }: { text: string; query: string }) {
  return (
    <>
      {splitHighlight(text, query).map((seg, i) =>
        seg.match ? <mark key={i}>{seg.text}</mark> : <span key={i}>{seg.text}</span>,
      )}
    </>
  );
}
