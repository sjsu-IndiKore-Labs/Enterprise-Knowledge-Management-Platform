import type { Metadata } from 'next';
import { Providers } from '@/components/Providers';
import { THEME_INIT_SCRIPT } from '@/components/ThemeToggle';
import { config } from '@/lib/config';
import './globals.css';

export const metadata: Metadata = {
  title: { default: config.appName, template: `%s · ${config.appName}` },
  description: 'Find, upload and search your organization’s knowledge.',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" suppressHydrationWarning>
      <head>
        <script dangerouslySetInnerHTML={{ __html: THEME_INIT_SCRIPT }} />
      </head>
      <body>
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
