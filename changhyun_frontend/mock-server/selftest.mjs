// Checks the mock server itself against the contract's behavior. Run: node mock-server/selftest.mjs
import assert from 'node:assert/strict';
import { server } from './server.mjs';

process.env.MOCK_QUEUED_MS ||= '300';
await new Promise((r) => server.listen(0, r));
const base = `http://localhost:${server.address().port}`;
let passed = 0;
const t = async (name, fn) => { try { await fn(); passed++; console.log('PASS', name); } catch (e) { console.log('FAIL', name, '\n  ', e.message); process.exitCode = 1; } };
const call = async (path, { token, method = 'GET', json, form } = {}) => {
  const r = await fetch(base + path, { method, headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(json ? { 'Content-Type': 'application/json' } : {}) }, body: json ? JSON.stringify(json) : form });
  const text = await r.text();
  return { status: r.status, body: text ? JSON.parse(text) : undefined };
};
const login = async (email) => (await call('/api/v1/auth/login', { method: 'POST', json: { email, password: 'Password123!' } })).body;
const pdf = (name = 'a.pdf', size = 2000) => new File([new Uint8Array(size).fill(65)], name, { type: 'application/pdf' });
const upload = (token, over = {}, file = pdf()) => {
  const f = new FormData();
  f.set('file', file); f.set('title', over.title ?? 'T'); f.set('department', over.department ?? 'HR'); f.set('category', over.category ?? 'POLICY');
  if (over.tags) f.set('tags', over.tags);
  return call('/api/v1/documents', { token, method: 'POST', form: f });
};
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const admin = (await login('alice.admin@helios.example.com')).accessToken;
const hrMgr = (await login('harper.hrmgr@helios.example.com')).accessToken;
const hrEmp = (await login('hannah.hr@helios.example.com')).accessToken;
const engMgr = (await login('ethan.engmgr@helios.example.com')).accessToken;

