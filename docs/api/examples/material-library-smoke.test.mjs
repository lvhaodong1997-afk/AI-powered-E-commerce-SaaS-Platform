import test from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { mkdtemp, writeFile, readFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const moduleUrl = new URL('./material-library-smoke.mjs', import.meta.url);
async function runner() {
  const module = await import(moduleUrl).catch(() => ({}));
  assert.equal(typeof module.runMaterialSmoke, 'function', 'export runMaterialSmoke');
  return module.runMaterialSmoke;
}

async function fixture(t, { mode = 'local', failCreate = false, failParse = false, anonymousCode = 401 } = {}) {
  const requests = [];
  const libraries = new Map();
  const videos = new Map();
  let sessionCount = 0;
  const server = createServer(async (req, res) => {
    const chunks = [];
    for await (const chunk of req) chunks.push(chunk);
    const raw = Buffer.concat(chunks).toString();
    const url = new URL(req.url, 'http://localhost');
    const path = url.pathname.replace('/admin-api', '');
    const body = req.headers['content-type']?.includes('application/json') ? JSON.parse(raw) : {};
    requests.push({ method: req.method, path, pathname: url.pathname, raw, headers: req.headers, body });
    const reply = (data, code = 0, status = 200) => {
      res.writeHead(status, { 'content-type': 'application/json' });
      res.end(JSON.stringify({ code, data, msg: 'secret-token https://private/x?Signature=secret-signature' }));
    };
    if (path === '/oss-upload') { res.writeHead(200); res.end(); return; }
    if (!req.headers.authorization) return reply(null, anonymousCode);
    if (path === '/tk/material-library/create') {
      if (failCreate === 'redirect') { res.writeHead(307, { location: '/unexpected-redirect' }); res.end(); return; }
      if (failCreate) return reply(null, 500, 500);
      if (!body.name?.trim()) return reply(null, 400);
      const id = libraries.size + 101; libraries.set(id, { ...body, id }); return reply(id);
    }
    if (path === '/tk/material-library/update') {
      libraries.set(body.id, { ...libraries.get(body.id), ...body }); return reply(true);
    }
    if (path === '/tk/material-library/get') return reply(libraries.get(Number(url.searchParams.get('id'))));
    if (path === '/tk/material-library/page') return reply({ list: [...libraries.values()], total: libraries.size });
    if (path === '/tk/material-video/upload') {
      if (raw.includes('invalid.txt') || /filename="empty.mp4"/.test(raw)) return reply(null, 400);
      const id = videos.size + 201;
      videos.set(id, { id, libraryId: 101, status: failParse ? 'FAILED' : 'AVAILABLE', usagePhase: 'GENERAL', segmentType: 'GENERAL' });
      return reply(id);
    }
    if (path === '/tk/material-video/get') return reply(videos.get(Number(url.searchParams.get('id'))));
    if (path === '/tk/material-video/page') return reply({ list: [...videos.values()], total: videos.size });
    if (path === '/tk/material-video/segment-summary') return reply([{ segmentType: 'GENERAL', count: videos.size }]);
    if (path === '/tk/material-video/usage-phase' || path === '/tk/material-video/segment-type') {
      for (const id of body.ids) Object.assign(videos.get(id), body.usagePhase ? { usagePhase: body.usagePhase } : { segmentType: body.segmentType });
      return reply(true);
    }
    if (path === '/tk/upload/material-video/session/create') {
      if (!body.fileName?.trim() || body.fileSize === 0 || body.fileName.endsWith('.txt')) return reply(null, 400);
      sessionCount++;
      return reply({ uploadId: `session-${sessionCount}`, uploadMode: mode, chunkSize: 8, totalChunks: 3,
        uploadUrl: `${baseUrl}/oss-upload`, objectKey: 'own/key.mp4', publicUrl: 'https://private/x?Signature=secret-signature',
        accessKeyId: 'secret-access-key', policy: 'secret-policy', signature: 'secret-signature', successActionStatus: '200' });
    }
    if (path === '/tk/upload/material-video/session/complete') {
      const id = videos.size + 201;
      videos.set(id, { id, libraryId: 101, status: 'AVAILABLE', usagePhase: body.usagePhase, segmentType: body.segmentType }); return reply(id);
    }
    if (path === '/tk/upload/material-video/chunk') return reply(true);
    if (path.startsWith('/tk/upload/material-video/session/')) return reply(req.method === 'DELETE' ? true : { status: 'UPLOADING' });
    return reply(null, 404, 404);
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const baseUrl = `http://127.0.0.1:${server.address().port}`;
  const directory = await mkdtemp(join(tmpdir(), 'material-smoke-'));
  const filePath = join(directory, 'fixture.mp4');
  await writeFile(filePath, Buffer.from('0000ftyp0000moov0000data'));
  t.after(async () => { server.closeAllConnections(); await new Promise(resolve => server.close(resolve)); await rm(directory, { recursive: true, force: true }); });
  return { baseUrl, requests, filePath, directory };
}

test('read-only default issues GETs and saves no credentials or signed URLs', async t => {
  const run = await runner(); const f = await fixture(t);
  const result = await run({ baseUrl: `${f.baseUrl}/admin-api`, token: 'secret-token', tenantId: 1, outputDir: f.directory });
  assert.equal(result.ok, true);
  assert.equal(result.libraryId, null);
  assert.ok(f.requests.every(req => req.method === 'GET'));
  const evidence = await readFile(join(f.directory, `${result.runId}.json`), 'utf8');
  assert.doesNotMatch(evidence, /secret-token|secret-signature|secret-access-key|secret-policy/);
});

test('origin base URLs with or without trailing slash always prepend /admin-api', async t => {
  const run = await runner(); const f = await fixture(t);
  for (const baseUrl of [f.baseUrl, `${f.baseUrl}/`]) {
    const result = await run({ baseUrl, token: 'secret-token' });
    assert.equal(result.ok, true);
  }
  assert.ok(f.requests.length > 0);
  assert.ok(f.requests.every(req => req.pathname.startsWith('/admin-api/tk/')),
    `Wrong API paths: ${f.requests.map(req => req.pathname).join(', ')}`);
});

for (const mode of ['local', 'oss']) test(`write mode covers ${mode}, scopes mutations, and retains primary material`, async t => {
  const run = await runner(); const f = await fixture(t, { mode });
  const result = await run({ baseUrl: f.baseUrl, token: 'secret-token', tenantId: 1, filePath: f.filePath, outputDir: f.directory, allowWrite: true, pollMs: 1, timeoutMs: 2000 });
  assert.equal(result.ok, true, JSON.stringify(result.checks));
  assert.equal(result.libraryId, 101); assert.equal(result.videoIds.length, 2);
  assert.ok(f.requests.some(req => req.path === '/tk/material-video/upload' && req.raw.includes('name="file"')));
  assert.ok(f.requests.some(req => req.path === '/tk/upload/material-video/session/complete'));
  assert.ok(!f.requests.some(req => req.method === 'DELETE' && req.path.startsWith('/tk/material-')));
  const evidence = JSON.stringify(result);
  assert.doesNotMatch(evidence, /secret-token|secret-signature|secret-access-key|secret-policy|Signature=/);
  if (mode === 'oss') {
    const oss = f.requests.find(req => req.path === '/oss-upload');
    assert.ok(oss); assert.equal(oss.headers.authorization, undefined); assert.equal(oss.headers['tenant-id'], undefined);
    for (const field of ['key', 'policy', 'OSSAccessKeyId', 'signature', 'success_action_status', 'file']) assert.ok(oss.raw.includes(`name="${field}"`));
  } else assert.equal(f.requests.filter(req => req.path === '/tk/upload/material-video/chunk').length, 3);
});

test('failed POST is never retried and report records failure without raw response text', async t => {
  const run = await runner(); const f = await fixture(t, { failCreate: true });
  const result = await run({ baseUrl: f.baseUrl, token: 'secret-token', filePath: f.filePath, allowWrite: true });
  assert.equal(result.ok, false);
  assert.equal(f.requests.filter(req => req.path === '/tk/material-library/create').length, 1);
  assert.doesNotMatch(JSON.stringify(result), /secret-token|secret-signature/);
});

test('FAILED parser state is a failed check and returned resource remains available for diagnosis', async t => {
  const run = await runner(); const f = await fixture(t, { failParse: true });
  const result = await run({ baseUrl: f.baseUrl, token: 'secret-token', filePath: f.filePath, allowWrite: true, pollMs: 1, timeoutMs: 2000 });
  assert.equal(result.ok, false); assert.ok(result.videoIds.includes(201));
  assert.ok(result.checks.some(check => check.status === 'failed' && check.name.includes('parse')));
});

test('POST redirect is never followed or retried', async t => {
  const run = await runner(); const f = await fixture(t, { failCreate: 'redirect' });
  const result = await run({ baseUrl: f.baseUrl, token: 'secret-token', filePath: f.filePath, allowWrite: true });
  assert.equal(result.ok, false);
  assert.equal(f.requests.filter(req => req.path === '/tk/material-library/create').length, 1);
  assert.equal(f.requests.some(req => req.path === '/unexpected-redirect'), false);
});

test('anonymous 403 does not masquerade as successful 401 validation', async t => {
  const run = await runner(); const f = await fixture(t, { anonymousCode: 403 });
  const result = await run({ baseUrl: f.baseUrl, token: 'secret-token' });
  assert.equal(result.ok, false);
  assert.equal(result.checks.find(check => check.name.includes('anonymous')).status, 'failed');
});

test('invalid write input and URL credentials fail before issuing requests', async t => {
  const run = await runner(); const f = await fixture(t);
  await assert.rejects(run({ baseUrl: f.baseUrl, token: 'secret-token', allowWrite: true }), /requires an actual video/);
  await assert.rejects(run({ baseUrl: f.baseUrl.replace('http://', 'http://user:password@'), token: 'secret-token' }), /without credentials/);
  await assert.rejects(run({ baseUrl: f.baseUrl, token: 'secret-token', allowWrite: 'false' }), /must be a boolean/);
  assert.equal(f.requests.length, 0);
});
