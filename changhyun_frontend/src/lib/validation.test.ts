import { describe, expect, it } from 'vitest';
import {
  canUpload,
  parseTags,
  titleFromFilename,
  uploadableDepartments,
  validateFile,
  validateUploadForm,
} from './validation';

const MB = 1024 * 1024;
const file = (name: string, size: number, type = '') => {
  const f = new File(['x'], name, { type });
  Object.defineProperty(f, 'size', { value: size });
  return f;
};

describe('validateFile', () => {
  it('accepts PDF and DOCX within the limit', () => {
    expect(validateFile(file('a.pdf', 1000, 'application/pdf'), 25 * MB)).toBeNull();
    expect(
      validateFile(
        file('a.DOCX', 1000, 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'),
        25 * MB,
      ),
    ).toBeNull();
  });

  it('rejects other types, empty and oversized files', () => {
    expect(validateFile(file('a.txt', 10, 'text/plain'), 25 * MB)).toMatch(/PDF and DOCX/);
    expect(validateFile(file('a.pdf', 0, 'application/pdf'), 25 * MB)).toMatch(/empty/);
    expect(validateFile(file('a.pdf', 26 * MB, 'application/pdf'), 25 * MB)).toMatch(/limit is 25 MB/);
  });
});

describe('titleFromFilename', () => {
  it('strips the extension and underscores', () => {
    expect(titleFromFilename('PTO_and_Leave__Policy.pdf')).toBe('PTO and Leave Policy');
  });
});

describe('parseTags', () => {
  it('trims, drops empties and de-duplicates case-insensitively', () => {
    expect(parseTags(' leave, PTO ,,pto, benefits ')).toEqual(['leave', 'PTO', 'benefits']);
  });
});

describe('role rules', () => {
  const all = ['GENERAL', 'HR', 'ENGINEERING'] as const;
  it('limits MANAGER to own department, ADMIN to all, EMPLOYEE to none', () => {
    expect(uploadableDepartments({ role: 'MANAGER', department: 'HR' }, [...all])).toEqual(['HR']);
    expect(uploadableDepartments({ role: 'ADMIN', department: 'GENERAL' }, [...all])).toEqual([...all]);
    expect(uploadableDepartments({ role: 'EMPLOYEE', department: 'HR' }, [...all])).toEqual([]);
    expect(uploadableDepartments(null, [...all])).toEqual([]);
  });
  it('only MANAGER and ADMIN can upload', () => {
    expect(canUpload({ role: 'EMPLOYEE' })).toBe(false);
    expect(canUpload({ role: 'MANAGER' })).toBe(true);
    expect(canUpload({ role: 'ADMIN' })).toBe(true);
  });
});

describe('validateUploadForm', () => {
  it('requires title, department and category', () => {
    expect(validateUploadForm({ title: ' ', department: '', category: '', author: '' })).toEqual({
      title: 'Title is required.',
      department: 'Choose a department.',
      category: 'Choose a category.',
    });
  });
  it('enforces max lengths from the contract', () => {
    const e = validateUploadForm({
      title: 'x'.repeat(201),
      department: 'HR',
      category: 'POLICY',
      author: 'y'.repeat(121),
    });
    expect(e.title).toMatch(/200/);
    expect(e.author).toMatch(/120/);
  });
});
