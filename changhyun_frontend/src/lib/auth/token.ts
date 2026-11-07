// Access token storage: in memory first, sessionStorage as a reload survivor (never localStorage).
// See contracts/auth.md. Storage access is wrapped because it can throw (private mode, SSR).

const KEY = 'ekm.accessToken';

let memoryToken: string | null = null;
const unauthorizedListeners = new Set<() => void>();

export function getToken(): string | null {
  if (memoryToken) return memoryToken;
  try {
    const stored = typeof window !== 'undefined' ? window.sessionStorage.getItem(KEY) : null;
    if (stored) memoryToken = stored;
  } catch {
    /* storage unavailable: memory only */
  }
  return memoryToken;
}

export function setToken(token: string | null): void {
  memoryToken = token;
  try {
    if (typeof window === 'undefined') return;
    if (token) window.sessionStorage.setItem(KEY, token);
    else window.sessionStorage.removeItem(KEY);
  } catch {
    /* ignore */
  }
}

/** Called by the API client on any 401 from an authenticated call. Returns an unsubscribe fn. */
export function onUnauthorized(listener: () => void): () => void {
  unauthorizedListeners.add(listener);
  return () => {
    unauthorizedListeners.delete(listener);
  };
}

export function emitUnauthorized(): void {
  unauthorizedListeners.forEach((listener) => listener());
}
