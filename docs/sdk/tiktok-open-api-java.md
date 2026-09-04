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
  static final String HMAC_KEY = requireEnv("TK_OPEN_API_HMAC_KEY");
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
    mac.init(new SecretKeySpec(HMAC_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
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

The workflow below uses placeholders for JSON parsing so it works with Jackson, Gson, or another existing library. Build JSON bytes once and sign precisely those same bytes.

```java
byte[] authBody = jsonBytes("{\"externalAccountId\":\"your-account-reference\",\"authMode\":\"REDIRECT\"}");
String authJson = TikTokOpenApi.api("POST", "/auth/sessions", authBody, "application/json", null);
String authSessionId = jsonText(authJson, "data.authSessionId");
String authorizeUrl = jsonText(authJson, "data.authorizeUrl"); // Open only in the user browser.

// After the user completes authorization, query the session. QR_CODE is also supported:
// create with authMode QR_CODE, display data.qrcodeUrl, then poll this endpoint while WAITING.
String sessionJson = TikTokOpenApi.api("GET", "/auth/sessions/" + authSessionId, null, null, null);
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
