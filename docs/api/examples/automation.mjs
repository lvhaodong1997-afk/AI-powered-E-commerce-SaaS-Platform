#!/usr/bin/env node
// Node.js 20+; no packages. Commands: resources | create | resume (see README).
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { pathToFileURL } from 'node:url';
import { Readable } from 'node:stream';
import { pipeline } from 'node:stream/promises';

const PREFIX = '/admin-api/tk/automation/v1';
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
export class ApiError extends Error {}

export function safeUrl(value) {
  let url;
  try { url = new URL(value); } catch { throw new ApiError('地址必须是 HTTP(S) URL'); }
  if (url.username || url.password || url.hash ||
      (url.protocol !== 'https:' && !(url.protocol === 'http:' && ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname)))) {
    throw new ApiError('生产地址必须使用无凭证的 HTTPS；HTTP 仅允许本机测试');
  }
  return url;
}

function writeJson(filename, data, exclusive = false) {
  fs.mkdirSync(path.dirname(path.resolve(filename)), { recursive: true });
  fs.writeFileSync(filename, JSON.stringify(data, null, 2), { encoding: 'utf8', mode: 0o600, flag: exclusive ? 'wx' : 'w' });
}

function readJson(filename) {
  return JSON.parse(fs.readFileSync(filename, 'utf8').replace(/^\uFEFF/, ''));
}

export function validateRequest(data) {
  for (const key of ['libraryId', 'bgmAssetId']) {
    if (!Number.isSafeInteger(data[key]) || data[key] <= 0) throw new ApiError(`${key} 必须是 resources 返回的正整数 ID`);
  }
  if (typeof data.scriptText !== 'string' || !data.scriptText.trim() || data.scriptText.length > 3000) {
    throw new ApiError('scriptText 必须是 1–3000 字的完整口播正文');
  }
  if (!['MINIMAX', 'DASHSCOPE', 'MIMO'].includes(data.ttsProvider)) throw new ApiError('ttsProvider 无效');
  const hasCode = typeof data.voiceCode === 'string' && !!data.voiceCode.trim();
  const hasProfile = data.voiceProfileId != null;
  if (hasCode === hasProfile) throw new ApiError('voiceCode 与 voiceProfileId 必须二选一');
  if (hasProfile && (!Number.isSafeInteger(data.voiceProfileId) || data.voiceProfileId <= 0)) throw new ApiError('voiceProfileId 必须是正整数');
  if (data.voiceCode != null && typeof data.voiceCode !== 'string') throw new ApiError('voiceCode 必须是字符串');
  if (data.ttsProvider === 'MIMO' && !hasProfile) throw new ApiError('MIMO 必须选择 voiceProfileId');
  const fixed = { translationEnabled: false, targetRegion: 'CN', durationMode: 'FOLLOW_NARRATION', width: 1080,
    height: 1920, fps: 30, subtitleFontSize: 'extra_large', subtitleStyle: 'classic_white', subtitlePositionMode: 'smart_safe' };
  for (const [key, expected] of Object.entries(fixed)) {
    if (key in data && data[key] !== expected) throw new ApiError(`${key} 不支持该值`);
  }
  const volume = data.bgmVolume ?? 0.4;
  if (typeof volume !== 'number' || !Number.isFinite(volume) || volume < 0 || volume > 1) throw new ApiError('bgmVolume 必须在 0–1 之间');
  if ('title' in data && (typeof data.title !== 'string' || data.title.length > 128)) throw new ApiError('title 必须是最多 128 字的字符串');
  for (const key of ['subtitleEnabled', 'subtitleKeywordEnabled', 'subtitleKaraokeEnabled']) {
    if (key in data && typeof data[key] !== 'boolean') throw new ApiError(`${key} 必须是布尔值`);
  }
  return data;
}

export class Client {
  constructor(env = process.env) {
    this.env = env;
    this.base = (env.BASE_URL || 'https://clipforgestudio.fnn.net.cn').replace(/\/$/, '');
    const url = safeUrl(this.base);
    if (!['', '/'].includes(url.pathname) || url.search) throw new ApiError('BASE_URL 只填域名，不包含 /admin-api 或查询参数');
    this.tenant = env.TENANT_ID || '';
    if (!/^\d+$/.test(this.tenant) || Number(this.tenant) <= 0) throw new ApiError('请设置自己的 TENANT_ID');
    this.tokenFile = env.TK_TOKEN_FILE || path.join(os.homedir(), '.clipforge', 'session.json');
    const saved = fs.existsSync(this.tokenFile) ? readJson(this.tokenFile) : {};
    if (Object.keys(saved).length && (saved.baseUrl !== this.base || saved.tenantId !== this.tenant)) {
      throw new ApiError('Token 文件属于其他环境/租户，请指定独立 TK_TOKEN_FILE');
    }
    this.access = env.TK_ACCESS_TOKEN || saved.accessToken;
    this.refresh = env.TK_REFRESH_TOKEN || saved.refreshToken;
  }

