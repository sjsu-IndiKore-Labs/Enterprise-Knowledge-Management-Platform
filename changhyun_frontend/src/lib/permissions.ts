import type { CurrentUser, Permission } from '@/lib/api/types';

type WithPermissions = Pick<CurrentUser, 'permissions'> | null | undefined;

/**
 * `permissions` from GET /users/me are UI hints only (contracts/auth.md): they decide what the portal
 * shows or disables. The server still checks every request, so hiding a button is never the protection.
 */
export function hasPermission(user: WithPermissions, permission: Permission): boolean {
  return user?.permissions?.includes(permission) ?? false;
}

export const canReadDocuments = (user: WithPermissions): boolean => hasPermission(user, 'DOCUMENT_READ');
export const canWriteDocuments = (user: WithPermissions): boolean => hasPermission(user, 'DOCUMENT_WRITE');
export const canSeeAnalytics = (user: WithPermissions): boolean => hasPermission(user, 'ANALYTICS_READ');
export const canAdminUsers = (user: WithPermissions): boolean => hasPermission(user, 'USER_ADMIN');
