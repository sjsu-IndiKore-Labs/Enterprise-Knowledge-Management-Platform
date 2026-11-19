'use client';

import { useEffect, useRef, useState } from 'react';

export const MAX_MESSAGE_LENGTH = 2000; // ChatRequest.message maxLength in core-api.yaml

interface Props {
  sending: boolean;
  onSend: (text: string) => void;
  onStop: () => void;
  /** Changing this value moves the keyboard focus back into the box (new chat, answer finished). */
  focusKey: string;
}

export function Composer({ sending, onSend, onStop, focusKey }: Props) {
  const [text, setText] = useState('');
  const ref = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    ref.current?.focus();
  }, [focusKey]);

  // Grow with the text, up to about six lines.
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    el.style.height = 'auto';
    el.style.height = `${Math.min(el.scrollHeight, 160)}px`;
  }, [text]);

  function submit() {
    const value = text.trim();
    if (!value || sending) return;
    onSend(value);
    setText('');
  }

  return (
    <form
      className="border-t border-line pt-3"
      onSubmit={(e) => {
        e.preventDefault();
        submit();
      }}
    >
      <label htmlFor="chat-input" className="sr-only">
        Ask a question about your documents
      </label>
      <div className="flex items-end gap-2">
        <textarea
          id="chat-input"
          ref={ref}
          rows={1}
          value={text}
          maxLength={MAX_MESSAGE_LENGTH}
          placeholder="Ask a question about your documents…"
          className="input max-h-40 resize-none"
          onChange={(e) => setText(e.target.value)}
          onKeyDown={(e) => {
            // Enter sends, Shift+Enter adds a line. Enter that confirms an IME composition (Korean, Japanese…) must not send.
            if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
              e.preventDefault();
              submit();
            }
          }}
        />
        {sending ? (
          <button type="button" className="btn shrink-0" onClick={onStop}>
            <span aria-hidden="true" className="inline-block h-3 w-3 rounded-sm bg-current" />
            Stop
          </button>
        ) : (
          <button type="submit" className="btn btn-primary shrink-0" disabled={text.trim() === ''}>
            Send
          </button>
        )}
      </div>
      <div className="mt-1 flex justify-between text-xs text-muted">
        <span>Enter to send, Shift+Enter for a new line. Answers use only documents you can access.</span>
        {text.length > MAX_MESSAGE_LENGTH - 200 && (
          <span className={text.length >= MAX_MESSAGE_LENGTH ? 'text-bad' : undefined}>
            {text.length}/{MAX_MESSAGE_LENGTH}
          </span>
        )}
      </div>
    </form>
  );
}
