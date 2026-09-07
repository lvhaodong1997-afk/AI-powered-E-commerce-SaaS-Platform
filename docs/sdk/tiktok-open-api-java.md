# TikTok Open API Java example

Use this code inside a server-side Java service. The HMAC key must come from server environment variables or a server-side secret manager; do not embed it in a client application or source repository.

```java
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class TikTokOpenApi {
  static final String BASE = "https://tkassetplant.fnn.net.cn/admin-api/tk/open/v1/tiktok";
  static final String CLIENT_ID = requireEnv("TK_OPEN_API_CLIENT_ID");
  static final String CLIENT_SECRET = requireEnv("TK_OPEN_API_CLIENT_SECRET");
  static final HttpClient HTTP = HttpClient.newHttpClient();

  static String requireEnv(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) throw new IllegalStateException("missing server environment variable: " + name);
    return value;
  }
  static String sha256(byte[] data) throws Exception {
    byte[] hash = MessageDigest.getInstance("SHA-256").digest(data);
    StringBuilder result = new StringBuilder();
    for (byte b : hash) result.append(String.format("%02x", b));
    return result.toString();
  }
  static String sign(String method, String target, String timestamp, String nonce, byte[] body) throws Exception {
    String canonical = method.toUpperCase() + "\n" + target + "\n" + timestamp + "\n" + nonce + "\n" + sha256(body);
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(CLIENT_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return Base64.getEncoder().encodeToString(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
  }
  static String api(String method, String path, byte[] body, String contentType, String idempotencyKey) throws Exception {
    byte[] actualBody = body == null ? new byte[0] : body;
    String timestamp = Long.toString(Instant.now().getEpochSecond());
    String nonce = UUID.randomUUID().toString().replace("-", "");
    String target = "/admin-api/tk/open/v1/tiktok" + path;
    HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(BASE + path))
        .header("X-TK-Client-Id", CLIENT_ID).header("X-TK-Timestamp", timestamp)
        .header("X-TK-Nonce", nonce).header("X-TK-Request-Id", UUID.randomUUID().toString())
        .header("X-TK-Signature", sign(method, target, timestamp, nonce, actualBody));
    if (contentType != null) request.header("Content-Type", contentType);
    if (idempotencyKey != null) request.header("Idempotency-Key", idempotencyKey);
    HttpResponse<String> response = HTTP.send(request.method(method,
        body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(actualBody)).build(),
        HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException(response.body());
    return response.body(); // Parse code/data with the JSON library already used by your service.
  }
}
```

Recommended minimum flow: create an `AUTO` session, open `data.launchUrl` in the user's browser, poll the signed session endpoint, then call `quick-tasks` with a public HTTPS video URL. The hosted page includes browser authorization and an A-generated QR image.

```java
byte[] authBody = jsonBytes("{\"externalAccountId\":\"your-account-reference\",\"authMode\":\"AUTO\"}");
String authJson = TikTokOpenApi.api("POST", "/auth/sessions", authBody, "application/json", null);
String launchUrl = jsonText(authJson, "data.launchUrl"); // Open in the user browser.
String authSessionId = jsonText(authJson, "data.authSessionId");

String authorizedJson = null;
long deadline = System.currentTimeMillis() + 15 * 60 * 1000;
while (System.currentTimeMillis() < deadline) {
  authorizedJson = TikTokOpenApi.api("GET", "/auth/sessions/" + authSessionId, null, null, null);
  String status = jsonText(authorizedJson, "data.status");
  if ("SUCCESS".equals(status)) break;
  if ("FAILED".equals(status) || "EXPIRED".equals(status)) {
    throw new IllegalStateException("authorization is " + status);
  }
  Thread.sleep(3000);
}
if (authorizedJson == null || !"SUCCESS".equals(jsonText(authorizedJson, "data.status"))) {
  throw new IllegalStateException("authorization polling timed out");
}

String quickJson = TikTokOpenApi.api("POST", "/publish/quick-tasks",
    jsonBytes("{\"externalAccountId\":\"your-account-reference\",\"videoUrl\":\"https://cdn.example.com/video.mp4\",\"postMode\":\"DIRECT_POST\",\"privacyLevel\":\"PUBLIC_TO_EVERYONE\"}"),
    "application/json", UUID.randomUUID().toString());
String taskId = jsonText(quickJson, "data.taskId");
```

