import type { DocumentStatus } from '@/lib/api/types';

const META: Record<DocumentStatus, { label: string; cls: string }> = {
  UPLOADED: { label: 'Uploaded', cls: 'bg-surface-2 text-muted' },
  QUEUED: { label: 'Queued', cls: 'bg-surface-2 text-muted' },
  PROCESSING: { label: 'Processing', cls: 'bg-warn-soft text-warn' },
  INDEXED: { label: 'Indexed', cls: 'bg-ok-soft text-ok' },
  FAILED: { label: 'Failed', cls: 'bg-bad-soft text-bad' },
};

export function StatusBadge({
  status,
  title,
}: {
  status: DocumentStatus;
  /** Tooltip, used to show the failure reason on FAILED. */
  title?: string;
}) {
  const m = META[status];
  return (
    <span
      title={title}
      className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-medium ${m.cls}`}
    >
      <span
        aria-hidden
        className={`h-1.5 w-1.5 rounded-full bg-current ${status === 'PROCESSING' ? 'animate-pulse' : ''}`}
      />
      {m.label}
    </span>
  );
}
