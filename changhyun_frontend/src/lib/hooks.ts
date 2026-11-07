'use client';

import { useCallback, useEffect, useState } from 'react';
import { api } from '@/lib/api/client';
import { unwrap } from '@/lib/api/errors';
import type { Department } from '@/lib/api/types';

export interface AsyncState<T> {
  data: T | undefined;
  error: unknown;
  loading: boolean;
  reload: () => void;
}

/**
 * Runs `fn` on mount and whenever it changes. Wrap `fn` in useCallback so its identity
 * only changes when its inputs do. Stale responses are ignored.
 */
export function useAsync<T>(fn: () => Promise<T>): AsyncState<T> {
  const [data, setData] = useState<T | undefined>(undefined);
  const [error, setError] = useState<unknown>(undefined);
  const [loading, setLoading] = useState(true);
  const [tick, setTick] = useState(0);

  useEffect(() => {
    // Each run owns its own flag: cleanup (unmount or new inputs) makes a stale response a no-op.
    let stale = false;
    setLoading(true);
    fn().then(
      (value) => {
        if (stale) return;
        setData(value);
        setError(undefined);
        setLoading(false);
      },
      (err) => {
        if (stale) return;
        setError(err);
        setLoading(false);
      },
    );
    return () => {
      stale = true;
    };
  }, [fn, tick]);

  const reload = useCallback(() => setTick((t) => t + 1), []);
  return { data, error, loading, reload };
}

/** Returns `value` after it has stopped changing for `delayMs` (for search-as-you-type filters). */
export function useDebounced<T>(value: T, delayMs = 300): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setDebounced(value), delayMs);
    return () => clearTimeout(t);
  }, [value, delayMs]);
  return debounced;
}

let departmentsCache: Department[] | null = null;

/** GET /api/v1/departments, cached for the page session. */
export function useDepartments(): Department[] {
  const [departments, setDepartments] = useState<Department[]>(departmentsCache ?? []);
  useEffect(() => {
    if (departmentsCache) return;
    let cancelled = false;
    api
      .GET('/api/v1/departments')
      .then((res) => {
        departmentsCache = unwrap(res);
        if (!cancelled) setDepartments(departmentsCache);
      })
      .catch(() => {
        /* dropdowns fall back to the enum values in lib/api/types.ts */
      });
    return () => {
      cancelled = true;
    };
  }, []);
  return departments;
}
