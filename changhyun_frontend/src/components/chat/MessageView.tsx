'use client';

import Link from 'next/link';
import type { Citation, FeedbackRating } from '@/lib/api/types';
import { canRate, type Turn } from '@/lib/chat/state';
import { citationLabel } from '@/lib/format';
import { Markdown } from './Markdown';

interface Props {
  turn: Turn;
  /** Only the last answer can be retried. */
  isLast: boolean;
  onOpenCitation: (citation: Citation) => void;
  onRate: (turn: Turn, rating: FeedbackRating) => void;
  onRetry: () => void;
}

const NOT_FOUND = "I couldn't find this in the documents you have access to.";

function Thumb({ down }: { down?: boolean }) {
  return (
    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" className={down ? 'rotate-180' : undefined}>
      <path d="M14 9V5a3 3 0 0 0-3-3l-4 9v11h11.28a2 2 0 0 0 2-1.7l1.38-9a2 2 0 0 0-2-2.3zM7 22H4a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2h3" />
    </svg>
  );
}

function CitationBadge({ n, citation, onOpen }: { n: number; citation?: Citation; onOpen: (c: Citation) => void }) {
  const base = 'mx-0.5 inline-flex h-5 min-w-5 items-center justify-center rounded-full px-1.5 align-baseline text-xs font-semibold';
  if (!citation) {
    // The sources arrive after the text, so a marker is inactive until its citation is known.
    return (
      <span className={`${base} bg-surface-2 text-muted`} title="Source not available yet">
        {n}
      </span>
    );
  }
  return (
    <button
      type="button"
      onClick={() => onOpen(citation)}
      className={`${base} bg-accent-soft text-accent hover:bg-accent hover:text-accent-ink`}
      aria-label={`Source ${n}: ${citationLabel(citation)}`}
      title={citationLabel(citation)}
    >
      {n}
    </button>
  );
}

export function MessageView({ turn, isLast, onOpenCitation, onRate, onRetry }: Props) {
  if (turn.role === 'USER') {
    return (
      <div className="flex justify-end">
        <div className="max-w-[85%] whitespace-pre-wrap break-words rounded-2xl bg-accent-soft px-4 py-2.5 text-sm">{turn.content}</div>
      </div>
    );
  }

  const streaming = turn.status === 'streaming';
  const notFound = turn.status === 'done' && turn.answered === false;
  const renderCite = (n: number, key: string) => (
    <CitationBadge key={key} n={n} citation={turn.citations.find((c) => c.citationNumber === n)} onOpen={onOpenCitation} />
  );

  return (
    <div className="flex">
      <div className="min-w-0 max-w-[92%] rounded-2xl border border-line bg-surface px-4 py-3">
        <div className="mb-1 text-xs font-medium text-muted">Assistant</div>

        {streaming && turn.content === '' && (
          <p className="text-sm text-muted motion-safe:animate-pulse" role="status">
            Searching your documents…
          </p>
        )}

        {notFound ? (
          <div className="rounded-lg border border-warn bg-warn-soft px-3 py-2.5 text-sm">
            <p className="font-medium">{turn.content || NOT_FOUND}</p>
            <p className="mt-1 text-muted">Try other keywords, search the document library, or ask the team that owns the topic.</p>
            <div className="mt-2 flex flex-wrap gap-2">
              <Link href="/search" className="btn">
                Search documents
              </Link>
              <Link href="/documents" className="btn">
                Browse documents
              </Link>
            </div>
          </div>
        ) : (
          turn.content !== '' && (
            <div>
              <Markdown text={turn.content} streaming={streaming} renderCite={renderCite} />
              {streaming && <span className="ml-0.5 inline-block h-4 w-1.5 translate-y-0.5 bg-muted motion-safe:animate-pulse" aria-hidden="true" />}
            </div>
          )
        )}

        {turn.status === 'stopped' && <p className="mt-2 text-xs text-muted">Stopped. The answer above is incomplete.</p>}

        {turn.status === 'error' && (
          <div className="mt-2 rounded-lg border border-bad bg-bad-soft px-3 py-2 text-sm" role="alert">
            <p>{turn.error ?? 'Something went wrong. Please try again.'}</p>
            {isLast && (
              <button type="button" className="btn mt-2" onClick={onRetry}>
                Try again
              </button>
            )}
          </div>
        )}

        {turn.status === 'stopped' && isLast && (
          <button type="button" className="btn mt-2" onClick={onRetry}>
            Ask again
          </button>
        )}

        {turn.status === 'done' && turn.citations.length > 0 && !notFound && (
          <div className="mt-3 border-t border-line pt-2">
            <h4 className="mb-1 text-xs font-semibold uppercase tracking-wide text-muted">Sources</h4>
            <ol className="flex flex-wrap gap-1.5">
              {turn.citations.map((c) => (
                <li key={c.chunkId}>
                  <button
                    type="button"
                    onClick={() => onOpenCitation(c)}
                    className="rounded-md border border-line px-2 py-1 text-left text-xs hover:bg-surface-2"
                    title={c.snippet}
                  >
                    <span className="mr-1 font-semibold text-accent">[{c.citationNumber}]</span>
                    {citationLabel(c)}
                  </button>
                </li>
              ))}
            </ol>
          </div>
        )}

        {canRate(turn) && (
          <div className="mt-2 flex items-center gap-1" role="group" aria-label="Was this answer helpful?">
            {(['UP', 'DOWN'] as const).map((rating) => (
              <button
                key={rating}
                type="button"
                onClick={() => onRate(turn, rating)}
                aria-pressed={turn.feedback === rating}
                aria-label={rating === 'UP' ? 'Helpful' : 'Not helpful'}
                title={rating === 'UP' ? 'Helpful' : 'Not helpful'}
                className={`rounded-md p-1.5 hover:bg-surface-2 ${turn.feedback === rating ? 'bg-accent-soft text-accent' : 'text-muted'}`}
              >
                <Thumb down={rating === 'DOWN'} />
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
