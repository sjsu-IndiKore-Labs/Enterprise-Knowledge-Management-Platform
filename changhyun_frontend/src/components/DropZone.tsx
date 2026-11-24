'use client';

import { useDropzone, type FileRejection } from 'react-dropzone';
import { formatBytes } from '@/lib/format';
import { ACCEPTED_MIME, validateFile } from '@/lib/validation';

interface Props {
  file: File | null;
  onFile: (file: File) => void;
  onClear: () => void;
  onError: (message: string) => void;
  maxBytes: number;
  disabled?: boolean;
}

/** One PDF/DOCX at a time. Validation messages come from lib/validation so they match the form rules. */
export function DropZone({ file, onFile, onClear, onError, maxBytes, disabled }: Props) {
  function handleDrop(accepted: File[], rejected: FileRejection[]) {
    if (rejected.length > 1 || (rejected.length === 0 && accepted.length > 1)) {
      onError('Upload one file at a time.');
      return;
    }
    const candidate = accepted[0] ?? rejected[0]?.file;
    if (!candidate) return;
    const problem = validateFile(candidate, maxBytes);
    if (problem) {
      onError(problem);
      return;
    }
    if (rejected.length) {
      onError('Only PDF and DOCX files are supported.');
      return;
    }
    onFile(candidate);
  }

  const { getRootProps, getInputProps, isDragActive, open } = useDropzone({
    onDrop: handleDrop,
    accept: ACCEPTED_MIME,
    multiple: false,
    disabled,
    noClick: true,
    noKeyboard: true,
  });

  if (file) {
    return (
      <div className="card flex items-center gap-3 px-4 py-3" data-testid="selected-file">
        <div className="min-w-0 flex-1">
          <div className="truncate text-sm font-medium">{file.name}</div>
          <div className="text-xs text-muted">{formatBytes(file.size)}</div>
        </div>
        <button type="button" className="btn" onClick={onClear} disabled={disabled}>
          Remove
        </button>
      </div>
    );
  }

  return (
    <div
      {...getRootProps()}
      className={`flex min-h-40 flex-col items-center justify-center gap-2 rounded-lg border-2 border-dashed px-4 py-8 text-center transition-colors ${
        isDragActive ? 'border-accent bg-accent-soft' : 'border-line bg-surface'
      } ${disabled ? 'opacity-50' : ''}`}
    >
      <input {...getInputProps()} aria-label="Choose a PDF or DOCX file" />
      <p className="text-sm font-medium">{isDragActive ? 'Drop the file here' : 'Drag a file here'}</p>
      <p className="text-xs text-muted">PDF or DOCX, up to {formatBytes(maxBytes)}</p>
      <button type="button" className="btn mt-1" onClick={open} disabled={disabled}>
        Choose file
      </button>
    </div>
  );
}
