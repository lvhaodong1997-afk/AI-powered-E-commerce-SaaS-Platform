package cn.iocoder.yudao.module.tk.service.upload;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.iocoder.yudao.module.infra.service.file.FileService;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkGenerationTaskMapper;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.File;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
public class TkGenerationOutputStorageServiceImpl implements TkGenerationOutputStorageService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter DOWNLOAD_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter OSS_GMT_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).withZone(ZoneOffset.UTC);
    private static final int OSS_HTTP_TIMEOUT_MILLIS = 120_000;

    @Resource
    private TkGenerationProperties generationProperties;
    @Resource
    private FileService fileService;
    @Resource
    private TkGenerationTaskMapper taskMapper;
    @Resource
    private AdminUserApi adminUserApi;
    private OssObjectUploader ossObjectUploader = new HttpOssObjectUploader();

    @Override
    public String uploadGeneratedAsset(TkGenerationTaskDO task, File source, String fileName, String contentType) {
        if (source == null || !source.isFile()) {
            throw new IllegalArgumentException("生成文件不存在：" + source);
        }
        return uploadGeneratedAsset(task, FileUtil.readBytes(source), fileName, contentType);
    }

    @Override
    public String uploadGeneratedAsset(TkGenerationTaskDO task, byte[] content, String fileName, String contentType) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("生成文件内容不能为空");
        }
        TkGenerationProperties.Oss oss = requireGenerationOss();
        String normalizedContentType = StrUtil.blankToDefault(contentType, "application/octet-stream");
        String objectKey = buildGeneratedAssetObjectKey(oss.getUploadPathPrefix(), task, LocalDate.now(), fileName);
        ossObjectUploader.upload(oss, objectKey, content, normalizedContentType);
        return toReadUrl(oss, objectKey, buildDownloadFileNameWithDailyNo(task, fileName));
    }

    @Override
    public String refreshGeneratedAssetReadUrl(TkGenerationTaskDO task, String outputUrl) {
        if (StrUtil.isBlank(outputUrl)) {
            return outputUrl;
        }
        TkGenerationProperties.Oss oss = currentGenerationOss();
        if (oss == null || oss.getReadUrlExpireSeconds() == null || oss.getReadUrlExpireSeconds() <= 0) {
            return outputUrl;
        }
        String objectKey = extractObjectKeyFromPublicUrl(oss, outputUrl);
        if (StrUtil.isBlank(objectKey)) {
            return outputUrl;
        }
        return toReadUrl(oss, objectKey, buildDownloadFileNameWithDailyNo(task, FileUtil.getName(objectKey)));
    }

    static String buildGeneratedAssetObjectKey(String uploadPathPrefix, TkGenerationTaskDO task,
                                               LocalDate date, String fileName) {
        return StrUtil.format("{}/{}/{}",
                buildGeneratedAssetDirectory(uploadPathPrefix, task),
                date.format(DATE_FORMATTER),
                normalizeFileName(fileName));
    }

    static String buildGeneratedAssetDirectory(String uploadPathPrefix, TkGenerationTaskDO task) {
        if (task == null || task.getId() == null || task.getTenantId() == null || task.getCompanyId() == null) {
            throw new IllegalArgumentException("生成任务缺少租户、公司或任务编号");
        }
        String prefix = StrUtil.removeSuffix(StrUtil.blankToDefault(uploadPathPrefix, "tk"), "/");
        return StrUtil.format("{}/{}/{}/generation-tasks/{}",
                prefix, task.getTenantId(), task.getCompanyId(), task.getId());
    }

    private static String normalizeFileName(String fileName) {
        String name = FileUtil.getName(StrUtil.blankToDefault(fileName, "generated.bin"));
        name = name.replaceAll("[\\\\/:*?\"<>|]+", "-")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-");
        return StrUtil.blankToDefault(name, "generated.bin");
    }

    private String getUploadPathPrefix() {
        TkGenerationProperties.Upload upload = generationProperties == null ? null : generationProperties.getUpload();
        TkGenerationProperties.Oss oss = upload == null ? null : upload.getOss();
        return oss == null ? "tk" : oss.getUploadPathPrefix();
    }

    private TkGenerationProperties.Oss currentGenerationOss() {
        TkGenerationProperties.Upload upload = generationProperties == null ? null : generationProperties.getUpload();
        return upload == null ? null : upload.getOss();
    }

    private TkGenerationProperties.Oss requireGenerationOss() {
        TkGenerationProperties.Upload upload = generationProperties == null ? null : generationProperties.getUpload();
        TkGenerationProperties.Oss oss = upload == null ? null : upload.getOss();
        if (upload == null || oss == null
                || !"oss".equalsIgnoreCase(StrUtil.blankToDefault(upload.getStorageType(), "local"))
                || !Boolean.TRUE.equals(oss.getEnabled())) {
            throw new IllegalStateException("生成视频 OSS 存储未启用");
        }
        if (StrUtil.hasBlank(oss.getBucket(), oss.getEndpoint(), oss.getPublicBaseUrl(),
                oss.getAccessKeyId(), oss.getAccessKeySecret())) {
            throw new IllegalStateException("生成视频 OSS 存储配置不完整");
        }
        return oss;
    }

    private static String toPublicUrl(TkGenerationProperties.Oss oss, String objectKey) {
        return StrUtil.removeSuffix(oss.getPublicBaseUrl(), "/") + "/" + objectKey;
    }

    private static String extractObjectKeyFromPublicUrl(TkGenerationProperties.Oss oss, String outputUrl) {
        try {
            String publicBaseUrl = StrUtil.removeSuffix(oss.getPublicBaseUrl(), "/");
            String normalizedOutputUrl = StrUtil.subBefore(outputUrl, "?", false);
            if (!StrUtil.startWithIgnoreCase(normalizedOutputUrl, publicBaseUrl + "/")) {
                return null;
            }
            URI uri = URI.create(normalizedOutputUrl);
            String objectKey = uri.getRawPath();
            String basePath = URI.create(publicBaseUrl).getRawPath();
            if (StrUtil.isNotBlank(basePath) && !"/".equals(basePath)) {
                objectKey = StrUtil.removePrefix(objectKey, basePath);
            }
            objectKey = StrUtil.removePrefix(objectKey, "/");
            return StrUtil.isBlank(objectKey) ? null : java.net.URLDecoder.decode(objectKey, StandardCharsets.UTF_8.name());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String toReadUrl(TkGenerationProperties.Oss oss, String objectKey, String downloadFileName) {
        String publicUrl = toPublicUrl(oss, objectKey);
        Integer expireSeconds = oss.getReadUrlExpireSeconds();
        if (expireSeconds == null || expireSeconds <= 0) {
            return publicUrl;
        }
        long expires = Instant.now().getEpochSecond() + expireSeconds;
        String contentDisposition = "attachment; filename=\"" + normalizeFileName(downloadFileName) + "\"";
        String dispositionQuery = "response-content-disposition=" + contentDisposition;
        String resource = "/" + oss.getBucket() + "/" + objectKey + "?" + dispositionQuery;
        String signature = TkOssRestSigner.sign("GET", "", "", String.valueOf(expires), resource, oss.getAccessKeySecret());
        return publicUrl + "?OSSAccessKeyId=" + encodeQuery(oss.getAccessKeyId())
                + "&Expires=" + expires
                + "&response-content-disposition=" + encodeQuery(contentDisposition)
                + "&Signature=" + encodeQuery(signature);
    }

    private String buildDownloadFileNameWithDailyNo(TkGenerationTaskDO task, String fileName) {
        String normalizedFileName = normalizeFileName(fileName);
        if (!"mp4".equalsIgnoreCase(FileUtil.extName(normalizedFileName))
                || !StrUtil.startWithIgnoreCase(normalizedFileName, "generated-")) {
            return normalizedFileName;
        }
        LocalDateTime createTime = task == null ? null : task.getCreateTime();
        LocalDate date = createTime == null ? LocalDate.now() : createTime.toLocalDate();
        return StrUtil.format("{}-{}-{}.mp4",
                date.format(DOWNLOAD_DATE_FORMATTER),
                resolveCreatorName(task),
                StrUtil.padPre(String.valueOf(resolveDailyUserVideoNo(task)), 3, '0'));
    }

    private String resolveCreatorName(TkGenerationTaskDO task) {
        String creator = task == null ? null : task.getCreator();
        if (StrUtil.isBlank(creator)) {
            return "用户";
        }
        if (adminUserApi != null && creator.matches("\\d+")) {
            try {
                AdminUserRespDTO user = adminUserApi.getUser(Long.valueOf(creator));
                if (user != null && StrUtil.isNotBlank(user.getNickname())) {
                    return normalizeFileName(user.getNickname());
                }
            } catch (Exception ignored) {
                // Fall through to a stable non-empty creator label.
            }
        }
        return "用户" + creator;
    }

    private int resolveDailyUserVideoNo(TkGenerationTaskDO task) {
        if (task == null || task.getId() == null || task.getCreateTime() == null
                || StrUtil.isBlank(task.getCreator()) || taskMapper == null) {
            return 1;
        }
        LocalDate date = task.getCreateTime().toLocalDate();
        List<Long> dailyTaskIds = taskMapper.selectDailyTaskIds(
                task.getTenantId(), task.getCreator(), date.atStartOfDay(), date.plusDays(1).atStartOfDay());
        for (int i = 0; i < dailyTaskIds.size(); i++) {
            if (task.getId().equals(dailyTaskIds.get(i))) {
                return i + 1;
            }
        }
        return 1;
    }

    private static String encodeQuery(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (Exception ex) {
            throw new IllegalStateException("OSS URL 编码失败", ex);
        }
    }

    interface OssObjectUploader {

        void upload(TkGenerationProperties.Oss oss, String objectKey, byte[] content, String contentType);

    }

    private static class HttpOssObjectUploader implements OssObjectUploader {

        @Override
        public void upload(TkGenerationProperties.Oss oss, String objectKey, byte[] content, String contentType) {
            String resource = "/" + oss.getBucket() + "/" + objectKey;
            String date = OSS_GMT_DATE_FORMATTER.format(Instant.now());
            String signature = TkOssRestSigner.sign("PUT", "", contentType, date, resource, oss.getAccessKeySecret());
            try (HttpResponse response = HttpRequest.put(uploadUrl(oss) + "/" + encodePath(objectKey))
                    .header("Date", date)
                    .header("Content-Type", contentType)
                    .header("Authorization", "OSS " + oss.getAccessKeyId() + ":" + signature)
                    .body(content)
                    .timeout(OSS_HTTP_TIMEOUT_MILLIS)
                    .execute()) {
                if (response.getStatus() < 200 || response.getStatus() >= 300) {
                    throw new IllegalStateException(StrUtil.format("生成视频上传 OSS 失败：HTTP {}，{}",
                            response.getStatus(), objectKey));
                }
            }
        }

        private static String uploadUrl(TkGenerationProperties.Oss oss) {
            String endpoint = normalizeEndpointHost(oss.getEndpoint());
            if (endpoint.startsWith(oss.getBucket() + ".")) {
                return "https://" + endpoint;
            }
            return "https://" + oss.getBucket() + "." + endpoint;
        }

        private static String encodePath(String objectKey) {
            return Arrays.stream(objectKey.split("/"))
                    .map(TkGenerationOutputStorageServiceImpl::encodeQuery)
                    .reduce((left, right) -> left + "/" + right)
                    .orElse("");
        }

        private static String normalizeEndpointHost(String endpoint) {
            return StrUtil.removePrefix(StrUtil.removePrefix(endpoint, "https://"), "http://");
        }

    }
}