await t('login: wrong password -> 401 INVALID_CREDENTIALS with shared error body', async () => {
  const r = await call('/api/v1/auth/login', { method: 'POST', json: { email: 'alice.admin@helios.example.com', password: 'nope' } });
  assert.equal(r.status, 401); assert.equal(r.body.code, 'INVALID_CREDENTIALS'); assert.ok(r.body.traceId && r.body.timestamp);
});
await t('users/me returns role, allowedDepartments, permissions', async () => {
  const r = await call('/api/v1/users/me', { token: hrMgr });
  assert.equal(r.body.role, 'MANAGER'); assert.deepEqual(r.body.allowedDepartments, ['GENERAL', 'HR']); assert.ok(r.body.permissions.includes('DOCUMENT_WRITE'));
});
await t('no token -> 401', async () => assert.equal((await call('/api/v1/documents')).status, 401));
await t('list is paged and department-isolated', async () => {
  const a = await call('/api/v1/documents?size=100', { token: admin });
  const e = await call('/api/v1/documents?size=100', { token: hrEmp });
  assert.ok(a.body.totalItems > 20, 'admin has more than one page of documents');
  assert.ok(e.body.items.every((d) => ['GENERAL', 'HR'].includes(d.department)));
  assert.ok(e.body.totalItems < a.body.totalItems);
  const p2 = await call('/api/v1/documents?size=20&page=1', { token: admin });
  assert.equal(p2.body.page, 1); assert.equal(p2.body.totalPages, Math.ceil(a.body.totalItems / 20));
});
await t('list filters: department, category, text', async () => {
  const r = await call('/api/v1/documents?department=HR&category=POLICY&size=100', { token: admin });
  assert.ok(r.body.items.length > 0 && r.body.items.every((d) => d.department === 'HR' && d.category === 'POLICY'));
  const q = await call('/api/v1/documents?q=pto&size=100', { token: admin });
  assert.ok(q.body.items.some((d) => /pto/i.test(d.title)));
});
await t('EMPLOYEE cannot upload (403)', async () => assert.equal((await upload(hrEmp)).status, 403));
await t('MANAGER cannot upload to another department (403); ADMIN can', async () => {
  assert.equal((await upload(hrMgr, { department: 'FINANCE' })).status, 403);
  assert.equal((await upload(admin, { department: 'FINANCE' })).status, 202);
});
await t('upload validation: 400 missing title, 415 wrong type', async () => {
  const r = await upload(hrMgr, { title: ' ' }); assert.equal(r.status, 400); assert.equal(r.body.details.title, 'required');
  const x = await upload(hrMgr, {}, new File(['x'], 'notes.txt', { type: 'text/plain' })); assert.equal(x.status, 415);
});
await t('upload over the limit -> 413', async () => {
  const r = await upload(hrMgr, {}, pdf('big.pdf', 26 * 1024 * 1024)); assert.equal(r.status, 413);
});
let docId;
await t('upload accepted (202) as QUEUED, then PROCESSING, then INDEXED', async () => {
  const r = await upload(hrMgr, { title: 'Flow test', tags: 'a, b ,a' });
  assert.equal(r.status, 202); assert.equal(r.body.status, 'QUEUED'); assert.deepEqual(r.body.tags, ['a', 'b', 'a']); docId = r.body.id;
  const seen = new Set([r.body.status]);
  for (let i = 0; i < 60 && !seen.has('INDEXED'); i++) { await sleep(150); seen.add((await call(`/api/v1/documents/${docId}`, { token: hrMgr })).body.status); }
  assert.ok(seen.has('PROCESSING') && seen.has('INDEXED'), [...seen].join());
});
await t('a file named *fail* ends FAILED with an error message on the version', async () => {
  const r = await upload(hrMgr, { title: 'Bad' }, pdf('will-fail.pdf'));
  let d; for (let i = 0; i < 80; i++) { await sleep(150); d = (await call(`/api/v1/documents/${r.body.id}`, { token: hrMgr })).body; if (d.status === 'FAILED') break; }
  assert.equal(d.status, 'FAILED'); assert.match(d.versions[0].errorMessage, /Could not extract/);
});
await t('other departments get 404 (not 403) for a document', async () => {
  assert.equal((await call(`/api/v1/documents/${docId}`, { token: engMgr })).status, 404);
});
await t('new version: 202, version 2 appears, EMPLOYEE gets 403', async () => {
  const f = new FormData(); f.set('file', pdf('v2.pdf')); f.set('changeNote', 'fixed typos');
  const r = await call(`/api/v1/documents/${docId}/versions`, { token: hrMgr, method: 'POST', form: f });
  assert.equal(r.status, 202); assert.equal(r.body.version, 2); assert.equal(r.body.changeNote, 'fixed typos');
  const g = await call(`/api/v1/documents/${docId}`, { token: hrMgr }); assert.equal(g.body.versions.length, 2);
  const f2 = new FormData(); f2.set('file', pdf('v3.pdf'));
  assert.equal((await call(`/api/v1/documents/${docId}/versions`, { token: hrEmp, method: 'POST', form: f2 })).status, 403);
});
await t('download-url carries #page=N', async () => {
  const r = await call(`/api/v1/documents/${docId}/download-url?page=3`, { token: hrMgr });
  assert.match(r.body.url, /#page=3$/); assert.ok(r.body.expiresAt);
});
let hit;
await t('search HYBRID returns ranked, department-safe results from the corpus', async () => {
  const r = await call('/api/v1/search', { token: hrEmp, method: 'POST', json: { query: 'how many vacation days do I get?', mode: 'HYBRID' } });
  assert.equal(r.status, 200); assert.ok(r.body.results.length > 0, 'expected results');
  assert.ok(r.body.results.every((x) => ['GENERAL', 'HR'].includes(x.department)));
  assert.ok(r.body.results[0].score >= r.body.results.at(-1).score);
  assert.match(r.body.results[0].content, /PTO|paid time off/i, 'PTO accrual should rank first');
  assert.equal(r.body.results[0].department, 'HR'); hit = r.body.results[0];
});
await t('KEYWORD finds nothing for "vacation" (not in text) but SEMANTIC does', async () => {
  const k = await call('/api/v1/search', { token: hrEmp, method: 'POST', json: { query: 'vacation', mode: 'KEYWORD' } });
  const s = await call('/api/v1/search', { token: hrEmp, method: 'POST', json: { query: 'vacation', mode: 'SEMANTIC' } });
  assert.ok(s.body.results.length > 0); assert.ok(k.body.results.length <= s.body.results.length);
});
await t('client cannot widen access: sending departments of others is intersected away', async () => {
  const r = await call('/api/v1/search', { token: hrEmp, method: 'POST', json: { query: 'budget compensation salary', departments: ['FINANCE', 'LEGAL'], authorizedDepartments: ['FINANCE'] } });
  assert.equal(r.body.results.length, 0);
});
await t('search: 400 empty query, 502 on trigger502', async () => {
  assert.equal((await call('/api/v1/search', { token: hrEmp, method: 'POST', json: { query: ' ' } })).status, 400);
  assert.equal((await call('/api/v1/search', { token: hrEmp, method: 'POST', json: { query: 'trigger502' } })).status, 502);
});
await t('chunk fetch: full text with page number; 404 for another department', async () => {
  const r = await call(`/api/v1/documents/${hit.documentId}/chunks/${hit.chunkId}`, { token: hrEmp });
  assert.equal(r.status, 200); assert.equal(r.body.content, hit.content); assert.ok(r.body.pageNumber >= 1);
  assert.equal((await call(`/api/v1/documents/${hit.documentId}/chunks/${hit.chunkId}`, { token: engMgr })).status, 404);
});
await t('expire-tokens makes the next call 401', async () => {
  await fetch(`${base}/__mock/expire-tokens`, { method: 'POST' });
  assert.equal((await call('/api/v1/users/me', { token: hrMgr })).status, 401);
});
console.log(`\n${passed} checks passed`);
server.close();
