package cn.iocoder.yudao.module.tk.service.open.tiktok;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.tk.controller.open.tiktok.vo.TkOpenTiktokMediaVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokMediaDO;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenTiktokMediaMapper;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiContext;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiException;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiIds;
import cn.iocoder.yudao.module.tk.service.upload.TkLocalUploadStorageService;
import cn.iocoder.yudao.module.tk.service.upload.TkOssObjectStorageClient;
import cn.iocoder.yudao.module.tk.service.upload.TkOssPostPolicySigner;
import cn.iocoder.yudao.module.tk.service.upload.TkOssPostPolicySigner.Policy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Set;
import java.util.TreeSet;

@Service
public class TkOpenTiktokMediaService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String[] EXTENSIONS = {"mp4", "mov", "webm"};
    private final TkOpenTiktokMediaMapper mediaMapper;
    private final TkLocalUploadStorageService localStorageService;
    private final TkGenerationProperties properties;
    private final TkOssObjectStorageClient ossObjectStorageService;

    public TkOpenTiktokMediaService(TkOpenTiktokMediaMapper mediaMapper,
                                    TkLocalUploadStorageService localStorageService,
                                    TkGenerationProperties properties,
                                    TkOssObjectStorageClient ossObjectStorageService) {
        this.mediaMapper = mediaMapper;
        this.localStorageService = localStorageService;
        this.properties = properties;
        this.ossObjectStorageService = ossObjectStorageService;
    }

    public TkOpenTiktokMediaVO.UploadSessionResp create(String fileName, Long fileSize, String contentType,
                                                        String sha256) {
        validateFile(fileName, fileSize, contentType, sha256);
        String clientId = TkOpenApiContext.getRequiredPrincipal().getClientId();
        String uploadId = TkOpenApiIds.next("upload");
        boolean oss = isOssEnabled();
        TkOpenTiktokMediaDO media = TkOpenTiktokMediaDO.builder()
                .uploadId(uploadId)
                .clientId(clientId)
                .uploadMode(oss ? "OSS" : "LOCAL")
                .fileName(fileName)
                .fileSize(fileSize)
                .contentType(StrUtil.blankToDefault(contentType, "video/" + normalizeExtension(fileName)))
                .sha256(StrUtil.isBlank(sha256) ? null : sha256.toLowerCase(java.util.Locale.ROOT))
                .uploadedSize(0L)
                .uploadedChunks("[]")
                .status("UPLOADING")
                .expireTime(LocalDateTime.now().plusHours(expireHours()))
                .build();
        if (oss) {
            if (!ossObjectStorageService.isConfigured() || properties.getUpload().getOss().getPublicBaseUrl() == null) {
                throw TkOpenApiException.unavailable("OSS_CONFIG_REQUIRED", "OSS upload is not configured");
            }
            String objectKey = buildObjectKey(clientId, uploadId, fileName, LocalDate.now().format(DATE));
            Policy policy = TkOssPostPolicySigner.signExact(
                    properties.getUpload().getOss().getAccessKeyId(), properties.getUpload().getOss().getAccessKeySecret(),
                    objectKey, maxFileSize(), properties.getUpload().getOss().getPolicyExpireSeconds(), sha256,
                    Clock.systemUTC());
            media.setObjectKey(objectKey);
            media.setFileUrl(StrUtil.removeSuffix(properties.getUpload().getOss().getPublicBaseUrl(), "/") + "/" + objectKey);
            mediaMapper.insert(media);
            return toOssResp(media, policy);
        }
        mediaMapper.insert(media);
        return toLocalResp(media);
    }

    public TkOpenTiktokMediaVO.UploadStatusResp getStatus(String uploadId) {
        TkOpenTiktokMediaDO media = requireUpload(uploadId);
        TkOpenTiktokMediaVO.UploadStatusResp response = new TkOpenTiktokMediaVO.UploadStatusResp();
        response.setUploadId(media.getUploadId());
        response.setMediaId(media.getMediaId());
        response.setUploadMode(media.getUploadMode());
        response.setFileSize(media.getFileSize());
        response.setUploadedSize(media.getUploadedSize());
        response.setStatus(media.getStatus());
        return response;
    }

    public void uploadChunk(String uploadId, Integer chunkIndex, byte[] chunk) {
        TkOpenTiktokMediaDO media = requireUpload(uploadId);
        requireUploading(media);
        if (!"LOCAL".equals(media.getUploadMode())) {
            throw TkOpenApiException.badRequest("UPLOAD_MODE_INVALID", "this upload session uses OSS direct upload");
        }
        if (chunk == null || chunk.length == 0) {
            throw TkOpenApiException.badRequest("MEDIA_FILE_INVALID", "chunk is empty");
        }
        int totalChunks = totalChunks(media.getFileSize());
        if (chunkIndex == null || chunkIndex < 0 || chunkIndex >= totalChunks) {
            throw TkOpenApiException.badRequest("MEDIA_FILE_INVALID", "chunk index is invalid");
        }
        int chunkSize = chunkSize();
        long expected = chunkIndex == totalChunks - 1
                ? media.getFileSize() - (long) chunkIndex * chunkSize : chunkSize;
        if (chunk.length != expected) {
            throw TkOpenApiException.badRequest("MEDIA_FILE_INVALID", "chunk size is invalid");
        }
        try {
            Files.createDirectories(localStorageService.getTmpDir(uploadId));
            Files.write(chunkPath(uploadId, chunkIndex), chunk);
        } catch (Exception ex) {
            throw TkOpenApiException.unavailable("MEDIA_UPLOAD_FAILED", "cannot store upload chunk");
        }
        long uploadedSize = uploadedSize(uploadId, totalChunks);
        mediaMapper.updateById(new TkOpenTiktokMediaDO().setId(media.getId())
                .setUploadedSize(uploadedSize).setUploadedChunks(uploadedChunkJson(uploadId, totalChunks)));
    }

    @Transactional(rollbackFor = Exception.class)
    public TkOpenTiktokMediaVO.MediaResp complete(String uploadId, Long fileSize, String sha256,
                                                   Long coverTimestampMs) {
        TkOpenTiktokMediaDO media = requireUpload(uploadId);
        requireUploading(media);
        if (fileSize == null || !fileSize.equals(media.getFileSize()) || (StrUtil.isNotBlank(media.getSha256())
                && !media.getSha256().equalsIgnoreCase(StrUtil.blankToDefault(sha256, "")))) {
            throw TkOpenApiException.badRequest("MEDIA_FILE_INVALID", "uploaded file metadata does not match");
        }
        media.setCoverTimestampMs(coverTimestampMs);
        if ("LOCAL".equals(media.getUploadMode())) {
            int totalChunks = totalChunks(media.getFileSize());
            if (uploadedChunks(uploadId, totalChunks).size() != totalChunks) {
                throw TkOpenApiException.badRequest("MEDIA_NOT_READY", "upload chunks are incomplete");
            }
            String mediaId = TkOpenApiIds.next("media");
            String relativePath = "open-api/" + media.getClientId() + "/" + mediaId + "/" + safeName(media.getFileName());
            Path target = localStorageService.resolveRelativePath(relativePath);
            try {
                Files.createDirectories(target.getParent());
                try (OutputStream output = Files.newOutputStream(target)) {
                    for (int index = 0; index < totalChunks; index++) Files.copy(chunkPath(uploadId, index), output);
                }
                if (Files.size(target) != media.getFileSize()) throw new IllegalStateException("file size mismatch");
                if (StrUtil.isNotBlank(media.getSha256()) && !media.getSha256().equalsIgnoreCase(sha256(target)))
                    throw new IllegalStateException("sha256 mismatch");
            } catch (Exception ex) {
                try { Files.deleteIfExists(target); } catch (Exception ignored) {}
                throw TkOpenApiException.badRequest("MEDIA_FILE_INVALID", "uploaded file validation failed");
            }
            media.setMediaId(mediaId);
            media.setFileUrl(localStorageService.toPublicUrl(relativePath));
            FileUtil.del(localStorageService.getTmpDir(uploadId).toFile());
        } else {
            if (StrUtil.isBlank(media.getObjectKey())) {
                throw TkOpenApiException.badRequest("MEDIA_FILE_INVALID", "OSS object is missing");
            }
            TkOssObjectStorageClient.ObjectMetadata metadata;
            try {
                metadata = ossObjectStorageService.headObject(media.getObjectKey());
            } catch (Exception ex) {
                throw TkOpenApiException.badRequest("MEDIA_NOT_READY", "OSS object is unavailable");
            }
            if (metadata.getContentLength() != media.getFileSize()
                    || (StrUtil.isNotBlank(media.getSha256())
                    && !media.getSha256().equalsIgnoreCase(StrUtil.blankToDefault(metadata.getSha256(), "")))) {
                throw TkOpenApiException.badRequest("MEDIA_FILE_INVALID", "OSS object metadata does not match");
            }
            media.setUploadedSize(media.getFileSize());
        }
        if (media.getMediaId() == null) media.setMediaId(TkOpenApiIds.next("media"));
        media.setStatus("READY");
        media.setCompletedTime(LocalDateTime.now());
        mediaMapper.updateById(media);
        TkOpenTiktokMediaVO.MediaResp response = new TkOpenTiktokMediaVO.MediaResp();
        response.setMediaId(media.getMediaId());
        response.setUploadId(media.getUploadId());
        response.setFileName(media.getFileName());
        response.setFileSize(media.getFileSize());
        response.setContentType(media.getContentType());
        response.setStatus(media.getStatus());
        return response;
    }

    public void cancel(String uploadId) {
        TkOpenTiktokMediaDO media = requireUpload(uploadId);
        requireUploading(media);
        if ("LOCAL".equals(media.getUploadMode())) FileUtil.del(localStorageService.getTmpDir(uploadId).toFile());
        else if (StrUtil.isNotBlank(media.getObjectKey()) && ossObjectStorageService.isConfigured())
            ossObjectStorageService.deleteObject(media.getObjectKey());
        mediaMapper.updateById(new TkOpenTiktokMediaDO().setId(media.getId()).setStatus("CANCELLED"));
    }

    public TkOpenTiktokMediaDO requireMedia(String mediaId) {
        String clientId = TkOpenApiContext.getRequiredPrincipal().getClientId();
        TkOpenTiktokMediaDO media = mediaMapper.selectByClientAndMediaId(clientId, mediaId);
        if (media == null) throw TkOpenApiException.notFound("MEDIA_NOT_FOUND", "media does not exist");
        return media;
    }

    private TkOpenTiktokMediaDO requireUpload(String uploadId) {
        String clientId = TkOpenApiContext.getRequiredPrincipal().getClientId();
        TkOpenTiktokMediaDO media = mediaMapper.selectByClientAndUploadId(clientId, uploadId);
        if (media == null) throw TkOpenApiException.notFound("MEDIA_NOT_FOUND", "upload session does not exist");
        if (media.getExpireTime().isBefore(LocalDateTime.now()) && "UPLOADING".equals(media.getStatus()))
            throw TkOpenApiException.badRequest("MEDIA_UPLOAD_EXPIRED", "upload session has expired");
        return media;
    }

    private void requireUploading(TkOpenTiktokMediaDO media) {
        if (!"UPLOADING".equals(media.getStatus())) {
            throw TkOpenApiException.badRequest("MEDIA_UPLOAD_STATUS_INVALID",
                    "upload session is no longer accepting changes");
        }
    }

    static String normalizeExtension(String fileName) {
        String extension = FileUtil.extName(StrUtil.blankToDefault(fileName, "")).toLowerCase(java.util.Locale.ROOT);
        if (!java.util.Arrays.asList(EXTENSIONS).contains(extension))
            throw new IllegalArgumentException("video extension must be mp4, mov or webm");
        return extension;
    }

    static String buildObjectKey(String clientId, String uploadId, String fileName, String date) {
        return "tk/open-api/" + clientId + "/" + date + "/" + uploadId + "." + normalizeExtension(fileName);
    }

    private boolean isOssEnabled() {
        return properties.getUpload() != null && properties.getUpload().getOss() != null
                && Boolean.TRUE.equals(properties.getUpload().getOss().getEnabled());
    }

    private int expireHours() {
        Integer value = properties.getUpload().getSessionExpireHours();
        return value == null || value <= 0 ? 24 : value;
    }

    private long maxFileSize() {
        Long value = properties.getUpload().getMaxFileSizeBytes();
        return value == null || value <= 0 ? 1_000_000_000L : value;
    }

    private void validateFile(String fileName, Long fileSize, String contentType, String sha256) {
        String extension = normalizeExtension(fileName);
        if (fileSize == null || fileSize <= 0) throw TkOpenApiException.badRequest("MEDIA_FILE_INVALID", "file size is invalid");
        if (fileSize > maxFileSize()) throw new TkOpenApiException("MEDIA_FILE_TOO_LARGE", "file is too large", 413);
        if (StrUtil.isNotBlank(contentType)) {
            String normalized = contentType.trim().toLowerCase(java.util.Locale.ROOT);
            boolean supported = "application/octet-stream".equals(normalized)
                    || ("mp4".equals(extension) && "video/mp4".equals(normalized))
                    || ("mov".equals(extension) && ("video/quicktime".equals(normalized) || "video/mov".equals(normalized)))
                    || ("webm".equals(extension) && "video/webm".equals(normalized));
            if (!supported) {
                throw TkOpenApiException.badRequest("MEDIA_FILE_INVALID", "content type does not match the video extension");
            }
        }
        if (StrUtil.isNotBlank(sha256) && !sha256.matches("(?i)^[0-9a-f]{64}$")) {
            throw TkOpenApiException.badRequest("MEDIA_FILE_INVALID", "sha256 must be a 64-character hexadecimal value");
        }
    }

    private TkOpenTiktokMediaVO.UploadSessionResp toOssResp(TkOpenTiktokMediaDO media, Policy policy) {
        TkOpenTiktokMediaVO.UploadSessionResp response = new TkOpenTiktokMediaVO.UploadSessionResp();
        response.setUploadId(media.getUploadId());
        response.setUploadMode(media.getUploadMode());
        response.setUploadUrl(ossUploadUrl());
        response.setObjectKey(media.getObjectKey());
        response.setFields(new TkOpenTiktokMediaVO.OssFields(policy.getPolicy(), policy.getAccessKeyId(),
                policy.getSignature(), media.getSha256()));
        response.setExpireTime(media.getExpireTime());
        return response;
    }

    private TkOpenTiktokMediaVO.UploadSessionResp toLocalResp(TkOpenTiktokMediaDO media) {
        TkOpenTiktokMediaVO.UploadSessionResp response = new TkOpenTiktokMediaVO.UploadSessionResp();
        response.setUploadId(media.getUploadId());
        response.setUploadMode(media.getUploadMode());
        response.setChunkSize(chunkSize());
        response.setTotalChunks(totalChunks(media.getFileSize()));
        response.setExpireTime(media.getExpireTime());
        return response;
    }

    private int chunkSize() {
        Integer value = properties.getUpload().getChunkSizeBytes();
        return value == null || value <= 0 ? 1024 * 1024 : value;
    }

    private int totalChunks(long fileSize) {
        return (int) Math.ceil(fileSize * 1.0D / chunkSize());
    }

    private Path chunkPath(String uploadId, int index) {
        return localStorageService.getTmpDir(uploadId).resolve(index + ".part");
    }

    private Set<Integer> uploadedChunks(String uploadId, int totalChunks) {
        Set<Integer> result = new TreeSet<>();
        for (int index = 0; index < totalChunks; index++) if (Files.isRegularFile(chunkPath(uploadId, index))) result.add(index);
        return result;
    }

    private long uploadedSize(String uploadId, int totalChunks) {
        long size = 0L;
        for (Integer index : uploadedChunks(uploadId, totalChunks)) {
            try { size += Files.size(chunkPath(uploadId, index)); } catch (Exception ignored) {}
        }
        return size;
    }

    private String uploadedChunkJson(String uploadId, int totalChunks) {
        return cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(uploadedChunks(uploadId, totalChunks));
    }

    private String safeName(String fileName) {
        return StrUtil.blankToDefault(FileUtil.getName(fileName), "video.mp4").replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) if (read > 0) digest.update(buffer, 0, read);
        }
        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest()) result.append(String.format("%02x", value & 0xff));
        return result.toString();
    }

    private String ossUploadUrl() {
        TkGenerationProperties.Oss oss = properties.getUpload().getOss();
        String endpoint = StrUtil.removePrefix(StrUtil.removePrefix(oss.getEndpoint(), "https://"), "http://");
        return endpoint.startsWith(oss.getBucket() + ".") ? "https://" + endpoint : "https://" + oss.getBucket() + "." + endpoint;
    }
}
