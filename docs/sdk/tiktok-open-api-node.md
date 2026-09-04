# TikTok Open API Node.js example

Run this only in a server process. Read credentials from server environment variables; never expose them to a browser or commit their values.

```js
import crypto from 'node:crypto';
import fs from 'node:fs/promises';

const base = 'https://tkassetplant.fnn.net.cn/admin-api/tk/open/v1/tiktok';
const clientId = process.env.TK_OPEN_API_CLIENT_ID;
const hmacKey = process.env.TK_OPEN_API_HMAC_KEY;
if (!clientId || !hmacKey) throw new Error('missing server-side Open API environment variables');

function sha256(bytes) {
  return crypto.createHash('sha256').update(bytes).digest('hex');
}
function sign(method, target, timestamp, nonce, body = Buffer.alloc(0)) {
  const canonical = `${method.toUpperCase()}\n${target}\n${timestamp}\n${nonce}\n${sha256(body)}`;
  return crypto.createHmac('sha256', hmacKey).update(canonical).digest('base64');
}
async function api(method, path, body, extraHeaders = {}) {
  const bytes = body === undefined ? Buffer.alloc(0) : Buffer.isBuffer(body) ? body : Buffer.from(JSON.stringify(body));
  const timestamp = String(Math.floor(Date.now() / 1000));
  const nonce = crypto.randomUUID().replaceAll('-', '');
  const target = `/admin-api/tk/open/v1/tiktok${path}`;
  const response = await fetch(`${base}${path}`, {
    method,
    headers: {
      'X-TK-Client-Id': clientId, 'X-TK-Timestamp': timestamp, 'X-TK-Nonce': nonce,
      'X-TK-Request-Id': crypto.randomUUID(), 'X-TK-Signature': sign(method, target, timestamp, nonce, bytes),
      ...(body !== undefined ? {'Content-Type': Buffer.isBuffer(body) ? 'application/octet-stream' : 'application/json'} : {}),
      ...extraHeaders
    }, body: body === undefined ? undefined : bytes
  });
  const result = await response.json();
  if (!response.ok || result.code !== 0) throw new Error(`${response.status} ${result.code}: ${result.msg}`);
  return result.data;
}
```

The following is the minimum authorization, upload, publish, and query sequence. A redirect authorization must be completed by a user in a browser. `QR_CODE` is also implemented: use it as `authMode`, display `qrcodeUrl`, then poll the same session endpoint.

```js
const auth = await api('POST', '/auth/sessions', {
  externalAccountId: 'your-account-reference', authMode: 'REDIRECT', clientState: 'your-state'
});
console.log(`Open this URL in the user browser: ${auth.authorizeUrl}`);

// Poll only after the user completes the authorization. For QR_CODE, poll while WAITING.
const authorized = await api('GET', `/auth/sessions/${encodeURIComponent(auth.authSessionId)}`);
if (authorized.status !== 'SUCCESS') throw new Error(`authorization is ${authorized.status}`);

const video = await fs.readFile('/absolute/path/video.mp4');
const digest = sha256(video);
const upload = await api('POST', '/media/uploads', {
  fileName: 'video.mp4', fileSize: video.length, contentType: 'video/mp4', sha256: digest
});

if (upload.uploadMode === 'LOCAL') {
  for (let index = 0; index < upload.totalChunks; index++) {
    const start = index * upload.chunkSize;
    await api('PUT', `/media/uploads/${upload.uploadId}/chunks/${index}`, video.subarray(start, start + upload.chunkSize));
  }
} else {
  const form = new FormData();
  form.append('key', upload.objectKey);
  form.append('policy', upload.fields.policy);
  form.append('OSSAccessKeyId', upload.fields.ossAccessKeyId);
  form.append('Signature', upload.fields.signature);
  if (upload.fields.xOssMetaSha256) form.append('x-oss-meta-sha256', upload.fields.xOssMetaSha256);
  form.append('file', new Blob([video], {type: 'video/mp4'}), 'video.mp4');
  const ossResponse = await fetch(upload.uploadUrl, {method: 'POST', body: form});
  if (!ossResponse.ok) throw new Error(`OSS upload failed: ${ossResponse.status}`);
}

const media = await api('POST', `/media/uploads/${upload.uploadId}/complete`, {fileSize: video.length, sha256: digest});
const task = await api('POST', '/publish/tasks', {
  connectionIds: [authorized.connectionId], mediaId: media.mediaId, postMode: 'DIRECT_POST',
  privacyLevel: 'PUBLIC_TO_EVERYONE', caption: 'Published by server integration'
}, {'Idempotency-Key': crypto.randomUUID()});
const current = await api('GET', `/publish/tasks/${task.taskId}`);
console.log(current.status);
```

For OSS mode, `POST /media/uploads/{uploadId}/complete` HEAD-validates object length and SHA-256 metadata. Do not create a publish task until it returns `status: READY`.