  saveTokens(data) {
    if (!data?.accessToken) throw new ApiError('鉴权响应缺少 accessToken');
    this.access = data.accessToken;
    this.refresh = data.refreshToken || this.refresh;
    writeJson(this.tokenFile, { baseUrl: this.base, tenantId: this.tenant, accessToken: this.access,
      refreshToken: this.refresh, expiresTime: data.expiresTime });
  }

  async send(method, endpoint, data, authenticated = true) {
    const headers = { 'Content-Type': 'application/json', 'tenant-id': this.tenant };
    if (authenticated && this.access) headers.Authorization = `Bearer ${this.access}`;
    let response;
    try {
      response = await fetch(this.base + endpoint, { method, headers, redirect: 'error',
        body: data === undefined ? undefined : JSON.stringify(data), signal: AbortSignal.timeout(30_000) });
    } catch { throw new ApiError('网络请求失败；创建响应不明时不要重复 create，请核对生成记录'); }
    try { return [response.status, await response.json()]; }
    catch { throw new ApiError('服务未返回有效 JSON；创建响应不明时请核对生成记录'); }
  }

  check(status, result) {
    if (status >= 400 || result.code !== 0) throw new ApiError(`接口失败：code=${result.code ?? status}；${result.msg || '请检查权限和参数'}`);
  }

  async login() {
    if (this.access) return;
    if (this.refresh) return this.renew();
    if (!this.env.TK_USERNAME || !this.env.TK_PASSWORD) {
      throw new ApiError('请配置 TK_ACCESS_TOKEN，或 TK_USERNAME / TK_PASSWORD（开启验证码时还需有效凭证）');
    }
    const data = { username: this.env.TK_USERNAME, password: this.env.TK_PASSWORD };
    if (this.env.TK_CAPTCHA_VERIFICATION) data.captchaVerification = this.env.TK_CAPTCHA_VERIFICATION;
    const [status, result] = await this.send('POST', '/admin-api/system/auth/login', data, false);
    this.check(status, result);
    this.saveTokens(result.data);
  }

  async renew() {
    if (!this.refresh) throw new ApiError('Token 已过期且无 refreshToken，请重新登录');
    const endpoint = '/admin-api/system/auth/refresh-token?' + new URLSearchParams({ refreshToken: this.refresh });
    const [status, result] = await this.send('POST', endpoint, undefined, false);
    this.check(status, result);
    this.saveTokens(result.data);
  }

  async request(method, endpoint, data) {
    let [status, result] = await this.send(method, endpoint, data);
    if (status === 401 || result.code === 401) {
      // Only an explicit authentication rejection is safe to resend, never a timeout.
      await this.renew();
      [status, result] = await this.send(method, endpoint, data);
    }
    this.check(status, result);
    return result.data;
  }

  async resources(libraryName) {
    const materialLibraries = [];
    for (let page = 1; page <= 100; page++) {
      const params = new URLSearchParams({ pageNo: String(page), pageSize: '100' });
      if (libraryName) params.set('name', libraryName);
      const batch = await this.request('GET', PREFIX + '/material-libraries?' + params);
      materialLibraries.push(...batch.list);
      if (!batch.list.length || materialLibraries.length >= batch.total) break;
      if (page === 100) throw new ApiError('素材库超过示例查询范围，请按 --library-name 筛选');
    }
    return { credits: await this.request('GET', PREFIX + '/credits'), materialLibraries,
      voices: await this.request('GET', PREFIX + '/voices'), bgms: await this.request('GET', PREFIX + '/bgms') };
  }

  async download(url, filename) {
    safeUrl(url);
    if (path.extname(filename).toLowerCase() !== '.mp4' || fs.existsSync(filename)) throw new ApiError('输出必须是尚不存在的 .mp4 文件');
    fs.mkdirSync(path.dirname(path.resolve(filename)), { recursive: true });
    const partial = `${filename}.part`;
    try {
      // Do not forward account credentials to signed storage URLs. Refuse redirects.
      const response = await fetch(url, { redirect: 'error', signal: AbortSignal.timeout(120_000) });
      if (!response.ok || !response.body) throw new Error('download response failed');
      await pipeline(Readable.fromWeb(response.body), fs.createWriteStream(partial, { flags: 'wx', mode: 0o600 }));
      // Hard-link creation is exclusive, even if another process creates output meanwhile.
      fs.linkSync(partial, filename);
      fs.unlinkSync(partial);
    } catch { throw new ApiError('下载失败，已保留 .part 文件；检查后删除 .part 并 resume，勿重新 create'); }
  }
}

function checkResources(request, resources) {
  if (!resources.materialLibraries.some((item) => item.id === request.libraryId)) throw new ApiError('该账号无法使用 libraryId，请先 resources 查询');
  const voices = resources.voices.filter((voice) => voice.ttsProvider === request.ttsProvider &&
    (request.voiceProfileId != null ? voice.voiceProfileId === request.voiceProfileId : voice.voiceCode === request.voiceCode));
  if (voices.length !== 1) throw new ApiError('音色不可用、重复或与 ttsProvider 不匹配，请先 resources 查询');
  if (!resources.bgms.some((item) => item.id === request.bgmAssetId)) throw new ApiError('bgmAssetId 不是当前账号可用的自有上传曲目');
}

