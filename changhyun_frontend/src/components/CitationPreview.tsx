'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { Highlighted } from '@/components/Highlighted';
import { useToast } from '@/components/ui/Toast';
import { api } from '@/lib/api/client';
import { ApiError, toMessage, unwrap } from '@/lib/api/errors';
import { useAsync } from '@/lib/hooks';

export interface CitationTarget {
  documentId: string;
  chunkId: string;
  /** Shown while the chunk loads and used as a fallback title. */
  documentTitle?: string;
}

interface Props {
  target: CitationTarget;
  /** Terms to highlight inside the chunk (the search query). */
  query?: string;
  onClose: () => void;
}

/**
 * Side panel showing the full text of one chunk plus a link to the source page.
 * Reusable for search results (M3) and chat citations (M6): both carry documentId + chunkId.
 */
export function CitationPreview({ target, query = '', onClose }: Props) {
  const toast = useToast();
  const closeRef = useRef<HTMLButtonElement>(null);
  const [opening, setOpening] = useState(false);

  const load = useCallback(
    async () =>
      unwrap(
        await api.GET('/api/v1/documents/{documentId}/chunks/{chunkId}', {
          params: { path: { documentId: target.documentId, chunkId: target.chunkId } },
        }),
      ),
    [target.documentId, target.chunkId],
  );
  const { data: chunk, error, loading, reload } = useAsync(load);

  useEffect(() => {
    closeRef.current?.focus();
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  async function openSource() {
    if (!chunk) return;
    setOpening(true);
    try {
      const res = unwrap(
        await api.GET('/api/v1/documents/{documentId}/download-url', {
          params: {
            path: { documentId: chunk.documentId },
            query: { version: chunk.version, page: chunk.pageNumber ?? undefined },
          },
        }),
      );
      window.open(res.url, '_blank', 'noopener,noreferrer');
    } catch (err) {
      toast.error(toMessage(err));
    } finally {
      setOpening(false);
    }
  }

  const title = chunk?.documentTitle ?? target.documentTitle ?? 'Source';
  const notFound = error instanceof ApiError && error.status === 404;

  return (
    <div className="fixed inset-0 z-40" role="presentation">
      <div className="absolute inset-0 bg-black/40" onClick={onClose} aria-hidden />
      <aside
        role="dialog"
        aria-modal="true"
        aria-labelledby="citation-title"
        className="absolute inset-y-0 right-0 flex w-full max-w-md flex-col border-l border-line bg-surface shadow-pop"
      >
        <header className="flex items-start gap-3 border-b border-line px-4 py-3">
          <div className="min-w-0 flex-1">
            <h2 id="citation-title" className="break-words text-base font-semibold">
              {title}
            </h2>
            {chunk && (
              <p className="mt-0.5 text-xs text-muted">
                v{chunk.version}
                {chunk.pageNumber != null && ` · page ${chunk.pageNumber}`}
                {chunk.sectionTitle && ` · ${chunk.sectionTitle}`}
              </p>
            )}
          </div>
          <button ref={closeRef} type="button" className="btn" onClick={onClose} aria-label="Close preview">
            Close
          </button>
        </header>

        <div className="flex-1 overflow-y-auto px-4 py-4 text-sm leading-relaxed">
          {loading && !chunk && <p className="text-muted">Loading passage…</p>}
          {error !== undefined && (
            <div role="alert" className="space-y-3">
              <p className="text-bad">
                {notFound ? 'This passage is no longer available, or you do not have access to it.' : toMessage(error)}
              </p>
              {!notFound && (
                <button type="button" className="btn" onClick={reload}>
                  Retry
                </button>
              )}
            </div>
          )}
          {chunk && (
            <blockquote className="whitespace-pre-wrap rounded-md border-l-4 border-accent bg-surface-2 px-4 py-3">
              <Highlighted text={chunk.content} query={query} />
            </blockquote>
          )}
        </div>

        {chunk && (
          <footer className="flex flex-wrap gap-2 border-t border-line px-4 py-3">
            <button type="button" className="btn btn-primary" disabled={opening} onClick={openSource}>
              {opening
                ? 'Opening…'
                : chunk.pageNumber != null
                  ? `Open at page ${chunk.pageNumber}`
                  : 'Open document'}
            </button>
          </footer>
        )}
      </aside>
    </div>
  );
}
