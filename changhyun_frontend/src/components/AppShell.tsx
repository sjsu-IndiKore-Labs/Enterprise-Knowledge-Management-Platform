'use client';

import Link from 'next/link';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { Suspense, useEffect } from 'react';
import { BuildingIcon, FileIcon, LayersIcon, LogoutIcon, SearchIcon, SparkIcon, UploadIcon } from '@/components/icons';
import { ConversationList } from '@/components/chat/ConversationList';
import { ThemeToggle } from '@/components/ThemeToggle';
import { useAuth } from '@/lib/auth/AuthContext';
import { ChatSessionProvider, useChatSession } from '@/lib/chat/ChatSession';
import { config } from '@/lib/config';
import { titleCase } from '@/lib/format';
import { useDepartments } from '@/lib/hooks';
import type { CurrentUser } from '@/lib/api/types';
import { hasPermission } from '@/lib/permissions';

/** Page name shown in the top bar. */
function sectionLabel(pathname: string): string {
  if (pathname.startsWith('/chat')) return 'Assistant';
  if (pathname === '/documents/upload') return 'Upload';
  if (pathname.startsWith('/documents/')) return 'Document';
  if (pathname.startsWith('/documents')) return 'Documents';
  if (pathname.startsWith('/search')) return 'Search';
  return config.appName;
}

function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  return ((parts[0]?.[0] ?? '?') + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase();
}

const itemBase = 'flex items-center gap-2.5 whitespace-nowrap rounded-md px-2.5 py-1.5 text-sm transition-colors';
const itemClass = (active: boolean) =>
  `${itemBase} ${active ? 'bg-surface-2 font-medium text-ink' : 'text-muted hover:bg-surface-2 hover:text-ink'}`;

// Entries are hidden for users without the permission (UI hint only: the server enforces access).
function SidebarNav({ user }: { user: CurrentUser }) {
  const pathname = usePathname();
  const params = useSearchParams();
  const departments = useDepartments();
  const activeDept = params.get('department');
  const onList = pathname === '/documents';
  const canRead = hasPermission(user, 'DOCUMENT_READ');
  const canWrite = hasPermission(user, 'DOCUMENT_WRITE');
  const deptName = (code: string) => departments.find((d) => d.code === code)?.name ?? titleCase(code);
  const deptCodes = user.allowedDepartments ?? [];
  const { chat, conversations, select, startNew, remove } = useChatSession();

  return (
    <nav aria-label="Main" className="flex min-h-0 flex-1 flex-col gap-1 overflow-y-auto px-3 pb-3">
      {canRead && (
        <div className="mb-2 grid grid-cols-2 gap-2">
          <Link
            href="/search"
            aria-current={pathname.startsWith('/search') ? 'page' : undefined}
            className={`flex items-center justify-center gap-2 rounded-lg border border-line bg-surface px-3 py-2 text-sm font-medium transition-colors hover:bg-surface-2 ${
              pathname.startsWith('/search') ? 'ring-1 ring-accent' : ''
            }`}
          >
            <SearchIcon /> Search
          </Link>
          <Link
            href="/chat"
            aria-current={pathname.startsWith('/chat') ? 'page' : undefined}
            className={`flex items-center justify-center gap-2 rounded-lg bg-accent-soft px-3 py-2 text-sm font-medium text-accent transition-colors hover:opacity-80 ${
              pathname.startsWith('/chat') ? 'ring-1 ring-accent' : ''
            }`}
          >
            <SparkIcon /> Ask
          </Link>
        </div>
      )}

      {canRead && (
        <Link href="/documents" aria-current={onList && !activeDept ? 'page' : undefined} className={itemClass(onList && !activeDept)}>
          <LayersIcon /> Documents
        </Link>
      )}

      {canRead && (
        <div className="hidden md:block">
          <div className="px-2.5 pb-1 pt-4 text-[11px] font-semibold uppercase tracking-wider text-muted">Chats</div>
          <div className="max-h-72 overflow-y-auto">
            <ConversationList
              items={conversations.items}
              loading={conversations.loading}
              error={conversations.error}
              activeId={chat.conversationId}
              onSelect={select}
              onNew={startNew}
              onDelete={remove}
              onRetry={conversations.reload}
            />
          </div>
        </div>
      )}

      {canRead && deptCodes.length > 0 && (
        <div className="hidden md:block">
          <div className="px-2.5 pb-1 pt-4 text-[11px] font-semibold uppercase tracking-wider text-muted">Departments</div>
          <ul className="space-y-0.5">
            {deptCodes.map((code) => {
              const active = onList && activeDept === code;
              return (
                <li key={code}>
                  <Link
                    href={`/documents?department=${code}`}
                    aria-current={active ? 'page' : undefined}
                    className={itemClass(active)}
                  >
                    <BuildingIcon /> <span className="truncate">{deptName(code)}</span>
                  </Link>
                </li>
              );
            })}
          </ul>
        </div>
      )}

      {canWrite && (
        <div className="md:mt-auto md:pt-4">
          <Link
            href="/documents/upload"
            aria-current={pathname === '/documents/upload' ? 'page' : undefined}
            className={itemClass(pathname === '/documents/upload')}
          >
            <UploadIcon /> Upload
          </Link>
        </div>
      )}
    </nav>
  );
}

