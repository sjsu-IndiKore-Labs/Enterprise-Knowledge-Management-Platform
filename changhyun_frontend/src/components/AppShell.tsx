'use client';

import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useEffect } from 'react';
import { ThemeToggle } from '@/components/ThemeToggle';
import { useAuth } from '@/lib/auth/AuthContext';
import { config } from '@/lib/config';
import { titleCase } from '@/lib/format';
import { canUpload } from '@/lib/validation';

const NAV = [
  { href: '/documents', label: 'Documents', match: (p: string) => p === '/documents' || /^\/documents\/(?!upload)/.test(p) },
  { href: '/documents/upload', label: 'Upload', match: (p: string) => p === '/documents/upload', needsUpload: true },
  { href: '/search', label: 'Search', match: (p: string) => p.startsWith('/search') },
];

export function AppShell({ children }: { children: React.ReactNode }) {
  const { user, status, logout } = useAuth();
  const pathname = usePathname();
  const router = useRouter();

  useEffect(() => {
    if (status === 'anonymous') router.replace(`/login?next=${encodeURIComponent(pathname)}`);
  }, [status, pathname, router]);

  if (status !== 'authenticated' || !user) {
    return (
      <div className="flex min-h-screen items-center justify-center text-sm text-muted" role="status">
        Loading…
      </div>
    );
  }

  const items = NAV.filter((n) => !n.needsUpload || canUpload(user));

  return (
    <div className="flex min-h-screen flex-col md:flex-row">
      <aside className="border-b border-line bg-surface md:w-56 md:shrink-0 md:border-b-0 md:border-r">
        <div className="px-4 py-4 text-base font-semibold">{config.appName}</div>
        <nav aria-label="Main" className="flex gap-1 overflow-x-auto px-2 pb-2 md:flex-col md:pb-4">
          {items.map((n) => {
            const active = n.match(pathname);
            return (
              <Link
                key={n.href}
                href={n.href}
                aria-current={active ? 'page' : undefined}
                className={`whitespace-nowrap rounded-md px-3 py-2 text-sm ${
                  active ? 'bg-accent-soft font-medium text-accent' : 'text-muted hover:bg-surface-2 hover:text-ink'
                }`}
              >
                {n.label}
              </Link>
            );
          })}
        </nav>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex flex-wrap items-center justify-end gap-3 border-b border-line bg-surface px-4 py-2.5">
          <div className="mr-auto text-sm text-muted" />
          <div className="text-right text-sm leading-tight">
            <div className="font-medium">{user.fullName}</div>
            <div className="text-xs text-muted">
              {titleCase(user.role)} · {titleCase(user.department)}
            </div>
          </div>
          <ThemeToggle />
          <button type="button" className="btn" onClick={logout}>
            Sign out
          </button>
        </header>
        <main className="mx-auto w-full max-w-6xl flex-1 p-4 md:p-6">{children}</main>
      </div>
    </div>
  );
}
