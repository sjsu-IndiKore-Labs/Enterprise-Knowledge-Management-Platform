import type { CurrentUser, DepartmentCode } from '@/lib/api/types';
import { formatBytes } from '@/lib/format';

export const ACCEPTED_MIME = {
  'application/pdf': ['.pdf'],
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document': ['.docx'],
} as const;

const ALLOWED_EXT = ['.pdf', '.docx'];
const ALLOWED_MIME = Object.keys(ACCEPTED_MIME);

export const TITLE_MAX = 200;
export const AUTHOR_MAX = 120;
export const CHANGE_NOTE_MAX = 500;

/** Returns an error message, or null when the file is acceptable. */
export function validateFile(file: File, maxBytes: number): string | null {
  const name = file.name.toLowerCase();
  const extOk = ALLOWED_EXT.some((ext) => name.endsWith(ext));
  const mimeOk = file.type === '' || ALLOWED_MIME.includes(file.type);
  if (!extOk || !mimeOk) return 'Only PDF and DOCX files are supported.';
  if (file.size === 0) return 'That file is empty.';
  if (file.size > maxBytes) {
    return `That file is ${formatBytes(file.size)}. The limit is ${formatBytes(maxBytes)}.`;
  }
  return null;
}

/** "Employee Handbook v3.pdf" -> "Employee Handbook v3" */
export function titleFromFilename(filename: string): string {
  return filename
    .replace(/\.(pdf|docx)$/i, '')
    .replace(/[_]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, TITLE_MAX);
}

/** Comma-separated input -> trimmed, de-duplicated (case-insensitive), non-empty tags. */
export function parseTags(input: string): string[] {
  const seen = new Set<string>();
  const out: string[] = [];
  for (const raw of input.split(',')) {
    const tag = raw.trim();
    const key = tag.toLowerCase();
    if (tag && !seen.has(key)) {
      seen.add(key);
      out.push(tag);
    }
  }
  return out;
}

export function canUpload(user: Pick<CurrentUser, 'role'> | null | undefined): boolean {
  return user?.role === 'ADMIN' || user?.role === 'MANAGER';
}

/** MANAGER: own department only. ADMIN: any. EMPLOYEE: none. UI hint only, the server enforces it. */
export function uploadableDepartments(
  user: Pick<CurrentUser, 'role' | 'department'> | null | undefined,
  all: DepartmentCode[],
): DepartmentCode[] {
  if (!user) return [];
  if (user.role === 'ADMIN') return all;
  if (user.role === 'MANAGER') return [user.department];
  return [];
}

export interface UploadFormValues {
  title: string;
  department: string;
  category: string;
  author: string;
}

export function validateUploadForm(v: UploadFormValues): Partial<Record<keyof UploadFormValues, string>> {
  const errors: Partial<Record<keyof UploadFormValues, string>> = {};
  if (!v.title.trim()) errors.title = 'Title is required.';
  else if (v.title.trim().length > TITLE_MAX) errors.title = `Title must be ${TITLE_MAX} characters or fewer.`;
  if (!v.department) errors.department = 'Choose a department.';
  if (!v.category) errors.category = 'Choose a category.';
  if (v.author.trim().length > AUTHOR_MAX) errors.author = `Author must be ${AUTHOR_MAX} characters or fewer.`;
  return errors;
}
