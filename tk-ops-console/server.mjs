import { createServer } from 'node:http';
import { spawn, execFile } from 'node:child_process';
import { promises as fs, createReadStream, createWriteStream } from 'node:fs';
import path from 'node:path';
import net from 'node:net';
import { fileURLToPath } from 'node:url';
import mysql from 'mysql2/promise';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const CONFIG_PATH = path.join(__dirname, 'config.json');
const PID_DIR = path.join(__dirname, '.pids');
const PUBLIC_DIR = path.join(__dirname, 'public');
const SERVICE_ORDER = ['backend', 'worker', 'frontend'];
const STOP_ORDER = ['frontend', 'worker', 'backend'];
const MAX_LOG_BYTES = 4 * 1024 * 1024;

const config = await loadConfig();
const pool = mysql.createPool({
  host: config.database.host,
  port: config.database.port,
  user: config.database.user,
  password: config.database.password,
  database: config.database.database,
  waitForConnections: true,
  connectionLimit: config.database.connectionLimit || 4,
  namedPlaceholders: false
});

await fs.mkdir(PID_DIR, { recursive: true });
await fs.mkdir(resolvePath(config.managedLogDir), { recursive: true });

const server = createServer(async (req, res) => {
  try {
    const url = new URL(req.url || '/', `http://${req.headers.host || '127.0.0.1'}`);
    if (url.pathname.startsWith('/api/')) {
      await handleApi(req, res, url);
      return;
    }
    await serveStatic(res, url.pathname);
  } catch (error) {
    sendJson(res, 500, { error: error.message || String(error) });
  }
});

server.listen(config.port, config.host, () => {
  console.log(`TK Ops Console: http://${config.host}:${config.port}`);
});

async function loadConfig() {
  const raw = JSON.parse(await fs.readFile(CONFIG_PATH, 'utf8'));
  return raw;
}

async function handleApi(req, res, url) {
  if (req.method === 'GET' && url.pathname === '/api/status') {
    sendJson(res, 200, await getStatus());
    return;
  }
  if (req.method === 'POST' && url.pathname === '/api/start') {
    sendJson(res, 200, await startTarget(url.searchParams.get('service') || 'all'));
    return;
  }
  if (req.method === 'POST' && url.pathname === '/api/stop') {
    sendJson(res, 200, await stopTarget(url.searchParams.get('service') || 'all'));
    return;
  }
  if (req.method === 'GET' && url.pathname === '/api/log-files') {
    sendJson(res, 200, await getLogFiles(url.searchParams.get('service') || 'all'));
    return;
  }
  if (req.method === 'GET' && url.pathname === '/api/logs') {
    sendJson(res, 200, await readLogs(url.searchParams));
    return;
  }
  if (req.method === 'GET' && url.pathname === '/api/business-logs') {
    sendJson(res, 200, await getBusinessLogs(url.searchParams));
    return;
  }
  if (req.method === 'GET' && url.pathname === '/api/business-log-detail') {
    sendJson(res, 200, await getBusinessLogDetail(url.searchParams));
    return;
  }
  if (req.method === 'GET' && url.pathname === '/api/business-log-summary') {
    sendJson(res, 200, await getBusinessLogSummary());
    return;
  }
  sendJson(res, 404, { error: 'Not found' });
}

async function serveStatic(res, pathname) {
  const normalized = pathname === '/' ? '/index.html' : pathname;
  const filePath = path.resolve(PUBLIC_DIR, `.${normalized}`);
  if (!isInside(filePath, PUBLIC_DIR)) {
    sendText(res, 403, 'Forbidden');
    return;
  }
  try {
    const stat = await fs.stat(filePath);
    if (!stat.isFile()) {
      sendText(res, 404, 'Not found');
      return;
    }
    const contentType = filePath.endsWith('.html') ? 'text/html; charset=utf-8'
      : filePath.endsWith('.css') ? 'text/css; charset=utf-8'
        : filePath.endsWith('.js') ? 'text/javascript; charset=utf-8'
          : 'application/octet-stream';
    res.writeHead(200, { 'Content-Type': contentType });
    createReadStream(filePath).pipe(res);
  } catch {
    sendText(res, 404, 'Not found');
  }
}

