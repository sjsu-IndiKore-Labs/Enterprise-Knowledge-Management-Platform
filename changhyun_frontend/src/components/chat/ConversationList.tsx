'use client';

import { useState } from 'react';
import { TrashIcon } from '@/components/icons';
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

const row = 'flex w-full items-center gap-2.5 rounded-md px-2.5 py-1.5 text-left text-sm transition-colors';

/** "New chat" plus the conversation history. Used in the sidebar and, on narrow screens, in the assistant page. */
export function ConversationList({ items, loading, error, activeId, onSelect, onNew, onDelete, onRetry }: Props) {
  const [confirming, setConfirming] = useState<string | null>(null);

  return (
    <div role="group" aria-label="Conversations" className="space-y-0.5">
      <button type="button" onClick={onNew} className={`${row} font-medium text-ink hover:bg-surface-2`}>
        <span aria-hidden className="w-4 text-center text-base leading-none text-accent">
          +
        </span>
        New chat
      </button>

      {loading && !items && <p className="px-2.5 py-1 text-sm text-muted">Loading…</p>}
      {error && !items && (
        <div className="px-2.5 py-1 text-sm">
          <p className="text-bad">Could not load your conversations.</p>
          <button type="button" className="btn mt-2" onClick={onRetry}>
            Retry
          </button>
        </div>
      )}
      {items && items.length === 0 && <p className="px-2.5 py-1 text-sm text-muted">No conversations yet. Ask your first question.</p>}

      <ul className="space-y-0.5">
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
                <div className={`group flex items-center rounded-md transition-colors ${active ? 'bg-surface-2' : 'hover:bg-surface-2'}`}>
                  <button
                    type="button"
                    onClick={() => onSelect(c.id)}
                    aria-current={active ? 'true' : undefined}
                    title={`${c.title} · ${formatDate(c.updatedAt)}`}
                    className="min-w-0 flex-1 px-2.5 py-1.5 text-left"
                  >
                    <span className={`block truncate text-sm ${active ? 'font-medium text-ink' : 'text-muted group-hover:text-ink'}`}>{c.title}</span>
                  </button>
                  <button
                    type="button"
                    onClick={() => setConfirming(c.id)}
                    aria-label={`Delete conversation “${c.title}”`}
                    className="mr-1 rounded p-1.5 text-muted hover:text-bad focus:opacity-100 md:opacity-0 md:group-hover:opacity-100"
                  >
                    <TrashIcon size={14} />
                  </button>
                </div>
              )}
            </li>
          );
        })}
      </ul>
    </div>
  );
}
