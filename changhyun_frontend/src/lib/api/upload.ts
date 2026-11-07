import { config } from '@/lib/config';
import { getToken, emitUnauthorized } from '@/lib/auth/token';
import { ApiError, type ApiErrorBody } from './errors';
import type { Document, DocumentVersion } from './types';

// fetch() cannot report upload progress, so multipart uploads use XMLHttpRequest.

export interface UploadFields {
  title: string;
  department: string;
  category: string;
  author?: string;
  /** Comma-separated, per the contract. */
  tags?: string;
}

function postForm<T>(
  path: string,
  form: FormData,
  onProgress?: (fraction: number) => void,
  signal?: AbortSignal,
): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('POST', `${config.apiBaseUrl}${path}`);
    const token = getToken();
    if (token) xhr.setRequestHeader('Authorization', `Bearer ${token}`);
    xhr.setRequestHeader('Accept', 'application/json');

    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable && onProgress) onProgress(e.loaded / e.total);
    };
    xhr.onerror = () => reject(new ApiError(0, undefined));
    xhr.onabort = () => reject(new DOMException('Upload cancelled', 'AbortError'));
    xhr.onload = () => {
      let body: unknown;
      try {
        body = xhr.responseText ? JSON.parse(xhr.responseText) : undefined;
      } catch {
        body = undefined;
      }
      if (xhr.status >= 200 && xhr.status < 300) {
        onProgress?.(1);
        resolve(body as T);
        return;
      }
      if (xhr.status === 401) emitUnauthorized();
      reject(new ApiError(xhr.status, body as Partial<ApiErrorBody> | undefined));
    };

    if (signal) {
      if (signal.aborted) {
        reject(new DOMException('Upload cancelled', 'AbortError'));
        return;
      }
      signal.addEventListener('abort', () => xhr.abort(), { once: true });
    }
    xhr.send(form);
  });
}

/** POST /api/v1/documents (202 -> Document, status QUEUED). */
export function uploadDocument(
  file: File,
  fields: UploadFields,
  onProgress?: (fraction: number) => void,
  signal?: AbortSignal,
): Promise<Document> {
  const form = new FormData();
  form.append('file', file);
  form.append('title', fields.title);
  form.append('department', fields.department);
  form.append('category', fields.category);
  if (fields.author) form.append('author', fields.author);
  if (fields.tags) form.append('tags', fields.tags);
  return postForm<Document>('/api/v1/documents', form, onProgress, signal);
}

/** POST /api/v1/documents/{id}/versions (202 -> DocumentVersion). */
export function uploadDocumentVersion(
  documentId: string,
  file: File,
  changeNote: string | undefined,
  onProgress?: (fraction: number) => void,
  signal?: AbortSignal,
): Promise<DocumentVersion> {
  const form = new FormData();
  form.append('file', file);
  if (changeNote) form.append('changeNote', changeNote);
  return postForm<DocumentVersion>(
    `/api/v1/documents/${encodeURIComponent(documentId)}/versions`,
    form,
    onProgress,
    signal,
  );
}
