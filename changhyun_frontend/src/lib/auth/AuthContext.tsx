'use client';

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { usePathname, useRouter } from 'next/navigation';
import { api } from '@/lib/api/client';
import { unwrap } from '@/lib/api/errors';
import type { CurrentUser } from '@/lib/api/types';
import { getToken, onUnauthorized, setToken } from './token';

type Status = 'loading' | 'authenticated' | 'anonymous';

interface AuthValue {
  user: CurrentUser | null;
  status: Status;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [status, setStatus] = useState<Status>('loading');

  // Restore the session after a reload: token from sessionStorage, profile from GET /users/me.
  useEffect(() => {
    if (!getToken()) {
      setStatus('anonymous');
      return;
    }
    let cancelled = false;
    api
      .GET('/api/v1/users/me')
      .then((res) => {
        if (cancelled) return;
        setUser(unwrap(res));
        setStatus('authenticated');
      })
      .catch(() => {
        if (cancelled) return;
        setToken(null);
        setStatus('anonymous');
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const logout = useCallback(() => {
    setToken(null);
    setUser(null);
    setStatus('anonymous');
    router.replace('/login');
  }, [router]);

  // Any 401 from an authenticated call means the token expired: back to /login, remembering where we were.
  useEffect(() => {
    return onUnauthorized(() => {
      setToken(null);
      setUser(null);
      setStatus('anonymous');
      if (pathname && !pathname.startsWith('/login')) {
        router.replace(`/login?next=${encodeURIComponent(pathname)}&expired=1`);
      }
    });
  }, [pathname, router]);

  const login = useCallback(async (email: string, password: string) => {
    const res = unwrap(await api.POST('/api/v1/auth/login', { body: { email, password } }));
    setToken(res.accessToken);
    setUser(res.user);
    setStatus('authenticated');
  }, []);

  const value = useMemo(() => ({ user, status, login, logout }), [user, status, login, logout]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
  return ctx;
}