`launchUrl` and `/launch/status` are public browser endpoints and do not use HMAC. The C server must sign session creation, signed status queries, connection queries, and publishing calls. `videoUrl` must be a public HTTPS URL; HTTP, loopback, private-network, and cloud-metadata destinations are rejected.

The full upload workflow below uses placeholders for JSON parsing so it works with Jackson, Gson, or another existing library. Build JSON bytes once and sign precisely those same bytes.

```java
byte[] authBody = jsonBytes("{\"externalAccountId\":\"your-account-reference\",\"authMode\":\"AUTO\"}");
String authJson = TikTokOpenApi.api("POST", "/auth/sessions", authBody, "application/json", null);
String authSessionId = jsonText(authJson, "data.authSessionId");
String launchUrl = jsonText(authJson, "data.launchUrl"); // Open this URL in the user browser.

// Create the session with authMode AUTO. The hosted launchUrl displays the
// browser authorization link and the data.qrcodeImageUrl QR image;
// no QR library is needed.
String sessionJson = null;
long deadline = System.currentTimeMillis() + 15 * 60 * 1000;
while (System.currentTimeMillis() < deadline) {
  sessionJson = TikTokOpenApi.api("GET", "/auth/sessions/" + authSessionId, null, null, null);
  String status = jsonText(sessionJson, "data.status");
  if ("SUCCESS".equals(status)) break;
  if ("FAILED".equals(status) || "EXPIRED".equals(status)) {
    throw new IllegalStateException("authorization is " + status);
  }
  Thread.sleep(3000);
}
if (sessionJson == null || !"SUCCESS".equals(jsonText(sessionJson, "data.status"))) {
  throw new IllegalStateException("authorization polling timed out");
}
String connectionId = jsonText(sessionJson, "data.connectionId");

byte[] video = java.nio.file.Files.readAllBytes(java.nio.file.Path.of("/absolute/path/video.mp4"));
String hash = TikTokOpenApi.sha256(video);
String uploadJson = TikTokOpenApi.api("POST", "/media/uploads",
    jsonBytes("{\"fileName\":\"video.mp4\",\"fileSize\":" + video.length + ",\"contentType\":\"video/mp4\",\"sha256\":\"" + hash + "\"}"),
    "application/json", null);

if ("LOCAL".equals(jsonText(uploadJson, "data.uploadMode"))) {
  // Use returned chunkSize and totalChunks; only the final chunk may be smaller.
  for (int index = 0; index < totalChunks(uploadJson); index++) {
    int start = index * chunkSize(uploadJson);
    int end = Math.min(video.length, start + chunkSize(uploadJson));
    TikTokOpenApi.api("PUT", "/media/uploads/" + uploadId(uploadJson) + "/chunks/" + index,
        java.util.Arrays.copyOfRange(video, start, end), "application/octet-stream", null);
  }
} else {
  // For OSS mode, POST objectKey and returned fields plus the file to data.uploadUrl.
  postOssForm(uploadJson, video, "video.mp4", "video/mp4");
}
String mediaJson = TikTokOpenApi.api("POST", "/media/uploads/" + uploadId(uploadJson) + "/complete",
    jsonBytes("{\"fileSize\":" + video.length + ",\"sha256\":\"" + hash + "\"}"), "application/json", null);
String mediaId = jsonText(mediaJson, "data.mediaId");
String taskJson = TikTokOpenApi.api("POST", "/publish/tasks",
    jsonBytes("{\"connectionIds\":[\"" + connectionId + "\"],\"mediaId\":\"" + mediaId + "\",\"postMode\":\"DIRECT_POST\",\"privacyLevel\":\"PUBLIC_TO_EVERYONE\"}"),
    "application/json", UUID.randomUUID().toString());
String taskId = jsonText(taskJson, "data.taskId");
String currentTask = TikTokOpenApi.api("GET", "/publish/tasks/" + taskId, null, null, null);
```

For an OSS upload, completion HEAD-validates object length and SHA-256 metadata before it returns `READY` media. An `Idempotency-Key` is mandatory for publishing and must be reused only with identical request content.
