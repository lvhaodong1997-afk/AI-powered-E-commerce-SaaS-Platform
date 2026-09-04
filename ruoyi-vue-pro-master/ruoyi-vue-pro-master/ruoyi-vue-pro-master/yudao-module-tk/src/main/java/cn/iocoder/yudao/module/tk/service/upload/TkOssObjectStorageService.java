package cn.iocoder.yudao.module.tk.service.upload;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkUploadSessionDO;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;

@Service
public class TkOssObjectStorageService implements TkOssObjectStorageClient {

    private static final DateTimeFormatter OSS_GMT_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).withZone(ZoneOffset.UTC);
    private static final int OSS_HTTP_TIMEOUT_MILLIS = 30_000;

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
        String resource = "/" + oss.getBucket() + "/" + objectKey;
        String date = OSS_GMT_DATE_FORMATTER.format(Instant.now());
        String signature = TkOssRestSigner.sign("DELETE", "", "", date, resource, oss.getAccessKeySecret());
        try (HttpResponse response = HttpRequest.delete(uploadUrl(oss) + "/" + encodePath(objectKey))
                .header("Date", date)
                .header("Authorization", "OSS " + oss.getAccessKeyId() + ":" + signature)
                .timeout(OSS_HTTP_TIMEOUT_MILLIS)
                .execute()) {
            if (response.getStatus() != 204 && response.getStatus() != 404) {
                throw new IllegalStateException(StrUtil.format("删除 OSS 文件失败，HTTP {}：{}",
                        response.getStatus(), objectKey));
            }
        }
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
                        response.header("x-oss-meta-sha256"));
            } catch (NumberFormatException ex) {
                throw new IllegalStateException("OSS 文件大小元数据无效：" + objectKey, ex);
            }
        }
    }

    public String buildTiktokObjectKey(TkUploadSessionDO session) {
        TkGenerationProperties.Oss oss = getOss();
        String prefix = StrUtil.removeSuffix(StrUtil.blankToDefault(oss == null ? null : oss.getUploadPathPrefix(), "tk"), "/");
        String extension = FileUtil.extName(StrUtil.blankToDefault(session.getFileName(), "video.mp4"))
                .toLowerCase(Locale.ROOT);
        return prefix + "/" + session.getTenantId() + "/" + session.getCompanyId()
                + "/tiktok-publish-media/" + session.getUploadId() + "." + extension;
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

    private String encodeQuery(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (Exception ex) {
            throw new IllegalStateException("OSS URL 编码失败", ex);
        }
    }

}
