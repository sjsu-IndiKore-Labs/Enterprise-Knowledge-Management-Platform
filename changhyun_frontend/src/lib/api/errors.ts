// Shared error body from contracts/conventions.md: { code, message, details?, traceId, timestamp }.

export interface ApiErrorBody {
  code: string;
  message: string;
  details?: Record<string, unknown>;
  traceId?: string;
  timestamp?: string;
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly traceId?: string;
  readonly details?: Record<string, unknown>;

  constructor(status: number, body: Partial<ApiErrorBody> | undefined, fallbackMessage?: string) {
    super(body?.message || fallbackMessage || defaultMessage(status));
    this.name = 'ApiError';
    this.status = status;
    this.code = body?.code ?? `HTTP_${status}`;
    this.traceId = body?.traceId;
    this.details = body?.details;
  }
}

export function defaultMessage(status: number): string {
  switch (status) {
    case 0:
      return 'Cannot reach the server. Check your connection and try again.';
    case 400:
      return 'The request was not valid.';
    case 401:
      return 'Your session has expired. Please sign in again.';
    case 403:
      return 'You do not have permission to do that.';
    case 404:
      return 'That item was not found, or you cannot access it.';
    case 413:
      return 'The file is larger than the allowed upload size.';
    case 415:
      return 'That file type is not supported. Upload a PDF or DOCX.';
    case 502:
    case 503:
      return 'A backend service is unavailable. Please try again shortly.';
    default:
      return 'Something went wrong. Please try again.';
  }
}

interface FetchResult<T> {
  data?: T;
  error?: unknown;
  response: Response;
}

/** Turns an openapi-fetch result into `data` or a thrown ApiError. */
export function unwrap<T>(result: FetchResult<T>): T {
  if (result.error !== undefined || !result.response.ok) {
    throw new ApiError(result.response.status, asErrorBody(result.error));
  }
  return result.data as T;
}

/** For endpoints that return 204 No Content. */
export function unwrapEmpty(result: FetchResult<unknown>): void {
  if (result.error !== undefined || !result.response.ok) {
    throw new ApiError(result.response.status, asErrorBody(result.error));
  }
}

function asErrorBody(value: unknown): Partial<ApiErrorBody> | undefined {
  return value && typeof value === 'object' ? (value as Partial<ApiErrorBody>) : undefined;
}

export function toMessage(err: unknown): string {
  if (err instanceof ApiError) return err.message;
  if (err instanceof Error && err.message) return err.message;
  return defaultMessage(500);
}
