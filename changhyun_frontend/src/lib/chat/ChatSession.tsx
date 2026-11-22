'use client';

import { usePathname, useRouter } from 'next/navigation';
import { createContext, useCallback, useContext, useEffect, useMemo, useRef } from 'react';
import { useToast } from '@/components/ui/Toast';
import { api } from '@/lib/api/client';
import { toMessage, unwrap, unwrapEmpty } from '@/lib/api/errors';
import type { Conversation } from '@/lib/api/types';
import { useAsync } from '@/lib/hooks';
import { useChat } from './useChat';

interface ChatSession {
  /** The open thread: turns, send, stop, retry, rate. */
  chat: ReturnType<typeof useChat>;
  /** The signed-in user's conversations, newest first. */
  conversations: { items: Conversation[] | undefined; loading: boolean; error: boolean; reload: () => void };
  /** Opens a conversation and goes to the assistant. */
  select: (id: string) => void;
  /** Clears the thread and goes to the assistant. */
  startNew: () => void;
  remove: (id: string) => Promise<void>;
}

const Ctx = createContext<ChatSession | null>(null);

/**
 * Holds the chat thread and the conversation list above the page, so the sidebar list and the
 * assistant page show the same state. It is mounted only while someone is signed in, so signing
 * out (or in as another user) always starts empty.
 */
export function ChatSessionProvider({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const toast = useToast();

  const loadConversations = useCallback(
    async () => unwrap(await api.GET('/api/v1/conversations', { params: { query: { page: 0, size: 50 } } })),
    [],
  );
  const list = useAsync(loadConversations);
  const { reload: reloadList } = list;
  const chat = useChat({ onConversationChange: reloadList });
  const { open, reset, conversationId } = chat;

  // Leaving the assistant stops a running answer and clears the thread.
  const previousPath = useRef(pathname);
  useEffect(() => {
    if (previousPath.current.startsWith('/chat') && !pathname.startsWith('/chat')) reset();
    previousPath.current = pathname;
  }, [pathname, reset]);

  const goToChat = useCallback(() => {
    if (!pathname.startsWith('/chat')) router.push('/chat');
  }, [pathname, router]);

  const select = useCallback(
    (id: string) => {
      void open(id);
      goToChat();
    },
    [open, goToChat],
  );

  const startNew = useCallback(() => {
    reset();
    goToChat();
  }, [reset, goToChat]);

  const remove = useCallback(
    async (id: string) => {
      try {
        unwrapEmpty(await api.DELETE('/api/v1/conversations/{conversationId}', { params: { path: { conversationId: id } } }));
        if (id === conversationId) reset();
      } catch (err) {
        toast.error(toMessage(err));
      }
      reloadList();
    },
    [conversationId, reset, toast, reloadList],
  );

  const value = useMemo<ChatSession>(
    () => ({
      chat,
      conversations: { items: list.data?.items, loading: list.loading, error: list.error !== undefined, reload: reloadList },
      select,
      startNew,
      remove,
    }),
    [chat, list.data, list.loading, list.error, reloadList, select, startNew, remove],
  );

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useChatSession(): ChatSession {
  const value = useContext(Ctx);
  if (!value) throw new Error('useChatSession must be used inside ChatSessionProvider');
  return value;
}
