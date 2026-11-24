// Dev-only bridge to the REAL core-api while it has no login yet (Milestone 4 on the backend side).
//
//   pnpm mock:proxy            listens on :4010 (the port NEXT_PUBLIC_API_BASE_URL already points to) - run it
//                              INSTEAD of `pnpm mock`
//   REAL_API=http://localhost:8080   where the real core-api is (default)
//
// What it fakes: POST /api/v1/auth/login and GET /api/v1/users/me (seed users, password Password123!).
// Everything else is forwarded unchanged to the real core-api, so the screens show what the real backend returns.
// The real core-api does not check tokens or departments yet, so the "user" only drives what the UI shows:
// this is NOT a security test. When core-api has real auth, stop using this and point the web app at it.
import crypto from 'node:crypto';
import http from 'node:http';
import { PASSWORD, USERS, currentUser } from './server.mjs';

const PORT = Number(process.env.MOCK_PORT || 4010);
const TARGET = new URL(process.env.REAL_API || 'http://localhost:8080');
const CORS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'Authorization, Content-Type, Accept',
  'Access-Control-Allow-Methods': 'GET, POST, PATCH, DELETE, OPTIONS',
  'Access-Control-Expose-Headers': 'Content-Disposition',
};
const tokens = new Map(); // fake token -> user

const json = (res, status, body) => {
  res.writeHead(status, { ...CORS, 'Content-Type': 'application/json' });
  res.end(JSON.stringify(body));
};
const readBody = (req) =>
  new Promise((resolve) => {
    const chunks = [];
    req.on('data', (c) => chunks.push(c));
    req.on('end', () => resolve(Buffer.concat(chunks)));
  });

const server = http.createServer(async (req, res) => {
  const path = new URL(req.url, 'http://x').pathname.replace(/\/+$/, '');
  if (req.method === 'OPTIONS') {
    res.writeHead(204, CORS);
    return res.end();
  }

  if (path === '/api/v1/auth/login' && req.method === 'POST') {
    let body = {};
    try {
      body = JSON.parse((await readBody(req)).toString() || '{}');
    } catch {
      /* fall through to 400 */
    }
    if (!body.email || !body.password) return json(res, 400, { code: 'VALIDATION_ERROR', message: 'Email and password are required.' });
    const user = USERS.find((u) => u.email.toLowerCase() === String(body.email).toLowerCase());
    if (!user || !user.active || body.password !== PASSWORD) return json(res, 401, { code: 'INVALID_CREDENTIALS', message: 'Invalid email or password.' });
    const token = crypto.randomBytes(24).toString('hex');
    tokens.set(token, user);
    return json(res, 200, { accessToken: token, tokenType: 'Bearer', expiresIn: 3600, user: currentUser(user) });
  }
  if (path === '/api/v1/users/me' && req.method === 'GET') {
    const user = tokens.get((req.headers.authorization || '').replace(/^Bearer\s+/i, ''));
    if (!user) return json(res, 401, { code: 'UNAUTHENTICATED', message: 'Sign in to continue.' });
    return json(res, 200, currentUser(user));
  }

  // Everything else goes to the real core-api, unchanged.
  const headers = { ...req.headers, host: TARGET.host };
  delete headers.connection;
  const upstream = http.request(
    { hostname: TARGET.hostname, port: TARGET.port, path: req.url, method: req.method, headers },
    (up) => {
      console.log(`-> ${req.method} ${req.url}  ${up.statusCode}`);
      res.writeHead(up.statusCode || 502, { ...up.headers, ...CORS });
      up.pipe(res);
    },
  );
  upstream.on('error', (err) => {
    console.log(`-> ${req.method} ${req.url}  unreachable (${err.code || err.message})`);
    if (!res.headersSent) json(res, 502, { code: 'BAD_GATEWAY', message: `The real core-api at ${TARGET.origin} is not reachable.` });
    else res.end();
  });
  req.pipe(upstream);
});

server.listen(PORT, () => {
  console.log(`Dev proxy on http://localhost:${PORT}  ->  real core-api ${TARGET.origin}`);
  console.log(`Faked here: login and /users/me (seed users, password ${PASSWORD}). Everything else is the real backend.`);
});