async function getStatus() {
  const services = {};
  for (const key of SERVICE_ORDER) {
    services[key] = await getServiceStatus(key);
  }
  const dependencies = {
    mysql: await probeDependency('mysql', config.dependencies.mysql.port),
    redis: await probeDependency('redis', config.dependencies.redis.port)
  };
  const business = await safeDb(async () => getBusinessLogSummary(), null);
  const states = Object.values(services).map((item) => item.status);
  const projectStatus = states.every((state) => state === 'running' || state === 'running_external')
    ? 'running'
    : states.some((state) => state === 'running' || state === 'running_external' || state === 'unhealthy')
      ? 'partial'
      : 'stopped';
  return { projectStatus, services, dependencies, business, time: new Date().toISOString() };
}

async function getServiceStatus(serviceKey) {
  const service = requireService(serviceKey);
  const pidInfo = await readPidInfo(serviceKey);
  const portOpen = await isPortOpen(service.port);
  const pidAlive = pidInfo?.pid ? await isPidAlive(pidInfo.pid) : false;
  let status = 'stopped';
  if (pidAlive && portOpen) {
    status = 'running';
  } else if (pidAlive && !portOpen) {
    status = 'starting';
  } else if (!pidAlive && portOpen) {
    status = 'running_external';
  } else if (pidInfo?.pid && !pidAlive) {
    status = 'stopped';
  }
  return {
    key: serviceKey,
    name: service.name,
    status,
    port: service.port,
    pid: pidAlive ? pidInfo.pid : null,
    startedAt: pidAlive ? pidInfo.startedAt : null,
    logFiles: serviceLogFiles(serviceKey, pidInfo?.logStamp),
    recentError: await recentError(serviceKey)
  };
}

async function probeDependency(key, port) {
  return {
    key,
    port,
    status: await isPortOpen(port) ? 'running' : 'stopped'
  };
}

async function startTarget(target) {
  const targets = target === 'all' ? SERVICE_ORDER : [target];
  const results = [];
  for (const key of targets) {
    results.push(await startService(key));
  }
  return { results, status: await getStatus() };
}

async function startService(serviceKey) {
  const service = requireService(serviceKey);
  const status = await getServiceStatus(serviceKey);
  if (status.status === 'running' || status.status === 'running_external' || status.status === 'starting') {
    return { service: serviceKey, action: 'skip', message: '服务已在运行', status: status.status };
  }
  const logStamp = timestamp();
  const managedLogDir = resolvePath(config.managedLogDir);
  await fs.mkdir(managedLogDir, { recursive: true });
  const outLog = path.join(managedLogDir, `${serviceKey}-${logStamp}.out.log`);
  const errLog = path.join(managedLogDir, `${serviceKey}-${logStamp}.err.log`);
  const currentOut = path.join(managedLogDir, `${serviceKey}-current.out.log`);
  const currentErr = path.join(managedLogDir, `${serviceKey}-current.err.log`);
  const outStream = createWriteStream(outLog, { flags: 'a' });
  const errStream = createWriteStream(errLog, { flags: 'a' });
  const currentOutStream = createWriteStream(currentOut, { flags: 'a' });
  const currentErrStream = createWriteStream(currentErr, { flags: 'a' });
  const child = spawn(service.command, {
    cwd: resolvePath(service.cwd),
    shell: true,
    detached: true,
    windowsHide: true,
    stdio: ['ignore', 'pipe', 'pipe']
  });
  child.stdout.pipe(outStream);
  child.stdout.pipe(currentOutStream);
  child.stderr.pipe(errStream);
  child.stderr.pipe(currentErrStream);
  child.unref();
  await writePidInfo(serviceKey, {
    pid: child.pid,
    startedAt: new Date().toISOString(),
    command: service.command,
    logStamp,
    outLog,
    errLog
  });
  return { service: serviceKey, action: 'start', pid: child.pid, message: '启动命令已发送' };
}

