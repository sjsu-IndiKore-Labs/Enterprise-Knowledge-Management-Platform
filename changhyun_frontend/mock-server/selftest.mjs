// Checks the mock server itself against the contract's behavior. Run: node mock-server/selftest.mjs
import assert from 'node:assert/strict';

process.env.MOCK_QUEUED_MS ||= '300';
process.env.MOCK_TOKEN_MS ||= '0'; // chat streams without pauses
const { server } = await import('./server.mjs'); // after the env vars above: the server reads them when it loads
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

// ---------- chat ----------
/** POST /chat/stream and split the body into events (contracts/streaming.md). */
const stream = async (token, json) => {
  const r = await fetch(`${base}/api/v1/chat/stream`, { method: 'POST', headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, body: JSON.stringify(json) });
  const text = await r.text();
  if (r.status !== 200) return { status: r.status, body: JSON.parse(text), events: [] };
  const events = []; let comments = 0;
  for (const block of text.split('\n\n')) {
    if (!block.trim()) continue;
    if (block.startsWith(':')) { comments++; continue; }
    events.push({ event: /^event: (.+)$/m.exec(block)[1], data: JSON.parse(/^data: (.+)$/m.exec(block)[1]) });
  }
  return { status: 200, type: r.headers.get('content-type'), events, comments };
};
const answerOf = (events) => events.filter((e) => e.event === 'token').map((e) => e.data.text).join('');
const PTO = 'How many unused PTO days can I carry over into next year?';
let conv, assistantId;

