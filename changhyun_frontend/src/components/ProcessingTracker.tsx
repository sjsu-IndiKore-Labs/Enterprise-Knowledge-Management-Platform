'use client';

import { useEffect, useRef, useState } from 'react';
import { api } from '@/lib/api/client';
import { toMessage, unwrap } from '@/lib/api/errors';
import type { DocumentDetail, DocumentStatus } from '@/lib/api/types';
import { isTerminalStatus } from '@/lib/api/types';
import { POLL_INTERVAL_MS } from '@/lib/config';

const STEPS: { status: DocumentStatus; label: string }[] = [
  { status: 'UPLOADED', label: 'Uploaded' },
  { status: 'QUEUED', label: 'Queued' },
  { status: 'PROCESSING', label: 'Processing' },
  { status: 'INDEXED', label: 'Indexed' },
];

/**
 * Polls GET /api/v1/documents/{id} every POLL_INTERVAL_MS until the status is INDEXED or FAILED.
 * Polling stops on unmount. Transient network errors are retried; the last error is surfaced.
 */
export function useDocumentPolling(documentId: string, initial?: DocumentStatus, intervalMs = POLL_INTERVAL_MS) {
  const [detail, setDetail] = useState<DocumentDetail | undefined>(undefined);
  const [status, setStatus] = useState<DocumentStatus>(initial ?? 'QUEUED');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    let timer: ReturnType<typeof setTimeout> | undefined;

    const tick = async () => {
      try {
        const doc = unwrap(
          await api.GET('/api/v1/documents/{documentId}', { params: { path: { documentId } } }),
        );
        if (cancelled) return;
        setDetail(doc);
        setStatus(doc.status);
        setError(null);
        if (isTerminalStatus(doc.status)) return;
      } catch (err) {
        if (cancelled) return;
        setError(toMessage(err));
      }
      timer = setTimeout(tick, intervalMs);
    };

    void tick();
    return () => {
      cancelled = true;
      if (timer) clearTimeout(timer);
    };
  }, [documentId, intervalMs]);

  return { detail, status, error };
}

export function failureReason(detail: DocumentDetail | undefined): string | undefined {
  if (!detail) return undefined;
  const failed = [...detail.versions].sort((a, b) => b.version - a.version).find((v) => v.errorMessage);
  return failed?.errorMessage ?? undefined;
}

interface Props {
  documentId: string;
  initialStatus?: DocumentStatus;
  onDone?: (detail: DocumentDetail) => void;
}

export function ProcessingTracker({ documentId, initialStatus, onDone }: Props) {
  const { detail, status, error } = useDocumentPolling(documentId, initialStatus);
  const notified = useRef(false);

  useEffect(() => {
    if (detail && isTerminalStatus(detail.status) && !notified.current) {
      notified.current = true;
      onDone?.(detail);
    }
  }, [detail, onDone]);

  const failed = status === 'FAILED';
  const currentIndex = Math.max(
    0,
    STEPS.findIndex((s) => s.status === status),
  );
  const reason = failureReason(detail);

  return (
    <div className="card p-4" aria-live="polite" data-testid="processing-tracker">
      <ol className="flex flex-wrap items-center gap-x-2 gap-y-2 text-sm">
        {STEPS.map((step, i) => {
          const done = !failed && i < currentIndex;
          const active = !failed && i === currentIndex;
          const finalOk = status === 'INDEXED' && step.status === 'INDEXED';
          const cls = failed
            ? i <= 2
              ? 'text-muted'
              : 'text-muted opacity-50'
            : finalOk
              ? 'text-ok font-medium'
              : active
                ? 'text-accent font-medium'
                : done
                  ? 'text-ink'
                  : 'text-muted';
          return (
            <li key={step.status} className={`flex items-center gap-2 ${cls}`} aria-current={active ? 'step' : undefined}>
              <span
                aria-hidden
                className={`flex h-5 w-5 items-center justify-center rounded-full border text-[11px] ${
                  done || finalOk ? 'border-ok bg-ok-soft text-ok' : active ? 'border-accent' : 'border-line'
                }`}
              >
                {done || finalOk ? '✓' : i + 1}
              </span>
              {step.label}
              {i < STEPS.length - 1 && <span aria-hidden className="mx-1 h-px w-6 bg-line" />}
            </li>
          );
        })}
      </ol>

      {status === 'INDEXED' && (
        <p className="mt-3 text-sm text-ok">Ready. This document is now searchable.</p>
      )}
      {failed && (
        <p role="alert" className="mt-3 text-sm text-bad">
          Processing failed{reason ? `: ${reason}` : '.'} You can upload the file again.
        </p>
      )}
      {!failed && status !== 'INDEXED' && (
        <p className="mt-3 text-xs text-muted">
          Checking every {Math.round(POLL_INTERVAL_MS / 1000)} seconds. You can leave this page; processing continues.
        </p>
      )}
      {error && !failed && (
        <p className="mt-2 text-xs text-warn" role="status">
          {error} Retrying…
        </p>
      )}
    </div>
  );
}