async function stopTarget(target) {
  const targets = target === 'all' ? STOP_ORDER : [target];
  const results = [];
  for (const key of targets) {
    results.push(await stopService(key));
  }
  return { results, status: await getStatus() };
}

async function stopService(serviceKey) {
  requireService(serviceKey);
  const pidInfo = await readPidInfo(serviceKey);
  if (!pidInfo?.pid || !(await isPidAlive(pidInfo.pid))) {
    await removePidInfo(serviceKey);
    return { service: serviceKey, action: 'skip', message: '没有由控制台启动的运行进程' };
  }
  await killProcessTree(pidInfo.pid);
  await removePidInfo(serviceKey);
  return { service: serviceKey, action: 'stop', pid: pidInfo.pid, message: '停止命令已发送' };
}

async function killProcessTree(pid) {
  await new Promise((resolve) => {
    execFile('taskkill', ['/PID', String(pid), '/T', '/F'], { windowsHide: true }, () => resolve());
  });
}

async function getLogFiles(serviceFilter) {
  const allowedDirs = allowedLogDirs();
  const entries = [];
  for (const dir of allowedDirs) {
    try {
      const files = await fs.readdir(dir, { withFileTypes: true });
      for (const file of files) {
        if (!file.isFile() || !/\.(log|out|err)(\.\d+)?$/i.test(file.name)) {
          continue;
        }
        const service = inferService(file.name);
        if (serviceFilter !== 'all' && service !== serviceFilter) {
          continue;
        }
        const fullPath = path.join(dir, file.name);
        const stat = await fs.stat(fullPath);
        entries.push({
          name: file.name,
          path: fullPath,
          service,
          size: stat.size,
          updatedAt: stat.mtime.toISOString()
        });
      }
    } catch {
      // ignore missing log dirs
    }
  }
  entries.sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
  return { files: entries };
}

async function readLogs(params) {
  const lines = clampNumber(params.get('lines'), 300, 20, 2000);
  const keyword = (params.get('keyword') || '').trim();
  const service = params.get('service') || 'all';
  const requestedFile = params.get('file');
  let files = [];
  if (requestedFile) {
    const safePath = resolveLogFile(requestedFile);
    files = [{ path: safePath, name: path.basename(safePath), service: inferService(path.basename(safePath)) }];
  } else {
    files = (await getLogFiles(service)).files.slice(0, 5);
  }
  const chunks = [];
  for (const file of files) {
    const content = await tailFile(file.path, lines);
    const filtered = keyword
      ? content.split(/\r?\n/).filter((line) => line.toLowerCase().includes(keyword.toLowerCase())).join('\n')
      : content;
    chunks.push({ file: file.name, service: file.service, content: filtered });
  }
  return { files: chunks, lines, keyword };
}

async function tailFile(filePath, lines) {
  const stat = await fs.stat(filePath);
  const start = Math.max(0, stat.size - MAX_LOG_BYTES);
  const handle = await fs.open(filePath, 'r');
  try {
    const buffer = Buffer.alloc(stat.size - start);
    await handle.read(buffer, 0, buffer.length, start);
    return buffer.toString('utf8').split(/\r?\n/).slice(-lines).join('\n');
  } finally {
    await handle.close();
  }
}

