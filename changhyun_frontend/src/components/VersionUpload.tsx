'use client';

import { useEffect, useRef, useState } from 'react';
import { DropZone } from '@/components/DropZone';
import { useToast } from '@/components/ui/Toast';
import { toMessage } from '@/lib/api/errors';
import { uploadDocumentVersion } from '@/lib/api/upload';
import { maxUploadBytes } from '@/lib/config';
import { CHANGE_NOTE_MAX } from '@/lib/validation';

interface Props {
  documentId: string;
  /** Called after the server accepted the new version (202). */
  onUploaded: () => void;
}

export function VersionUpload({ documentId, onUploaded }: Props) {
  const toast = useToast();
  const [file, setFile] = useState<File | null>(null);
  const [fileError, setFileError] = useState<string | null>(null);
  const [note, setNote] = useState('');
  const [progress, setProgress] = useState(0);
  const [busy, setBusy] = useState(false);
  const abortRef = useRef<AbortController | null>(null);

  useEffect(() => () => abortRef.current?.abort(), []);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!file) {
      setFileError('Choose a file to upload.');
      return;
    }
    const controller = new AbortController();
    abortRef.current = controller;
    setBusy(true);
    setProgress(0);
    try {
      await uploadDocumentVersion(documentId, file, note.trim() || undefined, setProgress, controller.signal);
      toast.success('New version accepted. Processing started.');
      setFile(null);
      setNote('');
      onUploaded();
    } catch (err) {
      if (!(err instanceof DOMException && err.name === 'AbortError')) toast.error(toMessage(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit} className="space-y-3" noValidate>
      <DropZone
        file={file}
        onFile={(f) => {
          setFile(f);
          setFileError(null);
        }}
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
      <div>
        <label htmlFor="changeNote" className="label">
          What changed? <span className="font-normal text-muted">(optional)</span>
        </label>
        <input
          id="changeNote"
          className="input"
          maxLength={CHANGE_NOTE_MAX}
          value={note}
          disabled={busy}
          onChange={(e) => setNote(e.target.value)}
        />
      </div>
      {busy && (
        <div
          role="progressbar"
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={Math.round(progress * 100)}
          className="h-2 overflow-hidden rounded-full bg-surface-2"
        >
          <div className="h-full bg-accent transition-[width]" style={{ width: `${progress * 100}%` }} />
        </div>
      )}
      <button type="submit" className="btn btn-primary" disabled={busy}>
        {busy ? 'Uploading…' : 'Upload new version'}
      </button>
    </form>
  );
}
