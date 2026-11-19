'use client';

import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { Suspense, useCallback, useEffect, useRef, useState } from 'react';
import { SearchIcon } from '@/components/icons';
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
  // useSearchParams needs a Suspense boundary for static rendering.
  return (
    <Suspense fallback={null}>
      <Documents />
    </Suspense>
  );
}

function Documents() {
  const { user } = useAuth();
  const departments = useDepartments();
  const params = useSearchParams();
  // The sidebar links to /documents?department=CODE; only known codes are accepted.
  const fromUrl = params.get('department');
  const urlDepartment = (DEPARTMENT_CODES as readonly string[]).includes(fromUrl ?? '') ? (fromUrl as DepartmentCode) : '';

  const [q, setQ] = useState('');
  const [department, setDepartment] = useState<DepartmentCode | ''>(urlDepartment);
  useEffect(() => setDepartment(urlDepartment), [urlDepartment]);
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
  const clearFilters = () => {
    setQ('');
    setDepartment('');
    setCategory('');
    setStatus('');
  };

  // Counts for the tiles (one tiny request per status, scoped to the chosen department).
  const loadCounts = useCallback(async () => {
    const count = async (s?: DocumentStatus) =>
      unwrap(
        await api.GET('/api/v1/documents', {
          params: { query: { page: 0, size: 1, department: department || undefined, status: s } },
        }),
      ).totalItems;
    const [all, indexed, processing, failed] = await Promise.all([count(), count('INDEXED'), count('PROCESSING'), count('FAILED')]);
    return { all, indexed, processing, failed };
  }, [department]);
  const { data: counts, reload: reloadCounts } = useAsync(loadCounts);
  // Keep the tiles fresh while documents are processing, and once more when the last one finishes.
  const wasInFlight = useRef(false);
  useEffect(() => {
    if (hasInFlight || wasInFlight.current) reloadCounts();
    wasInFlight.current = hasInFlight;
  }, [data, hasInFlight, reloadCounts]);

  const tiles: { label: string; value: number | undefined; status: DocumentStatus | ''; cls: string }[] = [
    { label: 'All documents', value: counts?.all, status: '', cls: 'bg-surface-2' },
    { label: 'Indexed', value: counts?.indexed, status: 'INDEXED', cls: 'bg-ok-soft text-ok' },
    { label: 'Processing', value: counts?.processing, status: 'PROCESSING', cls: 'bg-warn-soft text-warn' },
    { label: 'Failed', value: counts?.failed, status: 'FAILED', cls: 'bg-bad-soft text-bad' },
  ];

  const pill =
    'rounded-full border border-line bg-surface py-1.5 pl-3.5 pr-8 text-sm text-ink transition-colors hover:bg-surface-2';

  return (
    <section className="space-y-6">
      <header className="flex flex-wrap items-end justify-between gap-3 pt-2">
        <div>
          <h1 className="text-4xl font-bold tracking-tight">Documents</h1>
          <p className="mt-2 text-sm text-muted">
            {department ? `${deptName(department)} · ` : ''}Documents you have access to.
          </p>
        </div>
        {canUpload(user) && (
          <Link href="/documents/upload" className="btn btn-primary">
            Upload document
          </Link>
        )}
      </header>

      <div role="group" aria-label="Documents by status" className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {tiles.map((t) => {
          const active = t.status !== '' && status === t.status;
          return (
            <button
              key={t.label}
              type="button"
              aria-pressed={active}
              onClick={() => setStatus(t.status)}
              className={`rounded-xl px-4 py-4 text-left transition ${t.cls} ${active ? 'ring-2 ring-accent' : 'hover:opacity-80'}`}
            >
              <div className="text-2xl font-semibold tabular-nums">{t.value ?? '–'}</div>
              <div className="mt-1 text-sm">{t.label}</div>
            </button>
          );
        })}
      </div>

      <div className="space-y-3">
        <div className="relative">
          <label htmlFor="f-q" className="sr-only">
            Title or tag
          </label>
          <SearchIcon size={18} className="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-muted" />
          <input
            id="f-q"
            type="search"
            className="input rounded-xl py-3 pl-11 shadow-sm"
            placeholder="Filter by title or tag…"
            value={q}
            onChange={(e) => setQ(e.target.value)}
          />
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <label htmlFor="f-dept" className="sr-only">
            Department
          </label>
          <select id="f-dept" className={pill} value={department} onChange={(e) => setDepartment(e.target.value as DepartmentCode | '')}>
            <option value="">All departments</option>
            {(user?.allowedDepartments ?? DEPARTMENT_CODES).map((c) => (
              <option key={c} value={c}>
                {deptName(c)}
              </option>
            ))}
          </select>
          <label htmlFor="f-cat" className="sr-only">
            Category
          </label>
          <select id="f-cat" className={pill} value={category} onChange={(e) => setCategory(e.target.value as DocumentCategory | '')}>
            <option value="">All categories</option>
            {CATEGORIES.map((c) => (
              <option key={c} value={c}>
                {titleCase(c)}
              </option>
            ))}
          </select>
          <label htmlFor="f-status" className="sr-only">
            Status
          </label>
          <select id="f-status" className={pill} value={status} onChange={(e) => setStatus(e.target.value as DocumentStatus | '')}>
            <option value="">Any status</option>
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {titleCase(s)}
              </option>
            ))}
          </select>
          {filtersActive && (
            <button type="button" onClick={clearFilters} className="px-2 text-sm text-muted hover:text-ink">
              Clear filters
            </button>
          )}
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
        <div className="p-6 text-sm text-muted" role="status">
          Loading documents…
        </div>
      ) : data && data.items.length === 0 ? (
        <div className="rounded-xl border border-dashed border-line p-10 text-center">
          <p className="text-sm font-medium">{filtersActive ? 'No documents match these filters.' : 'No documents yet.'}</p>
          {!filtersActive && canUpload(user) && (
            <Link href="/documents/upload" className="btn btn-primary mt-4">
              Upload the first one
            </Link>
          )}
        </div>
      ) : (
        data && (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[44rem] text-left text-sm">
              <thead className="text-[11px] uppercase tracking-wider text-muted">
                <tr className="border-b border-line">
                  <th className="px-3 py-2.5 font-semibold">Doc</th>
                  <th className="px-3 py-2.5 font-semibold">Department</th>
                  <th className="px-3 py-2.5 font-semibold">Category</th>
                  <th className="px-3 py-2.5 font-semibold">Author</th>
                  <th className="px-3 py-2.5 text-right font-semibold">Status</th>
                  <th className="px-3 py-2.5 text-right font-semibold">Updated</th>
                </tr>
              </thead>
              <tbody>
                {data.items.map((d) => (
                  <tr key={d.id} className="border-b border-line/60 transition-colors last:border-0 hover:bg-surface-2">
                    <td className="max-w-sm px-3 py-3">
                      <div className="flex items-center gap-3">
                        <span
                          aria-hidden
                          title={fileKind(d)}
                          className="flex h-8 w-8 shrink-0 items-center justify-center rounded-md bg-surface-2 text-[9px] font-bold tracking-wide text-muted"
                        >
                          {fileKind(d)}
                        </span>
                        <div className="min-w-0">
                          <Link href={`/documents/${d.id}`} className="font-medium hover:text-accent hover:underline">
                            {d.title}
                          </Link>
                          <span className="ml-2 text-xs text-muted">v{d.currentVersion}</span>
                          {d.tags.length > 0 && <div className="truncate text-xs text-muted">{d.tags.join(', ')}</div>}
                        </div>
                      </div>
                    </td>
                    <td className="whitespace-nowrap px-3 py-3 text-muted">{deptName(d.department)}</td>
                    <td className="px-3 py-3 text-muted">{titleCase(d.category)}</td>
                    <td className="px-3 py-3 text-muted">{d.author ?? '—'}</td>
                    <td className="px-3 py-3 text-right">
                      <StatusBadge
                        status={d.status}
                        title={d.status === 'FAILED' ? 'Processing failed. Open the document for details.' : undefined}
                      />
                    </td>
                    <td className="whitespace-nowrap px-3 py-3 text-right text-muted">{formatDate(d.updatedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )
      )}

      {data && data.totalItems > 0 && (
        <nav
          aria-label="Pagination"
          className="sticky bottom-4 mx-auto flex w-fit items-center gap-4 rounded-full border border-line bg-surface px-4 py-2 text-sm shadow-pop"
        >
          <span className="text-muted">
            {page * PAGE_SIZE + 1}–{Math.min((page + 1) * PAGE_SIZE, data.totalItems)} of {data.totalItems}
          </span>
          <div className="flex gap-1">
            <button
              type="button"
              aria-label="Previous page"
              className="rounded-full px-2.5 py-0.5 hover:bg-surface-2 disabled:opacity-40"
              disabled={page === 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              ‹
            </button>
            <button
              type="button"
              aria-label="Next page"
              className="rounded-full px-2.5 py-0.5 hover:bg-surface-2 disabled:opacity-40"
              disabled={page + 1 >= totalPages}
              onClick={() => setPage((p) => p + 1)}
            >
              ›
            </button>
          </div>
        </nav>
      )}
    </section>
  );
}

/** "PDF" / "DOCX" label for the file icon, from the content type or file name. */
function fileKind(d: { contentType?: string; originalFilename?: string }): string {
  const t = `${d.contentType ?? ''} ${d.originalFilename ?? ''}`.toLowerCase();
  if (t.includes('pdf')) return 'PDF';
  if (t.includes('word') || t.includes('docx')) return 'DOCX';
  return 'FILE';
}