async function getBusinessLogs(params) {
  const page = clampNumber(params.get('page'), 1, 1, 100000);
  const pageSize = clampNumber(params.get('pageSize'), 20, 1, 100);
  const type = normalizeType(params.get('type') || 'all');
  const level = (params.get('level') || 'all').toLowerCase();
  const keyword = (params.get('keyword') || '').trim().toLowerCase();
  const startTime = params.get('startTime');
  const endTime = params.get('endTime');
  const allRows = await safeDb(async () => {
    const sources = [];
    if (type === 'all' || type === 'business') {
      sources.push(...await queryUnifiedBusinessLogs(startTime, endTime));
    }
    if (type === 'all' || type === 'generation') {
      sources.push(...await queryGenerationLogs(startTime, endTime));
    }
    if (type === 'all' || type === 'publish') {
      sources.push(...await queryPublishLogs(startTime, endTime));
    }
    if (type === 'all' || type === 'credit') {
      sources.push(...await queryCreditLogs(startTime, endTime));
    }
    if (type === 'all' || type === 'reference') {
      sources.push(...await queryReferenceLogs(startTime, endTime));
    }
    return sources;
  }, []);
  const filtered = allRows
    .filter((row) => level === 'all' || row.level.toLowerCase() === level || row.status?.toLowerCase() === level)
    .filter((row) => !keyword || JSON.stringify(row).toLowerCase().includes(keyword))
    .sort((a, b) => String(b.time).localeCompare(String(a.time)));
  const offset = (page - 1) * pageSize;
  return {
    list: filtered.slice(offset, offset + pageSize),
    total: filtered.length,
    page,
    pageSize
  };
}

async function getBusinessLogDetail(params) {
  const source = normalizeType(params.get('source') || params.get('type') || '');
  const id = Number(params.get('id'));
  if (!source || !Number.isFinite(id)) {
    throw new Error('source 和 id 必填');
  }
  return safeDb(async () => {
    if (source === 'business') {
      const [rows] = await pool.query('SELECT * FROM tk_business_log WHERE id = ?', [id]);
      return { source, detail: rows[0] || null };
    }
    if (source === 'generation') {
      const [rows] = await pool.query('SELECT * FROM tk_generation_task WHERE id = ? AND deleted = b\'0\'', [id]);
      return { source, detail: rows[0] || null };
    }
    if (source === 'publish') {
      const [rows] = await pool.query('SELECT * FROM tk_tiktok_publish_task WHERE id = ? AND deleted = b\'0\'', [id]);
      const [details] = await pool.query('SELECT * FROM tk_tiktok_publish_detail WHERE publish_task_id = ? AND deleted = b\'0\' ORDER BY id DESC', [id]);
      return { source, detail: rows[0] || null, children: details };
    }
    if (source === 'credit') {
      const [rows] = await pool.query('SELECT * FROM tk_credit_log WHERE id = ? AND deleted = b\'0\'', [id]);
      return { source, detail: rows[0] || null };
    }
    if (source === 'reference') {
      const [rows] = await pool.query('SELECT * FROM tk_reference_analysis WHERE id = ? AND deleted = b\'0\'', [id]);
      const [options] = await pool.query('SELECT id, option_no, title, conversion_level FROM tk_reference_script_option WHERE analysis_id = ? AND deleted = b\'0\' ORDER BY option_no ASC', [id]);
      return { source, detail: rows[0] || null, children: options };
    }
    throw new Error(`未知业务日志来源: ${source}`);
  }, { source, detail: null, dbError: true });
}

