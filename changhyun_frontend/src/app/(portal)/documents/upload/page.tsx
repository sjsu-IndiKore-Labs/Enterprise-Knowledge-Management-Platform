'use client';

import Link from 'next/link';
import { useEffect, useMemo, useRef, useState } from 'react';
import { DropZone } from '@/components/DropZone';
import { ProcessingTracker } from '@/components/ProcessingTracker';
import { useToast } from '@/components/ui/Toast';
import { toMessage } from '@/lib/api/errors';
import { uploadDocument } from '@/lib/api/upload';
import { CATEGORIES, DEPARTMENT_CODES, type Document, type DepartmentCode } from '@/lib/api/types';
import { useAuth } from '@/lib/auth/AuthContext';
import { maxUploadBytes } from '@/lib/config';
import { titleCase } from '@/lib/format';
import { useDepartments } from '@/lib/hooks';
import {
  AUTHOR_MAX,
  TITLE_MAX,
  canUpload,
  parseTags,
  titleFromFilename,
  uploadableDepartments,
  validateUploadForm,
} from '@/lib/validation';

type Phase = 'form' | 'uploading' | 'tracking';

export default function UploadPage() {
  const { user } = useAuth();
  const toast = useToast();
  const departments = useDepartments();

  const [phase, setPhase] = useState<Phase>('form');
  const [file, setFile] = useState<File | null>(null);
  const [fileError, setFileError] = useState<string | null>(null);
  const [title, setTitle] = useState('');
  const [titleTouched, setTitleTouched] = useState(false);
  const [department, setDepartment] = useState('');
  const [category, setCategory] = useState('');
  const [author, setAuthor] = useState('');
  const [tags, setTags] = useState('');
  const [errors, setErrors] = useState<Record<string, string | undefined>>({});
  const [progress, setProgress] = useState(0);
  const [created, setCreated] = useState<Document | null>(null);
  const abortRef = useRef<AbortController | null>(null);

  const allCodes = useMemo<DepartmentCode[]>(
    () => (departments.length ? departments.map((d) => d.code) : DEPARTMENT_CODES),
    [departments],
  );
  const allowed = useMemo(() => uploadableDepartments(user, allCodes), [user, allCodes]);
  const deptLabel = (code: string) => departments.find((d) => d.code === code)?.name ?? titleCase(code);

  // Defaults: MANAGER is locked to their own department, ADMIN starts on theirs; author is the signed-in user.
  useEffect(() => {
    if (!user) return;
    setDepartment((cur) => cur || (allowed.includes(user.department) ? user.department : (allowed[0] ?? '')));
    setAuthor((cur) => cur || user.fullName);
  }, [user, allowed]);

  // Cancel an in-flight upload if the page is left.
  useEffect(() => () => abortRef.current?.abort(), []);

  if (!canUpload(user)) {
    return (
      <section className="card p-6">
        <h1 className="text-lg font-semibold">Upload a document</h1>
        <p className="mt-2 text-sm text-muted">
          Only managers and admins can upload documents. Ask a manager in your department to add it.
        </p>
      </section>
    );
  }

  function pickFile(f: File) {
    setFile(f);
    setFileError(null);
    if (!titleTouched) setTitle(titleFromFilename(f.name));
  }

  function reset() {
    setPhase('form');
    setFile(null);
    setFileError(null);
    setTitle('');
    setTitleTouched(false);
    setCategory('');
    setTags('');
    setErrors({});
    setProgress(0);
    setCreated(null);
  }

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    const found = validateUploadForm({ title, department, category, author });
    setErrors(found);
    if (!file) {
      setFileError('Choose a file to upload.');
      return;
    }
    if (Object.keys(found).length) return;

    const controller = new AbortController();
    abortRef.current = controller;
    setPhase('uploading');
    setProgress(0);
    try {
      const doc = await uploadDocument(
        file,
        {
          title: title.trim(),
          department,
          category,
          author: author.trim() || undefined,
          tags: parseTags(tags).join(',') || undefined,
        },
        setProgress,
        controller.signal,
      );
      setCreated(doc);
      setPhase('tracking');
      toast.success('Upload accepted. Processing started.');
    } catch (err) {
      setPhase('form');
      if (err instanceof DOMException && err.name === 'AbortError') return;
      toast.error(toMessage(err));
    }
  }

  const busy = phase === 'uploading';

  return (
    <section className="mx-auto max-w-2xl space-y-5">
      <header>
        <h1 className="text-xl font-semibold">Upload a document</h1>
        <p className="mt-1 text-sm text-muted">PDF or DOCX, one file at a time.</p>
      </header>

      {phase === 'tracking' && created ? (
        <div className="space-y-4">
          <div className="card flex flex-wrap items-center gap-3 px-4 py-3">
            <div className="min-w-0 flex-1">
              <div className="truncate text-sm font-medium">{created.title}</div>
              <div className="text-xs text-muted">
                {deptLabel(created.department)} · {titleCase(created.category)}
              </div>
            </div>
            <Link href={`/documents/${created.id}`} className="btn">
              View document
            </Link>
          </div>
          <ProcessingTracker documentId={created.id} initialStatus={created.status} />
          <button type="button" className="btn" onClick={reset}>
            Upload another
          </button>
        </div>
      ) : (
        <form onSubmit={onSubmit} className="space-y-5" noValidate>
          <div>
            <DropZone
              file={file}
              onFile={pickFile}
              onClear={() => setFile(null)}
              onError={setFileError}
              maxBytes={maxUploadBytes}
              disabled={busy}
            />
            {fileError && (
              <p role="alert" className="field-error">
                {fileError}
              </p>
            )}
          </div>

          <div className="card space-y-4 p-4">
            <div>
              <label htmlFor="title" className="label">
                Title
              </label>
              <input
                id="title"
                className="input"
                value={title}
                maxLength={TITLE_MAX + 50}
                disabled={busy}
                onChange={(e) => {
                  setTitle(e.target.value);
                  setTitleTouched(true);
                }}
              />
              {errors.title && <p className="field-error">{errors.title}</p>}
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
              <div>
                <label htmlFor="department" className="label">
                  Department
                </label>
                <select
                  id="department"
                  className="input"
                  value={department}
                  disabled={busy || allowed.length <= 1}
                  onChange={(e) => setDepartment(e.target.value)}
                >
                  {allowed.length > 1 && <option value="">Select…</option>}
                  {allowed.map((code) => (
                    <option key={code} value={code}>
                      {deptLabel(code)}
                    </option>
                  ))}
                </select>
                {user?.role === 'MANAGER' && (
                  <p className="mt-1 text-xs text-muted">Managers can upload to their own department.</p>
                )}
                {errors.department && <p className="field-error">{errors.department}</p>}
              </div>
              <div>
                <label htmlFor="category" className="label">
                  Category
                </label>
                <select
                  id="category"
                  className="input"
                  value={category}
                  disabled={busy}
                  onChange={(e) => setCategory(e.target.value)}
                >
                  <option value="">Select…</option>
                  {CATEGORIES.map((c) => (
                    <option key={c} value={c}>
                      {titleCase(c)}
                    </option>
                  ))}
                </select>
                {errors.category && <p className="field-error">{errors.category}</p>}
              </div>
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
              <div>
                <label htmlFor="author" className="label">
                  Author <span className="font-normal text-muted">(optional)</span>
                </label>
                <input
                  id="author"
                  className="input"
                  value={author}
                  maxLength={AUTHOR_MAX + 20}
                  disabled={busy}
                  onChange={(e) => setAuthor(e.target.value)}
                />
                {errors.author && <p className="field-error">{errors.author}</p>}
              </div>
              <div>
                <label htmlFor="tags" className="label">
                  Tags <span className="font-normal text-muted">(comma-separated)</span>
                </label>
                <input
                  id="tags"
                  className="input"
                  placeholder="leave, pto, benefits"
                  value={tags}
                  disabled={busy}
                  onChange={(e) => setTags(e.target.value)}
                />
              </div>
            </div>
          </div>

          {busy && (
            <div aria-live="polite">
              <div className="mb-1 flex justify-between text-xs text-muted">
                <span>Uploading…</span>
                <span>{Math.round(progress * 100)}%</span>
              </div>
              <div
                role="progressbar"
                aria-valuemin={0}
                aria-valuemax={100}
                aria-valuenow={Math.round(progress * 100)}
                className="h-2 overflow-hidden rounded-full bg-surface-2"
              >
                <div className="h-full bg-accent transition-[width]" style={{ width: `${progress * 100}%` }} />
              </div>
            </div>
          )}

          <div className="flex gap-2">
            <button type="submit" className="btn btn-primary" disabled={busy}>
              {busy ? 'Uploading…' : 'Upload'}
            </button>
            {busy && (
              <button type="button" className="btn" onClick={() => abortRef.current?.abort()}>
                Cancel
              </button>
            )}
          </div>
        </form>
      )}
    </section>
  );
}
