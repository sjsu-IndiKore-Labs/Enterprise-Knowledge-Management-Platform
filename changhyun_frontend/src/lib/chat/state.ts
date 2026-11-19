import type { ChatMessage, Citation, FeedbackRating } from '@/lib/api/types';
import type { StreamEvent } from './stream';

/** One message as the chat view shows it. */
export interface Turn {
  /** Stable key for React (never changes while the turn is on screen). */
  key: string;
  role: 'USER' | 'ASSISTANT';
  content: string;
  citations: Citation[];
  /** false = "not found in your documents". null = unknown yet (still streaming, or a user message). */
  answered: boolean | null;
  feedback: FeedbackRating | null;
  status: 'streaming' | 'done' | 'stopped' | 'error';
  error?: string;
  /** Server message id of an assistant answer. Needed for feedback. Arrives with the `meta` event. */
  messageId: string | null;
}

let localCounter = 0;
export const localKey = (prefix: string): string => `${prefix}-${Date.now()}-${localCounter++}`;

export function userTurn(text: string): Turn {
  return { key: localKey('u'), role: 'USER', content: text, citations: [], answered: null, feedback: null, status: 'done', messageId: null };
}

export function pendingAnswer(): Turn {
  return { key: localKey('a'), role: 'ASSISTANT', content: '', citations: [], answered: null, feedback: null, status: 'streaming', messageId: null };
}

/** Applies one stream event to the assistant turn it belongs to. Pure, so it is easy to test. */
export function applyEvent(turn: Turn, event: StreamEvent): Turn {
  switch (event.type) {
    case 'meta':
      return { ...turn, messageId: event.messageId || null };
    case 'token':
      return { ...turn, content: turn.content + event.text };
    case 'citations':
      return { ...turn, citations: event.citations };
    case 'done':
      return { ...turn, status: 'done', answered: event.answered };
    case 'error':
      return { ...turn, status: 'error', error: event.message };
  }
}

export function fromMessage(m: ChatMessage): Turn {
  return {
    key: m.id,
    role: m.role,
    content: m.content,
    citations: m.citations ?? [],
    answered: m.answered ?? null,
    feedback: m.feedback ?? null,
    status: 'done',
    messageId: m.role === 'ASSISTANT' ? m.id : null,
  };
}

/** Feedback can only be sent for a finished answer the server knows about. */
export const canRate = (t: Turn): boolean => t.role === 'ASSISTANT' && t.status === 'done' && t.messageId !== null;