async function getBusinessLogSummary() {
  return safeDb(async () => {
    const [generation] = await pool.query("SELECT COUNT(*) total, SUM(status = 'FAILED') failed FROM tk_generation_task WHERE deleted = b'0' AND DATE(create_time) = CURDATE()");
    const [publish] = await pool.query("SELECT COUNT(*) total, SUM(status IN ('FAILED','PARTIAL_SUCCESS')) failed FROM tk_tiktok_publish_task WHERE deleted = b'0' AND DATE(create_time) = CURDATE()");
    const [credit] = await pool.query("SELECT COALESCE(SUM(credits), 0) credits FROM tk_credit_log WHERE deleted = b'0' AND DATE(create_time) = CURDATE() AND action = 'FREEZE'");
    const [recentFailures] = await pool.query(`
      SELECT 'generation' type, id, status, fail_reason message, create_time time FROM tk_generation_task WHERE deleted = b'0' AND status = 'FAILED'
      UNION ALL
      SELECT 'publish' type, id, status, CONCAT('失败数:', failed_count) message, create_time time FROM tk_tiktok_publish_task WHERE deleted = b'0' AND status IN ('FAILED','PARTIAL_SUCCESS')
      UNION ALL
      SELECT 'reference' type, id, status, fail_reason message, create_time time FROM tk_reference_analysis WHERE deleted = b'0' AND status = 'FAILED'
      ORDER BY time DESC LIMIT 5
    `);
    return {
      todayGenerationTotal: Number(generation[0]?.total || 0),
      todayGenerationFailed: Number(generation[0]?.failed || 0),
      todayPublishTotal: Number(publish[0]?.total || 0),
      todayPublishFailed: Number(publish[0]?.failed || 0),
      todayFrozenCredits: Number(credit[0]?.credits || 0),
      recentFailures
    };
  }, {
    todayGenerationTotal: 0,
    todayGenerationFailed: 0,
    todayPublishTotal: 0,
    todayPublishFailed: 0,
    todayFrozenCredits: 0,
    recentFailures: [],
    dbError: true
  });
}

async function queryUnifiedBusinessLogs(startTime, endTime) {
  if (!(await tableExists('tk_business_log'))) {
    return [];
  }
  const { where, values } = timeWhere('create_time', startTime, endTime, true);
  const [rows] = await pool.query(`
    SELECT id, biz_type, biz_id, level, action, status, message, tenant_id, operator_id, create_time
    FROM tk_business_log ${where}
    ORDER BY create_time DESC LIMIT 500
  `, values);
  return rows.map((row) => ({
    id: row.id,
    source: 'business',
    type: row.biz_type,
    bizId: row.biz_id,
    level: normalizeLevel(row.level, row.status),
    status: row.status,
    action: row.action,
    message: row.message,
    tenantId: row.tenant_id,
    operatorId: row.operator_id,
    time: row.create_time
  }));
}

async function queryGenerationLogs(startTime, endTime) {
  const { where, values } = timeWhere('create_time', startTime, endTime);
  const [rows] = await pool.query(`
    SELECT id, tenant_id, company_id, status, progress, title, fail_reason, output_url, create_time
    FROM tk_generation_task WHERE deleted = b'0' ${where}
    ORDER BY create_time DESC LIMIT 500
  `, values);
  return rows.map((row) => ({
    id: row.id,
    source: 'generation',
    type: 'generation',
    bizId: row.id,
    level: normalizeLevel(null, row.status),
    status: row.status,
    action: '生成任务',
    message: row.fail_reason || `${row.title || '未命名任务'}，进度 ${row.progress || 0}%`,
    tenantId: row.tenant_id,
    companyId: row.company_id,
    outputUrl: row.output_url,
    time: row.create_time
  }));
}

async function queryPublishLogs(startTime, endTime) {
  const { where, values } = timeWhere('t.create_time', startTime, endTime);
  const [rows] = await pool.query(`
    SELECT t.id, t.tenant_id, t.company_id, t.status, t.title, t.account_count, t.success_count, t.failed_count, t.pending_count,
           MAX(d.fail_reason) fail_reason, t.create_time
    FROM tk_tiktok_publish_task t
    LEFT JOIN tk_tiktok_publish_detail d ON d.publish_task_id = t.id AND d.deleted = b'0'
    WHERE t.deleted = b'0' ${where}
    GROUP BY t.id
    ORDER BY t.create_time DESC LIMIT 500
  `, values);
  return rows.map((row) => ({
    id: row.id,
    source: 'publish',
    type: 'publish',
    bizId: row.id,
    level: normalizeLevel(null, row.status),
    status: row.status,
    action: 'TikTok发布',
    message: row.fail_reason || `${row.title || '发布任务'}，成功 ${row.success_count || 0}/${row.account_count || 0}`,
    tenantId: row.tenant_id,
    companyId: row.company_id,
    time: row.create_time
  }));
}

