'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { CitationPreview, type CitationTarget } from '@/components/CitationPreview';
import { Composer } from '@/components/chat/Composer';
import { ConversationList } from '@/components/chat/ConversationList';
import { MessageView } from '@/components/chat/MessageView';
import { useToast } from '@/components/ui/Toast';
import { api } from '@/lib/api/client';
import { toMessage, unwrap, unwrapEmpty } from '@/lib/api/errors';
import type { Citation } from '@/lib/api/types';
import { useAuth } from '@/lib/auth/AuthContext';
import { useChat } from '@/lib/chat/useChat';
import { useAsync } from '@/lib/hooks';
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
  const toast = useToast();

  const loadConversations = useCallback(
    async () => unwrap(await api.GET('/api/v1/conversations', { params: { query: { page: 0, size: 50 } } })),
    [],
  );
  const conversations = useAsync(loadConversations);
  const chat = useChat({ onConversationChange: conversations.reload });

  const [preview, setPreview] = useState<CitationTarget | null>(null);
  const [historyOpen, setHistoryOpen] = useState(false); // only used on narrow screens

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

  const send = (text: string) => {
    stickToBottom.current = true;
    void chat.send(text);
  };
  const select = (id: string) => {
    stickToBottom.current = true;
    setHistoryOpen(false);
    setPreview(null);
    void chat.open(id);
  };
  const startNew = () => {
    setHistoryOpen(false);
    setPreview(null);
    chat.reset();
  };
  const openCitation = (c: Citation) => setPreview({ documentId: c.documentId, chunkId: c.chunkId, documentTitle: c.documentTitle });

  async function remove(id: string) {
    try {
      unwrapEmpty(await api.DELETE('/api/v1/conversations/{conversationId}', { params: { path: { conversationId: id } } }));
      if (id === chat.conversationId) startNew();
      conversations.reload();
    } catch (err) {
      toast.error(toMessage(err));
      conversations.reload();
    }
  }

  if (!canReadDocuments(user)) {
    return (
      <div className="card p-6 text-sm" role="alert">
        Your account cannot use the assistant.
      </div>
    );
  }

  const empty = chat.turns.length === 0 && !chat.loading;
  const items = conversations.data?.items;

  return (
    <div className="flex h-[calc(100dvh-15rem)] min-h-[28rem] flex-col gap-4 md:h-[calc(100dvh-7rem)] md:flex-row">
      <aside className={`${historyOpen ? 'flex' : 'hidden'} min-h-0 flex-1 flex-col md:flex md:w-64 md:flex-none md:border-r md:border-line md:pr-4`}>
        <ConversationList
          items={items}
          loading={conversations.loading}
          error={conversations.error !== undefined}
          activeId={chat.conversationId}
          onSelect={select}
          onNew={startNew}
          onDelete={remove}
          onRetry={conversations.reload}
        />
      </aside>

      <section className={`${historyOpen ? 'hidden' : 'flex'} min-h-0 min-w-0 flex-1 flex-col md:flex`} aria-label="Assistant">
        <div className="mb-2 flex items-center justify-between gap-2">
          <h1 className="text-lg font-semibold">Assistant</h1>
          <button type="button" className="btn md:hidden" onClick={() => setHistoryOpen(true)}>
            History
          </button>
        </div>

        <div ref={scrollRef} onScroll={onScroll} className="min-h-0 flex-1 space-y-4 overflow-y-auto pr-1" role="log" aria-live="polite" aria-busy={chat.sending}>
          {chat.loading && <p className="text-sm text-muted">Loading conversation…</p>}

          {empty && (
            <div className="mx-auto mt-6 max-w-xl text-center">
              <p className="text-base font-medium">Ask anything about your company documents</p>
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
