# TikTok Open API Python example

This example is for a backend service. It reads client credentials only from server environment variables; do not place them in client-side code or source control.

```python
import base64
import hashlib
import hmac
import json
import os
import time
import uuid
from pathlib import Path

import requests

BASE = "https://tkassetplant.fnn.net.cn/admin-api/tk/open/v1/tiktok"
CLIENT_ID = os.environ["TK_OPEN_API_CLIENT_ID"]
CLIENT_SECRET = os.environ["TK_OPEN_API_CLIENT_SECRET"].encode("utf-8")

def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()

def signature(method: str, target: str, timestamp: str, nonce: str, body: bytes = b"") -> str:
    canonical = "\n".join([method.upper(), target, timestamp, nonce, sha256(body)])
    return base64.b64encode(hmac.new(CLIENT_SECRET, canonical.encode("utf-8"), hashlib.sha256).digest()).decode("ascii")

def api(method: str, path: str, payload=None, headers=None):
    raw = b"" if payload is None else payload if isinstance(payload, bytes) else json.dumps(payload, separators=(",", ":")).encode("utf-8")
    timestamp, nonce = str(int(time.time())), uuid.uuid4().hex
    target = "/admin-api/tk/open/v1/tiktok" + path
    request_headers = {
        "X-TK-Client-Id": CLIENT_ID, "X-TK-Timestamp": timestamp, "X-TK-Nonce": nonce,
        "X-TK-Request-Id": uuid.uuid4().hex, "X-TK-Signature": signature(method, target, timestamp, nonce, raw),
    }
    if payload is not None:
        request_headers["Content-Type"] = "application/octet-stream" if isinstance(payload, bytes) else "application/json"
    request_headers.update(headers or {})
    response = requests.request(method, BASE + path, data=None if payload is None else raw, headers=request_headers, timeout=30)
    result = response.json()
    if not response.ok or result["code"] != 0:
        raise RuntimeError(f"{response.status_code} {result['code']}: {result['msg']}")
    return result["data"]

def wait_for_authorization(auth_session_id: str):
    deadline = time.time() + 15 * 60
    while time.time() < deadline:
        status = api("GET", f"/auth/sessions/{auth_session_id}")
        if status["status"] == "SUCCESS":
            return status
        if status["status"] in ("FAILED", "EXPIRED"):
            raise RuntimeError("authorization is " + status["status"] + ": " + str(status.get("failReason") or ""))
        time.sleep(3)
    raise RuntimeError("authorization polling timed out")
```

Recommended minimum flow: create an `AUTO` session, open `launchUrl` in the user's browser, poll the signed session endpoint, then call `quick-tasks` with a public HTTPS video URL. The hosted page displays browser authorization and the A-generated QR image.

```python
auth = api("POST", "/auth/sessions", {
    "externalAccountId": "your-account-reference", "authMode": "AUTO"
})
print("Open in the user browser:", auth["launchUrl"])

authorized = wait_for_authorization(auth["authSessionId"])

task = api("POST", "/publish/quick-tasks", {
    "externalAccountId": authorized["externalAccountId"],
    "videoUrl": "https://cdn.example.com/videos/video.mp4",
    "fileName": "video.mp4", "contentType": "video/mp4",
    "postMode": "DIRECT_POST", "privacyLevel": "PUBLIC_TO_EVERYONE",
    "caption": "Published by server integration"
}, {"Idempotency-Key": uuid.uuid4().hex})
print(task["taskId"])
```

`launchUrl` and `/launch/status` are public browser endpoints and do not use HMAC. The C server must still sign session creation, signed status queries, connection queries, and publishing calls. `videoUrl` must be a public HTTPS URL; HTTP, loopback, private-network, and cloud-metadata destinations are rejected.

The full upload flow below is for private files or callers that need OSS direct upload or LOCAL chunk upload. Use a user browser to complete a `REDIRECT` authorization. `QR_CODE` is also available: display `qrcodeImageUrl` or `launchUrl` and poll the session while it is `WAITING`.

```python
auth = api("POST", "/auth/sessions", {
    "externalAccountId": "your-account-reference", "authMode": "AUTO", "clientState": "your-state"
})
print("Open in the user browser:", auth["launchUrl"])

authorized = wait_for_authorization(auth["authSessionId"])

video = Path("/absolute/path/video.mp4").read_bytes()
digest = sha256(video)
upload = api("POST", "/media/uploads", {
    "fileName": "video.mp4", "fileSize": len(video), "contentType": "video/mp4", "sha256": digest
})

if upload["uploadMode"] == "LOCAL":
    size = upload["chunkSize"]
    for index in range(upload["totalChunks"]):
        api("PUT", f"/media/uploads/{upload['uploadId']}/chunks/{index}", video[index * size:(index + 1) * size])
else:
    fields = {
        "key": upload["objectKey"], "policy": upload["fields"]["policy"],
        "OSSAccessKeyId": upload["fields"]["ossAccessKeyId"],
        "Signature": upload["fields"]["signature"],
    }
    if upload["fields"].get("xOssMetaSha256"):
        fields["x-oss-meta-sha256"] = upload["fields"]["xOssMetaSha256"]
    oss_response = requests.post(upload["uploadUrl"], data=fields,
                                 files={"file": ("video.mp4", video, "video/mp4")}, timeout=300)
    oss_response.raise_for_status()

media = api("POST", f"/media/uploads/{upload['uploadId']}/complete", {"fileSize": len(video), "sha256": digest})
task = api("POST", "/publish/tasks", {
    "connectionIds": [authorized["connectionId"]], "mediaId": media["mediaId"],
    "postMode": "DIRECT_POST", "privacyLevel": "PUBLIC_TO_EVERYONE", "caption": "Published by server integration"
}, {"Idempotency-Key": uuid.uuid4().hex})
print(api("GET", f"/publish/tasks/{task['taskId']}")["status"])
```

For an OSS upload, completion performs HEAD metadata validation of object length and SHA-256 before returning `READY` media. Reuse an `Idempotency-Key` only for the same publish request.
