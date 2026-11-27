// k6 load test for POST /api/v1/chat. Owner: Manali.
//   k6 run -e API=http://localhost:8080 -e PASSWORD=<devPassword> manali_devops/load-test/chat.js
//   docker run --rm -v "$PWD:/repo:ro" -w /repo/manali_devops/load-test -e API=... -e PASSWORD=... grafana/k6:0.54.0 run chat.js
//
// core-api limits chat to 20 requests/min per user (ekm.rate-limit.chat-per-minute). The queries come from 6 seed users,
// so 20 VUs WILL hit that limit. 429 is therefore counted as an *expected* response (metric `rate_limited`), not
// as a failure: `http_req_failed` and the p95 only measure requests core-api actually served.
// To measure raw capacity instead, start core-api with EKM_RATE_LIMIT_CHAT_PER_MINUTE=100000 (never on a shared env).
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';
import { SharedArray } from 'k6/data';
import papaparse from 'https://jslib.k6.io/papaparse/5.1.1/index.js';

const API = __ENV.API || 'http://localhost:8080';
const PASSWORD = __ENV.PASSWORD || 'Password123!'; // dev seed password: resources/seed/users.json (devPassword)
const queries = new SharedArray('queries', () =>
  papaparse.parse(open('../../resources/load-test/queries.csv'), { header: true }).data.filter((r) => r.askedBy));

const rateLimited = new Rate('rate_limited');
http.setResponseCallback(http.expectedStatuses(200, 429));

export const options = {
  vus: Number(__ENV.VUS || 20),
  duration: __ENV.DURATION || '5m',
  thresholds: {
    http_req_failed: ['rate<0.02'],                          // non-200/429 responses
    'http_req_duration{name:chat,status:200}': ['p(95)<6000'], // latency of answered chats only
    checks: ['rate>0.98'],
  },
};

export function setup() {
  const tokens = {};
  for (const email of [...new Set(queries.map((q) => q.askedBy))]) {
    const r = http.post(`${API}/api/v1/auth/login`, JSON.stringify({ email, password: PASSWORD }),
      { headers: { 'Content-Type': 'application/json' }, tags: { name: 'login' } });
    if (r.status !== 200) throw new Error(`login failed for ${email}: HTTP ${r.status}`);
    tokens[email] = r.json('accessToken');
  }
  return tokens;
}

export default function (tokens) {
  const q = queries[Math.floor(Math.random() * queries.length)];
  const r = http.post(`${API}/api/v1/chat`, JSON.stringify({ message: q.question }), {
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${tokens[q.askedBy]}` },
    tags: { name: 'chat' },
  });
  rateLimited.add(r.status === 429);
  check(r, { 'served (200) or rate-limited (429)': (res) => res.status === 200 || res.status === 429 });
  sleep(1 + Math.random() * 2);
}
