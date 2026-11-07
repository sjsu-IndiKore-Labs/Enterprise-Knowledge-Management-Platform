'use client';

import Link from 'next/link';
import { useCallback, useEffect, useState } from 'react';
import { StatusBadge } from '@/components/StatusBadge';
import { api } from '@/lib/api/client';
import { toMessage, unwrap } from '@/lib/api/errors';
import {
  CATEGORIES,
  DEPARTMENT_CODES,
  STATUSES,
  isTerminalStatus,
  type DepartmentCode,
  type DocumentCategory,
  type DocumentStatus,
} from '@/lib/api/types';
import { useAuth } from '@/lib/auth/AuthContext';
import { POLL_INTERVAL_MS } from '@/lib/config';
import { formatDate, titleCase } from '@/lib/format';
import { useAsync, useDebounced, useDepartments } from '@/lib/hooks';
import { canUpload } from '@/lib/validation';

const PAGE_SIZE = 20;

export default function DocumentsPage() {
  const { user } = useAuth();
  const departments = useDepartments();

  const [q, setQ] = useState('');
  const [department, setDepartment] = useState<DepartmentCode | ''>('');
  const [category, setCategory] = useState<DocumentCategory | ''>('');
  const [status, setStatus] = useState<DocumentStatus | ''>('');
  const [page, setPage] = useState(0);
  const debouncedQ = useDebounced(q.trim());

  // Any filter change goes back to the first page.
  useEffect(() => setPage(0), [debouncedQ, department, category, status]);

  const load = useCallback(async () => {
    return unwrap(
      await api.GET('/api/v1/documents', {
        params: {
          query: {
            page,
            size: PAGE_SIZE,
            sort: 'updatedAt,desc',
            q: debouncedQ || undefined,
            department: department || undefined,
            category: category || undefined,
            status: status || undefined,
          },
        },
      }),
    );
  }, [page, debouncedQ, department, category, status]);

  const { data, error, loading, reload } = useAsync(load);

  // While anything is still being processed, refresh the list in the background.
  const hasInFlight = data?.items.some((d) => !isTerminalStatus(d.status)) ?? false;
  useEffect(() => {
    if (!hasInFlight) return;
    const t = setInterval(reload, POLL_INTERVAL_MS);
    return () => clearInterval(t);
  }, [hasInFlight, reload]);

  const deptName = (code: string) => departments.find((d) => d.code === code)?.name ?? titleCase(code);
  const filtersActive = Boolean(debouncedQ || department || category || status);
  const totalPages = data?.totalPages ?? 0;

  return (
    <section className="space-y-4">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold">Documents</h1>
          <p className="mt-1 text-sm text-muted">Documents you have access to.</p>
        </div>
        {canUpload(user) && (
          <Link href="/documents/upload" className="btn btn-primary">
            Upload document
          </Link>
        )}
      </header>

      <div className="card grid gap-3 p-3 sm:grid-cols-2 lg:grid-cols-4">
        <div className="sm:col-span-2 lg:col-span-1">
          <label htmlFor="f-q" className="label">
            Title or tag
          </label>
          <input
            id="f-q"
            type="search"
            className="input"
            placeholder="Filter…"
            value={q}
            onChange={(e) => setQ(e.target.value)}
          />
        </div>
        <div>
          <label htmlFor="f-dept" className="label">
            Department
          </label>
          <select
            id="f-dept"
            className="input"
            value={department}
            onChange={(e) => setDepartment(e.target.value as DepartmentCode | '')}
          >
            <option value="">All</option>
            {(user?.allowedDepartments ?? DEPARTMENT_CODES).map((c) => (
              <option key={c} value={c}>
                {deptName(c)}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor="f-cat" className="label">
            Category
          </label>
          <select
            id="f-cat"
            className="input"
            value={category}
            onChange={(e) => setCategory(e.target.value as DocumentCategory | '')}
          >
            <option value="">All</option>
            {CATEGORIES.map((c) => (
              <option key={c} value={c}>
                {titleCase(c)}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor="f-status" className="label">
            Status
          </label>
          <select
            id="f-status"
            className="input"
            value={status}
            onChange={(e) => setStatus(e.target.value as DocumentStatus | '')}
          >
            <option value="">All</option>
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {titleCase(s)}
              </option>
            ))}
          </select>
        </div>
      </div>

      {error ? (
        <div role="alert" className="card flex items-center justify-between gap-3 p-4 text-sm">
          <span className="text-bad">{toMessage(error)}</span>
          <button type="button" className="btn" onClick={reload}>
            Retry
          </button>
        </div>
      ) : loading && !data ? (
        <div className="card p-6 text-sm text-muted" role="status">
          Loading documents…
        </div>
      ) : data && data.items.length === 0 ? (
        <div className="card p-8 text-center">
          <p className="text-sm font-medium">{filtersActive ? 'No documents match these filters.' : 'No documents yet.'}</p>
          {!filtersActive && canUpload(user) && (
            <Link href="/documents/upload" className="btn btn-primary mt-4">
              Upload the first one
            </Link>
          )}
        </div>
      ) : (
        data && (
          <div className="card overflow-x-auto">
            <table className="w-full min-w-[40rem] text-left text-sm">
              <thead className="border-b border-line text-xs uppercase tracking-wide text-muted">
                <tr>
                  <th className="px-4 py-2.5 font-medium">Title</th>
                  <th className="px-4 py-2.5 font-medium">Department</th>
                  <th className="px-4 py-2.5 font-medium">Category</th>
                  <th className="px-4 py-2.5 font-medium">Version</th>
                  <th className="px-4 py-2.5 font-medium">Status</th>
                  <th className="px-4 py-2.5 font-medium">Updated</th>
                </tr>
              </thead>
              <tbody>
                {data.items.map((d) => (
                  <tr key={d.id} className="border-b border-line last:border-0 hover:bg-surface-2">
                    <td className="max-w-xs px-4 py-3">
                      <Link href={`/documents/${d.id}`} className="font-medium text-accent hover:underline">
                        {d.title}
                      </Link>
                      {d.tags.length > 0 && (
                        <div className="mt-0.5 truncate text-xs text-muted">{d.tags.join(', ')}</div>
                      )}
                    </td>
                    <td className="px-4 py-3">{deptName(d.department)}</td>
                    <td className="px-4 py-3">{titleCase(d.category)}</td>
                    <td className="px-4 py-3">v{d.currentVersion}</td>
                    <td className="px-4 py-3">
                      <StatusBadge
                        status={d.status}
                        title={d.status === 'FAILED' ? 'Processing failed. Open the document for details.' : undefined}
                      />
                    </td>
                    <td className="px-4 py-3 text-muted">{formatDate(d.updatedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )
      )}

      {data && totalPages > 1 && (
        <nav aria-label="Pagination" className="flex items-center justify-between text-sm">
          <span className="text-muted">
            Page {data.page + 1} of {totalPages} · {data.totalItems} documents
          </span>
          <div className="flex gap-2">
            <button type="button" className="btn" disabled={page === 0} onClick={() => setPage((p) => Math.max(0, p - 1))}>
              Previous
            </button>
            <button
              type="button"
              className="btn"
              disabled={page + 1 >= totalPages}
              onClick={() => setPage((p) => p + 1)}
            >
              Next
            </button>
          </div>
        </nav>
      )}
    </section>
  );
}
