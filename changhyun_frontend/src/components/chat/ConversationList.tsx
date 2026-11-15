'use client';

import { useState } from 'react';
import type { Conversation } from '@/lib/api/types';
import { formatDate } from '@/lib/format';

interface Props {
  items: Conversation[] | undefined;
  loading: boolean;
  error: boolean;
  activeId: string | null;
  onSelect: (id: string) => void;
  onNew: () => void;
  onDelete: (id: string) => void;
  onRetry: () => void;
}

export function ConversationList({ items, loading, error, activeId, onSelect, onNew, onDelete, onRetry }: Props) {
  const [confirming, setConfirming] = useState<string | null>(null);

  return (
    <nav aria-label="Conversations" className="flex h-full min-h-0 flex-col">
      <button type="button" className="btn btn-primary mb-3 w-full" onClick={onNew}>
        New chat
      </button>

      <div className="min-h-0 flex-1 overflow-y-auto">
        {loading && !items && <p className="px-1 text-sm text-muted">Loading…</p>}
        {error && !items && (
          <div className="px-1 text-sm">
            <p className="text-bad">Could not load your conversations.</p>
            <button type="button" className="btn mt-2" onClick={onRetry}>
              Retry
            </button>
          </div>
        )}
        {items && items.length === 0 && <p className="px-1 text-sm text-muted">No conversations yet. Ask your first question.</p>}

        <ul className="space-y-1">
          {items?.map((c) => {
            const active = c.id === activeId;
            return (
              <li key={c.id}>
                {confirming === c.id ? (
                  <div className="rounded-md border border-bad bg-bad-soft p-2 text-sm" role="group" aria-label={`Delete “${c.title}”?`}>
                    <p className="mb-2">Delete this chat?</p>
                    <div className="flex gap-2">
                      <button
                        type="button"
                        className="btn"
                        onClick={() => {
                          setConfirming(null);
                          onDelete(c.id);
                        }}
                      >
                        Delete
                      </button>
                      <button type="button" className="btn" onClick={() => setConfirming(null)}>
                        Cancel
                      </button>
                    </div>
                  </div>
                ) : (
                  <div className={`group flex items-center rounded-md ${active ? 'bg-accent-soft' : 'hover:bg-surface-2'}`}>
                    <button
                      type="button"
                      onClick={() => onSelect(c.id)}
                      aria-current={active ? 'true' : undefined}
                      className="min-w-0 flex-1 px-3 py-2 text-left"
                    >
                      <span className={`block truncate text-sm ${active ? 'font-medium text-accent' : ''}`}>{c.title}</span>
                      <span className="block text-xs text-muted">{formatDate(c.updatedAt)}</span>
                    </button>
                    <button
                      type="button"
                      onClick={() => setConfirming(c.id)}
                      aria-label={`Delete conversation “${c.title}”`}
                      className="mr-1 rounded p-1.5 text-muted hover:bg-surface hover:text-bad focus:opacity-100 md:opacity-0 md:group-hover:opacity-100"
                    >
                      <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                        <path d="M3 6h18M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
                      </svg>
                    </button>
                  </div>
                )}
              </li>
            );
          })}
        </ul>
      </div>
    </nav>
  );
}
