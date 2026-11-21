#!/usr/bin/env node
// Stateful mock of core-api for developing and checking the web portal without the real backend.
// Zero dependencies (Node >= 18). Follows contracts/openapi/core-api.yaml (documents, versions,
// download-url, chunks, search, auth) and contracts/auth.md. Not a replacement for the real service.
//
//   node mock-server/server.mjs                 # http://localhost:4010 (same port as the Prism mock)
//   PORT=8080 node mock-server/server.mjs
//
// What Prism cannot do and this does:
//   - status progression after upload: QUEUED -> PROCESSING -> INDEXED (or FAILED if the file name contains "fail")
//   - roles and departments from the seed users (EMPLOYEE cannot upload, MANAGER only to own department, ADMIN anywhere)
//   - 404 for documents outside the caller's departments, 401 without or after expiring a token
//   - 413 over 25 MB, 415 for other file types, 400 for missing fields, 502 when the query contains "trigger502"
//   - search over the real sample corpus (resources/corpus) when present, with HYBRID / SEMANTIC / KEYWORD modes
//
// Dev controls:  POST /__mock/expire-tokens   (next call returns 401)    POST /__mock/reset   (back to seed data)

import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';

const PORT = Number(process.env.PORT || 4010);
const MAX_BYTES = Number(process.env.MOCK_MAX_UPLOAD_MB || 25) * 1024 * 1024;
const HERE = path.dirname(fileURLToPath(import.meta.url));
const CORPUS_DIR = process.env.CORPUS_DIR || path.resolve(HERE, '../../resources/corpus');
// Timings of the fake ingestion pipeline, measured from upload (ms).
const T_PROCESSING = Number(process.env.MOCK_QUEUED_MS || 4000);
const T_DONE = Number(process.env.MOCK_DONE_MS || 9000);

const DEPARTMENTS = [
  ['GENERAL', 'General'],
  ['HR', 'Human Resources'],
  ['ENGINEERING', 'Engineering'],
  ['FINANCE', 'Finance'],
  ['LEGAL', 'Legal'],
  ['MARKETING', 'Marketing'],
];
const DEPT_CODES = DEPARTMENTS.map(([c]) => c);
const CATEGORIES = ['POLICY', 'SOP', 'MANUAL', 'GUIDE', 'REPORT', 'OTHER'];
const PASSWORD = 'Password123!';
const U = (n, email, fullName, role, department, active = true) => ({
  id: `11111111-1111-4111-8111-${String(n).padStart(12, '0')}`,
  email, fullName, role, department, active, createdAt: '2026-10-01T09:00:00Z',
});
const USERS = [
  U(1, 'alice.admin@helios.example.com', 'Alice Admin', 'ADMIN', 'GENERAL'),
  U(2, 'harper.hrmgr@helios.example.com', 'Harper Hughes', 'MANAGER', 'HR'),
  U(3, 'hannah.hr@helios.example.com', 'Hannah Reyes', 'EMPLOYEE', 'HR'),
  U(4, 'ethan.engmgr@helios.example.com', 'Ethan Park', 'MANAGER', 'ENGINEERING'),
  U(5, 'eric.eng@helios.example.com', 'Eric Nguyen', 'EMPLOYEE', 'ENGINEERING'),
  U(6, 'frank.finmgr@helios.example.com', 'Frank Ortiz', 'MANAGER', 'FINANCE'),
  U(7, 'fiona.fin@helios.example.com', 'Fiona Brooks', 'EMPLOYEE', 'FINANCE'),
  U(8, 'lena.legalmgr@helios.example.com', 'Lena Schmidt', 'MANAGER', 'LEGAL'),
  U(9, 'leo.legal@helios.example.com', 'Leo Martins', 'EMPLOYEE', 'LEGAL'),
  U(10, 'mark.mktmgr@helios.example.com', 'Mark Olsen', 'MANAGER', 'MARKETING'),
  U(11, 'maya.mkt@helios.example.com', 'Maya Chen', 'EMPLOYEE', 'MARKETING'),
  U(12, 'ivan.inactive@helios.example.com', 'Ivan Idle', 'EMPLOYEE', 'MARKETING', false),
];

