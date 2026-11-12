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
    private TkGenerationProperties.Oss fixedOss;

    public TkOssObjectStorageService() { }
    private TkOssObjectStorageService(TkGenerationProperties properties, TkGenerationProperties.Oss oss) {
        this.generationProperties = properties; this.fixedOss = oss;
    }
    @javax.annotation.PostConstruct
    public void validateEnabledPublishConfiguration() { if (isPublishEnabled()) publishConfig(); }
    public boolean isPublishEnabled() {
        return generationProperties != null && Boolean.TRUE.equals(generationProperties.getUpload().getPublishOss().getEnabled());
    }
    public TkGenerationProperties.Oss publishConfig() {
        TkGenerationProperties.Oss p = generationProperties.getUpload().getPublishOss();
        if (!Boolean.TRUE.equals(p.getEnabled())) return getOss();
        validatePublishConfig(p, generationProperties.getUpload().getOss());
        return p;
    }
    public static void validatePublishConfig(TkGenerationProperties.Oss p, TkGenerationProperties.Oss source) {
        if (StrUtil.hasBlank(p.getBucket(), p.getEndpoint(), p.getRegion(), p.getPublicBaseUrl(),
                p.getAccessKeyId(), p.getAccessKeySecret()) || source == null || StrUtil.isBlank(source.getRegion())
                || !p.getRegion().equals(source.getRegion()) || p.getBucket().equals(source.getBucket())
                || !"https://tkpublish.fnn.net.cn".equals(StrUtil.removeSuffix(p.getPublicBaseUrl(), "/"))
                || p.getReadUrlExpireSeconds() == null || p.getReadUrlExpireSeconds() != 0
                || !p.getBucket().matches("[a-z0-9][a-z0-9-]{1,61}[a-z0-9]")
                || !p.getEndpoint().matches("(?:https://)?(?:[a-z0-9-]+\\.)?oss-[a-z0-9-]+(?:-internal)?\\.aliyuncs\\.com"))
            throw new IllegalStateException("独立发布 OSS 配置不完整：须独立桶、同地域、tkpublish HTTPS 域名及无签名读取");
    }
    public TkOssObjectStorageService publishStorage() {
        return isPublishEnabled() ? new TkOssObjectStorageService(generationProperties, publishConfig()) : this;
    }
    /** Select using a persisted URL; never fall back to the current write destination. */
    public TkOssObjectStorageService storageForUrl(String url) {
        if (generationProperties == null || generationProperties.getUpload() == null)
            throw new IllegalArgumentException("文件不是受信 OSS 来源");
        TkGenerationProperties.Oss material = generationProperties.getUpload().getOss();
        TkGenerationProperties.Oss publish = generationProperties.getUpload().getPublishOss();
        for (TkGenerationProperties.Oss candidate : new TkGenerationProperties.Oss[]{material, publish}) {
            TkOssObjectStorageService storage = new TkOssObjectStorageService(generationProperties, candidate);
            if (StrUtil.isNotBlank(storage.toObjectKey(url))) return storage;
        }
        throw new IllegalArgumentException("文件不是受信 OSS 来源");
    }
    public String objectKeyForUrl(String url) {
        String key = toObjectKey(url);
        if (StrUtil.isBlank(key) || key.startsWith("/") || key.contains("..") || key.contains("\\")
                || key.contains("%") || key.contains("?") || key.contains("#") || key.contains(":"))
            throw new IllegalArgumentException("OSS 对象路径无效");
        return key;
    }
    public String bucket() { return getOss().getBucket(); }
    public String unsignedUrl(String key) { return StrUtil.removeSuffix(getOss().getPublicBaseUrl(), "/") + "/" + encodePath(key); }
    public void deleteByUrl(String url) { TkOssObjectStorageService owner = storageForUrl(url); owner.deleteObject(owner.objectKeyForUrl(url)); }
    public TkOssObjectStorageService storageForSession(TkUploadSessionDO session) {
        return StrUtil.isBlank(session.getStorageUrl()) ? new TkOssObjectStorageService(generationProperties, generationProperties.getUpload().getOss()) : storageForUrl(session.getStorageUrl());
    }

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
                        response.header("x-oss-meta-sha256"), response.header("ETag"), response.header("x-oss-version-id"), response.header("Content-Type"));
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
        if (fixedOss == null) {
            try { return storageForUrl(url).resolveReadUrl(url); } catch (IllegalArgumentException ignored) { return url; }
        }
        String objectKey = toObjectKey(url);
        return StrUtil.isBlank(objectKey) ? url : toReadUrl(objectKey);
    }

    /** Called only after the caller has checked the persisted object's client ownership. */
    public String createPullReadUrl(String objectKey, String pullBaseUrl, int expireSeconds) {
        if (!isConfigured()) throw new IllegalStateException("OSS pull signing is unavailable");
        URI base = URI.create(pullBaseUrl);
        if (!"https".equalsIgnoreCase(base.getScheme()) || base.getHost() == null
                || base.getRawUserInfo() != null || base.getPort() != -1 || base.getRawQuery() != null
                || base.getRawFragment() != null || !(base.getPath().isEmpty() || "/".equals(base.getPath()))
                || StrUtil.isBlank(objectKey) || objectKey.startsWith("/") || objectKey.contains("..")
                || objectKey.contains("\\") || objectKey.contains("%") || objectKey.contains("?") || objectKey.contains("#")
                || expireSeconds < 3600 || expireSeconds > 604800)
            throw new IllegalArgumentException("Invalid OSS pull signing request");
        TkGenerationProperties.Oss oss = getOss();
        long expires = Instant.now().getEpochSecond() + expireSeconds;
        String signature = TkOssRestSigner.sign("GET", "", "", String.valueOf(expires),
                "/" + oss.getBucket() + "/" + objectKey, oss.getAccessKeySecret());
        return "https://" + base.getHost() + "/" + encodePath(objectKey)
                + "?OSSAccessKeyId=" + encodeQuery(oss.getAccessKeyId())
                + "&Expires=" + expires + "&Signature=" + encodeQuery(signature);
    }

    /** Returns whether the URL belongs to the currently configured OSS public base URL. */
    public boolean isOwnedObjectUrl(String url) {
        if (fixedOss != null) return StrUtil.isNotBlank(url) && StrUtil.isNotBlank(toObjectKey(url));
        try { storageForUrl(url).objectKeyForUrl(url); return true; } catch (IllegalArgumentException ex) { return false; }
    }

    /** Resolve only URLs belonging to configured OSS; never fetch a URL supplied by the browser. */
    public String requireOwnedObjectKey(String url, Long tenantId, Long companyId) {
        TkOssObjectStorageService owner = fixedOss == null ? storageForUrl(url) : this;
        String key = owner.objectKeyForUrl(url);
        owner.validateOwnedKey(key, tenantId, companyId);
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
        return uploadFile(objectKey, source, maxBytes, "video/mp4");
    }

    public ObjectMetadata uploadFile(String objectKey, Path source, long maxBytes, String contentType) {
        if (!java.util.Arrays.asList("video/mp4", "video/quicktime", "video/mov", "video/webm", "image/jpeg").contains(contentType)) {
            throw new IllegalArgumentException("不支持的 OSS 媒体类型");
        }
        HttpURLConnection connection = null;
        ScheduledFuture<?> deadline = null;
        try {
            long size = Files.size(source);
            if (size <= 0 || size > maxBytes) throw new IllegalArgumentException("视频文件超过大小限制");
            connection = openTransfer("PUT", objectKey, contentType);
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

    /** Conditional server-side OSS copy. Source credentials are never put in a URL. */
    public ObjectMetadata copyFrom(TkOssObjectStorageService source, String sourceKey, String targetKey, ObjectMetadata identity) {
        if (identity == null || StrUtil.isBlank(identity.getEtag()) || identity.getContentLength() <= 0
                || identity.getContentLength() > 4_000_000_000L) throw new IllegalArgumentException("发布源文件信息无效");
        String copySource = "/" + source.bucket() + "/" + encodePath(sourceKey);
        if (StrUtil.isNotBlank(identity.getVersionId())) copySource += "?versionId=" + encodeQuery(identity.getVersionId());
        java.util.Map<String, String> headers = new java.util.TreeMap<>();
        headers.put("x-oss-copy-source", copySource);
        headers.put("x-oss-copy-source-if-match", identity.getEtag());
        if (identity.getContentLength() <= 128L * 1024 * 1024) {
            String xml = copyRequest("PUT", targetKey, "", headers, "");
            if (!xml.contains("<CopyObjectResult")) throw new IllegalStateException("OSS 发布副本复制失败");
        } else {
            String mime = identity.getContentType();
            if (StrUtil.isBlank(mime) || mime.contains("\r") || mime.contains("\n"))
                throw new IllegalArgumentException("OSS 源文件缺少有效 Content-Type");
            String uploadId = xmlValue(copyRequest("POST", targetKey, "uploads", java.util.Collections.singletonMap("Content-Type", mime), ""), "UploadId");
            try {
                StringBuilder complete = new StringBuilder("<CompleteMultipartUpload>");
                long partSize = 64L * 1024 * 1024;
                for (long start = 0, part = 1; start < identity.getContentLength(); start += partSize, part++) {
                    java.util.Map<String, String> partHeaders = new java.util.TreeMap<>(headers);
                    partHeaders.put("x-oss-copy-source-range", "bytes=" + start + "-" + Math.min(start + partSize - 1, identity.getContentLength() - 1));
                    String etag = xmlValue(copyRequest("PUT", targetKey, "partNumber=" + part + "&uploadId=" + encodeQuery(uploadId), partHeaders, ""), "ETag");
                    complete.append("<Part><PartNumber>").append(part).append("</PartNumber><ETag>")
                            .append(etag.replace("&", "&amp;").replace("<", "&lt;").replace("\"", "&quot;"))
                            .append("</ETag></Part>");
                }
                complete.append("</CompleteMultipartUpload>");
                String result = copyRequest("POST", targetKey, "uploadId=" + encodeQuery(uploadId), java.util.Collections.emptyMap(), complete.toString());
                if (!result.contains("<CompleteMultipartUploadResult")) throw new IllegalStateException("OSS 分片副本合并失败");
            } catch (RuntimeException failure) {
                try { copyRequest("DELETE", targetKey, "uploadId=" + encodeQuery(uploadId), java.util.Collections.emptyMap(), ""); }
                catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
                throw failure;
            }
        }
        requireIdentity(identity, source.headObject(sourceKey));
        ObjectMetadata result = headObject(targetKey);
        if (result.getContentLength() != identity.getContentLength()) throw new IllegalStateException("OSS 发布副本大小校验失败");
        return result;
    }

    String copyRequest(String method, String key, String query, java.util.Map<String, String> headers, String body) {
        TkGenerationProperties.Oss oss = getOss();
        if (!isConfigured()) throw new IllegalStateException("发布 OSS 配置不完整");
        String date = OSS_GMT_DATE_FORMATTER.format(Instant.now());
        StringBuilder canonical = new StringBuilder();
        new java.util.TreeMap<>(headers).forEach((name, value) -> {
            if (name.startsWith("x-oss-")) canonical.append(name).append(":").append(value).append("\n");
        });
        canonical.append("/").append(oss.getBucket()).append("/").append(key);
        if (!query.isEmpty()) canonical.append("?").append(query);
        String contentType = body.isEmpty() ? headers.getOrDefault("Content-Type", "") : "application/xml";
        String signature = TkOssRestSigner.sign(method, "", contentType, date, canonical.toString(), oss.getAccessKeySecret());
        HttpURLConnection connection = null; ScheduledFuture<?> deadline = null;
        try {
            connection = openCopyConnection(uploadUrl(oss) + "/" + encodePath(key) + (query.isEmpty() ? "" : "?" + query));
            connection.setInstanceFollowRedirects(false); connection.setConnectTimeout(15000); connection.setReadTimeout(60000);
            connection.setRequestMethod(method); connection.setRequestProperty("Date", date);
            connection.setRequestProperty("Authorization", "OSS " + oss.getAccessKeyId() + ":" + signature);
            for (java.util.Map.Entry<String,String> header : headers.entrySet()) connection.setRequestProperty(header.getKey(), header.getValue());
            if (!contentType.isEmpty()) connection.setRequestProperty("Content-Type", contentType);
            final HttpURLConnection active = connection;
            deadline = TRANSFER_TIMEOUT.schedule(active::disconnect, 90, TimeUnit.SECONDS);
            if (!body.isEmpty()) {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8); connection.setDoOutput(true); connection.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream output = connection.getOutputStream()) { output.write(bytes); }
            }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new IllegalStateException("OSS 发布副本准备失败，HTTP " + status);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (InputStream input = connection.getInputStream()) {
                byte[] buffer = new byte[4096]; int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > 65536) throw new IllegalStateException("OSS 副本响应过大");
                    output.write(buffer, 0, count);
                }
            }
            String xml = new String(output.toByteArray(), StandardCharsets.UTF_8);
            if (xml.contains("<Error>")) throw new IllegalStateException("OSS 发布副本准备失败");
            return xml;
        } catch (IOException ex) { throw new IllegalStateException("OSS 发布副本请求失败或超时", ex); }
        finally { if (deadline != null) deadline.cancel(false); if (connection != null) connection.disconnect(); }
    }
    HttpURLConnection openCopyConnection(String url) throws IOException {
        return (HttpURLConnection) URI.create(url).toURL().openConnection();
    }
    private static String xmlValue(String xml, String tag) {
        try {
            javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            org.w3c.dom.Node node = factory.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
                    .getElementsByTagName(tag).item(0);
            if (node == null || StrUtil.isBlank(node.getTextContent())) throw new IllegalStateException("OSS 副本响应缺少 " + tag);
            return node.getTextContent();
        } catch (Exception ex) { throw new IllegalStateException("OSS 副本响应无效", ex); }
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
        return fixedOss != null ? fixedOss : generationProperties == null || generationProperties.getUpload() == null
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
        if (oss == null || StrUtil.isBlank(url)) {
            return null;
        }
        String objectKey = toObjectKey(url, oss.getPublicBaseUrl());
        if (StrUtil.isNotBlank(objectKey)) {
            return objectKey;
        }
        if (StrUtil.hasBlank(oss.getBucket(), oss.getEndpoint())) {
            return null;
        }
        return toObjectKey(url, uploadUrl(oss));
    }

    private String toObjectKey(String url, String baseUrl) {
        if (StrUtil.isBlank(baseUrl)) {
            return null;
        }
        String normalizedBaseUrl = StrUtil.removeSuffix(baseUrl, "/");
        String normalizedUrl = StrUtil.subBefore(url, "?", false);
        try {
            URI uri = URI.create(normalizedUrl);
            URI base = URI.create(normalizedBaseUrl);
            if (uri.getHost() == null || !uri.getHost().equalsIgnoreCase(base.getHost())
                    || !Objects.equals(uri.getScheme(), base.getScheme()) || uri.getPort() != base.getPort()
                    || !uri.getRawPath().startsWith(StrUtil.removeSuffix(StrUtil.blankToDefault(base.getRawPath(), ""), "/") + "/")) return null;
            if (uri.getUserInfo() != null || uri.getFragment() != null || uri.getRawPath().toLowerCase(Locale.ROOT).matches(".*%(?:2e|2f|5c|25).*")) return null;
            String objectPath = uri.getRawPath();
            String basePath = URI.create(normalizedBaseUrl).getRawPath();
            if (StrUtil.isNotBlank(basePath) && !"/".equals(basePath)) {
                objectPath = StrUtil.removePrefix(objectPath, basePath);
            }
            objectPath = StrUtil.removePrefix(objectPath, "/");
            return StrUtil.isBlank(objectPath) ? null : URLDecoder.decode(objectPath.replace("+", "%2B"), StandardCharsets.UTF_8.name());
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
