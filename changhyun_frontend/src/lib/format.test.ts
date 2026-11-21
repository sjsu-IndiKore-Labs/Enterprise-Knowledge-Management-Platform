import { describe, expect, it } from 'vitest';
import { formatBytes, formatDate, titleCase } from './format';

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
