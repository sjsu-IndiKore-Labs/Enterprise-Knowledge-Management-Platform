// Runtime configuration. NEXT_PUBLIC_* values are inlined at build time (see Dockerfile build args).
// Names come from contracts/env or .env.example; do not add new ones without updating those.

function positiveNumber(raw: string | undefined, fallback: number): number {
  const n = Number(raw);
  return Number.isFinite(n) && n > 0 ? n : fallback;
}

export const config = {
  /** Base URL of core-api, without trailing slash. Paths in the contract already include /api/v1. */
  apiBaseUrl: (process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:4010').replace(/\/+$/, ''),
  appName: process.env.NEXT_PUBLIC_APP_NAME ?? 'Helios Knowledge Portal',
  maxUploadMb: positiveNumber(process.env.NEXT_PUBLIC_MAX_UPLOAD_MB, 25),
} as const;

export const maxUploadBytes = config.maxUploadMb * 1024 * 1024;

/** How often the processing tracker re-reads GET /documents/{id}. */
export const POLL_INTERVAL_MS = 3000;