export function AppShell({ children }: { children: React.ReactNode }) {
  const { user, status } = useAuth();
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

  // Mounted only while signed in, so chat state never outlives the session.
  return (
    <ChatSessionProvider>
      <Shell user={user}>{children}</Shell>
    </ChatSessionProvider>
  );
}

function Shell({ user, children }: { user: CurrentUser; children: React.ReactNode }) {
  const { logout } = useAuth();
  const pathname = usePathname();

  return (
    <div className="flex min-h-screen flex-col md:flex-row">
      <aside className="flex flex-col border-b border-line bg-sidebar md:sticky md:top-3 md:m-3 md:mr-0 md:h-[calc(100dvh-1.5rem)] md:w-64 md:shrink-0 md:rounded-xl md:border">
        <div className="flex items-center gap-2.5 px-5 py-4 md:pt-5">
          <span aria-hidden className="flex h-6 w-6 shrink-0 items-center justify-center rounded-md bg-accent text-xs font-bold text-accent-ink">
            {config.appName.trim()[0]?.toUpperCase() ?? 'E'}
          </span>
          <span className="truncate text-[15px] font-semibold tracking-tight">{config.appName}</span>
        </div>
        <Suspense fallback={null}>
          <SidebarNav user={user} />
        </Suspense>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex flex-wrap items-center gap-3 bg-bg px-5 py-2 md:h-14 md:flex-nowrap md:px-10 md:py-0">
          <div className="mr-auto flex items-center gap-2 text-sm text-muted">
            <FileIcon />
            <span>{sectionLabel(pathname)}</span>
          </div>
          <ThemeToggle />
          <div className="flex items-center gap-2.5">
            <span
              aria-hidden
              className="flex h-8 w-8 items-center justify-center rounded-full bg-accent-soft text-xs font-semibold text-accent"
            >
              {initials(user.fullName)}
            </span>
            <div className="hidden text-sm leading-tight sm:block">
              <div className="font-medium">{user.fullName}</div>
              <div className="text-xs text-muted">
                {titleCase(user.role)} · {titleCase(user.department)}
              </div>
            </div>
          </div>
          <button
            type="button"
            onClick={logout}
            aria-label="Sign out"
            title="Sign out"
            className="rounded-md p-2 text-muted transition-colors hover:bg-surface-2 hover:text-ink"
          >
            <LogoutIcon size={18} />
          </button>
        </header>
        <main className="mx-auto w-full max-w-5xl flex-1 p-5 md:px-10 md:pb-8 md:pt-4">{children}</main>
      </div>
    </div>
  );
}
