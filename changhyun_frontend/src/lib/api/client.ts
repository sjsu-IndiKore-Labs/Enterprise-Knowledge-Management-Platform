import createClient, { type Middleware } from 'openapi-fetch';
import { config } from '@/lib/config';
import { emitUnauthorized, getToken } from '@/lib/auth/token';
import type { paths } from './schema';

// Typed client generated from contracts/openapi/core-api.yaml (`pnpm gen:api`).
const authMiddleware: Middleware = {
  onRequest({ request }) {
    const token = getToken();
    if (token) request.headers.set('Authorization', `Bearer ${token}`);
    return request;
  },
  onResponse({ request, response }) {
    // A 401 on login itself means "wrong password" and is handled by the form, not a session expiry.
    if (response.status === 401 && !new URL(request.url).pathname.endsWith('/auth/login')) {
      emitUnauthorized();
    }
    return response;
  },
};

export const api = createClient<paths>({ baseUrl: config.apiBaseUrl });
api.use(authMiddleware);
