# TikTok Open API Node.js example

Run this only in a server process. Read credentials from server environment variables; never expose them to a browser or commit their values.

```js
import crypto from 'node:crypto';
import fs from 'node:fs/promises';

const base = 'https://tkassetplant.fnn.net.cn/admin-api/tk/open/v1/tiktok';
const clientId = process.env.TK_OPEN_API_CLIENT_ID;
const clientSecret = process.env.TK_OPEN_API_CLIENT_SECRET;
if (!clientId || !clientSecret) throw new Error('missing server-side Open API environment variables');

function sha256(bytes) {
  return crypto.createHash('sha256').update(bytes).digest('hex');
}
function sign(method, target, timestamp, nonce, body = Buffer.alloc(0)) {
  const canonical = `${method.toUpperCase()}\n${target}\n${timestamp}\n${nonce}\n${sha256(body)}`;
  return crypto.createHmac('sha256', clientSecret).update(canonical).digest('base64');
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

async function waitForAuthorization(authSessionId) {
  const deadline = Date.now() + 15 * 60 * 1000;
  while (Date.now() < deadline) {
    const status = await api('GET', `/auth/sessions/${encodeURIComponent(authSessionId)}`);
    if (status.status === 'SUCCESS') return status;
    if (status.status === 'FAILED' || status.status === 'EXPIRED') {
      throw new Error(`authorization is ${status.status}: ${status.failReason || ''}`);
    }
    await new Promise(resolve => setTimeout(resolve, 3000));
  }
  throw new Error('authorization polling timed out');
}
```

Recommended minimum flow: create an `AUTO` session, open the returned `launchUrl` in the user's browser, then call `quick-tasks` with a public HTTPS video URL. The hosted page shows both browser authorization and the A-generated QR image, so the C-side browser does not need QR generation or TikTok OAuth callback code.

```js
const auth = await api('POST', '/auth/sessions', {
  externalAccountId: 'your-account-reference', authMode: 'AUTO'
});
console.log(`Open in the user browser: ${auth.launchUrl}`);

const authorized = await waitForAuthorization(auth.authSessionId);

const task = await api('POST', '/publish/quick-tasks', {
  externalAccountId: authorized.externalAccountId,
  videoUrl: 'https://cdn.example.com/videos/video.mp4',
  fileName: 'video.mp4', contentType: 'video/mp4',
  postMode: 'DIRECT_POST', privacyLevel: 'PUBLIC_TO_EVERYONE',
  caption: 'Published by server integration'
}, {'Idempotency-Key': crypto.randomUUID()});
console.log(task.taskId);
```

`launchUrl` and its `/launch/status` polling endpoint are public browser endpoints and do not use HMAC. The C server must still sign session creation, signed status queries, connection queries, and publishing calls. `videoUrl` must be a public HTTPS URL; HTTP, loopback, private-network, and cloud-metadata destinations are rejected.

The following is the full upload flow for private files or callers that need OSS direct upload or LOCAL chunk upload. A redirect authorization must be completed by a user in a browser. `QR_CODE` is also implemented: use it as `authMode`, display `qrcodeImageUrl` or `launchUrl`, then poll the same session endpoint.

```js
const auth = await api('POST', '/auth/sessions', {
  externalAccountId: 'your-account-reference', authMode: 'AUTO', clientState: 'your-state'
});
console.log(`Open this URL in the user browser: ${auth.launchUrl}`);

const authorized = await waitForAuthorization(auth.authSessionId);

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

## Photo upload and publish (Node 20+)

Reuse the `api`, `sha256`, `fs`, and `crypto` helpers above. The result of PHOTO complete is asynchronous. See [photo contract](../openapi/tiktok-photo-open-api.md) for limits, retry and callbacks. Preserve each uploadId and the publish idempotency key in your database.

```js
async function uploadPhoto(path, fileName, contentType) {
  const bytes = await fs.readFile(path);
  const digest = sha256(bytes);
  const upload = await api('POST', '/media/uploads', {
    mediaType: 'PHOTO', fileName, fileSize: bytes.length, contentType, sha256: digest
  });
  if (upload.uploadMode === 'LOCAL') {
    for (let i = 0; i < upload.totalChunks; i++) {
      await api('PUT', `/media/uploads/${upload.uploadId}/chunks/${i}`,
        bytes.subarray(i * upload.chunkSize, (i + 1) * upload.chunkSize));
    }
  } else {
    const form = new FormData();
    for (const [key, value] of Object.entries({
      key: upload.objectKey, policy: upload.fields.policy,
      OSSAccessKeyId: upload.fields.ossAccessKeyId, Signature: upload.fields.signature,
      'x-oss-object-acl': upload.fields.xOssObjectAcl,
      'Content-Type': upload.fields.contentType,
      'x-oss-meta-sha256': upload.fields.xOssMetaSha256
    })) if (value) form.append(key, value);
    form.append('file', new Blob([bytes], {type: contentType}), fileName);
    const response = await fetch(upload.uploadUrl, {method: 'POST', body: form});
    if (!response.ok) throw new Error(`OSS photo upload: ${response.status}`);
  }
  await api('POST', `/media/uploads/${upload.uploadId}/complete`, {fileSize: bytes.length, sha256: digest});
  for (let i = 0; i < 150; i++) {
    const state = await api('GET', `/media/uploads/${upload.uploadId}`);
    if (state.status === 'READY') return state.mediaId;
    if (['FAILED', 'EXPIRED', 'CANCELLED'].includes(state.status)) throw new Error(state.failReason || state.status);
    await new Promise(resolve => setTimeout(resolve, 2000));
  }
  throw new Error(`Conversion polling timed out; resume uploadId ${upload.uploadId}`);
}
const photoMediaIds = [await uploadPhoto('/images/a.heic', 'a.heic', 'image/heic'),
  await uploadPhoto('/images/b.png', 'b.png', 'image/png')];
const photoRequestKey = crypto.randomUUID(); // Persist once and reuse only with identical request bytes.
const photoTask = await api('POST', '/publish/tasks', {
  mediaType: 'PHOTO', connectionIds: [authorized.connectionId], photoMediaIds,
  photoCoverIndex: 0, title: 'Product photos', caption: 'Details', postMode: 'DIRECT_POST',
  privacyLevel: 'SELF_ONLY', autoAddMusic: true
}, {'Idempotency-Key': photoRequestKey});
console.log(await api('GET', `/publish/tasks/${photoTask.taskId}`));
console.log(await api('GET', `/publish/tasks/${photoTask.taskId}/details`));
```
