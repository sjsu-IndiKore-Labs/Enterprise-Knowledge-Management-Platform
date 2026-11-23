import { describe, expect, it } from 'vitest';
import { formatBytes, formatDate, titleCase, citationLabel } from './format';

describe('format', () => {
  it('formats byte sizes', () => {
    expect(formatBytes(512)).toBe('512 B');
    expect(formatBytes(1536)).toBe('1.5 KB');
    expect(formatBytes(25 * 1024 * 1024)).toBe('25 MB');
    expect(formatBytes(-1)).toBe('-');
  });
  it('handles missing and invalid dates', () => {
    expect(formatDate(null)).toBe('-');
    expect(formatDate('not a date')).toBe('-');
  });
  it('title-cases department codes but keeps short ones', () => {
    expect(titleCase('ENGINEERING')).toBe('Engineering');
    expect(titleCase('HR')).toBe('HR');
  });
});

describe('citationLabel', () => {
  it('shows the page for PDFs, the section for DOCX, the version as a last resort', () => {
    expect(citationLabel({ documentTitle: 'PTO Policy', pageNumber: 2, sectionTitle: 'Carryover', version: 1 })).toBe('PTO Policy · page 2');
    expect(citationLabel({ documentTitle: 'Onboarding', pageNumber: null, sectionTitle: 'Benefits Enrollment', version: 1 })).toBe('Onboarding · Benefits Enrollment');
    expect(citationLabel({ documentTitle: 'Notes', pageNumber: null, sectionTitle: null, version: 3 })).toBe('Notes · version 3');
  });
});