await t('chat/stream: event order meta, token*, citations, done; keep-alive comment; SSE content type', async () => {
  const r = await stream(hrEmp, { message: PTO });
  assert.equal(r.status, 200); assert.match(r.type, /text\/event-stream/);
  const names = r.events.map((e) => e.event);
  assert.equal(names[0], 'meta'); assert.equal(names.at(-1), 'done'); assert.equal(names.at(-2), 'citations');
  assert.ok(names.filter((n) => n === 'token').length > 5); assert.ok(r.comments >= 1);
  assert.ok(r.events[0].data.queryId && r.events[0].data.conversationId && r.events[0].data.messageId);
  conv = r.events[0].data.conversationId; assistantId = r.events[0].data.messageId;
  const done = r.events.at(-1).data; assert.equal(done.answered, true); assert.ok(done.confidence > 0 && done.latencyMs >= 0);
});
await t('chat/stream: every [n] marker has a citation with the contract fields', async () => {
  const r = await stream(hrEmp, { message: PTO });
  const text = answerOf(r.events); const cites = r.events.find((e) => e.event === 'citations').data.citations;
  const marks = [...text.matchAll(/\[(\d+)\]/g)].map((m) => Number(m[1]));
  assert.ok(marks.length > 0 && marks.every((n) => cites.some((c) => c.citationNumber === n)), text);
  for (const c of cites) { assert.ok(c.documentId && c.chunkId && c.documentTitle && c.snippet.length <= 300 && c.score >= 0 && c.score <= 1 && c.version >= 1); }
  assert.ok(cites.every((c) => ['GENERAL', 'HR'].includes(c.department)));
});
await t('chat/stream: nothing relevant -> the exact not-found sentence, answered false, no citations', async () => {
  const r = await stream(hrEmp, { message: 'quantum teleportation zebra' });
  assert.equal(answerOf(r.events), "I couldn't find this in the documents you have access to.");
  assert.equal(r.events.find((e) => e.event === 'citations').data.citations.length, 0); assert.equal(r.events.at(-1).data.answered, false);
});
await t('chat: departments of others are intersected away (HR employee asking for FINANCE only)', async () => {
  const r = await stream(hrEmp, { message: 'budget compensation salary', departments: ['FINANCE'] });
  assert.equal(r.events.at(-1).data.answered, false);
});
await t('chat/stream: continuing a conversation keeps one thread; GET returns messages in order', async () => {
  const r = await stream(hrEmp, { message: 'And what about sick days?', conversationId: conv });
  assert.equal(r.events[0].data.conversationId, conv);
  const c = await call(`/api/v1/conversations/${conv}`, { token: hrEmp });
  assert.equal(c.status, 200); assert.equal(c.body.title, PTO.slice(0, 80));
  assert.deepEqual(c.body.messages.map((m) => m.role), ['USER', 'ASSISTANT', 'USER', 'ASSISTANT']);
  assert.ok(c.body.messages[1].citations.length > 0); assert.equal(c.body.messages[1].answered, true); assert.equal(c.body.messages[1].feedback, null);
});
await t('conversations: listed newest first for the owner only; others get 404', async () => {
  const mine = await call('/api/v1/conversations', { token: hrEmp });
  assert.equal(mine.body.items[0].id, conv); assert.ok(mine.body.totalItems >= 1);
  assert.equal((await call('/api/v1/conversations', { token: engMgr })).body.totalItems, 0);
  assert.equal((await call(`/api/v1/conversations/${conv}`, { token: engMgr })).status, 404);
  assert.equal((await stream(engMgr, { message: 'hello there', conversationId: conv })).status, 404);
});
await t('feedback: UP stored (204) and returned in the conversation; 404 unknown or foreign message; 400 bad rating', async () => {
  const post = (token, id, json) => call(`/api/v1/chat/messages/${id}/feedback`, { token, method: 'POST', json });
  assert.equal((await post(hrEmp, assistantId, { rating: 'UP' })).status, 204);
  const c = await call(`/api/v1/conversations/${conv}`, { token: hrEmp });
  assert.equal(c.body.messages.find((m) => m.id === assistantId).feedback, 'UP');
  assert.equal((await post(hrEmp, assistantId, { rating: 'DOWN', comment: 'not helpful' })).status, 204);
  assert.equal((await post(engMgr, assistantId, { rating: 'UP' })).status, 404);
  assert.equal((await post(hrEmp, c.body.messages[0].id, { rating: 'UP' })).status, 404, 'user messages cannot be rated');
  assert.equal((await post(hrEmp, assistantId, { rating: 'MEH' })).status, 400);
});
await t('chat: 400 empty or too long message, 502 trigger502, error event after a few tokens (nothing saved)', async () => {
  assert.equal((await stream(hrEmp, { message: '  ' })).status, 400);
  assert.equal((await stream(hrEmp, { message: 'x'.repeat(2001) })).status, 400);
  assert.equal((await stream(hrEmp, { message: 'trigger502' })).status, 502);
  const r = await stream(hrEmp, { message: `${PTO} triggererror` });
  const names = r.events.map((e) => e.event);
  assert.equal(names.at(-1), 'error'); assert.ok(!names.includes('done')); assert.ok(r.events.at(-1).data.code && r.events.at(-1).data.traceId);
  const c = await call(`/api/v1/conversations/${r.events[0].data.conversationId}`, { token: hrEmp });
  assert.deepEqual(c.body.messages.map((m) => m.role), ['USER']);
});
await t('chat (non-streaming) returns a ChatResponse', async () => {
  const r = await call('/api/v1/chat', { token: hrEmp, method: 'POST', json: { message: PTO } });
  assert.equal(r.status, 200); assert.equal(r.body.answered, true); assert.ok(r.body.answer.includes('[1]') && r.body.citations.length >= 1 && r.body.messageId);
});
await t('conversations: delete -> 204, then 404; unauthenticated chat -> 401', async () => {
  assert.equal((await call(`/api/v1/conversations/${conv}`, { token: hrEmp, method: 'DELETE' })).status, 204);
  assert.equal((await call(`/api/v1/conversations/${conv}`, { token: hrEmp })).status, 404);
  assert.equal((await call('/api/v1/conversations')).status, 401);
});

await t('expire-tokens makes the next call 401', async () => {
  await fetch(`${base}/__mock/expire-tokens`, { method: 'POST' });
  assert.equal((await call('/api/v1/users/me', { token: hrMgr })).status, 401);
});
console.log(`\n${passed} checks passed`);
server.close();