async function queryCreditLogs(startTime, endTime) {
  const { where, values } = timeWhere('create_time', startTime, endTime);
  const [rows] = await pool.query(`
    SELECT id, tenant_id, biz_type, biz_id, action, credits, status, remark, create_time
    FROM tk_credit_log WHERE deleted = b'0' ${where}
    ORDER BY create_time DESC LIMIT 500
  `, values);
  return rows.map((row) => ({
    id: row.id,
    source: 'credit',
    type: 'credit',
    bizId: row.biz_id,
    level: normalizeLevel(null, row.status),
    status: row.status,
    action: `积分${row.action}`,
    message: `${row.biz_type || '-'} ${row.credits || 0} 积分${row.remark ? `，${row.remark}` : ''}`,
    tenantId: row.tenant_id,
    time: row.create_time
  }));
}

async function queryReferenceLogs(startTime, endTime) {
  const { where, values } = timeWhere('create_time', startTime, endTime);
  const [rows] = await pool.query(`
    SELECT id, tenant_id, company_id, library_id, source_url, product_name, status, fail_reason, create_time
    FROM tk_reference_analysis WHERE deleted = b'0' ${where}
    ORDER BY create_time DESC LIMIT 500
  `, values);
  return rows.map((row) => ({
    id: row.id,
    source: 'reference',
    type: 'reference',
    bizId: row.id,
    level: normalizeLevel(null, row.status),
    status: row.status,
    action: '对标分析',
    message: row.fail_reason || `${row.product_name || '未识别商品'}：${row.source_url}`,
    tenantId: row.tenant_id,
    companyId: row.company_id,
    time: row.create_time
  }));
}

async function tableExists(tableName) {
  const [rows] = await pool.query('SELECT COUNT(1) count FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?', [tableName]);
  return Number(rows[0]?.count || 0) > 0;
}

function timeWhere(column, startTime, endTime, omitAnd = false) {
  const clauses = [];
  const values = [];
  if (startTime) {
    clauses.push(`${column} >= ?`);
    values.push(startTime);
  }
  if (endTime) {
    clauses.push(`${column} <= ?`);
    values.push(endTime);
  }
  if (clauses.length === 0) {
    return { where: '', values };
  }
  return { where: `${omitAnd ? 'WHERE' : 'AND'} ${clauses.join(' AND ')}`, values };
}

async function safeDb(fn, fallback) {
  try {
    return await fn();
  } catch (error) {
    if (Array.isArray(fallback)) {
      return fallback;
    }
    return { ...(fallback || {}), error: error.message || String(error) };
  }
}

function normalizeLevel(level, status) {
  if (level) {
    return String(level).toLowerCase();
  }
  const normalized = String(status || '').toUpperCase();
  if (['FAILED', 'REFUNDED', 'ERROR'].includes(normalized)) {
    return 'error';
  }
  if (['PARTIAL_SUCCESS', 'PROCESSING', 'IN_PROGRESS', 'PENDING'].includes(normalized)) {
    return 'warn';
  }
  return 'info';
}

function normalizeType(type) {
  const value = String(type || '').toLowerCase();
  const aliases = {
    all: 'all',
    business: 'business',
    unified: 'business',
    generation: 'generation',
    publish: 'publish',
    tiktok: 'publish',
    credit: 'credit',
    reference: 'reference'
  };
  return aliases[value] || value;
}

function requireService(serviceKey) {
  const service = config.services[serviceKey];
  if (!service) {
    throw new Error(`未知服务: ${serviceKey}`);
  }
  return service;
}

