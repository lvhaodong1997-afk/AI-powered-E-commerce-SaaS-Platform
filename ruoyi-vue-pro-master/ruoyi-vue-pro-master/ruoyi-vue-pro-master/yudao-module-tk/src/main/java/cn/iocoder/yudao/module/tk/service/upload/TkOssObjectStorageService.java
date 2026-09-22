package cn.iocoder.yudao.module.tk.service.upload;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkUploadSessionDO;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.*;

@Service
public class TkOssObjectStorageService implements TkOssObjectStorageClient {

    private static final DateTimeFormatter OSS_GMT_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).withZone(ZoneOffset.UTC);
    private static final int OSS_HTTP_TIMEOUT_MILLIS = 30_000;
    private static final int OSS_DELETE_MAX_ATTEMPTS = 2;
    private static final ScheduledExecutorService TRANSFER_TIMEOUT = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "tk-oss-transfer-timeout"); thread.setDaemon(true); return thread;
    });

    @Resource
    private TkGenerationProperties generationProperties;

    public boolean isConfigured() {
        TkGenerationProperties.Oss oss = getOss();
        return oss != null && !StrUtil.hasBlank(oss.getBucket(), oss.getEndpoint(), oss.getAccessKeyId(),
                oss.getAccessKeySecret());
    }

    public void deleteObject(String objectKey) {
        if (StrUtil.isBlank(objectKey)) {
            return;
        }
        TkGenerationProperties.Oss oss = getOss();
        if (!isConfigured()) {
            throw new IllegalStateException("OSS 删除配置不完整");
        }
        for (int attempt = 1; attempt <= OSS_DELETE_MAX_ATTEMPTS; attempt++) {
            String resource = "/" + oss.getBucket() + "/" + objectKey;
            String date = OSS_GMT_DATE_FORMATTER.format(Instant.now());
            String signature = TkOssRestSigner.sign("DELETE", "", "", date, resource, oss.getAccessKeySecret());
            HttpURLConnection connection = null;
            try {
                connection = openDeleteConnection(uploadUrl(oss) + "/" + encodePath(objectKey));
                connection.setRequestProperty("Date", date);
                connection.setRequestProperty("Authorization", "OSS " + oss.getAccessKeyId() + ":" + signature);
                int status = connection.getResponseCode();
                if (status == 204 || status == 404) {
                    return;
                }
                String body = StrUtil.trim(readResponseBody(connection, status));
                if (attempt == OSS_DELETE_MAX_ATTEMPTS || !isRetryableDeleteStatus(status)) {
                    throw new IllegalStateException(StrUtil.format("删除 OSS 文件失败，HTTP {}：{}，响应：{}",
                            status, objectKey, StrUtil.sub(body, 0, 500)));
                }
            } catch (IOException ex) {
                if (attempt == OSS_DELETE_MAX_ATTEMPTS) {
                    throw new IllegalStateException("删除 OSS 文件失败，网络异常：" + objectKey, ex);
                }
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
    }

    static HttpURLConnection openDeleteConnection(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(OSS_HTTP_TIMEOUT_MILLIS);
        connection.setRequestMethod("DELETE");
        return connection;
    }

    private String readResponseBody(HttpURLConnection connection, int status) {
        InputStream input = null;
        try {
            input = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (input == null) {
                return "";
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[512];
            int total = 0;
            int count;
            while (total < 500 && (count = input.read(buffer, 0, Math.min(buffer.length, 500 - total))) != -1) {
                output.write(buffer, 0, count);
                total += count;
            }
            return output.toString(StandardCharsets.UTF_8.name());
        } catch (IOException ignored) {
            return "";
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException ignored) {
                    // Ignore response cleanup failures after the status is known.
                }
            }
        }
    }

    private boolean isRetryableDeleteStatus(int status) {
        return status == 403 || status == 408 || status == 429 || status >= 500;
    }

    public ObjectMetadata headObject(String objectKey) {
        if (StrUtil.isBlank(objectKey)) {
            throw new IllegalArgumentException("OSS object key is required");
        }
        TkGenerationProperties.Oss oss = getOss();
        if (!isConfigured()) {
            throw new IllegalStateException("OSS metadata configuration is incomplete");
        }
        String resource = "/" + oss.getBucket() + "/" + objectKey;
        String date = OSS_GMT_DATE_FORMATTER.format(Instant.now());
        String signature = TkOssRestSigner.sign("HEAD", "", "", date, resource, oss.getAccessKeySecret());
        try (HttpResponse response = HttpRequest.head(uploadUrl(oss) + "/" + encodePath(objectKey))
                .header("Date", date)
                .header("Authorization", "OSS " + oss.getAccessKeyId() + ":" + signature)
                .timeout(OSS_HTTP_TIMEOUT_MILLIS)
                .execute()) {
            if (response.getStatus() != 200) {
                throw new IllegalStateException(StrUtil.format("读取 OSS 文件元数据失败，HTTP {}：{}",
                        response.getStatus(), objectKey));
            }
            try {
                return new ObjectMetadata(Long.parseLong(response.header("Content-Length")),
                        response.header("x-oss-meta-sha256"), response.header("ETag"), response.header("x-oss-version-id"));
            } catch (NumberFormatException ex) {
                throw new IllegalStateException("OSS 文件大小元数据无效：" + objectKey, ex);
            }
        }
    }

    /**
     * Rebuilds a GET URL for an OSS object stored as a public-base URL.
     * Historical records contain the unsigned URL, so callers resolve it at read time.
     */
    public String resolveReadUrl(String url) {
        if (StrUtil.isBlank(url) || !isConfigured()) {
            return url;
        }
        String objectKey = toObjectKey(url);
        return StrUtil.isBlank(objectKey) ? url : toReadUrl(objectKey);
    }

    /** Resolve only URLs belonging to configured OSS; never fetch a URL supplied by the browser. */
    public String requireOwnedObjectKey(String url, Long tenantId, Long companyId) {
        String key = toObjectKey(url);
        validateOwnedKey(key, tenantId, companyId);
        return key;
    }

    public void validateOwnedKey(String key, Long tenantId, Long companyId) {
        TkGenerationProperties.Oss oss = getOss();
        String prefix = StrUtil.removeSuffix(StrUtil.blankToDefault(oss == null ? null : oss.getUploadPathPrefix(), "tk"), "/");
        if (tenantId == null || companyId == null || key == null
                || !key.startsWith(prefix + "/" + tenantId + "/" + companyId + "/")
                || key.contains("..") || key.contains("\\") || key.contains("?") || key.contains("#") || key.contains(":"))
            throw new IllegalArgumentException("媒体不属于当前租户的 OSS 对象");
    }

    public static void requireIdentity(ObjectMetadata expected, ObjectMetadata actual) {
        if (expected == null || actual == null || StrUtil.isBlank(expected.getEtag())
                || !Objects.equals(expected.getEtag(), actual.getEtag())
                || !Objects.equals(expected.getVersionId(), actual.getVersionId())
                || expected.getContentLength() != actual.getContentLength())
            throw new IllegalArgumentException("OSS 文件已改变，请重新上传");
    }

    /** Signed origin request, streamed with both a byte cap and a wall-clock deadline. */
    public void downloadToFile(String objectKey, Path destination, ObjectMetadata expected, long maxBytes) {
        if (expected == null || StrUtil.isBlank(expected.getEtag()) || expected.getContentLength() <= 0
                || expected.getContentLength() > maxBytes) throw new IllegalArgumentException("OSS 文件信息无效");
        HttpURLConnection connection = null;
        ScheduledFuture<?> deadline = null;
        boolean success = false;
        try {
            connection = openTransfer("GET", objectKey, "");
            connection.setRequestProperty("If-Match", expected.getEtag());
            final HttpURLConnection active = connection;
            deadline = TRANSFER_TIMEOUT.schedule(active::disconnect, 300, TimeUnit.SECONDS);
            if (connection.getResponseCode() != 200) throw new IllegalStateException("读取 OSS 视频失败");
            requireIdentity(expected, new ObjectMetadata(connection.getContentLengthLong(), null,
                    connection.getHeaderField("ETag"), connection.getHeaderField("x-oss-version-id")));
            try (InputStream input = connection.getInputStream(); OutputStream output = Files.newOutputStream(destination)) {
                long copied = copyBounded(input, output, maxBytes);
                if (copied != expected.getContentLength()) throw new IllegalArgumentException("OSS 视频大小已改变");
            }
            success = true;
        } catch (IOException ex) { throw new IllegalStateException("读取 OSS 视频失败或超时"); }
        finally {
            if (deadline != null) deadline.cancel(false);
            if (connection != null) connection.disconnect();
            if (!success) try { Files.deleteIfExists(destination); } catch (IOException ignored) { }
        }
    }

    public ObjectMetadata uploadFile(String objectKey, Path source, long maxBytes) {
        HttpURLConnection connection = null;
        ScheduledFuture<?> deadline = null;
        try {
            long size = Files.size(source);
            if (size <= 0 || size > maxBytes) throw new IllegalArgumentException("视频文件超过大小限制");
            connection = openTransfer("PUT", objectKey, "video/mp4");
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(size);
            final HttpURLConnection active = connection;
            deadline = TRANSFER_TIMEOUT.schedule(active::disconnect, 300, TimeUnit.SECONDS);
            try (InputStream input = Files.newInputStream(source); OutputStream output = connection.getOutputStream()) {
                if (copyBounded(input, output, maxBytes) != size) throw new IllegalArgumentException("视频文件大小已改变");
            }
            if (connection.getResponseCode() != 200) throw new IllegalStateException("写入 OSS 视频失败");
            String etag = connection.getHeaderField("ETag");
            ObjectMetadata actual = headObject(objectKey);
            requireIdentity(new ObjectMetadata(size, null, etag, connection.getHeaderField("x-oss-version-id")), actual);
            return actual;
        } catch (IOException ex) { throw new IllegalStateException("写入 OSS 视频失败或超时"); }
        finally {
            if (deadline != null) deadline.cancel(false);
            if (connection != null) connection.disconnect();
        }
    }

    private HttpURLConnection openTransfer(String method, String objectKey, String contentType) throws IOException {
        if (!isConfigured() || StrUtil.isBlank(objectKey) || objectKey.contains(":") || objectKey.contains("..")
                || objectKey.startsWith("/") || objectKey.contains("\\")) throw new IllegalArgumentException("OSS 对象无效");
        TkGenerationProperties.Oss oss = getOss();
        HttpURLConnection connection = (HttpURLConnection) URI.create(uploadUrl(oss) + "/" + encodePath(objectKey)).toURL().openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(15000); connection.setReadTimeout(30000);
        connection.setRequestMethod(method);
        String date = OSS_GMT_DATE_FORMATTER.format(Instant.now());
        String signature = TkOssRestSigner.sign(method, "", contentType, date, "/" + oss.getBucket() + "/" + objectKey, oss.getAccessKeySecret());
        connection.setRequestProperty("Date", date);
        connection.setRequestProperty("Authorization", "OSS " + oss.getAccessKeyId() + ":" + signature);
        if (!contentType.isEmpty()) connection.setRequestProperty("Content-Type", contentType);
        return connection;
    }

    public static long copyBounded(InputStream input, OutputStream output, long limit) throws IOException {
        byte[] buffer = new byte[65536];
        long total = 0; int count;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(300);
        while ((count = input.read(buffer)) != -1) {
            total += count;
            if (total > limit) throw new IllegalArgumentException("视频文件超过大小限制");
            if (Thread.currentThread().isInterrupted() || System.nanoTime() > deadline) throw new IOException("transfer interrupted");
            output.write(buffer, 0, count);
        }
        return total;
    }

    public String browserUploadUrl() {
        TkGenerationProperties.Oss oss = getOss();
        if (oss == null || !isConfigured()) {
            throw new IllegalStateException("OSS 上传配置不完整");
        }
        return uploadUrl(oss);
    }

    public String publicUrlForObjectKey(String objectKey) {
        if (StrUtil.isBlank(objectKey) || !isConfigured()) {
            throw new IllegalArgumentException("OSS 对象不能为空");
        }
        return toReadUrl(objectKey);
    }

    public String buildTiktokObjectKey(TkUploadSessionDO session) {
        TkGenerationProperties.Oss oss = getOss();
        String prefix = StrUtil.removeSuffix(StrUtil.blankToDefault(oss == null ? null : oss.getUploadPathPrefix(), "tk"), "/");
        String extension = FileUtil.extName(StrUtil.blankToDefault(session.getFileName(), "video.mp4"))
                .toLowerCase(Locale.ROOT);
        return prefix + "/" + session.getTenantId() + "/" + session.getCompanyId()
                + "/tiktok-publish-media/" + session.getUploadId() + "." + extension;
    }

    public String buildSocialObjectKey(TkUploadSessionDO session) {
        TkGenerationProperties.Oss oss = getOss();
        String prefix = StrUtil.removeSuffix(StrUtil.blankToDefault(oss == null ? null : oss.getUploadPathPrefix(), "tk"), "/");
        return prefix + "/" + session.getTenantId() + "/" + session.getCompanyId()
                + "/social-media/" + session.getUploadId() + ".mp4";
    }

    private TkGenerationProperties.Oss getOss() {
        return generationProperties == null || generationProperties.getUpload() == null
                ? null : generationProperties.getUpload().getOss();
    }

    private String uploadUrl(TkGenerationProperties.Oss oss) {
        String endpoint = StrUtil.removePrefix(StrUtil.removePrefix(oss.getEndpoint(), "https://"), "http://");
        if (endpoint.startsWith(oss.getBucket() + ".")) {
            return "https://" + endpoint;
        }
        return "https://" + oss.getBucket() + "." + endpoint;
    }

    private String encodePath(String objectKey) {
        return Arrays.stream(objectKey.split("/"))
                .map(this::encodeQuery)
                .reduce((left, right) -> left + "/" + right)
                .orElse("");
    }

    private String toReadUrl(String objectKey) {
        TkGenerationProperties.Oss oss = getOss();
        String publicUrl = StrUtil.removeSuffix(oss.getPublicBaseUrl(), "/") + "/" + encodePath(objectKey);
        Integer expireSeconds = oss.getReadUrlExpireSeconds();
        if (expireSeconds == null || expireSeconds <= 0) {
            return publicUrl;
        }
        long expires = Instant.now().getEpochSecond() + expireSeconds;
        String resource = "/" + oss.getBucket() + "/" + objectKey;
        String signature = TkOssRestSigner.sign("GET", "", "", String.valueOf(expires), resource,
                oss.getAccessKeySecret());
        return publicUrl + "?OSSAccessKeyId=" + encodeQuery(oss.getAccessKeyId())
                + "&Expires=" + expires
                + "&Signature=" + encodeQuery(signature);
    }

    private String toObjectKey(String url) {
        TkGenerationProperties.Oss oss = getOss();
        if (oss == null || StrUtil.isBlank(oss.getPublicBaseUrl())) {
            return null;
        }
        String publicBaseUrl = StrUtil.removeSuffix(oss.getPublicBaseUrl(), "/");
        String normalizedUrl = StrUtil.subBefore(url, "?", false);
        if (!StrUtil.startWithIgnoreCase(normalizedUrl, publicBaseUrl + "/")) {
            return null;
        }
        try {
            URI uri = URI.create(normalizedUrl);
            String objectPath = uri.getRawPath();
            String basePath = URI.create(publicBaseUrl).getRawPath();
            if (StrUtil.isNotBlank(basePath) && !"/".equals(basePath)) {
                objectPath = StrUtil.removePrefix(objectPath, basePath);
            }
            objectPath = StrUtil.removePrefix(objectPath, "/");
            return StrUtil.isBlank(objectPath) ? null : URLDecoder.decode(objectPath, StandardCharsets.UTF_8.name());
        } catch (Exception ignored) {
            return null;
        }
    }

    private String encodeQuery(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (Exception ex) {
            throw new IllegalStateException("OSS URL 编码失败", ex);
        }
    }

}
