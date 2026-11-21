'use client';

import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useCallback, useState } from 'react';
import { ProcessingTracker, failureReason } from '@/components/ProcessingTracker';
import { StatusBadge } from '@/components/StatusBadge';
import { VersionUpload } from '@/components/VersionUpload';
import { useToast } from '@/components/ui/Toast';
import { api } from '@/lib/api/client';
import { ApiError, toMessage, unwrap } from '@/lib/api/errors';
import { isTerminalStatus } from '@/lib/api/types';
import { useAuth } from '@/lib/auth/AuthContext';
import { formatBytes, formatDate, formatDateTime, titleCase } from '@/lib/format';
import { useAsync, useDepartments } from '@/lib/hooks';
import { canUpload } from '@/lib/validation';

export default function DocumentDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { user } = useAuth();
  const toast = useToast();
  const departments = useDepartments();
  const [downloading, setDownloading] = useState<number | null>(null);

  const load = useCallback(
    async () => unwrap(await api.GET('/api/v1/documents/{documentId}', { params: { path: { documentId: id } } })),
    [id],
  );
  const { data: doc, error, loading, reload } = useAsync(load);

  async function download(version?: number) {
    setDownloading(version ?? 0);
    try {
      const res = unwrap(
        await api.GET('/api/v1/documents/{documentId}/download-url', {
          params: { path: { documentId: id }, query: { version } },
        }),
      );
      window.open(res.url, '_blank', 'noopener,noreferrer');
    } catch (err) {
      toast.error(toMessage(err));
    } finally {
      setDownloading(null);
    }
  }

  if (error) {
    const notFound = error instanceof ApiError && error.status === 404;
    return (
      <section className="card p-6" role="alert">
        <h1 className="text-lg font-semibold">{notFound ? 'Document not found' : 'Could not load the document'}</h1>
        <p className="mt-2 text-sm text-muted">
          {notFound ? 'It may have been deleted, or you may not have access to it.' : toMessage(error)}
        </p>
        <div className="mt-4 flex gap-2">
          <Link href="/documents" className="btn">
            Back to documents
          </Link>
          {!notFound && (
            <button type="button" className="btn" onClick={reload}>
              Retry
            </button>
          )}
        </div>
      </section>
    );
  }
  if (loading && !doc) {
    return (
      <div className="card p-6 text-sm text-muted" role="status">
        Loading…
      </div>
    );
  }
  if (!doc) return null;

  const deptName = departments.find((d) => d.code === doc.department)?.name ?? titleCase(doc.department);
  const canAddVersion = canUpload(user) && (user?.role === 'ADMIN' || user?.department === doc.department);
  const processing = !isTerminalStatus(doc.status);
  const versions = [...doc.versions].sort((a, b) => b.version - a.version);
  const reason = failureReason(doc);

  return (
    <section className="space-y-5">
      <Link href="/documents" className="text-sm text-muted hover:text-ink">
        ← Documents
      </Link>

      <header className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <h1 className="break-words text-xl font-semibold">{doc.title}</h1>
          <div className="mt-2 flex flex-wrap items-center gap-2 text-sm text-muted">
            <StatusBadge status={doc.status} title={doc.status === 'FAILED' ? reason : undefined} />
            <span>{deptName}</span>
            <span aria-hidden>·</span>
            <span>{titleCase(doc.category)}</span>
          </div>
        </div>
        <button
          type="button"
          className="btn"
          disabled={downloading !== null || doc.status === 'FAILED'}
          onClick={() => download()}
        >
          {downloading === 0 ? 'Opening…' : 'Download current'}
        </button>
      </header>

      {processing && <ProcessingTracker documentId={doc.id} initialStatus={doc.status} onDone={reload} />}
      {doc.status === 'FAILED' && (
        <p role="alert" className="rounded-md bg-bad-soft px-4 py-3 text-sm text-bad">
          Processing failed{reason ? `: ${reason}` : '.'}
          {canAddVersion ? ' Upload the file again as a new version below.' : ''}
        </p>
      )}

      <div className="card p-4">
        <h2 className="mb-3 text-sm font-semibold">Details</h2>
        <dl className="grid gap-x-6 gap-y-3 text-sm sm:grid-cols-2">
          <Meta label="Author" value={doc.author || '-'} />
          <Meta label="Current version" value={`v${doc.currentVersion}`} />
          <Meta label="Pages" value={doc.pageCount != null ? String(doc.pageCount) : '-'} />
          <Meta label="File" value={doc.originalFilename || '-'} />
          <Meta label="Created" value={formatDateTime(doc.createdAt)} />
          <Meta label="Updated" value={formatDateTime(doc.updatedAt)} />
          <div className="sm:col-span-2">
            <dt className="text-xs text-muted">Tags</dt>
            <dd className="mt-1 flex flex-wrap gap-1.5">
              {doc.tags.length === 0 ? (
                <span className="text-muted">-</span>
              ) : (
                doc.tags.map((t) => (
                  <span key={t} className="rounded-full bg-surface-2 px-2.5 py-0.5 text-xs">
                    {t}
                  </span>
                ))
              )}
            </dd>
          </div>
        </dl>
      </div>

      <div className="card overflow-x-auto">
        <h2 className="px-4 pt-4 text-sm font-semibold">Versions</h2>
        <table className="mt-2 w-full min-w-[36rem] text-left text-sm">
          <thead className="border-b border-line text-xs uppercase tracking-wide text-muted">
            <tr>
              <th className="px-4 py-2 font-medium">Version</th>
              <th className="px-4 py-2 font-medium">File</th>
              <th className="px-4 py-2 font-medium">Status</th>
              <th className="px-4 py-2 font-medium">Uploaded</th>
              <th className="px-4 py-2 font-medium">Note</th>
              <th className="px-4 py-2" />
            </tr>
          </thead>
          <tbody>
            {versions.map((v) => (
              <tr key={v.id} className="border-b border-line last:border-0">
                <td className="px-4 py-2.5 font-medium">
                  v{v.version}
                  {v.version === doc.currentVersion && <span className="ml-2 text-xs text-muted">current</span>}
                </td>
                <td className="px-4 py-2.5">
                  <div className="max-w-[16rem] truncate">{v.originalFilename}</div>
                  <div className="text-xs text-muted">{formatBytes(v.sizeBytes)}</div>
                </td>
                <td className="px-4 py-2.5">
                  <StatusBadge status={v.status} title={v.errorMessage ?? undefined} />
                </td>
                <td className="px-4 py-2.5 text-muted">{formatDate(v.uploadedAt)}</td>
                <td className="max-w-[14rem] px-4 py-2.5 text-muted">{v.changeNote || '-'}</td>
                <td className="px-4 py-2.5 text-right">
                  <button
                    type="button"
                    className="btn"
                    disabled={downloading !== null || v.status === 'FAILED'}
                    onClick={() => download(v.version)}
                  >
                    {downloading === v.version ? 'Opening…' : 'Download'}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {canAddVersion && (
        <div className="card p-4">
          <h2 className="mb-3 text-sm font-semibold">Upload a new version</h2>
          <VersionUpload documentId={doc.id} onUploaded={reload} />
        </div>
      )}
    </section>
  );
}

function Meta({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs text-muted">{label}</dt>
      <dd className="mt-0.5 break-words">{value}</dd>
    </div>
  );
}