async function finish(client, options) {
  const record = readJson(options.state);
  if ((record.baseUrl || client.base) !== client.base || String(record.tenantId || client.tenant) !== client.tenant) throw new ApiError('任务记录属于其他环境或租户');
  if (!Number.isSafeInteger(record.taskId) || record.taskId <= 0) {
    throw new ApiError('记录没有 taskId；请先在网页核对响应不明的提交，确认后填入 taskId，再 resume');
  }
  const endpoint = PREFIX + '/tasks/' + record.taskId;
  const deadline = Date.now() + options.timeout * 1000;
  while (Date.now() < deadline) {
    const task = await client.request('GET', endpoint);
    if (task.failed === true || task.status === 'FAILED') throw new ApiError(`任务失败：${task.failCode || ''}；${task.failReason || ''}`);
    if (task.completed === true && task.status === 'SUCCESS') {
      if (task.subtitleKaraokeStatus && !['SUCCESS', 'READY'].includes(task.subtitleKaraokeStatus)) {
        console.log(`字幕诊断：${task.subtitleKaraokeStatus}；${task.subtitleKaraokeReason || ''}`);
      }
      const asset = await client.request('GET', endpoint + '/download?' + new URLSearchParams({ fileName: path.basename(options.output) }));
      await client.download(asset.downloadUrl, options.output);
      console.log(`已保存视频：${options.output}`);
      return;
    }
    await sleep(Math.min(options.pollInterval * 1000, Math.max(0, deadline - Date.now())));
  }
  throw new ApiError('等待超时；任务可能仍在运行，请保留记录并使用 resume 继续查询');
}

export async function main(argv = process.argv.slice(2)) {
  const help = `ClipForge 出片 API 示例（create 会提交可能扣积分的任务）\n` +
    `node automation.mjs resources [--library-name 芒果干]\n` +
    `node automation.mjs create --request request.json --state task.json --output video.mp4 [--dry-run]\n` +
    `node automation.mjs resume --state task.json --output video.mp4\n` +
    `选项：--timeout 900 --poll-interval 3；环境变量见 .env.example 和 README.md`;
  if (!argv.length || argv.includes('--help') || argv.includes('-h')) { console.log(help); return; }
  const command = argv[0];
  if (!['resources', 'create', 'resume'].includes(command)) throw new ApiError('命令应为 resources、create 或 resume');
  const options = { request: 'request.json', state: 'task.json', output: 'video.mp4', timeout: 900, pollInterval: 3, dryRun: false };
  const keys = { '--request': 'request', '--state': 'state', '--output': 'output', '--library-name': 'libraryName', '--timeout': 'timeout', '--poll-interval': 'pollInterval' };
  for (let index = 1; index < argv.length; index++) {
    if (argv[index] === '--dry-run') { options.dryRun = true; continue; }
    const key = keys[argv[index]];
    if (!key || !argv[index + 1] || argv[index + 1].startsWith('--')) throw new ApiError('未知或缺少值的命令行参数');
    options[key] = ['timeout', 'pollInterval'].includes(key) ? Number(argv[++index]) : argv[++index];
  }
  if (!Number.isFinite(options.timeout) || !Number.isFinite(options.pollInterval) || options.timeout <= 0 || options.pollInterval <= 0) throw new ApiError('timeout 和 poll-interval 必须大于 0；生产建议轮询间隔 3–5 秒');
  if (options.dryRun && command !== 'create') throw new ApiError('--dry-run 仅用于 create');
  let request;
  if (command === 'create') {
    if (fs.existsSync(options.state)) throw new ApiError('已有任务记录，拒绝重复提交；请使用 resume 或为新的任务指定新的 --state');
    request = validateRequest(readJson(options.request));
    if (options.dryRun) { console.log('本地校验通过；尚未验证资源权限，也未提交生成任务'); return; }
  }
  const client = new Client();
  await client.login();
  if (command === 'resources') { console.log(JSON.stringify(await client.resources(options.libraryName), null, 2)); return; }
  if (fs.existsSync(options.output) || path.extname(options.output).toLowerCase() !== '.mp4') throw new ApiError('--output 必须是尚不存在的 .mp4 文件');
  if (command === 'create') {
    checkResources(request, await client.resources());
    const record = { status: 'SUBMITTING', baseUrl: client.base, tenantId: client.tenant, title: request.title, submittedAt: new Date().toISOString() };
    writeJson(options.state, record, true);
    const created = await client.request('POST', PREFIX + '/tasks', request);
    Object.assign(record, { taskId: created.taskId, status: 'SUBMITTED' });
    writeJson(options.state, record);
    console.log(`已提交 taskId=${created.taskId}；任务记录=${options.state}`);
  }
  await finish(client, options);
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  main().catch((error) => {
    // Keep transport errors free of refresh URLs, request headers and signed media URLs.
    console.error('错误：' + (error instanceof ApiError ? error.message : '本地配置/文件或响应格式错误，请检查文件与参数'));
    process.exitCode = 1;
  });
}
