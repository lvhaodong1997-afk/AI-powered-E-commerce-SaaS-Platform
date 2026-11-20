package cn.iocoder.yudao.module.tk.service.open.x;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenXMediaDO;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenXMediaMapper;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiIds;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiException;
import cn.iocoder.yudao.module.tk.service.channel.TkOpenXAccess;
import cn.iocoder.yudao.module.tk.service.upload.TkLocalUploadStorageService;
import cn.iocoder.yudao.module.tk.service.upload.TkOssObjectStorageClient;
import cn.iocoder.yudao.module.tk.service.upload.TkOssObjectStorageService;
import cn.iocoder.yudao.module.tk.service.upload.TkOssPostPolicySigner;
import cn.iocoder.yudao.module.tk.service.upload.TkOssPostPolicySigner.Policy;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Service
@RequiredArgsConstructor
public class TkOpenXMediaService {
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String OBJECT_PREFIX = "tk/open-api-x/";
    private final TkOpenXAccess access;
    private final TkOpenXMediaMapper mediaMapper;
    private final TkLocalUploadStorageService localStorageService;
    private final TkGenerationProperties properties;
    private final TkOssObjectStorageClient ossObjectStorageService;
    @Value("${tk.open-api.publish.pull-url-expire-seconds:86400}")
    private int pullUrlExpireSeconds = 86400;

    public Map<String, Object> create(String fileName, Long fileSize, String contentType, String sha256) {
        String clientId = access.requireClient("x.publish");
        validateFile(fileName, fileSize, contentType, sha256);
        String uploadId = TkOpenApiIds.next("upload");
        boolean oss = isOssEnabled();
        TkOpenXMediaDO media = TkOpenXMediaDO.builder()
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
        Map<String, Object> response = new LinkedHashMap<>();
        if (oss) {
            if (!ossObjectStorageService.isConfigured() || uploadOss().getPublicBaseUrl() == null) {
                throw TkOpenApiException.unavailable("OSS_CONFIG_REQUIRED", "OSS upload is not configured");
            }
            String objectKey = buildObjectKey(clientId, uploadId, fileName, LocalDate.now().format(DATE));
            Policy policy = TkOssPostPolicySigner.signExact(
                    uploadOss().getAccessKeyId(), uploadOss().getAccessKeySecret(),
                    objectKey, maxFileSize(), uploadOss().getPolicyExpireSeconds(), sha256, Clock.systemUTC());
            media.setObjectKey(objectKey);
            media.setFileUrl(StrUtil.removeSuffix(uploadOss().getPublicBaseUrl(), "/") + "/" + objectKey);
            mediaMapper.insert(media);
            response.put("uploadId", uploadId);
            response.put("uploadMode", "OSS");
            response.put("uploadUrl", ossUploadUrl());
            response.put("objectKey", objectKey);
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("policy", policy.getPolicy());
            fields.put("accessKeyId", policy.getAccessKeyId());
            fields.put("signature", policy.getSignature());
            fields.put("sha256", media.getSha256());
            response.put("fields", fields);
            response.put("expireTime", media.getExpireTime());
            return response;
        }
        mediaMapper.insert(media);
        response.put("uploadId", uploadId);
        response.put("uploadMode", "LOCAL");
        response.put("chunkSize", chunkSize());
        response.put("totalChunks", totalChunks(fileSize));
        response.put("expireTime", media.getExpireTime());
        return response;
    }

    public Map<String, Object> getStatus(String uploadId) {
        TkOpenXMediaDO media = requireUpload(uploadId);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("uploadId", media.getUploadId());
        response.put("mediaId", media.getMediaId());
        response.put("uploadMode", media.getUploadMode());
        response.put("fileSize", media.getFileSize());
        response.put("uploadedSize", media.getUploadedSize());
        response.put("status", media.getStatus());
        response.put("failReason", media.getFailReason());
        return response;
    }