function serviceLogFiles(serviceKey, logStamp) {
  const dir = resolvePath(config.managedLogDir);
  const files = [
    path.join(dir, `${serviceKey}-current.out.log`),
    path.join(dir, `${serviceKey}-current.err.log`)
  ];
  if (logStamp) {
    files.push(path.join(dir, `${serviceKey}-${logStamp}.out.log`));
    files.push(path.join(dir, `${serviceKey}-${logStamp}.err.log`));
  }
  return files;
}

async function recentError(serviceKey) {
  const errFile = path.join(resolvePath(config.managedLogDir), `${serviceKey}-current.err.log`);
  try {
    const content = await tailFile(errFile, 20);
    return content.split(/\r?\n/).filter(Boolean).slice(-3).join('\n');
  } catch {
    return '';
  }
}

async function readPidInfo(serviceKey) {
  try {
    return JSON.parse(await fs.readFile(pidPath(serviceKey), 'utf8'));
  } catch {
    return null;
  }
}

async function writePidInfo(serviceKey, info) {
  await fs.writeFile(pidPath(serviceKey), JSON.stringify(info, null, 2), 'utf8');
}

async function removePidInfo(serviceKey) {
  try {
    await fs.unlink(pidPath(serviceKey));
  } catch {
    // ignore
  }
}

function pidPath(serviceKey) {
  return path.join(PID_DIR, `${serviceKey}.json`);
}

async function isPidAlive(pid) {
  try {
    process.kill(pid, 0);
    return true;
  } catch {
    return false;
  }
}

async function isPortOpen(port) {
  return new Promise((resolve) => {
    const socket = net.createConnection({ host: '127.0.0.1', port, timeout: 800 });
    socket.on('connect', () => {
      socket.destroy();
      resolve(true);
    });
    socket.on('timeout', () => {
      socket.destroy();
      resolve(false);
    });
    socket.on('error', () => resolve(false));
  });
}

function resolvePath(value) {
  return path.resolve(__dirname, value);
}

function allowedLogDirs() {
  return config.logDirs.map(resolvePath);
}

function resolveLogFile(value) {
  const fullPath = path.resolve(value);
  const allowed = allowedLogDirs();
  if (!allowed.some((dir) => isInside(fullPath, dir))) {
    throw new Error('日志文件不在允许读取的目录内');
  }
  return fullPath;
}

function inferService(fileName) {
  const lower = fileName.toLowerCase();
  if (lower.includes('worker') || lower.includes('uvicorn')) {
    return 'worker';
  }
  if (lower.includes('front') || lower.includes('admin') || lower.includes('vite') || lower.includes('ui')) {
    return 'frontend';
  }
  if (lower.includes('server') || lower.includes('yudao')) {
    return 'backend';
  }
  if (lower.includes('mysql')) {
    return 'mysql';
  }
  return 'other';
}

function isInside(child, parent) {
  const relative = path.relative(path.resolve(parent), path.resolve(child));
  return relative === '' || (!relative.startsWith('..') && !path.isAbsolute(relative));
}

function timestamp() {
  const now = new Date();
  const pad = (value) => String(value).padStart(2, '0');
  return `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}-${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`;
}

function clampNumber(value, fallback, min, max) {
  const number = Number(value);
  if (!Number.isFinite(number)) {
    return fallback;
  }
  return Math.max(min, Math.min(max, Math.floor(number)));
}

function sendJson(res, statusCode, payload) {
  res.writeHead(statusCode, {
    'Content-Type': 'application/json; charset=utf-8',
    'Access-Control-Allow-Origin': '*'
  });
  res.end(JSON.stringify(payload));
}

function sendText(res, statusCode, text) {
  res.writeHead(statusCode, { 'Content-Type': 'text/plain; charset=utf-8' });
  res.end(text);
}
