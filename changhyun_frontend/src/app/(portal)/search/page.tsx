'use client';

import Link from 'next/link';
import { useRef, useState } from 'react';
import { CitationPreview, type CitationTarget } from '@/components/CitationPreview';
import { Highlighted } from '@/components/Highlighted';
import { api } from '@/lib/api/client';
import { ApiError, toMessage, unwrap } from '@/lib/api/errors';
import {
  CATEGORIES,
  DEPARTMENT_CODES,
  SEARCH_MODES,
  type DepartmentCode,
  type DocumentCategory,
  type SearchMode,
  type SearchResponse,
} from '@/lib/api/types';
import { useAuth } from '@/lib/auth/AuthContext';
import { formatScore, titleCase } from '@/lib/format';
import { excerpt } from '@/lib/highlight';
import { useDepartments } from '@/lib/hooks';

const MODE_HELP: Record<SearchMode, string> = {
  HYBRID: 'Meaning and exact words combined (recommended)',
  SEMANTIC: 'Finds passages with a similar meaning',
  KEYWORD: 'Matches the exact words you type',
};

export default function SearchPage() {
  const { user } = useAuth();
  const departments = useDepartments();

  const [query, setQuery] = useState('');
  const [mode, setMode] = useState<SearchMode>('HYBRID');
  const [department, setDepartment] = useState<DepartmentCode | ''>('');
  const [category, setCategory] = useState<DocumentCategory | ''>('');

  const [response, setResponse] = useState<SearchResponse | null>(null);
  const [submittedQuery, setSubmittedQuery] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [preview, setPreview] = useState<CitationTarget | null>(null);
  const latest = useRef(0);

  const deptName = (code: string) => departments.find((d) => d.code === code)?.name ?? titleCase(code);

  interface Options {
    mode: SearchMode;
    department: DepartmentCode | '';
    category: DocumentCategory | '';
  }

  async function execute(q: string, opts: Options) {
    const ticket = ++latest.current;
    setBusy(true);
    setError(null);
    try {
      // Only narrowing hints are sent. The server intersects `departments` with the caller's
      // allowed departments (derived from the JWT), so the client can never widen access.
      const res = unwrap(
        await api.POST('/api/v1/search', {
          body: {
            query: q,
            mode: opts.mode,
            topK: 10,
            departments: opts.department ? [opts.department] : undefined,
            categories: opts.category ? [opts.category] : undefined,
          },
        }),
      );
      if (ticket !== latest.current) return;
      setResponse(res);
      setSubmittedQuery(q);
    } catch (err) {
      if (ticket !== latest.current) return;
      setResponse(null);
      setError(
        err instanceof ApiError && err.status === 502
          ? 'Search is temporarily unavailable. Please try again in a moment.'
          : toMessage(err),
      );
    } finally {
      if (ticket === latest.current) setBusy(false);
    }
  }

  function run(e: React.FormEvent) {
    e.preventDefault();
    const q = query.trim();
    if (q) void execute(q, { mode, department, category });
  }

  // Changing mode or a filter after a search re-runs that search, so the results never go stale.
  function change(next: Partial<Options>) {
    const opts = { mode, department, category, ...next };
    setMode(opts.mode);
    setDepartment(opts.department);
    setCategory(opts.category);
    if (submittedQuery) void execute(submittedQuery, opts);
  }

  return (
    <section className="space-y-5">
      <header>
        <h1 className="text-xl font-semibold">Search</h1>
        <p className="mt-1 text-sm text-muted">Find passages across the documents you can access.</p>
      </header>

      <form onSubmit={run} className="card space-y-4 p-4" role="search">
        <div className="flex flex-col gap-2 sm:flex-row">
          <label htmlFor="q" className="sr-only">
            Search query
          </label>
          <input
            id="q"
            type="search"
            className="input flex-1"
            placeholder="e.g. how many vacation days do I get?"
            maxLength={1000}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            autoFocus
          />
          <button type="submit" className="btn btn-primary" disabled={busy || !query.trim()}>
            {busy ? 'Searching…' : 'Search'}
          </button>
        </div>

        <div className="flex flex-wrap items-end gap-4">
          <fieldset>
            <legend className="label">Mode</legend>
            <div className="inline-flex overflow-hidden rounded-md border border-line" role="radiogroup" aria-label="Search mode">
              {SEARCH_MODES.map((m) => (
                <label
                  key={m}
                  title={MODE_HELP[m]}
                  className={`cursor-pointer px-3 py-1.5 text-sm ${
                    mode === m ? 'bg-accent text-accent-ink' : 'bg-surface text-muted hover:bg-surface-2'
                  }`}
                >
                  <input
                    type="radio"
                    name="mode"
                    value={m}
                    checked={mode === m}
                    onChange={() => change({ mode: m })}
                    className="sr-only"
                  />
                  {titleCase(m)}
                </label>
              ))}
            </div>
          </fieldset>
          <div>
            <label htmlFor="s-dept" className="label">
              Department
            </label>
            <select
              id="s-dept"
              className="input"
              value={department}
              onChange={(e) => change({ department: e.target.value as DepartmentCode | '' })}
            >
              <option value="">All I can access</option>
              {(user?.allowedDepartments ?? DEPARTMENT_CODES).map((c) => (
                <option key={c} value={c}>
                  {deptName(c)}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label htmlFor="s-cat" className="label">
              Category
            </label>
            <select
              id="s-cat"
              className="input"
              value={category}
              onChange={(e) => change({ category: e.target.value as DocumentCategory | '' })}
            >
              <option value="">All</option>
              {CATEGORIES.map((c) => (
                <option key={c} value={c}>
                  {titleCase(c)}
                </option>
              ))}
            </select>
          </div>
        </div>
        <p className="text-xs text-muted">{MODE_HELP[mode]}</p>
      </form>

      {error && (
        <div role="alert" className="card p-4 text-sm text-bad">
          {error}
        </div>
      )}

      {response && (
        <div aria-live="polite" className="space-y-3">
          <p className="text-sm text-muted">
            {response.results.length === 0
              ? `No results for “${submittedQuery}”.`
              : `${response.results.length} result${response.results.length === 1 ? '' : 's'} for “${submittedQuery}” · ${response.latencyMs} ms`}
          </p>
          {response.results.length === 0 && (
            <div className="card p-6 text-sm text-muted">
              Try different words, switch to Hybrid mode, or remove the department and category filters.
            </div>
          )}
          <ul className="space-y-3">
            {response.results.map((r) => (
              <li key={r.chunkId} className="card p-4">
                <div className="flex flex-wrap items-baseline justify-between gap-2">
                  <Link href={`/documents/${r.documentId}`} className="font-medium text-accent hover:underline">
                    {r.documentTitle}
                  </Link>
                  <span className="text-xs text-muted" title="Relevance score">
                    score {formatScore(r.score)}
                  </span>
                </div>
                <div className="mt-1 flex flex-wrap gap-x-2 text-xs text-muted">
                  <span>{deptName(r.department)}</span>
                  {r.category && <span>· {titleCase(r.category)}</span>}
                  <span>· v{r.version}</span>
                  {r.pageNumber != null && <span>· page {r.pageNumber}</span>}
                  {r.sectionTitle && <span>· {r.sectionTitle}</span>}
                </div>
                <p className="mt-2 text-sm leading-relaxed">
                  <Highlighted text={excerpt(r.content, submittedQuery)} query={submittedQuery} />
                </p>
                <div className="mt-3">
                  <button
                    type="button"
                    className="btn"
                    onClick={() =>
                      setPreview({ documentId: r.documentId, chunkId: r.chunkId, documentTitle: r.documentTitle })
                    }
                  >
                    Preview passage
                  </button>
                </div>
              </li>
            ))}
          </ul>
        </div>
      )}

      {preview && <CitationPreview target={preview} query={submittedQuery} onClose={() => setPreview(null)} />}
    </section>
  );
}
