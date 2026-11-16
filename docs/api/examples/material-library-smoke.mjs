#!/usr/bin/env node
/**
 * Node 18+; built-ins only. Default: GET queries, with an anonymous auth check.
 * Credentials are environment variables, never command-line arguments:
 * MATERIAL_API_BASE_URL=https://host/admin-api MATERIAL_API_TOKEN=... MATERIAL_API_TENANT_ID=1
 * node material-library-smoke.mjs [--allow-write] [--file video.mp4] [--output evidence-dir]
 * Write mode creates and RETAINS a unique ECOMMERCE library and two uploaded videos.
 * It cancels only its own disposable upload session. No video/library deletion or paid generation.
 * A failed POST is never retried; inspect evidence before starting another run.
 */
import { readFile, stat, mkdir, writeFile } from 'node:fs/promises';
import { basename, extname, join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { randomUUID } from 'node:crypto';
import { Blob } from 'node:buffer';
import { setTimeout as delay } from 'node:timers/promises';

const LIB = '/tk/material-library';
const VIDEO = '/tk/material-video';
const SESSION = '/tk/upload/material-video/session';
const SAFE_ENUMS = /^(AVAILABLE|FAILED|UPLOADING|PARSING|COMPLETED|CANCELLED|CANCELED|GENERAL|ATTENTION|PRODUCT_SHOW|RESULT_EFFECT|S[1-7]_[A-Z]+|local|oss|ECOMMERCE)$/;
const SAFE_NUMBERS = new Set(['id', 'libraryId', 'fileSize', 'size', 'duration', 'width', 'height', 'chunkIndex', 'chunkSize', 'totalChunks', 'uploadedSize', 'total', 'count', 'pageNo', 'pageSize', 'code']);

// Allowlist evidence instead of trying to redact every possible provider error/secret.
// Raw response messages, arbitrary strings, URLs, policies, signatures and headers never enter evidence.
function evidence(value, key = '') {
  if (value === null || value === undefined) return value ?? null;
  if (typeof value === 'boolean') return value;
  if (typeof value === 'number') return SAFE_NUMBERS.has(key) && Number.isFinite(value) ? value : '[omitted]';
  if (typeof value === 'string') {
    if (['status', 'usagePhase', 'segmentType', 'uploadMode', 'materialPurpose'].includes(key) && SAFE_ENUMS.test(value)) return value;
    if (key === 'uploadId' && /^[a-zA-Z0-9_-]{1,128}$/.test(value)) return value;
    return '[omitted]';
  }
  if (Array.isArray(value)) return value.slice(0, 100).map(item => evidence(item, key === 'ids' ? 'id' : key));
  return Object.fromEntries(Object.entries(value).map(([name, item]) => [name, evidence(item, name)]));
}

function resourceId(value) {
  if (!Number.isSafeInteger(value) || value <= 0) throw new Error('Expected a positive safe-integer resource ID');
  return value;
}

function uploadId(value) {
  if (typeof value !== 'string' || !/^[a-zA-Z0-9_-]{1,128}$/.test(value)) throw new Error('Invalid upload ID returned');
  return value;
}

function multipart(fields, bytes, filename, mime, fileField = 'file') {
  const form = new FormData();
  for (const [name, value] of Object.entries(fields)) form.append(name, String(value));
  form.append(fileField, new Blob([bytes], { type: mime }), filename);
  return form;
}

/** Returns sanitized evidence, checks and retained resource IDs. Never logs credentials. */
export async function runMaterialSmoke({ baseUrl, token, tenantId, filePath, outputDir,
  allowWrite = false, pollMs = 3000, timeoutMs = 180000 } = {}) {
  const base = new URL(baseUrl);
  if (!['http:', 'https:'].includes(base.protocol) || base.username || base.password || base.search || base.hash) throw new Error('Use an HTTP(S) base URL without credentials, query or fragment');
  if (!token || typeof token !== 'string' || /[\r\n]/.test(token)) throw new Error('A platform access token is required');
  if (typeof allowWrite !== 'boolean') throw new Error('allowWrite must be a boolean');
  if (!Number.isFinite(pollMs) || pollMs < 1 || !Number.isFinite(timeoutMs) || timeoutMs < 1) throw new Error('pollMs and timeoutMs must be positive numbers');
  // URL.pathname normalizes an empty assignment back to '/'. Check the separate
  // string before assignment so an origin consistently targets /admin-api.
  const normalizedPath = base.pathname.replace(/\/+$/, '');
  base.pathname = normalizedPath || '/admin-api';
  const apiBase = base.href.replace(/\/$/, '');
  let bytes, filename, mime;
  if (allowWrite) {
    if (!filePath) throw new Error('Write mode requires an actual video file');
    const info = await stat(filePath);
    const extension = extname(filePath).toLowerCase();
    if (!info.isFile() || info.size < 1 || info.size > 100 * 1024 * 1024 || !['.mp4', '.mov', '.webm'].includes(extension)) throw new Error('Use a nonempty mp4/mov/webm file up to 100 MiB');
    bytes = await readFile(filePath);
    filename = basename(filePath);
    mime = extension === '.mov' ? 'video/quicktime' : extension === '.webm' ? 'video/webm' : 'video/mp4';
  }
  const report = { runId: `material-smoke-${new Date().toISOString().replace(/[:.]/g, '-')}-${randomUUID().slice(0, 8)}`,
    startedAt: new Date().toISOString(), mode: allowWrite ? 'write' : 'read-only', ok: false,
    libraryId: null, videoIds: [], sessionIds: [], checks: [], trace: [],
    retention: 'Primary library and videos are retained. Only the runner-owned disposable session is cancelled. No automatic cleanup or paid generation.' };

  async function request(method, path, { body, anonymous = false, external = false } = {}) {
    if (!allowWrite && method !== 'GET') throw new Error('Write request blocked by read-only mode');
    // External OSS traffic intentionally has no Authorization, tenant-id, cookies or redirects.
    const headers = external || anonymous ? {} : { Authorization: `Bearer ${token.replace(/^Bearer\s+/i, '')}` };
    if (!external && tenantId !== undefined && tenantId !== null) headers['tenant-id'] = String(tenantId);
    const form = body instanceof FormData;
    if (body !== undefined && !form) headers['content-type'] = 'application/json';
    const entry = { sequence: report.trace.length + 1, method,
      path: external ? '[OSS upload URL omitted]' : path,
      auth: external || anonymous ? 'none' : 'Bearer [omitted]', tenantHeader: !external && tenantId != null,
      request: form ? { multipartFields: [...body.keys()] } : evidence(body), startedAt: new Date().toISOString() };
    report.trace.push(entry);
    try {
      const response = await fetch(external ? path : `${apiBase}${path}`, {
        method, headers, body: body === undefined ? undefined : form ? body : JSON.stringify(body),
        redirect: 'error', signal: AbortSignal.timeout(timeoutMs)
      });
      entry.httpStatus = response.status;
      if (external) {
        await response.arrayBuffer(); // Drain, but never retain provider response text.
        entry.ok = response.ok;
        if (!response.ok) throw new Error('OSS upload failed');
        return { data: true, code: 0, httpStatus: response.status };
      }
      const text = await response.text();
      let parsed;
      try { parsed = JSON.parse(text); } catch { throw new Error('API returned a non-JSON response'); }
      const code = typeof parsed?.code === 'number' ? parsed.code : null;
      entry.code = code;
      entry.response = evidence(parsed?.data);
      entry.ok = response.ok && code === 0;
      return { data: parsed?.data, code, httpStatus: response.status, ok: entry.ok };
    } catch (error) {
      entry.ok = false;
      entry.error = error?.name === 'TimeoutError' ? 'Request timed out; outcome may be unknown; no retry performed' : 'Request failed; outcome may be unknown; no retry performed';
      throw new Error(entry.error);
    } finally { entry.finishedAt = new Date().toISOString(); }
  }

  async function checked(name, action) {
    const check = { name, status: 'running' }; report.checks.push(check);
    try { const value = await action(); check.status = 'passed'; return value; }
    catch { check.status = 'failed'; check.reason = 'Assertion or request failed; consult sanitized request trace'; return undefined; }
  }
  async function success(method, path, body) {
    const response = await request(method, path, { body });
    if (!response.ok) throw new Error('Expected successful API envelope');
    return response.data;
  }
  async function negative(name, method, path, body, codes, anonymous = false) {
    return checked(name, async () => {
      const response = await request(method, path, { body, anonymous });
      if (!codes.includes(response.code) && !codes.includes(response.httpStatus)) throw new Error('Expected rejection code');
    });
  }
  async function pollVideo(id, label) {
    return checked(`${label}: parse AVAILABLE`, async () => {
      const deadline = Date.now() + timeoutMs;
      do {
        const video = await success('GET', `${VIDEO}/get?id=${id}`);
        if (video?.id !== id || video.libraryId !== report.libraryId) throw new Error('Video ownership mismatch');
        if (video.status === 'AVAILABLE') return video;
        if (video.status === 'FAILED') throw new Error('Parser failed');
        await delay(Math.min(pollMs, Math.max(1, deadline - Date.now())));
      } while (Date.now() < deadline);
      throw new Error('Parser timeout');
    });
  }
  async function createSession(name) {
    const session = await checked(name, () => success('POST', `${SESSION}/create`, { libraryId: report.libraryId, fileName: filename, fileSize: bytes.length, contentType: mime }));
    if (session) { uploadId(session.uploadId); report.sessionIds.push(session.uploadId); }
    return session;
  }

  try {
    await checked('library page query', () => success('GET', `${LIB}/page?pageNo=1&pageSize=10`));
    await checked('video page query', () => success('GET', `${VIDEO}/page?pageNo=1&pageSize=10`));
    await negative('anonymous query rejected with 401', 'GET', `${LIB}/page?pageNo=1&pageSize=1`, undefined, [401], true);
    if (allowWrite) {
      const name = report.runId;
      const created = await checked('create dedicated ECOMMERCE library', async () => {
        const id = resourceId(await success('POST', `${LIB}/create`, { name, materialPurpose: 'ECOMMERCE', description: 'Dedicated API smoke test; retained for end-to-end generation', defaulted: false, status: 0 }));
        report.libraryId = id; return id;
      });
      if (created) {
        await checked('get own library', async () => {
          const library = await success('GET', `${LIB}/get?id=${created}`);
          if (library?.id !== created || library.materialPurpose !== 'ECOMMERCE') throw new Error('Library mismatch');
        });
        await checked('update own library', () => success('PUT', `${LIB}/update`, { id: created, name, materialPurpose: 'ECOMMERCE', description: 'API smoke updated; retained for end-to-end generation' }));
        await checked('search dedicated library', async () => {
          const page = await success('GET', `${LIB}/page?pageNo=1&pageSize=10&name=${encodeURIComponent(name)}&materialPurpose=ECOMMERCE`);
          if (!page?.list?.some(item => item.id === created)) throw new Error('Created library absent from search');
        });
        const ordinary = await checked('ordinary multipart upload', async () => {
          const id = resourceId(await success('POST', `${VIDEO}/upload`, multipart({ libraryId: created, tags: 'api-smoke', usagePhase: 'GENERAL', segmentType: 'GENERAL' }, bytes, filename, mime)));
          report.videoIds.push(id); return id;
        });
        if (ordinary) {
          await pollVideo(ordinary, 'ordinary upload');
          await checked('update own video usage', () => success('PUT', `${VIDEO}/usage-phase`, { ids: [ordinary], usagePhase: 'ATTENTION' }));
          await checked('update own video segment', () => success('PUT', `${VIDEO}/segment-type`, { ids: [ordinary], segmentType: 'S1_HOOK' }));
          await checked('verify own video metadata update', async () => {
            const video = await success('GET', `${VIDEO}/get?id=${ordinary}`);
            if (video?.usagePhase !== 'ATTENTION' || video?.segmentType !== 'S1_HOOK') throw new Error('Metadata update mismatch');
          });
        }
        await negative('empty library name rejected', 'POST', `${LIB}/create`, { name: '', materialPurpose: 'ECOMMERCE' }, [400]);
        await negative('empty session filename rejected', 'POST', `${SESSION}/create`, { libraryId: created, fileName: '', fileSize: bytes.length }, [400]);
        await negative('invalid extension rejected', 'POST', `${VIDEO}/upload`, multipart({ libraryId: created }, Buffer.from('invalid'), 'invalid.txt', 'text/plain'), [400, 1060000012]);
        await negative('empty file rejected', 'POST', `${VIDEO}/upload`, multipart({ libraryId: created }, Buffer.alloc(0), 'empty.mp4', mime), [400, 1060000010]);

        // This disposable session has no uploaded object and is independent of retained material.
        const disposable = await createSession('create disposable session for cancellation');
        if (disposable) {
          const path = `${SESSION}/${encodeURIComponent(disposable.uploadId)}`;
          await checked('get own disposable session', () => success('GET', path));
          await checked('cancel own disposable session', () => success('DELETE', path));
        }
        const session = await createSession('create session for upload completion');
        if (session) {
          await checked('get own upload session', () => success('GET', `${SESSION}/${encodeURIComponent(session.uploadId)}`));
          const uploaded = await checked('upload bytes via selected session mode', async () => {
            if (session.uploadMode === 'oss') {
              const endpoint = new URL(session.uploadUrl);
              if (!['https:', 'http:'].includes(endpoint.protocol) || endpoint.username || endpoint.password) throw new Error('Invalid OSS endpoint');
              for (const field of ['objectKey', 'policy', 'accessKeyId', 'signature']) if (!session[field]) throw new Error('Missing OSS form field');
              await request('POST', endpoint.href, { external: true, body: multipart({ key: session.objectKey, policy: session.policy, OSSAccessKeyId: session.accessKeyId,
                signature: session.signature, success_action_status: session.successActionStatus || '200' }, bytes, filename, mime) });
            } else if (session.uploadMode === 'local') {
              if (!Number.isSafeInteger(session.chunkSize) || session.chunkSize < 1 || session.totalChunks !== Math.ceil(bytes.length / session.chunkSize)) throw new Error('Invalid chunk geometry');
              for (let index = 0; index < session.totalChunks; index++) {
                const chunk = bytes.subarray(index * session.chunkSize, Math.min(bytes.length, (index + 1) * session.chunkSize));
                await success('POST', '/tk/upload/material-video/chunk', multipart({ uploadId: session.uploadId, chunkIndex: index }, chunk, `${index}.part`, 'application/octet-stream', 'chunk'));
              }
            } else throw new Error('Unknown upload mode');
            return true;
          });
          if (uploaded) {
            const completed = await checked('complete own upload session', async () => {
              const body = { uploadId: session.uploadId, libraryId: created, fileName: filename, fileSize: bytes.length, contentType: mime, tags: 'api-smoke-session', usagePhase: 'PRODUCT_SHOW', segmentType: 'GENERAL' };
              if (session.uploadMode === 'oss') body.objectKey = session.objectKey;
              const id = resourceId(await success('POST', `${SESSION}/complete`, body)); report.videoIds.push(id); return id;
            });
            if (completed) await pollVideo(completed, 'session upload');
          }
        }
        await checked('query own video page', () => success('GET', `${VIDEO}/page?pageNo=1&pageSize=10&libraryId=${created}`));
        await checked('query own segment summary', () => success('GET', `${VIDEO}/segment-summary?libraryId=${created}`));
        await checked('get retained library statistics', () => success('GET', `${LIB}/get?id=${created}`));
      }
    }
  } catch { report.checks.push({ name: 'runner prerequisite', status: 'failed', reason: 'Unexpected invalid response or request; raw details omitted' }); }
  report.finishedAt = new Date().toISOString();
  report.ok = report.checks.length > 0 && report.checks.every(check => check.status === 'passed');
  report.summary = { passed: report.checks.filter(check => check.status === 'passed').length, failed: report.checks.filter(check => check.status === 'failed').length };
  if (outputDir) {
    await mkdir(outputDir, { recursive: true });
    await writeFile(join(outputDir, `${report.runId}.json`), `${JSON.stringify(report, null, 2)}\n`, { flag: 'wx', mode: 0o600 });
  }
  return report;
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  try {
    const args = process.argv.slice(2);
    const options = { baseUrl: process.env.MATERIAL_API_BASE_URL, token: process.env.MATERIAL_API_TOKEN, tenantId: process.env.MATERIAL_API_TENANT_ID,
      filePath: process.env.MATERIAL_SMOKE_FILE, outputDir: process.env.MATERIAL_SMOKE_OUTPUT_DIR, allowWrite: false };
    for (let index = 0; index < args.length; index++) {
      if (args[index] === '--allow-write') options.allowWrite = true;
      else if (['--file', '--output'].includes(args[index]) && args[index + 1] && !args[index + 1].startsWith('--')) options[args[index++] === '--file' ? 'filePath' : 'outputDir'] = args[index];
      else throw new Error('Unsupported argument');
    }
    const result = await runMaterialSmoke(options);
    process.stdout.write(`${JSON.stringify(result, null, 2)}\n`);
    process.exitCode = result.ok ? 0 : 1;
  } catch {
    process.stderr.write('Smoke runner failed. Check base URL, token environment variable, arguments, file and output directory. Raw error details are intentionally omitted.\n');
    process.exitCode = 1;
  }
}
