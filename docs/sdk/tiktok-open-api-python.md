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
HMAC_KEY = os.environ["TK_OPEN_API_HMAC_KEY"].encode("utf-8")

def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()

def signature(method: str, target: str, timestamp: str, nonce: str, body: bytes = b"") -> str:
    canonical = "\n".join([method.upper(), target, timestamp, nonce, sha256(body)])
    return base64.b64encode(hmac.new(HMAC_KEY, canonical.encode("utf-8"), hashlib.sha256).digest()).decode("ascii")

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
```

Use a user browser to complete a `REDIRECT` authorization. `QR_CODE` is available as an alternative: display `qrcodeUrl` and poll the session while it is `WAITING`.

```python
auth = api("POST", "/auth/sessions", {
    "externalAccountId": "your-account-reference", "authMode": "REDIRECT", "clientState": "your-state"
})
print("Open in the user browser:", auth["authorizeUrl"])

authorized = api("GET", f"/auth/sessions/{auth['authSessionId']}")
if authorized["status"] != "SUCCESS":
    raise RuntimeError("authorization is " + authorized["status"])

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
