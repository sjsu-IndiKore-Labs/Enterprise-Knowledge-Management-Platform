'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { useToast } from '@/components/ui/Toast';
import { api } from '@/lib/api/client';
import { toMessage, unwrap, unwrapEmpty } from '@/lib/api/errors';
import type { FeedbackRating } from '@/lib/api/types';
import { applyEvent, fromMessage, pendingAnswer, userTurn, type Turn } from './state';
import { isAbort, streamChat } from './stream';

interface Options {
  /** Called when a conversation was created or changed, so the sidebar list can refresh. */
  onConversationChange?: () => void;
}

/** State and actions of one chat thread: send with streaming, stop, retry, open an old conversation, rate. */
export function useChat({ onConversationChange }: Options = {}) {
  const toast = useToast();
  const [turns, setTurns] = useState<Turn[]>([]);
  const [conversationId, setConversationId] = useState<string | null>(null);
  const [sending, setSending] = useState(false);
  const [loading, setLoading] = useState(false);

  const turnsRef = useRef<Turn[]>([]);
  const idRef = useRef<string | null>(null);
  const abortRef = useRef<AbortController | null>(null);
  /** Bumped by every send, open and reset. Work from an older run never touches the screen. */
  const runRef = useRef(0);
  const onChangeRef = useRef(onConversationChange);

  useEffect(() => {
    turnsRef.current = turns;
  }, [turns]);
  useEffect(() => {
    onChangeRef.current = onConversationChange;
  }, [onConversationChange]);

  const setConversation = useCallback((id: string | null) => {
    idRef.current = id;
    setConversationId(id);
  }, []);

  const cancelRun = useCallback(() => {
    runRef.current += 1;
    abortRef.current?.abort();
    abortRef.current = null;
    setSending(false);
  }, []);

  // Leaving the page stops a running answer.
  useEffect(() => cancelRun, [cancelRun]);

  const send = useCallback(
    async (text: string, isRetry = false) => {
      if (abortRef.current) return; // one answer at a time
      const controller = new AbortController();
      abortRef.current = controller;
      const run = ++runRef.current;
      const live = () => runRef.current === run;
      const updateAnswer = (fn: (t: Turn) => Turn) =>
        setTurns((cur) => (live() && cur.length > 0 && cur[cur.length - 1].role === 'ASSISTANT' ? [...cur.slice(0, -1), fn(cur[cur.length - 1])] : cur));

      setSending(true);
      setTurns((cur) => (isRetry ? [...cur, pendingAnswer()] : [...cur, userTurn(text), pendingAnswer()]));
      try {
        await streamChat(
          { message: text, conversationId: idRef.current },
          {
            signal: controller.signal,
            onEvent: (event) => {
              if (!live()) return;
              if (event.type === 'meta' && event.conversationId) setConversation(event.conversationId);
              updateAnswer((t) => applyEvent(t, event));
            },
          },
        );
      } catch (err) {
        if (isAbort(err)) updateAnswer((t) => ({ ...t, status: 'stopped' }));
        else updateAnswer((t) => ({ ...t, status: 'error', error: toMessage(err) }));
      } finally {
        if (live()) {
          abortRef.current = null;
          setSending(false);
          onChangeRef.current?.();
        }
      }
    },
    [setConversation],
  );

  const stop = useCallback(() => abortRef.current?.abort(), []);

  /** Asks the last question again, replacing the answer that failed or was stopped. */
  const retry = useCallback(() => {
    const cur = turnsRef.current;
    const lastQuestion = [...cur].reverse().find((t) => t.role === 'USER');
    if (!lastQuestion || sending) return;
    setTurns(cur.slice(0, -1));
    void send(lastQuestion.content, true);
  }, [send, sending]);

  const open = useCallback(
    async (id: string) => {
      cancelRun();
      const run = runRef.current;
      setLoading(true);
      setConversation(id);
      setTurns([]);
      try {
        const detail = unwrap(await api.GET('/api/v1/conversations/{conversationId}', { params: { path: { conversationId: id } } }));
        if (runRef.current !== run) return;
        setTurns(detail.messages.map(fromMessage));
      } catch (err) {
        if (runRef.current !== run) return;
        setConversation(null);
        toast.error(toMessage(err));
        onChangeRef.current?.(); // the list may be out of date (deleted elsewhere)
      } finally {
        if (runRef.current === run) setLoading(false);
      }
    },
    [cancelRun, setConversation, toast],
  );

  const reset = useCallback(() => {
    cancelRun();
    setConversation(null);
    setTurns([]);
    setLoading(false);
  }, [cancelRun, setConversation]);

  const rate = useCallback(
    async (turn: Turn, rating: FeedbackRating) => {
      if (!turn.messageId || turn.feedback === rating) return;
      const previous = turn.feedback;
      const setFeedback = (value: FeedbackRating | null) =>
        setTurns((cur) => cur.map((t) => (t.key === turn.key ? { ...t, feedback: value } : t)));
      setFeedback(rating); // optimistic
      try {
        unwrapEmpty(
          await api.POST('/api/v1/chat/messages/{messageId}/feedback', {
            params: { path: { messageId: turn.messageId } },
            body: { rating },
          }),
        );
      } catch (err) {
        setFeedback(previous);
        toast.error(toMessage(err));
      }
    },
    [toast],
  );

  return { turns, conversationId, sending, loading, send, stop, retry, open, reset, rate };
}