    public void uploadChunk(String uploadId, Integer chunkIndex, byte[] chunk) {
        TkOpenXMediaDO media = requireUploadForUpdate(uploadId);
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
        int size = chunkSize();
        long expected = chunkIndex == totalChunks - 1
                ? media.getFileSize() - (long) chunkIndex * size : size;
        if (chunk.length != expected) {
            throw TkOpenApiException.badRequest("MEDIA_FILE_INVALID", "chunk size is invalid");
        }
        try {
            Files.createDirectories(localStorageService.getTmpDir(uploadId));
            Files.write(chunkPath(uploadId, chunkIndex), chunk);
        } catch (Exception ex) {
            throw TkOpenApiException.unavailable("MEDIA_UPLOAD_FAILED", "cannot store upload chunk");
        }
        mediaMapper.updateById(new TkOpenXMediaDO().setId(media.getId())
                .setUploadedSize(uploadedSize(uploadId, totalChunks))
                .setUploadedChunks(uploadedChunkJson(uploadId, totalChunks)));
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> complete(String uploadId, Long fileSize, String sha256) {
        TkOpenXMediaDO media = requireUploadForUpdate(uploadId);
        requireUploading(media);
        if (fileSize == null || !fileSize.equals(media.getFileSize()) || (StrUtil.isNotBlank(media.getSha256())
                && !media.getSha256().equalsIgnoreCase(StrUtil.blankToDefault(sha256, "")))) {
            throw TkOpenApiException.badRequest("MEDIA_FILE_INVALID", "uploaded file metadata does not match");
        }
        if ("LOCAL".equals(media.getUploadMode())) {
            int totalChunks = totalChunks(media.getFileSize());
            if (uploadedChunks(uploadId, totalChunks).size() != totalChunks) {
                throw TkOpenApiException.badRequest("MEDIA_NOT_READY", "upload chunks are incomplete");
            }
            String mediaId = TkOpenApiIds.next("media");
            String relativePath = "open-api-x/" + media.getClientId() + "/" + mediaId + "/" + safeName(media.getFileName());
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
            if (StrUtil.isBlank(media.getObjectKey()) || !media.getObjectKey().startsWith(OBJECT_PREFIX + media.getClientId() + "/")) {
                throw TkOpenApiException.badRequest("MEDIA_FILE_INVALID", "OSS object is missing");
            }
            TkOssObjectStorageClient.ObjectMetadata metadata;
            try {
                metadata = storageForMedia(media).headObject(media.getObjectKey());
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
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("mediaId", media.getMediaId());
        response.put("uploadId", media.getUploadId());
        response.put("fileName", media.getFileName());
        response.put("fileSize", media.getFileSize());
        response.put("contentType", media.getContentType());
        response.put("status", media.getStatus());
        return response;
    }

    public TkOpenXMediaDO requireReady(String clientId, String mediaId) {
        if (mediaId == null || mediaId.isEmpty()) {
            throw TkOpenApiException.notFound("MEDIA_NOT_FOUND", "media does not exist");
        }
        TkOpenXMediaDO media = mediaMapper.selectByClientAndMediaId(clientId, mediaId);
        if (media == null) throw TkOpenApiException.notFound("MEDIA_NOT_FOUND", "media does not exist");
        if (!"READY".equals(media.getStatus()) || media.getExpireTime() == null
                || media.getExpireTime().isBefore(LocalDateTime.now())) {
            throw TkOpenApiException.badRequest("MEDIA_NOT_READY", "media is not ready or has expired");
        }
        if (media.getFileSize() == null || media.getFileSize() <= 0 || media.getFileSize() > maxFileSize()) {
            throw TkOpenApiException.badRequest("MEDIA_FILE_INVALID", "media size is invalid");
        }
        return media;
    }

    public String resolveReadUrl(TkOpenXMediaDO media) {
        TkOpenXMediaDO stored = mediaMapper.selectByClientAndMediaId(media.getClientId(), media.getMediaId());
        if (stored == null || !stored.getClientId().equals(media.getClientId())
                || !java.util.Objects.equals(stored.getObjectKey(), media.getObjectKey())) {
            throw TkOpenApiException.notFound("MEDIA_NOT_FOUND", "media does not exist");
        }
        if ("OSS".equalsIgnoreCase(stored.getUploadMode()) && StrUtil.isNotBlank(stored.getObjectKey())
                && stored.getObjectKey().startsWith(OBJECT_PREFIX + stored.getClientId() + "/")
                && ossObjectStorageService instanceof TkOssObjectStorageService) {
            return ((TkOssObjectStorageService) storageForMedia(stored))
                    .createPullReadUrl(stored.getObjectKey(), null, pullUrlExpireSeconds);
        }
        if ("LOCAL".equalsIgnoreCase(stored.getUploadMode()) && StrUtil.isNotBlank(stored.getFileUrl())) {
            java.util.Optional<Path> local = localStorageService.resolveLocalPath(stored.getFileUrl());
            if (local.isPresent() && Files.isRegularFile(local.get())) return stored.getFileUrl();
        }
        throw TkOpenApiException.badRequest("MEDIA_NOT_READY", "media source is not available");
    }

    private TkOpenXMediaDO requireUploadForUpdate(String uploadId) {
        String clientId = access.requireClient("x.publish");
        TkOpenXMediaDO media = mediaMapper.selectByClientAndUploadIdForUpdate(clientId, uploadId);
        if (media == null) throw TkOpenApiException.notFound("MEDIA_NOT_FOUND", "upload session does not exist");
        if (media.getExpireTime() != null && media.getExpireTime().isBefore(LocalDateTime.now())
                && "UPLOADING".equals(media.getStatus())) {
            throw TkOpenApiException.badRequest("MEDIA_UPLOAD_EXPIRED", "upload session has expired");
        }
        return media;
    }

    private TkOpenXMediaDO requireUpload(String uploadId) {
        String clientId = access.requireClient("x.publish");
        TkOpenXMediaDO media = mediaMapper.selectByClientAndUploadId(clientId, uploadId);
        if (media == null) throw TkOpenApiException.notFound("MEDIA_NOT_FOUND", "upload session does not exist");
        return media;
    }

    private void requireUploading(TkOpenXMediaDO media) {
        if (!"UPLOADING".equals(media.getStatus())) {
            throw TkOpenApiException.badRequest("MEDIA_UPLOAD_STATUS_INVALID",
                    "upload session is no longer accepting changes");
        }
    }

    static String normalizeExtension(String fileName) {
        String extension = FileUtil.extName(StrUtil.blankToDefault(fileName, "")).toLowerCase(java.util.Locale.ROOT);
        if (!java.util.Arrays.asList("mp4", "mov", "webm").contains(extension))
            throw TkOpenApiException.badRequest("MEDIA_FILE_INVALID", "video extension must be mp4, mov or webm");
        return extension;
    }

    static String buildObjectKey(String clientId, String uploadId, String fileName, String date) {
        return OBJECT_PREFIX + clientId + "/" + date + "/" + uploadId + "." + normalizeExtension(fileName);
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

    private TkGenerationProperties.Oss uploadOss() {
        TkGenerationProperties.Oss publish = properties.getUpload().getPublishOss();
        if (Boolean.TRUE.equals(publish.getEnabled())) {
            TkOssObjectStorageService.validatePublishConfig(publish, properties.getUpload().getOss());
            return publish;
        }
        return properties.getUpload().getOss();
    }

    private TkOssObjectStorageClient storageForMedia(TkOpenXMediaDO media) {
        return ossObjectStorageService instanceof TkOssObjectStorageService
                ? ((TkOssObjectStorageService) ossObjectStorageService).storageForUrl(media.getFileUrl()) : ossObjectStorageService;
    }

    private boolean isOssEnabled() {
        return properties.getUpload() != null && uploadOss() != null && Boolean.TRUE.equals(uploadOss().getEnabled());
    }

    private int expireHours() {
        Integer value = properties.getUpload() == null ? null : properties.getUpload().getSessionExpireHours();
        return value == null || value <= 0 ? 24 : value;
    }

    private long maxFileSize() {
        Long value = properties.getUpload() == null ? null : properties.getUpload().getMaxFileSizeBytes();
        return value == null || value <= 0 ? 1_000_000_000L : value;
    }

    private int chunkSize() {
        Integer value = properties.getUpload() == null ? null : properties.getUpload().getChunkSizeBytes();
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
        TkGenerationProperties.Oss oss = uploadOss();
        String endpoint = StrUtil.removePrefix(StrUtil.removePrefix(oss.getEndpoint(), "https://"), "http://");
        return endpoint.startsWith(oss.getBucket() + ".") ? "https://" + endpoint : "https://" + oss.getBucket() + "." + endpoint;
    }
}