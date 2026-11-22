import { describe, expect, it } from 'vitest';
import { canAdminUsers, canReadDocuments, canSeeAnalytics, canWriteDocuments, hasPermission } from './permissions';

const employee = { permissions: ['DOCUMENT_READ' as const] };
const manager = { permissions: ['DOCUMENT_READ' as const, 'DOCUMENT_WRITE' as const] };
const admin = { permissions: ['DOCUMENT_READ' as const, 'DOCUMENT_WRITE' as const, 'ANALYTICS_READ' as const, 'USER_ADMIN' as const] };

describe('permissions', () => {
  it('follows the permissions list from /users/me', () => {
    expect(canWriteDocuments(employee)).toBe(false);
    expect(canWriteDocuments(manager)).toBe(true);
    expect(canSeeAnalytics(manager)).toBe(false);
    expect(canSeeAnalytics(admin)).toBe(true);
    expect(canAdminUsers(admin)).toBe(true);
    expect(canReadDocuments(employee)).toBe(true);
  });

  it('denies everything without a user or without a permissions list', () => {
    expect(hasPermission(null, 'DOCUMENT_READ')).toBe(false);
    expect(hasPermission(undefined, 'DOCUMENT_READ')).toBe(false);
    expect(hasPermission({} as never, 'DOCUMENT_READ')).toBe(false);
  });
});
