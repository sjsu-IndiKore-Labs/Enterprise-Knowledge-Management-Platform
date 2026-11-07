'use client';

import { useEffect, useState } from 'react';

const KEY = 'ekm.theme';
type Theme = 'light' | 'dark';

// The inline script in app/layout.tsx sets data-theme before first paint; this only toggles it.
export function ThemeToggle() {
  const [theme, setTheme] = useState<Theme>('light');

  useEffect(() => {
    setTheme(document.documentElement.dataset.theme === 'dark' ? 'dark' : 'light');
  }, []);

  function toggle() {
    const next: Theme = theme === 'dark' ? 'light' : 'dark';
    document.documentElement.dataset.theme = next;
    setTheme(next);
    try {
      window.localStorage.setItem(KEY, next);
    } catch {
      /* preference just won't persist */
    }
  }

  return (
    <button
      type="button"
      onClick={toggle}
      aria-label={`Switch to ${theme === 'dark' ? 'light' : 'dark'} theme`}
      className="rounded-md border border-line px-2.5 py-1.5 text-sm text-muted hover:bg-surface-2 hover:text-ink"
    >
      {theme === 'dark' ? 'Light' : 'Dark'}
    </button>
  );
}

export const THEME_INIT_SCRIPT = `try{var t=localStorage.getItem('${KEY}');if(!t){t=matchMedia('(prefers-color-scheme: dark)').matches?'dark':'light'}document.documentElement.dataset.theme=t}catch(e){}`;
