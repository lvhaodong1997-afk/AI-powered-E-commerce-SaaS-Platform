package cn.iocoder.yudao.module.tk.service.upload;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.iocoder.yudao.module.tk.controller.admin.upload.vo.TkUploadSessionCompleteReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.upload.vo.TkUploadSessionRespVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkMaterialLibraryDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkMaterialVideoDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkUploadSessionDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkMaterialLibraryMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.TkMaterialVideoMapper;
import cn.iocoder.yudao.module.tk.enums.TkMaterialSegmentTypeEnum;
import cn.iocoder.yudao.module.tk.enums.TkMaterialUsagePhaseEnum;
import cn.iocoder.yudao.module.tk.enums.TkMaterialVideoStatusEnum;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import cn.iocoder.yudao.module.tk.service.material.TkMaterialVideoParseService;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.upload.TkOssPostPolicySigner.Policy;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.tk.enums.ErrorCodeConstants.*;

@Service
@Validated
public class TkMaterialOssUploadServiceImpl implements TkMaterialOssUploadService {

    private static final String[] ALLOWED_EXTENSIONS = {"mp4", "mov", "webm"};
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter OSS_GMT_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).withZone(ZoneOffset.UTC);
    private static final int OSS_HTTP_TIMEOUT_MILLIS = 30_000;

    @Resource
    private TkMaterialLibraryMapper libraryMapper;
    @Resource
    private TkMaterialVideoMapper videoMapper;
    @Resource
    private TkDataScopeService dataScopeService;
    @Resource
    private TkMaterialVideoParseService materialVideoParseService;
    @Resource
    private TkGenerationProperties generationProperties;
    @Resource
    private TkUploadSessionService uploadSessionService;

    @Override
    public TkUploadSessionRespVO createMaterialVideoSession(Long libraryId, String fileName, Long fileSize, String contentType) {
        TkMaterialLibraryDO library = validateLibraryWritable(libraryId);
        validateFileBasics(fileName, fileSize);
        validateOssConfig();

        String uploadId = UUID.randomUUID().toString().replace("-", "");
        String keyPrefix = objectKeyPrefix(library);
        String objectKey = keyPrefix + uploadId + "." + extension(fileName);
        Policy policy = TkOssPostPolicySigner.sign(getOss().getAccessKeyId(), getOss().getAccessKeySecret(),
                keyPrefix, getMaxFileSize(), getOss().getPolicyExpireSeconds(), Clock.systemUTC());

        if (uploadSessionService != null) {
            uploadSessionService.create(uploadId, library, fileName, fileSize,
                    StrUtil.blankToDefault(contentType, "video/" + extension(fileName)), "oss");
        }
        TkUploadSessionRespVO respVO = new TkUploadSessionRespVO();
        respVO.setUploadId(uploadId);
        respVO.setUploadMode("oss");
        respVO.setUploadUrl(uploadUrl());
        respVO.setPublicUrl(toReadUrl(objectKey));
        respVO.setObjectKey(objectKey);
        respVO.setAccessKeyId(policy.getAccessKeyId());
        respVO.setPolicy(policy.getPolicy());
        respVO.setSignature(policy.getSignature());
        respVO.setSuccessActionStatus("200");
        respVO.setExpiration(policy.getExpiration());
        respVO.setUploadedSize(0L);
        respVO.setUploadedChunks(java.util.Collections.emptySet());
        return respVO;
    }

    @Override
    public Long completeMaterialVideoUpload(TkUploadSessionCompleteReqVO reqVO) {
        if (reqVO == null) {
            throw exception(TK_UPLOAD_FILE_EMPTY);
        }
        TkMaterialLibraryDO library = validateLibraryWritable(reqVO.getLibraryId());
        if (uploadSessionService != null) {
            TkUploadSessionDO session = uploadSessionService.validateAccessible(reqVO.getUploadId());
            if (!reqVO.getLibraryId().equals(session.getLibraryId())
                    || !reqVO.getFileSize().equals(session.getFileSize())) {
                throw new IllegalArgumentException("上传会话与文件信息不一致");
            }
        }
        validateFileBasics(reqVO.getFileName(), reqVO.getFileSize());
        validateOssConfig();
        validateObjectKey(library, reqVO.getUploadId(), reqVO.getObjectKey());

        String fileUrl = toReadUrl(reqVO.getObjectKey());
        assertOssObjectReady(reqVO.getObjectKey(), reqVO.getFileSize());

        String extension = extension(reqVO.getFileName());
        TkMaterialUsagePhaseEnum normalizedPhase = TkMaterialUsagePhaseEnum.normalize(reqVO.getUsagePhase());
        TkMaterialSegmentTypeEnum normalizedSegment = TkMaterialSegmentTypeEnum.normalize(reqVO.getSegmentType());
        TkMaterialVideoDO video = TkMaterialVideoDO.builder()
                .companyId(library.getCompanyId())
                .libraryId(library.getId())
                .fileName(reqVO.getFileName())
                .fileUrl(fileUrl)
                .size(reqVO.getFileSize())
                .format(extension)
                .tags(reqVO.getTags())
                .usagePhase(normalizedPhase.getCode())
                .segmentType(normalizedSegment.getCode())
                .status(TkMaterialVideoStatusEnum.PARSING)
                .build();
        video.setTenantId(library.getTenantId());
        videoMapper.insert(video);

        libraryMapper.updateById(new TkMaterialLibraryDO()
                .setId(library.getId())
                .setVideoCount((library.getVideoCount() == null ? 0 : library.getVideoCount()) + 1)
                .setTotalSize((library.getTotalSize() == null ? 0L : library.getTotalSize()) + reqVO.getFileSize()));
        materialVideoParseService.submit(library.getTenantId(), video.getId());
        if (uploadSessionService != null) {
            uploadSessionService.markCompleted(reqVO.getUploadId());
        }
        return video.getId();
    }

    @Override
    public boolean isEnabled() {
        TkGenerationProperties.Upload upload = generationProperties.getUpload();
        return upload != null && "oss".equalsIgnoreCase(StrUtil.blankToDefault(upload.getStorageType(), "local"))
                && Boolean.TRUE.equals(getOss().getEnabled());
    }

    @Override
    public boolean isManagedUrl(String url) {
        return StrUtil.isNotBlank(toObjectKey(url));
    }

    @Override
    public void deleteByUrl(String url) {
        String objectKey = toObjectKey(url);
        if (StrUtil.isBlank(objectKey)) {
            return;
        }
        String resource = "/" + getOss().getBucket() + "/" + objectKey;
        String date = gmtDate();
        String signature = TkOssRestSigner.sign("DELETE", "", "", date, resource, getOss().getAccessKeySecret());
        try (HttpResponse response = HttpRequest.delete(uploadUrl() + "/" + encodePath(objectKey))
                .header("Date", date)
                .header("Authorization", "OSS " + getOss().getAccessKeyId() + ":" + signature)
                .timeout(OSS_HTTP_TIMEOUT_MILLIS)
                .execute()) {
            if (response.getStatus() != 204 && response.getStatus() != 404) {
                throw new IllegalStateException(StrUtil.format("删除 OSS 文件失败，HTTP {}：{}", response.getStatus(), url));
            }
        }
    }

    private void assertOssObjectReady(String objectKey, Long expectedSize) {
        String resource = "/" + getOss().getBucket() + "/" + objectKey;
        String date = gmtDate();
        String signature = TkOssRestSigner.sign("HEAD", "", "", date, resource, getOss().getAccessKeySecret());
        try (HttpResponse response = HttpRequest.head(uploadUrl() + "/" + encodePath(objectKey))
                .header("Date", date)
                .header("Authorization", "OSS " + getOss().getAccessKeyId() + ":" + signature)
                .timeout(OSS_HTTP_TIMEOUT_MILLIS)
                .execute()) {
            if (response.getStatus() < 200 || response.getStatus() >= 300) {
                throw new IllegalStateException(StrUtil.format("OSS 文件校验失败，HTTP {}：{}", response.getStatus(), objectKey));
            }
            String contentLength = response.header("Content-Length");
            if (StrUtil.isBlank(contentLength) || Long.parseLong(contentLength) != expectedSize) {
                throw new IllegalStateException("OSS 文件大小和上传记录不一致");
            }
        }
    }

    private TkMaterialLibraryDO validateLibraryWritable(Long libraryId) {
        TkMaterialLibraryDO library = libraryMapper.selectById(libraryId);
        if (library == null) {
            throw exception(TK_MATERIAL_LIBRARY_NOT_EXISTS);
        }
        dataScopeService.validateWritable(library.getTenantId(), library.getCompanyId());
        return library;
    }

    private void validateFileBasics(String fileName, Long fileSize) {
        if (fileSize == null || fileSize <= 0) {
            throw exception(TK_UPLOAD_FILE_EMPTY);
        }
        if (fileSize > getMaxFileSize()) {
            throw exception(TK_UPLOAD_FILE_TOO_LARGE);
        }
        if (!Arrays.asList(ALLOWED_EXTENSIONS).contains(extension(fileName))) {
            throw exception(TK_UPLOAD_FILE_EXTENSION_INVALID);
        }
    }

    private void validateObjectKey(TkMaterialLibraryDO library, String uploadId, String objectKey) {
        if (StrUtil.isBlank(uploadId) || StrUtil.isBlank(objectKey)
                || objectKey.contains("..") || objectKey.startsWith("/") || objectKey.contains("\\")) {
            throw new IllegalArgumentException("OSS 上传对象无效");
        }
        String objectFileName = objectKey.substring(objectKey.lastIndexOf('/') + 1);
        if (!isMaterialObjectKeyForLibrary(library, objectKey) || !objectFileName.startsWith(uploadId + ".")) {
            throw new IllegalArgumentException("OSS 上传对象和当前素材库不匹配");
        }
    }

    private void validateOssConfig() {
        TkGenerationProperties.Oss oss = getOss();
        if (StrUtil.hasBlank(oss.getBucket(), oss.getEndpoint(), oss.getPublicBaseUrl(),
                oss.getAccessKeyId(), oss.getAccessKeySecret())) {
            throw new IllegalStateException("OSS 上传配置不完整");
        }
    }

    private long getMaxFileSize() {
        Long maxFileSize = generationProperties.getUpload().getMaxFileSizeBytes();
        return maxFileSize == null || maxFileSize <= 0 ? 100L * 1024 * 1024 : maxFileSize;
    }

    private String objectKeyPrefix(TkMaterialLibraryDO library) {
        return objectKeyBasePrefix(library) + LocalDate.now().format(DATE_FORMATTER) + "/";
    }

    private String objectKeyBasePrefix(TkMaterialLibraryDO library) {
        return StrUtil.removeSuffix(StrUtil.blankToDefault(getOss().getUploadPathPrefix(), "tk"), "/")
                + "/" + library.getTenantId() + "/" + library.getCompanyId()
                + "/material-libraries/" + library.getId() + "/material-videos/";
    }

    private String legacyObjectKeyBasePrefix(TkMaterialLibraryDO library) {
        return StrUtil.removeSuffix(StrUtil.blankToDefault(getOss().getUploadPathPrefix(), "tk"), "/")
                + "/" + library.getTenantId() + "/" + library.getCompanyId() + "/material-videos/";
    }

    private boolean isMaterialObjectKeyForLibrary(TkMaterialLibraryDO library, String objectKey) {
        return objectKey.startsWith(objectKeyBasePrefix(library))
                || objectKey.startsWith(legacyObjectKeyBasePrefix(library));
    }

    private String uploadUrl() {
        String endpoint = normalizeEndpointHost(getOss().getEndpoint());
        if (endpoint.startsWith(getOss().getBucket() + ".")) {
            return "https://" + endpoint;
        }
        return "https://" + getOss().getBucket() + "." + endpoint;
    }

    private String toPublicUrl(String objectKey) {
        return StrUtil.removeSuffix(getOss().getPublicBaseUrl(), "/") + "/" + objectKey;
    }

    private String toReadUrl(String objectKey) {
        String publicUrl = toPublicUrl(objectKey);
        Integer expireSeconds = getOss().getReadUrlExpireSeconds();
        if (expireSeconds == null || expireSeconds <= 0) {
            return publicUrl;
        }
        long expires = Instant.now().getEpochSecond() + expireSeconds;
        String resource = "/" + getOss().getBucket() + "/" + objectKey;
        String signature = TkOssRestSigner.sign("GET", "", "", String.valueOf(expires), resource, getOss().getAccessKeySecret());
        return publicUrl + "?OSSAccessKeyId=" + encodeQuery(getOss().getAccessKeyId())
                + "&Expires=" + expires
                + "&Signature=" + encodeQuery(signature);
    }

    private String toObjectKey(String url) {
        String baseUrl = StrUtil.removeSuffix(getOss().getPublicBaseUrl(), "/") + "/";
        if (!StrUtil.startWith(url, baseUrl)) {
            return null;
        }
        String objectKey = StrUtil.removePrefix(url, baseUrl);
        int queryIndex = objectKey.indexOf('?');
        return queryIndex >= 0 ? objectKey.substring(0, queryIndex) : objectKey;
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

    private String normalizeEndpointHost(String endpoint) {
        return StrUtil.removePrefix(StrUtil.removePrefix(endpoint, "https://"), "http://");
    }

    private String extension(String fileName) {
        return StrUtil.blankToDefault(FileUtil.extName(StrUtil.blankToDefault(fileName, "")), "")
                .toLowerCase(Locale.ROOT);
    }

    private TkGenerationProperties.Oss getOss() {
        return generationProperties.getUpload().getOss();
    }

    private String gmtDate() {
        return formatOssGmtDate(Instant.now());
    }

    static String formatOssGmtDate(Instant instant) {
        return OSS_GMT_DATE_FORMATTER.format(instant);
    }
}
