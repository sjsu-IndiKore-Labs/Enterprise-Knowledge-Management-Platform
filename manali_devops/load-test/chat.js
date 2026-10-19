// k6 load test for POST /api/v1/chat. Run: k6 run -e API=http://localhost:8080 manali_devops/load-test/chat.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import papaparse from 'https://jslib.k6.io/papaparse/5.1.1/index.js';

const API = __ENV.API || 'http://localhost:8080';
const PASSWORD = __ENV.PASSWORD || 'Password123!'; // dev seed password (resources/seed/README.md)
const queries = new SharedArray('queries', () =>
  papaparse.parse(open('../../resources/load-test/queries.csv'), { header: true }).data.filter((r) => r.askedBy));

export const options = {
  vus: 20,
  duration: '5m',
  thresholds: { http_req_failed: ['rate<0.02'], 'http_req_duration{name:chat}': ['p(95)<6000'] },
};

export function setup() {
  const tokens = {};
  for (const email of [...new Set(queries.map((q) => q.askedBy))]) {
    const r = http.post(`${API}/api/v1/auth/login`, JSON.stringify({ email, password: PASSWORD }),
      { headers: { 'Content-Type': 'application/json' } });
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
  check(r, { 'status 200': (res) => res.status === 200 });
  sleep(1 + Math.random() * 2);
}
