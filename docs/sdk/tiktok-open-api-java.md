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

## Photo upload and publish (Java 17, Jackson)

Use the existing `TikTokOpenApi.api` signing helper. The following methods use Jackson `ObjectMapper`/`JsonNode` (add your application's Jackson dependency), and JDK HTTP multipart; no secret is sent to OSS. Persist uploadId before upload and persist the publish key. [Photo contract](../openapi/tiktok-photo-open-api.md) describes conversion and retry semantics.

```java
final class PhotoExample {
  static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();
  static com.fasterxml.jackson.databind.JsonNode call(String method, String path, Object body, String key) throws Exception {
    var root = JSON.readTree(TikTokOpenApi.api(method, path,
        body == null ? null : JSON.writeValueAsBytes(body), body == null ? null : "application/json", key));
    if (root.path("code").asInt(-1) != 0) throw new IllegalStateException(root.path("msg").asText());
    return root.path("data");
  }
  static String uploadPhoto(java.nio.file.Path path, String mime) throws Exception {
    byte[] bytes = java.nio.file.Files.readAllBytes(path);
    String digest = TikTokOpenApi.sha256(bytes);
    var upload = call("POST", "/media/uploads", java.util.Map.of("mediaType", "PHOTO", "fileName", path.getFileName().toString(),
        "fileSize", bytes.length, "contentType", mime, "sha256", digest), null);
    String id = upload.path("uploadId").asText();
    if ("LOCAL".equals(upload.path("uploadMode").asText())) {
      int size = upload.path("chunkSize").asInt();
      for (int i = 0; i < upload.path("totalChunks").asInt(); i++) {
        TikTokOpenApi.api("PUT", "/media/uploads/" + id + "/chunks/" + i,
            java.util.Arrays.copyOfRange(bytes, i * size, Math.min(bytes.length, (i + 1) * size)), "application/octet-stream", null);
      }
    } else {
      String boundary = "photo" + java.util.UUID.randomUUID().toString().replace("-", "");
      var fields = new java.util.LinkedHashMap<String, String>();
      var f = upload.path("fields");
      fields.put("key", upload.path("objectKey").asText()); fields.put("policy", f.path("policy").asText());
      fields.put("OSSAccessKeyId", f.path("ossAccessKeyId").asText()); fields.put("Signature", f.path("signature").asText());
      fields.put("x-oss-object-acl", f.path("xOssObjectAcl").asText()); fields.put("Content-Type", f.path("contentType").asText());
      if (!f.path("xOssMetaSha256").isNull() && !f.path("xOssMetaSha256").asText().isEmpty()) fields.put("x-oss-meta-sha256", f.path("xOssMetaSha256").asText());
      var out = new java.io.ByteArrayOutputStream();
      for (var entry : fields.entrySet()) out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + entry.getKey()
          + "\"\r\n\r\n" + entry.getValue() + "\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
      out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"photo.bin\"\r\nContent-Type: "
          + mime + "\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
      out.write(bytes); out.write(("\r\n--" + boundary + "--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
      var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(upload.path("uploadUrl").asText()))
          .timeout(java.time.Duration.ofSeconds(90)).header("Content-Type", "multipart/form-data; boundary=" + boundary)
          .POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build();
      var response = java.net.http.HttpClient.newHttpClient().send(request, java.net.http.HttpResponse.BodyHandlers.discarding());
      if (response.statusCode() / 100 != 2) throw new IllegalStateException("OSS upload " + response.statusCode());
    }
    call("POST", "/media/uploads/" + id + "/complete", java.util.Map.of("fileSize", bytes.length, "sha256", digest), null);
    for (int i = 0; i < 150; i++) {
      var state = call("GET", "/media/uploads/" + id, null, null);
      if ("READY".equals(state.path("status").asText())) return state.path("mediaId").asText();
      if (java.util.List.of("FAILED", "EXPIRED", "CANCELLED").contains(state.path("status").asText())) throw new IllegalStateException(state.path("failReason").asText());
      Thread.sleep(2000);
    }
    throw new IllegalStateException("Resume querying uploadId " + id);
  }
  static void publish(String connectionId) throws Exception {
    var ids = java.util.List.of(uploadPhoto(java.nio.file.Path.of("/images/a.heic"), "image/heic"),
        uploadPhoto(java.nio.file.Path.of("/images/b.png"), "image/png"));
    String key = java.util.UUID.randomUUID().toString(); // Save once for retries.
    var task = call("POST", "/publish/tasks", java.util.Map.of("mediaType", "PHOTO", "connectionIds", java.util.List.of(connectionId),
        "photoMediaIds", ids, "photoCoverIndex", 0, "title", "Product photos", "caption", "Details", "postMode", "DIRECT_POST",
        "privacyLevel", "SELF_ONLY", "autoAddMusic", true), key);
    String taskId = task.path("taskId").asText();
    System.out.println(call("GET", "/publish/tasks/" + taskId, null, null));
    System.out.println(call("GET", "/publish/tasks/" + taskId + "/details", null, null));
  }
}
```
