'use client';

import { useEffect, useRef, useState } from 'react';
import { CitationPreview, type CitationTarget } from '@/components/CitationPreview';
import { Composer } from '@/components/chat/Composer';
import { ConversationList } from '@/components/chat/ConversationList';
import { MessageView } from '@/components/chat/MessageView';
import type { Citation } from '@/lib/api/types';
import { useAuth } from '@/lib/auth/AuthContext';
import { useChatSession } from '@/lib/chat/ChatSession';
import { canReadDocuments } from '@/lib/permissions';

// Starter questions from the sample corpus (resources/eval/golden_qa.jsonl). Answers depend on the indexed documents.
const SUGGESTIONS = [
  'How many unused PTO days can I carry over into next year?',
  'What is the minimum password length?',
  'Within how many days must I submit an expense report?',
  'How many days per week can I work remotely?',
];

export default function ChatPage() {
  const { user } = useAuth();
  const { chat, conversations, select, startNew, remove } = useChatSession();

  const [preview, setPreview] = useState<CitationTarget | null>(null);
  const [historyOpen, setHistoryOpen] = useState(false); // history panel, only used on narrow screens

  // Keep the newest text in view, unless the reader scrolled up to read something.
  const scrollRef = useRef<HTMLDivElement>(null);
  const stickToBottom = useRef(true);
  const onScroll = () => {
    const el = scrollRef.current;
    if (el) stickToBottom.current = el.scrollHeight - el.scrollTop - el.clientHeight < 80;
  };
  useEffect(() => {
    const el = scrollRef.current;
    if (el && stickToBottom.current) el.scrollTop = el.scrollHeight;
  }, [chat.turns]);

  // Switching or clearing the thread (from here or from the sidebar) closes the source panel and follows the new text.
  const cleared = chat.turns.length === 0;
  useEffect(() => {
    stickToBottom.current = true;
    setPreview(null);
    setHistoryOpen(false);
  }, [chat.conversationId, cleared]);

  const send = (text: string) => {
    stickToBottom.current = true;
    void chat.send(text);
  };
  const openCitation = (c: Citation) => setPreview({ documentId: c.documentId, chunkId: c.chunkId, documentTitle: c.documentTitle });

  if (!canReadDocuments(user)) {
    return (
      <div className="card p-6 text-sm" role="alert">
        Your account cannot use the assistant.
      </div>
    );
  }

  const empty = chat.turns.length === 0 && !chat.loading;

  return (
    <div className="flex h-[calc(100dvh-15rem)] min-h-[28rem] flex-col md:h-[calc(100dvh-6.5rem)]">
      <h1 className="sr-only">Assistant</h1>

      {/* Narrow screens have no sidebar list, so the history opens here. */}
      <div className="mb-2 flex items-center justify-between gap-2 md:hidden">
        <button type="button" className="btn" onClick={() => setHistoryOpen((o) => !o)} aria-expanded={historyOpen}>
          History
        </button>
        <button type="button" className="btn" onClick={startNew}>
          New chat
        </button>
      </div>
      {historyOpen && (
        <div className="min-h-0 flex-1 overflow-y-auto md:hidden">
          <ConversationList
            items={conversations.items}
            loading={conversations.loading}
            error={conversations.error}
            activeId={chat.conversationId}
            onSelect={select}
            onNew={startNew}
            onDelete={remove}
            onRetry={conversations.reload}
          />
        </div>
      )}

      <section className={`${historyOpen ? 'hidden md:flex' : 'flex'} mx-auto min-h-0 w-full min-w-0 max-w-3xl flex-1 flex-col`} aria-label="Assistant">
        <div ref={scrollRef} onScroll={onScroll} className="min-h-0 flex-1 space-y-4 overflow-y-auto pr-1" role="log" aria-live="polite" aria-busy={chat.sending}>
          {chat.loading && <p className="text-sm text-muted">Loading conversation…</p>}

          {empty && (
            <div className="mx-auto mt-[10vh] max-w-xl text-center">
              <p className="text-2xl font-semibold tracking-tight">Ask anything about your company documents</p>
              <p className="mt-1 text-sm text-muted">Answers come only from documents you can access, with sources you can open.</p>
              <ul className="mt-4 grid gap-2 text-left sm:grid-cols-2">
                {SUGGESTIONS.map((q) => (
                  <li key={q}>
                    <button type="button" className="card w-full p-3 text-left text-sm hover:bg-surface-2 disabled:opacity-50" disabled={chat.sending} onClick={() => send(q)}>
                      {q}
                    </button>
                  </li>
                ))}
              </ul>
            </div>
          )}

          {chat.turns.map((turn, i) => (
            <MessageView
              key={turn.key}
              turn={turn}
              isLast={i === chat.turns.length - 1}
              onOpenCitation={openCitation}
              onRate={chat.rate}
              onRetry={chat.retry}
            />
          ))}
        </div>

        <Composer sending={chat.sending} onSend={send} onStop={chat.stop} focusKey={`${chat.conversationId ?? 'new'}:${chat.sending}`} />
      </section>

      {preview && <CitationPreview target={preview} onClose={() => setPreview(null)} />}
    </div>
  );
}