// ---------- helpers ----------
const uuidFrom = (s) => {
  const h = crypto.createHash('md5').update(s).digest('hex');
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-4${h.slice(13, 16)}-8${h.slice(17, 20)}-${h.slice(20, 32)}`;
};
const nowIso = () => new Date().toISOString();
const allowedDepartments = (u) => (u.role === 'ADMIN' ? DEPT_CODES : [...new Set(['GENERAL', u.department])]);
const permissions = (u) => ({
  ADMIN: ['DOCUMENT_READ', 'DOCUMENT_WRITE', 'ANALYTICS_READ', 'USER_ADMIN'],
  MANAGER: ['DOCUMENT_READ', 'DOCUMENT_WRITE'],
  EMPLOYEE: ['DOCUMENT_READ'],
})[u.role];
const currentUser = (u) => ({ ...u, allowedDepartments: allowedDepartments(u), permissions: permissions(u) });
const canWriteTo = (u, dept) => u.role === 'ADMIN' || (u.role === 'MANAGER' && u.department === dept);

function parseCsv(text) {
  const rows = [];
  for (const line of text.split(/\r?\n/).filter(Boolean)) {
    const cells = [];
    let cur = '', q = false;
    for (const ch of line) {
      if (ch === '"') q = !q;
      else if (ch === ',' && !q) { cells.push(cur); cur = ''; }
      else cur += ch;
    }
    cells.push(cur);
    rows.push(cells);
  }
  const [head, ...rest] = rows;
  return rest.map((r) => Object.fromEntries(head.map((h, i) => [h, r[i]])));
}

/** "## Heading" sections become chunks; "<!-- pagebreak -->" markers advance the page number. */
function chunkMarkdown(md, documentId, version, title) {
  const chunks = [];
  let page = 1, heading = title, buf = [];
  const flush = (pg) => {
    const text = buf.join('\n').trim();
    if (text.length > 40) {
      chunks.push({
        id: uuidFrom(`${documentId}:${version}:${chunks.length}`), documentId, documentTitle: title, version,
        chunkIndex: chunks.length, pageNumber: pg, sectionTitle: heading, content: text.replace(/\n{3,}/g, '\n\n'),
      });
    }
    buf = [];
  };
  let sectionPage = 1;
  for (const line of md.split('\n')) {
    if (line.includes('<!-- pagebreak -->')) { page += 1; continue; }
    const h = /^##\s+(.*)$/.exec(line);
    if (h) { flush(sectionPage); heading = h[1].trim(); sectionPage = page; continue; }
    if (/^#\s/.test(line) || /^Document ID:/.test(line)) continue;
    buf.push(line);
  }
  flush(sectionPage);
  return { chunks, pageCount: page };
}

// ---------- seed data ----------
let docs, chunksByDoc, tokens, tokenEpoch;

function fakeVersion(documentId, version, file, size, status, uploadedBy, extra = {}) {
  return {
    id: uuidFrom(`${documentId}:v${version}`), documentId, version, status,
    originalFilename: file, contentType: contentTypeFor(file), sizeBytes: size, pageCount: extra.pageCount ?? null,
    chunkCount: extra.chunkCount ?? null, changeNote: extra.changeNote ?? null, errorMessage: null,
    uploadedBy, uploadedAt: extra.uploadedAt ?? '2026-10-10T10:00:00Z', indexedAt: extra.uploadedAt ?? '2026-10-10T10:02:00Z',
    createdMs: 0, fixed: true,
  };
}
const contentTypeFor = (f) =>
  /\.docx$/i.test(f) ? 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' : 'application/pdf';

function seed() {
  docs = []; chunksByDoc = new Map(); tokens = new Map(); tokenEpoch = 1;
  const admin = USERS[0].id;
  const manifestPath = path.join(CORPUS_DIR, 'manifest.csv');
  if (fs.existsSync(manifestPath)) {
    const rows = parseCsv(fs.readFileSync(manifestPath, 'utf8'));
    const byId = new Map();
    for (const r of rows) {
      const g = byId.get(r.docId) || [];
      g.push(r); byId.set(r.docId, g);
    }
    for (const [docId, group] of byId) {
      group.sort((a, b) => Number(a.version) - Number(b.version));
      const id = uuidFrom(`doc:${docId}`);
      const last = group[group.length - 1];
      const versions = [];
      const chunks = [];
      for (const r of group) {
        const v = Number(r.version);
        const srcPath = path.join(CORPUS_DIR, 'source', r.sourceMarkdown);
        let size = 120000 + v * 3000;
        let parsed = { chunks: [], pageCount: 3 };
        if (fs.existsSync(srcPath)) {
          const md = fs.readFileSync(srcPath, 'utf8');
          size = md.length * 6;
          parsed = chunkMarkdown(md, id, v, r.title);
        }
        chunks.push(...parsed.chunks);
        versions.push(fakeVersion(id, v, r.file, size, 'INDEXED', admin, {
          pageCount: parsed.pageCount, chunkCount: parsed.chunks.length,
          changeNote: v > 1 ? 'Updated policy text' : null, uploadedAt: `${r.effectiveDate}T09:00:00Z`,
        }));
      }
      chunksByDoc.set(id, chunks);
      docs.push({
        id, title: last.title, department: last.department, category: last.category, author: last.author,
        tags: [docId.toLowerCase(), last.category.toLowerCase()], createdBy: admin,
        createdAt: `${group[0].effectiveDate}T09:00:00Z`, updatedAt: `${last.effectiveDate}T09:00:00Z`, versions,
      });
    }
  } else {
    console.warn(`[mock] ${manifestPath} not found: using a few built-in documents instead.`);
    const base = [
      ['HR', 'POLICY', 'PTO and Leave Policy', 'Full-time employees accrue paid time off (PTO) every month. Employees may carry over a maximum of 5 unused PTO days into the next calendar year.'],
      ['GENERAL', 'POLICY', 'Remote Work Policy', 'Employees may work remotely up to three days per week with manager approval. Home office equipment is reimbursed up to 500 dollars.'],
      ['FINANCE', 'SOP', 'Procurement and Purchase Approval SOP', 'Purchases above 5,000 dollars require approval from the department head and finance.'],
      ['ENGINEERING', 'SOP', 'Incident Response Runbook', 'Declare a severity level, open an incident channel and page the on-call engineer.'],
    ];
    base.forEach(([department, category, title, text], i) => {
      const id = uuidFrom(`builtin:${i}`);
      chunksByDoc.set(id, [{ id: uuidFrom(`${id}:c`), documentId: id, documentTitle: title, version: 1, chunkIndex: 0,
        pageNumber: 1, sectionTitle: 'Overview', content: text }]);
      docs.push({ id, title, department, category, author: 'People Operations', tags: [category.toLowerCase()],
        createdBy: admin, createdAt: '2026-09-01T09:00:00Z', updatedAt: '2026-09-01T09:00:00Z',
        versions: [fakeVersion(id, 1, `${title.replace(/\s+/g, '_')}.pdf`, 90000, 'INDEXED', admin, { pageCount: 1, chunkCount: 1 })] });
    });
  }
  // Extra rows so the library has more than one page (default page size is 20).
  const topics = ['Quarterly Planning', 'Vendor Checklist', 'Security Training', 'Launch Playbook', 'Hiring Guide', 'Audit Notes'];
  for (let i = 1; i <= 14; i++) {
    const department = DEPT_CODES[i % DEPT_CODES.length];
    const title = `${topics[i % topics.length]} ${2020 + (i % 7)} (sample ${i})`;
    const id = uuidFrom(`extra:${i}`);
    chunksByDoc.set(id, [{ id: uuidFrom(`${id}:c`), documentId: id, documentTitle: title, version: 1, chunkIndex: 0, pageNumber: 1,
      sectionTitle: 'Summary', content: `${title} is placeholder content generated by the mock server for list paging.` }]);
    docs.push({ id, title, department, category: CATEGORIES[i % CATEGORIES.length], author: null, tags: ['sample'],
      createdBy: admin, createdAt: `2026-08-${String(10 + (i % 18)).padStart(2, '0')}T09:00:00Z`,
      updatedAt: `2026-08-${String(10 + (i % 18)).padStart(2, '0')}T09:00:00Z`,
      versions: [fakeVersion(id, 1, `sample_${i}.pdf`, 40000 + i * 900, 'INDEXED', admin, { pageCount: 2, chunkCount: 1 })] });
  }
}

// Status of a version over time (uploads made while the mock runs).
function versionStatus(v) {
  if (v.fixed) return v.status;
  const age = Date.now() - v.createdMs;
  if (age < T_PROCESSING) return 'QUEUED';
  if (age < T_DONE) return 'PROCESSING';
  return /fail/i.test(v.originalFilename) ? 'FAILED' : 'INDEXED';
}
function publicVersion(v) {
  const status = versionStatus(v);
  const { createdMs, fixed, ...rest } = v;
  void createdMs; void fixed;
  return {
    ...rest, status,
    errorMessage: status === 'FAILED' ? 'Could not extract text from this file (mock failure: file name contains "fail").' : null,
    indexedAt: status === 'INDEXED' ? v.indexedAt ?? nowIso() : null,
    chunkCount: status === 'INDEXED' ? v.chunkCount ?? 1 : null,
  };
}
function publicDoc(d) {
  const vs = d.versions.map(publicVersion);
  const latest = vs[vs.length - 1];
  const indexed = vs.filter((v) => v.status === 'INDEXED');
  const current = indexed.length ? indexed[indexed.length - 1] : vs[0];
  return {
    id: d.id, title: d.title, department: d.department, category: d.category, author: d.author, tags: d.tags,
    currentVersion: current.version, status: latest.status, originalFilename: latest.originalFilename,
    contentType: latest.contentType, pageCount: current.pageCount ?? null, createdBy: d.createdBy,
    createdAt: d.createdAt, updatedAt: d.updatedAt,
  };
}
function ensureChunks(d) {
  // Chunks for uploaded files appear once their version is INDEXED.
  const have = chunksByDoc.get(d.id) || [];
  for (const v of d.versions) {
    if (v.fixed || versionStatus(v) !== 'INDEXED' || have.some((c) => c.version === v.version)) continue;
    have.push({ id: uuidFrom(`${d.id}:${v.version}:0`), documentId: d.id, documentTitle: d.title, version: v.version,
      chunkIndex: 0, pageNumber: 1, sectionTitle: 'Uploaded content',
      content: `Mock content for "${v.originalFilename}" (${d.title}, version ${v.version}). The real service would contain the extracted text here.` });
  }
  chunksByDoc.set(d.id, have);
  return have;
}

// ---------- http plumbing ----------
const CORS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'Authorization, Content-Type, Accept',
  'Access-Control-Allow-Methods': 'GET, POST, PATCH, DELETE, OPTIONS',
  'Access-Control-Expose-Headers': 'Content-Disposition',
};
const send = (res, status, body, headers = {}) => {
  const payload = body === undefined ? '' : JSON.stringify(body);
  res.writeHead(status, { ...CORS, ...(payload ? { 'Content-Type': 'application/json' } : {}), ...headers });
  res.end(payload);
};
const fail = (res, status, code, message, details) =>
  send(res, status, { code, message, ...(details ? { details } : {}), traceId: crypto.randomBytes(8).toString('hex'), timestamp: nowIso() });

function auth(req, res) {
  const m = /^Bearer (.+)$/.exec(req.headers.authorization || '');
  const rec = m && tokens.get(m[1]);
  if (!rec || rec.epoch !== tokenEpoch) {
    fail(res, 401, 'UNAUTHENTICATED', 'Missing, invalid or expired token.');
    return null;
  }
  return USERS.find((u) => u.id === rec.userId);
}
function readBody(req, limit) {
  return new Promise((resolve, reject) => {
    const parts = []; let size = 0;
    req.on('data', (c) => { size += c.length; if (size > limit) { reject(Object.assign(new Error('too large'), { status: 413 })); req.destroy(); } else parts.push(c); });
    req.on('end', () => resolve(Buffer.concat(parts)));
    req.on('error', reject);
  });
}
const readJson = async (req) => { const b = await readBody(req, 1e6); try { return b.length ? JSON.parse(b.toString()) : {}; } catch { return null; } };

function parseMultipart(buf, contentType) {
  const m = /boundary=(?:"([^"]+)"|([^;]+))/.exec(contentType || '');
  if (!m) return null;
  const boundary = Buffer.from(`--${m[1] || m[2]}`);
  const fields = {}; let file = null;
  let pos = buf.indexOf(boundary);
  while (pos !== -1) {
    const next = buf.indexOf(boundary, pos + boundary.length);
    if (next === -1) break;
    const part = buf.subarray(pos + boundary.length + 2, next - 2); // strip CRLF after boundary and before the next one
    const split = part.indexOf('\r\n\r\n');
    if (split !== -1) {
      const head = part.subarray(0, split).toString();
      const body = part.subarray(split + 4);
      const name = /name="([^"]*)"/.exec(head)?.[1];
      const filename = /filename="([^"]*)"/.exec(head)?.[1];
      if (filename !== undefined) file = { name, filename, type: /Content-Type:\s*([^\r\n]+)/i.exec(head)?.[1]?.trim() || '', size: body.length };
      else if (name) fields[name] = body.toString();
    }
    pos = next;
  }
  return { fields, file };
}

function checkFile(res, file) {
  if (!file || !file.filename) { fail(res, 400, 'VALIDATION_ERROR', 'A file is required.', { file: 'required' }); return false; }
  const okExt = /\.(pdf|docx)$/i.test(file.filename);
  const okType = !file.type || file.type === 'application/pdf' || file.type.includes('wordprocessingml.document') || file.type === 'application/octet-stream';
  if (!okExt || !okType) { fail(res, 415, 'UNSUPPORTED_MEDIA_TYPE', 'Only PDF and DOCX files are supported.'); return false; }
  if (file.size === 0) { fail(res, 400, 'VALIDATION_ERROR', 'The file is empty.', { file: 'empty' }); return false; }
  if (file.size > MAX_BYTES) { fail(res, 413, 'PAYLOAD_TOO_LARGE', `File exceeds the ${MAX_BYTES / 1024 / 1024} MB limit.`); return false; }
  return true;
}

const visibleDoc = (u, id) => docs.find((d) => d.id === id && allowedDepartments(u).includes(d.department));
const notFound = (res) => fail(res, 404, 'DOCUMENT_NOT_FOUND', 'Document not found.');

// ---------- search ----------
const STOP = new Set(['policy', 'policies', 'many', 'much', 'day', 'days', 'the', 'a', 'an', 'of', 'to', 'in', 'is', 'are', 'do', 'does', 'i', 'we', 'my', 'me', 'and', 'or', 'for', 'on', 'how', 'what', 'can', 'get', 'it', 'be', 'at', 'by', 'with']);
const SYNONYMS = {
  vacation: ['pto', 'accrue', 'accrual', 'carryover'], holiday: ['holidays', 'pto'], days: [],
  remote: ['telework', 'home', 'wfh', 'hybrid'], wfh: ['remote', 'home'], laptop: ['equipment', 'device', 'computer'],
  expense: ['reimbursement', 'reimbursed', 'receipt', 'travel'], expenses: ['expense', 'reimbursement', 'travel'],
  salary: ['compensation', 'pay', 'bands'], pay: ['compensation', 'salary'], fired: ['termination', 'offboarding'],
  password: ['credentials', 'authentication', 'mfa'], outage: ['incident', 'severity', 'on-call'], buy: ['purchase', 'procurement'],
};
const tokenize = (s) => s.toLowerCase().match(/[a-z0-9][a-z0-9'-]*/g)?.filter((t) => t.length > 1 && !STOP.has(t)) ?? [];
const stem = (t) => t.replace(/(ing|ed|es|s)$/, '');
function scoreChunk(terms, chunk) {
  const text = `${chunk.sectionTitle || ''} ${chunk.content}`.toLowerCase();
  const words = tokenize(text).map(stem);
  const set = new Set(words);
  const hit = (t) => set.has(stem(t));
  // Exact words count fully; a synonym only counts for half, so literal matches rank first.
  const keyword = terms.length ? terms.filter(hit).length / terms.length : 0;
  const semantic = terms.length
    ? terms.reduce((sum, t) => sum + (hit(t) ? 1 : (SYNONYMS[t] || []).some(hit) ? 0.5 : 0), 0) / terms.length
    : 0;
  return { keyword, semantic, titleBoost: titleBoost(terms, chunk) };
}
// Small bonus when the query (or a synonym) appears in the document or section title.
function titleBoost(terms, chunk) {
  const titleWords = new Set(tokenize(`${chunk.documentTitle || ''} ${chunk.sectionTitle || ''}`).map(stem));
  return terms.some((t) => [t, ...(SYNONYMS[t] || [])].some((w) => titleWords.has(stem(w)))) ? 0.15 : 0;
}
function doSearch(u, body) {
  const terms = tokenize(body.query);
  const mode = body.mode || 'HYBRID';
  const allowed = allowedDepartments(u);
  const wanted = Array.isArray(body.departments) && body.departments.length ? body.departments.filter((d) => allowed.includes(d)) : allowed;
  const cats = Array.isArray(body.categories) && body.categories.length ? body.categories : null;
  const out = [];
  for (const d of docs) {
    if (!wanted.includes(d.department) || (cats && !cats.includes(d.category))) continue;
    const pd = publicDoc(d);
    for (const c of ensureChunks(d)) {
      if (c.version !== pd.currentVersion) continue;
      const { keyword, semantic, titleBoost: boost } = scoreChunk(terms, c);
      const base = mode === 'KEYWORD' ? keyword : mode === 'SEMANTIC' ? semantic : 0.5 * keyword + 0.5 * semantic;
      const score = base > 0 ? Math.min(1, base + boost) : 0;
      if (score <= 0 || (mode === 'KEYWORD' && keyword === 0)) continue;
      out.push({
        chunkId: c.id, documentId: d.id, documentTitle: d.title, department: d.department, category: d.category,
        version: c.version, pageNumber: c.pageNumber, sectionTitle: c.sectionTitle, content: c.content,
        score: Math.round(score * 1000) / 1000, semanticScore: Math.round(semantic * 1000) / 1000, keywordScore: Math.round(keyword * 1000) / 1000,
      });
    }
  }
  out.sort((a, b) => b.score - a.score);
  return out.slice(0, Math.min(Math.max(Number(body.topK) || 10, 1), 50));
}

// A tiny valid PDF so "Open at page N" has something to show.
function samplePdf() {
  const objs = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>',
    null,
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
  ];
  const stream = 'BT /F1 22 Tf 72 700 Td (Mock document served by mock-server) Tj ET';
  objs[3] = `<< /Length ${stream.length} >>\nstream\n${stream}\nendstream`;
  let pdf = '%PDF-1.4\n'; const off = [];
  objs.forEach((o, i) => { off.push(pdf.length); pdf += `${i + 1} 0 obj\n${o}\nendobj\n`; });
  const xref = pdf.length;
  pdf += `xref\n0 ${objs.length + 1}\n0000000000 65535 f \n${off.map((o) => `${String(o).padStart(10, '0')} 00000 n \n`).join('')}`;
  pdf += `trailer\n<< /Size ${objs.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`;
  return Buffer.from(pdf);
}

// ---------- routes ----------
const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://${req.headers.host}`);
  const p = url.pathname.replace(/\/+$/, '') || '/';
  const q = url.searchParams;
  try {
    if (req.method === 'OPTIONS') { res.writeHead(204, CORS); return res.end(); }
    if (p === '/__files/sample.pdf') { res.writeHead(200, { ...CORS, 'Content-Type': 'application/pdf' }); return res.end(samplePdf()); }
    if (p === '/__mock/expire-tokens' && req.method === 'POST') { tokenEpoch += 1; return send(res, 200, { ok: true, note: 'All tokens are now invalid; the next API call returns 401.' }); }
    if (p === '/__mock/reset' && req.method === 'POST') { seed(); return send(res, 200, { ok: true }); }

    if (p === '/api/v1/auth/login' && req.method === 'POST') {
      const b = await readJson(req);
      if (!b || !b.email || !b.password) return fail(res, 400, 'VALIDATION_ERROR', 'Email and password are required.');
      const u = USERS.find((x) => x.email.toLowerCase() === String(b.email).toLowerCase());
      if (!u || !u.active || b.password !== PASSWORD) return fail(res, 401, 'INVALID_CREDENTIALS', 'Invalid email or password.');
      const t = crypto.randomBytes(24).toString('hex');
      tokens.set(t, { userId: u.id, epoch: tokenEpoch });
      return send(res, 200, { accessToken: t, tokenType: 'Bearer', expiresIn: 3600, user: currentUser(u) });
    }
    if (p === '/api/v1/departments' && req.method === 'GET') return send(res, 200, DEPARTMENTS.map(([code, name]) => ({ code, name })));

    // everything below needs a valid token
    let user = null;
    if (p.startsWith('/api/v1/')) {
      user = auth(req, res);
      if (!user) return;
    }

    if (p === '/api/v1/users/me' && req.method === 'GET') return send(res, 200, currentUser(user));

    if (p === '/api/v1/documents' && req.method === 'GET') {
      const page = Math.max(0, Number(q.get('page') || 0)), size = Math.min(100, Math.max(1, Number(q.get('size') || 20)));
      const text = (q.get('q') || '').trim().toLowerCase();
      let list = docs.filter((d) => allowedDepartments(user).includes(d.department)).map(publicDoc);
      if (q.get('department')) list = list.filter((d) => d.department === q.get('department'));
      if (q.get('category')) list = list.filter((d) => d.category === q.get('category'));
      if (q.get('status')) list = list.filter((d) => d.status === q.get('status'));
      if (text) list = list.filter((d) => d.title.toLowerCase().includes(text) || d.tags.some((t) => t.toLowerCase().includes(text)));
      list.sort((a, b) => (a.updatedAt < b.updatedAt ? 1 : -1));
      return send(res, 200, { page, size, totalItems: list.length, totalPages: Math.ceil(list.length / size), items: list.slice(page * size, page * size + size) });
    }

    if (p === '/api/v1/documents' && req.method === 'POST') {
      if (Number(req.headers['content-length'] || 0) > MAX_BYTES + 65536) { req.resume(); return fail(res, 413, 'PAYLOAD_TOO_LARGE', `File exceeds the ${MAX_BYTES / 1024 / 1024} MB limit.`); }
      const mp = parseMultipart(await readBody(req, MAX_BYTES + 1048576), req.headers['content-type']);
      if (!mp) return fail(res, 400, 'VALIDATION_ERROR', 'Expected multipart/form-data.');
      const { fields, file } = mp;
      if (user.role === 'EMPLOYEE') return fail(res, 403, 'FORBIDDEN', 'Your role cannot upload documents.');
      const bad = {};
      if (!fields.title?.trim()) bad.title = 'required';
      else if (fields.title.length > 200) bad.title = 'max 200 characters';
      if (!DEPT_CODES.includes(fields.department)) bad.department = 'invalid';
      if (!CATEGORIES.includes(fields.category)) bad.category = 'invalid';
      if ((fields.author || '').length > 120) bad.author = 'max 120 characters';
      if (Object.keys(bad).length) return fail(res, 400, 'VALIDATION_ERROR', 'Some fields are invalid.', bad);
      if (!canWriteTo(user, fields.department)) return fail(res, 403, 'FORBIDDEN', 'You can only upload to your own department.');
      if (!checkFile(res, file)) return;
      const id = crypto.randomUUID();
      const d = {
        id, title: fields.title.trim(), department: fields.department, category: fields.category, author: fields.author?.trim() || null,
        tags: (fields.tags || '').split(',').map((t) => t.trim()).filter(Boolean), createdBy: user.id, createdAt: nowIso(), updatedAt: nowIso(),
        versions: [{ ...fakeVersion(id, 1, file.filename, file.size, 'QUEUED', user.id, { uploadedAt: nowIso() }), fixed: false, createdMs: Date.now(), indexedAt: null }],
      };
      docs.unshift(d);
      return send(res, 202, publicDoc(d));
    }

    let m;
    if ((m = /^\/api\/v1\/documents\/([^/]+)$/.exec(p)) && req.method === 'GET') {
      const d = visibleDoc(user, m[1]);
      if (!d) return notFound(res);
      return send(res, 200, { ...publicDoc(d), versions: d.versions.map(publicVersion) });
    }
    if ((m = /^\/api\/v1\/documents\/([^/]+)\/versions$/.exec(p)) && req.method === 'POST') {
      const mp = parseMultipart(await readBody(req, MAX_BYTES + 1048576), req.headers['content-type']);
      const d = visibleDoc(user, m[1]);
      if (!d) return notFound(res);
      if (!canWriteTo(user, d.department)) return fail(res, 403, 'FORBIDDEN', 'You cannot add versions to this document.');
      if (!mp || !checkFile(res, mp.file)) return;
      const version = d.versions.length + 1;
      const v = { ...fakeVersion(d.id, version, mp.file.filename, mp.file.size, 'QUEUED', user.id, { uploadedAt: nowIso(), changeNote: mp.fields.changeNote?.slice(0, 500) || null }), fixed: false, createdMs: Date.now(), indexedAt: null };
      d.versions.push(v); d.updatedAt = nowIso();
      return send(res, 202, publicVersion(v));
    }
    if ((m = /^\/api\/v1\/documents\/([^/]+)\/download-url$/.exec(p)) && req.method === 'GET') {
      const d = visibleDoc(user, m[1]);
      if (!d) return notFound(res);
      const page = q.get('page');
      return send(res, 200, { url: `http://localhost:${PORT}/__files/sample.pdf${page ? `#page=${Number(page)}` : ''}`, expiresAt: new Date(Date.now() + 300000).toISOString() });
    }
    if ((m = /^\/api\/v1\/documents\/([^/]+)\/chunks\/([^/]+)$/.exec(p)) && req.method === 'GET') {
      const d = visibleDoc(user, m[1]);
      const c = d && ensureChunks(d).find((x) => x.id === m[2]);
      if (!c) return notFound(res);
      return send(res, 200, c);
    }

    if (p === '/api/v1/search' && req.method === 'POST') {
      const b = await readJson(req);
      if (!b || typeof b.query !== 'string' || !b.query.trim()) return fail(res, 400, 'VALIDATION_ERROR', 'query is required.', { query: 'required' });
      if (/trigger502/i.test(b.query)) return fail(res, 502, 'UPSTREAM_UNAVAILABLE', 'The search service is unavailable.');
      const started = Date.now();
      const results = doSearch(user, b); // client-sent authorizedDepartments is ignored on purpose
      return send(res, 200, { query: b.query, mode: b.mode || 'HYBRID', results, latencyMs: Date.now() - started + 40 });
    }

    return fail(res, 404, 'NOT_FOUND', `No mock route for ${req.method} ${p}`);
  } catch (e) {
    if (e.status === 413) return fail(res, 413, 'PAYLOAD_TOO_LARGE', `File exceeds the ${MAX_BYTES / 1024 / 1024} MB limit.`);
    console.error(e);
    return fail(res, 500, 'INTERNAL_ERROR', 'Mock server error.');
  }
});

seed();
if (process.argv[1] === fileURLToPath(import.meta.url)) {
  server.listen(PORT, () => {
    console.log(`Mock core-api on http://localhost:${PORT}  (${docs.length} documents, ${USERS.filter((u) => u.active).length} users)`);
    console.log(`Log in with any seed user, password ${PASSWORD}:`);
    for (const u of USERS.filter((x) => x.active)) console.log(`  ${u.role.padEnd(8)} ${u.department.padEnd(12)} ${u.email}`);
    console.log('Dev controls: POST /__mock/expire-tokens (next call -> 401), POST /__mock/reset');
    console.log('Tips: a file name containing "fail" ends in FAILED; a search containing "trigger502" returns 502.');
  });
}
export { server, seed };
