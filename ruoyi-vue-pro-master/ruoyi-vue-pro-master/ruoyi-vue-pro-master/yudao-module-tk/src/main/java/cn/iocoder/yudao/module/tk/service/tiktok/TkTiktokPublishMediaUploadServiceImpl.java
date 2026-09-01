package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.iocoder.yudao.module.tk.controller.admin.upload.vo.TkUploadSessionRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.upload.vo.TkUploadSessionStatusRespVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokPublishMediaDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkUploadSessionDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkTiktokPublishMediaMapper;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import cn.iocoder.yudao.module.tk.service.upload.TkLocalUploadStorageService;
import cn.iocoder.yudao.module.tk.service.upload.TkOssPostPolicySigner;
import cn.iocoder.yudao.module.tk.service.upload.TkOssObjectStorageService;
import cn.iocoder.yudao.module.tk.service.upload.TkOssRestSigner;
import cn.iocoder.yudao.module.tk.service.upload.TkOssPostPolicySigner.Policy;
import cn.iocoder.yudao.module.tk.service.upload.TkUploadSessionService;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Resource;
import java.net.URLEncoder;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.tk.enums.ErrorCodeConstants.*;

@Service
@Validated
@Slf4j
public class TkTiktokPublishMediaUploadServiceImpl implements TkTiktokPublishMediaUploadService {

    private static final String[] ALLOWED_EXTENSIONS = {"mp4", "mov", "webm"};
    private static final byte[] WEBM_EBML_HEADER = new byte[]{0x1A, 0x45, (byte) 0xDF, (byte) 0xA3};
    private static final DateTimeFormatter OSS_GMT_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).withZone(ZoneOffset.UTC);
    private static final int OSS_HTTP_TIMEOUT_MILLIS = 30_000;

    @Resource
    private TkTiktokPublishMediaMapper mediaMapper;
    @Resource
    private TkLocalUploadStorageService storageService;
    @Resource
    private TkDataScopeService dataScopeService;
    @Resource
    private TkUploadSessionService uploadSessionService;
    @Resource
    private TkGenerationProperties generationProperties;
    @Resource
    private TkOssObjectStorageService ossObjectStorageService;

    @Override
    public TkUploadSessionRespVO createSession(String fileName, Long fileSize, String contentType) {
        validateFileBasics(fileName, fileSize);
        TkUserScope scope = requireScope();
        String uploadId = UUID.randomUUID().toString().replace("-", "");
        if (isOssUploadEnabled()) {
            return createOssSession(uploadId, scope, fileName, fileSize, contentType);
        }
        int chunkSize = getChunkSize();
        int totalChunks = (int) Math.ceil(fileSize * 1.0D / chunkSize);
        Path tmpDir = storageService.getTmpDir(uploadId);
        Properties manifest = new Properties();
        manifest.setProperty("uploadId", uploadId);
        manifest.setProperty("tenantId", String.valueOf(scope.getTenantId()));
        manifest.setProperty("companyId", String.valueOf(scope.getCompanyId()));
        manifest.setProperty("creator", StrUtil.blankToDefault(scope.getUserIdString(), ""));
        manifest.setProperty("fileName", fileName);
        manifest.setProperty("fileSize", String.valueOf(fileSize));
        manifest.setProperty("contentType", StrUtil.blankToDefault(contentType, "video/" + extension(fileName)));
        manifest.setProperty("chunkSize", String.valueOf(chunkSize));
        manifest.setProperty("totalChunks", String.valueOf(totalChunks));
        manifest.setProperty("status", "UPLOADING");
        try {
            Files.createDirectories(tmpDir);
            try (OutputStream outputStream = Files.newOutputStream(manifestPath(tmpDir))) {
                manifest.store(outputStream, "TK TikTok publish media upload session");
            }
        } catch (IOException ex) {
            throw new IllegalStateException("创建 TikTok 上传会话失败：" + ex.getMessage(), ex);
        }
        if (uploadSessionService != null) {
            uploadSessionService.createTiktok(uploadId, scope.getCompanyId(), fileName, fileSize,
                    manifest.getProperty("contentType"), "local");
        }
        TkUploadSessionRespVO response = new TkUploadSessionRespVO();
        response.setUploadId(uploadId);
        response.setUploadMode("local");
        response.setChunkSize(chunkSize);
        response.setTotalChunks(totalChunks);
        response.setUploadedSize(0L);
        response.setUploadedChunks(Collections.emptySet());
        return response;
    }

    private TkUploadSessionRespVO createOssSession(String uploadId, TkUserScope scope,
                                                    String fileName, Long fileSize, String contentType) {
        validateOssConfig();
        String objectKey = objectKey(scope, uploadId, fileName);
        Policy policy = TkOssPostPolicySigner.signExact(getOss().getAccessKeyId(), getOss().getAccessKeySecret(),
                objectKey, getMaxFileSize(), getOss().getPolicyExpireSeconds(), Clock.systemUTC());
        String normalizedContentType = StrUtil.blankToDefault(contentType, "video/" + extension(fileName));
        if (uploadSessionService != null) {
            uploadSessionService.createTiktok(uploadId, scope.getCompanyId(), fileName, fileSize,
                    normalizedContentType, "oss");
        }
        TkUploadSessionRespVO response = new TkUploadSessionRespVO();
        response.setUploadId(uploadId);
        response.setUploadMode("oss");
        response.setUploadUrl(uploadUrl());
        response.setPublicUrl(toReadUrl(objectKey));
        response.setObjectKey(objectKey);
        response.setAccessKeyId(policy.getAccessKeyId());
        response.setPolicy(policy.getPolicy());
        response.setSignature(policy.getSignature());
        response.setSuccessActionStatus("200");
        response.setExpiration(policy.getExpiration());
        response.setUploadedSize(0L);
        response.setUploadedChunks(Collections.emptySet());
        return response;
    }

    @Override
    public String refreshReadUrl(String fileUrl) {
        if (!hasOssCredentials() || StrUtil.isBlank(getOss().getPublicBaseUrl()) || StrUtil.isBlank(fileUrl)) {
            return fileUrl;
        }
        String publicBaseUrl = StrUtil.removeSuffix(getOss().getPublicBaseUrl(), "/");
        String prefix = publicBaseUrl + "/";
        if (!StrUtil.startWithIgnoreCase(fileUrl, prefix)) {
            return fileUrl;
        }
        String objectKey = fileUrl.substring(prefix.length());
        int queryIndex = objectKey.indexOf('?');
        if (queryIndex >= 0) {
            objectKey = objectKey.substring(0, queryIndex);
        }
        if (StrUtil.isBlank(objectKey) || objectKey.contains("..") || objectKey.startsWith("/")) {
            return fileUrl;
        }
        return toReadUrl(objectKey);
    }

    @Override
    public TkUploadSessionStatusRespVO getSessionStatus(String uploadId) {
        Path manifestFile = manifestPath(storageService.getTmpDir(uploadId));
        if (!Files.isRegularFile(manifestFile) && uploadSessionService != null) {
            TkUploadSessionDO session = uploadSessionService.validateAccessible(uploadId);
            if ("oss".equalsIgnoreCase(session.getStorageMode())) {
                return ossSessionStatus(uploadId, session);
            }
        }
        Properties manifest = validateSession(uploadId);
        int totalChunks = Integer.parseInt(manifest.getProperty("totalChunks"));
        Set<Integer> chunks = uploadedChunks(uploadId, totalChunks);
        TkUploadSessionStatusRespVO response = new TkUploadSessionStatusRespVO();
        response.setUploadId(uploadId);
        response.setChunkSize(Integer.parseInt(manifest.getProperty("chunkSize")));
        response.setTotalChunks(totalChunks);
        response.setFileSize(Long.parseLong(manifest.getProperty("fileSize")));
        response.setUploadedChunks(chunks);
        response.setUploadedSize(uploadedSize(uploadId, chunks));
        response.setStatus(manifest.getProperty("status", "UPLOADING"));
        return response;
    }

    private TkUploadSessionStatusRespVO ossSessionStatus(String uploadId, TkUploadSessionDO session) {
        long fileSize = session.getFileSize() == null ? 0L : session.getFileSize();
        int chunkSize = getChunkSize();
        TkUploadSessionStatusRespVO response = new TkUploadSessionStatusRespVO();
        response.setUploadId(uploadId);
        response.setChunkSize(chunkSize);
        response.setTotalChunks((int) Math.ceil(fileSize * 1.0D / chunkSize));
        response.setFileSize(fileSize);
        response.setUploadedChunks(Collections.emptySet());
        response.setUploadedSize(0L);
        response.setStatus(StrUtil.blankToDefault(session.getStatus(), "UPLOADING"));
        return response;
    }

    @Override
    public void uploadChunk(String uploadId, Integer chunkIndex, MultipartFile chunk) {
        if (chunk == null || chunk.isEmpty()) {
            throw exception(TK_UPLOAD_FILE_EMPTY);
        }
        Properties manifest = validateSession(uploadId);
        int totalChunks = Integer.parseInt(manifest.getProperty("totalChunks"));
        if (chunkIndex == null || chunkIndex < 0 || chunkIndex >= totalChunks) {
            throw new IllegalArgumentException("分片序号无效");
        }
        long expected = Long.parseLong(manifest.getProperty("fileSize"));
        int chunkSize = Integer.parseInt(manifest.getProperty("chunkSize"));
        if (chunk.getSize() > chunkSize || (chunkIndex < totalChunks - 1 && chunk.getSize() != chunkSize)) {
            throw new IllegalArgumentException("分片大小无效");
        }
        try {
            Files.copy(chunk.getInputStream(), chunkPath(storageService.getTmpDir(uploadId), chunkIndex),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new IllegalStateException("保存 TikTok 上传分片失败：" + ex.getMessage(), ex);
        }
        if (uploadedSize(uploadId, uploadedChunks(uploadId, totalChunks)) > expected) {
            throw new IllegalArgumentException("上传分片大小超出文件大小");
        }
    }

    @Override
    public TkTiktokPublishMediaDO complete(String uploadId, String coverUrl) {
        Path manifestFile = manifestPath(storageService.getTmpDir(uploadId));
        if (!Files.isRegularFile(manifestFile) && uploadSessionService != null) {
            TkUploadSessionDO session = uploadSessionService.validateAccessible(uploadId);
            if ("oss".equalsIgnoreCase(session.getStorageMode())) {
                return completeOss(uploadId, coverUrl, session);
            }
        }
        Properties manifest = validateSession(uploadId);
        int totalChunks = Integer.parseInt(manifest.getProperty("totalChunks"));
        Set<Integer> chunks = uploadedChunks(uploadId, totalChunks);
        if (chunks.size() != totalChunks) {
            throw new IllegalStateException("上传分片不完整");
        }
        TkUserScope scope = requireScope();
        long fileSize = Long.parseLong(manifest.getProperty("fileSize"));
        String fileName = manifest.getProperty("fileName");
        String extension = extension(fileName);
        String relativePath = StrUtil.format("tk/{}/{}/tiktok-publish-media/{}-{}",
                scope.getTenantId(), scope.getCompanyId(), uploadId, safeName(fileName));
        Path finalPath = storageService.resolveRelativePath(relativePath);
        try {
            Files.createDirectories(finalPath.getParent());
            mergeChunks(storageService.getTmpDir(uploadId), chunks, finalPath);
            if (Files.size(finalPath) != fileSize || !isValidVideoContainer(finalPath, extension)) {
                Files.deleteIfExists(finalPath);
                throw exception(TK_UPLOAD_FILE_INVALID);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("合并 TikTok 上传视频失败：" + ex.getMessage(), ex);
        }
        TkTiktokPublishMediaDO media = TkTiktokPublishMediaDO.builder()
                .companyId(scope.getCompanyId())
                .fileName(fileName)
                .fileUrl(storageService.toPublicUrl(relativePath))
                .coverUrl(coverUrl)
                .fileSize(fileSize)
                .mimeType(manifest.getProperty("contentType"))
                .status("READY")
                .build();
        media.setTenantId(scope.getTenantId());
        media.setCreator(scope.getUserIdString());
        mediaMapper.insert(media);
        if (uploadSessionService != null) {
            uploadSessionService.markCompleted(uploadId);
        }
        FileUtil.del(storageService.getTmpDir(uploadId).toFile());
        return media;
    }

    private TkTiktokPublishMediaDO completeOss(String uploadId, String coverUrl, TkUploadSessionDO session) {
        TkUserScope scope = requireScope();
        validatePersistedSessionScope(scope, session);
        String fileName = StrUtil.blankToDefault(session.getFileName(), "video.mp4");
        Long fileSize = session.getFileSize();
        if (fileSize == null || fileSize <= 0) {
            throw new IllegalArgumentException("上传会话文件信息无效");
        }
        validateOssConfig();
        String objectKey = objectKey(scope, uploadId, fileName);
        assertOssObjectReady(objectKey, fileSize);
        TkTiktokPublishMediaDO media = TkTiktokPublishMediaDO.builder()
                .companyId(scope.getCompanyId())
                .fileName(fileName)
                .fileUrl(toPublicUrl(objectKey))
                .coverUrl(coverUrl)
                .fileSize(fileSize)
                .mimeType(StrUtil.blankToDefault(session.getContentType(), "video/" + extension(fileName)))
                .status("READY")
                .build();
        media.setTenantId(scope.getTenantId());
        media.setCreator(scope.getUserIdString());
        mediaMapper.insert(media);
        if (uploadSessionService != null) {
            uploadSessionService.markCompleted(uploadId);
        }
        return media;
    }

    @Override
    public void cancel(String uploadId) {
        Path manifestFile = manifestPath(storageService.getTmpDir(uploadId));
        if (!Files.isRegularFile(manifestFile) && uploadSessionService != null) {
            TkUploadSessionDO session = uploadSessionService.validateAccessible(uploadId);
            if ("oss".equalsIgnoreCase(session.getStorageMode())) {
                TkUserScope scope = requireScope();
                validatePersistedSessionScope(scope, session);
                if (hasOssCredentials() && ossObjectStorageService != null) {
                    try {
                        ossObjectStorageService.deleteObject(objectKey(scope, uploadId, session.getFileName()));
                    } catch (Exception ex) {
                        log.warn("[cancel][failed to delete TikTok OSS object, uploadId({})]", uploadId, ex);
                    }
                }
                uploadSessionService.cancel(uploadId);
                return;
            }
        }
        validateSession(uploadId);
        if (uploadSessionService != null) {
            uploadSessionService.cancel(uploadId);
        }
        FileUtil.del(storageService.getTmpDir(uploadId).toFile());
    }

    private Properties validateSession(String uploadId) {
        if (StrUtil.isBlank(uploadId)) {
            throw new IllegalArgumentException("上传会话不能为空");
        }
        TkUserScope scope = requireScope();
        Path manifestFile = manifestPath(storageService.getTmpDir(uploadId));
        if (!Files.isRegularFile(manifestFile)) {
            if (uploadSessionService == null) {
                throw new IllegalArgumentException("上传会话不存在");
            }
            TkUploadSessionDO session = uploadSessionService.validateAccessible(uploadId);
            validatePersistedSessionScope(scope, session);
            if (!"local".equalsIgnoreCase(session.getStorageMode())) {
                throw new IllegalArgumentException("上传会话存储方式无效");
            }
            return recoverManifest(manifestFile, uploadId, session);
        }
        Properties manifest = new Properties();
        try (InputStream inputStream = Files.newInputStream(manifestFile)) {
            manifest.load(inputStream);
        } catch (IOException ex) {
            throw new IllegalStateException("读取上传会话失败：" + ex.getMessage(), ex);
        }
        if (!String.valueOf(scope.getTenantId()).equals(manifest.getProperty("tenantId"))
                || !String.valueOf(scope.getCompanyId()).equals(manifest.getProperty("companyId"))) {
            throw new IllegalArgumentException("无权访问上传会话");
        }
        if (uploadSessionService != null) {
            TkUploadSessionDO session = uploadSessionService.validateAccessible(uploadId);
            validatePersistedSessionScope(scope, session);
        }
        return manifest;
    }

    private Properties recoverManifest(Path manifestFile, String uploadId, TkUploadSessionDO session) {
        String fileName = StrUtil.blankToDefault(session.getFileName(), "video.mp4");
        Long fileSize = session.getFileSize();
        if (fileSize == null || fileSize <= 0) {
            throw new IllegalArgumentException("上传会话文件信息无效");
        }
        int chunkSize = getChunkSize();
        int totalChunks = (int) Math.ceil(fileSize * 1.0D / chunkSize);
        Properties manifest = new Properties();
        manifest.setProperty("uploadId", uploadId);
        manifest.setProperty("tenantId", String.valueOf(session.getTenantId()));
        manifest.setProperty("companyId", String.valueOf(session.getCompanyId()));
        manifest.setProperty("creator", StrUtil.blankToDefault(session.getCreator(), ""));
        manifest.setProperty("fileName", fileName);
        manifest.setProperty("fileSize", String.valueOf(fileSize));
        manifest.setProperty("contentType", StrUtil.blankToDefault(session.getContentType(),
                "video/" + extension(fileName)));
        manifest.setProperty("chunkSize", String.valueOf(chunkSize));
        manifest.setProperty("totalChunks", String.valueOf(totalChunks));
        manifest.setProperty("status", StrUtil.blankToDefault(session.getStatus(), "UPLOADING"));
        try {
            Files.createDirectories(manifestFile.getParent());
            try (OutputStream outputStream = Files.newOutputStream(manifestFile)) {
                manifest.store(outputStream, "TK TikTok publish media upload session (recovered)");
            }
        } catch (IOException ex) {
            throw new IllegalStateException("恢复上传会话失败：" + ex.getMessage(), ex);
        }
        return manifest;
    }

    private void validatePersistedSessionScope(TkUserScope scope, TkUploadSessionDO session) {
        if (session == null || !scope.getTenantId().equals(session.getTenantId())
                || !scope.getCompanyId().equals(session.getCompanyId())) {
            throw new IllegalArgumentException("无权访问上传会话");
        }
    }

    private TkUserScope requireScope() {
        TkUserScope scope = dataScopeService.getCurrentScope();
        if (scope == null || scope.getTenantId() == null) {
            throw exception(TK_USER_SCOPE_NOT_CONFIGURED);
        }
        if (scope.getCompanyId() != null) {
            return scope;
        }
        Long companyId = dataScopeService.getWritableCompanyId(null);
        if (companyId == null) {
            throw exception(TK_USER_SCOPE_NOT_CONFIGURED);
        }
        return new TkUserScope(scope.getUserId(), scope.getTenantId(), scope.getUserLevel(), companyId);
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

    private int getChunkSize() {
        Integer size = generationProperties.getUpload().getChunkSizeBytes();
        return size == null || size <= 0 ? 1 * 1024 * 1024 : size;
    }

    private long getMaxFileSize() {
        Long size = generationProperties.getUpload().getMaxFileSizeBytes();
        return size == null || size <= 0 ? 1_000_000_000L : size;
    }

    private Set<Integer> uploadedChunks(String uploadId, int totalChunks) {
        Set<Integer> chunks = new TreeSet<>();
        Path tmpDir = storageService.getTmpDir(uploadId);
        for (int i = 0; i < totalChunks; i++) {
            if (Files.isRegularFile(chunkPath(tmpDir, i))) {
                chunks.add(i);
            }
        }
        return chunks;
    }

    private long uploadedSize(String uploadId, Set<Integer> chunks) {
        long total = 0L;
        for (Integer chunk : chunks) {
            try {
                total += Files.size(chunkPath(storageService.getTmpDir(uploadId), chunk));
            } catch (IOException ignored) {
                // Status is advisory while a chunk is being replaced.
            }
        }
        return total;
    }

    private void mergeChunks(Path tmpDir, Set<Integer> chunks, Path target) throws IOException {
        try (OutputStream outputStream = Files.newOutputStream(target)) {
            for (Integer chunk : chunks) {
                Files.copy(chunkPath(tmpDir, chunk), outputStream);
            }
        }
    }

    private boolean isValidVideoContainer(Path path, String extension) throws IOException {
        if ("webm".equals(extension)) {
            byte[] header = new byte[WEBM_EBML_HEADER.length];
            try (InputStream inputStream = Files.newInputStream(path)) {
                return inputStream.read(header) == header.length && Arrays.equals(header, WEBM_EBML_HEADER);
            }
        }
        return containsMarker(path, "ftyp") && containsMarker(path, "moov");
    }

    private boolean containsMarker(Path path, String markerText) throws IOException {
        byte[] marker = markerText.getBytes(StandardCharsets.US_ASCII);
        byte[] buffer = new byte[8192];
        byte[] overlap = new byte[marker.length - 1];
        int overlapLength = 0;
        try (InputStream inputStream = Files.newInputStream(path)) {
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                byte[] candidate = new byte[overlapLength + read];
                System.arraycopy(overlap, 0, candidate, 0, overlapLength);
                System.arraycopy(buffer, 0, candidate, overlapLength, read);
                if (indexOf(candidate, marker) >= 0) {
                    return true;
                }
                overlapLength = Math.min(overlap.length, candidate.length);
                System.arraycopy(candidate, candidate.length - overlapLength, overlap, 0, overlapLength);
            }
        }
        return false;
    }

    private int indexOf(byte[] content, byte[] marker) {
        for (int i = 0; i <= content.length - marker.length; i++) {
            boolean matched = true;
            for (int j = 0; j < marker.length; j++) {
                if (content[i + j] != marker[j]) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                return i;
            }
        }
        return -1;
    }

    private boolean isOssUploadEnabled() {
        TkGenerationProperties.Upload upload = generationProperties == null ? null : generationProperties.getUpload();
        TkGenerationProperties.Oss oss = upload == null ? null : upload.getOss();
        return upload != null
                && "oss".equalsIgnoreCase(StrUtil.blankToDefault(upload.getStorageType(), "local"))
                && oss != null
                && Boolean.TRUE.equals(oss.getEnabled())
                && !StrUtil.hasBlank(oss.getBucket(), oss.getEndpoint(), oss.getPublicBaseUrl(),
                oss.getAccessKeyId(), oss.getAccessKeySecret());
    }

    private boolean hasOssCredentials() {
        TkGenerationProperties.Upload upload = generationProperties == null ? null : generationProperties.getUpload();
        TkGenerationProperties.Oss oss = upload == null ? null : upload.getOss();
        return oss != null && !StrUtil.hasBlank(oss.getBucket(), oss.getEndpoint(), oss.getAccessKeyId(),
                oss.getAccessKeySecret());
    }

    private void validateOssConfig() {
        TkGenerationProperties.Oss oss = getOss();
        if (oss == null || StrUtil.hasBlank(oss.getBucket(), oss.getEndpoint(), oss.getPublicBaseUrl(),
                oss.getAccessKeyId(), oss.getAccessKeySecret())) {
            throw new IllegalStateException("OSS 上传配置不完整");
        }
    }

    private String objectKey(TkUserScope scope, String uploadId, String fileName) {
        String prefix = StrUtil.removeSuffix(StrUtil.blankToDefault(getOss().getUploadPathPrefix(), "tk"), "/");
        return prefix + "/" + scope.getTenantId() + "/" + scope.getCompanyId()
                + "/tiktok-publish-media/" + uploadId + "." + extension(fileName);
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

    private String uploadUrl() {
        String endpoint = normalizeEndpointHost(getOss().getEndpoint());
        if (endpoint.startsWith(getOss().getBucket() + ".")) {
            return "https://" + endpoint;
        }
        return "https://" + getOss().getBucket() + "." + endpoint;
    }

    private String toReadUrl(String objectKey) {
        String publicUrl = toPublicUrl(objectKey);
        Integer expireSeconds = getOss().getReadUrlExpireSeconds();
        if (expireSeconds == null || expireSeconds <= 0) {
            return publicUrl;
        }
        long expires = Instant.now().getEpochSecond() + expireSeconds;
        String resource = "/" + getOss().getBucket() + "/" + objectKey;
        String signature = TkOssRestSigner.sign("GET", "", "", String.valueOf(expires), resource,
                getOss().getAccessKeySecret());
        return publicUrl + "?OSSAccessKeyId=" + encodeQuery(getOss().getAccessKeyId())
                + "&Expires=" + expires + "&Signature=" + encodeQuery(signature);
    }

    private String toPublicUrl(String objectKey) {
        return StrUtil.removeSuffix(getOss().getPublicBaseUrl(), "/") + "/" + objectKey;
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

    private TkGenerationProperties.Oss getOss() {
        return generationProperties == null || generationProperties.getUpload() == null
                ? null : generationProperties.getUpload().getOss();
    }

    private String gmtDate() {
        return OSS_GMT_DATE_FORMATTER.format(Instant.now());
    }

    private Path manifestPath(Path tmpDir) {
        return tmpDir.resolve("manifest.properties");
    }

    private Path chunkPath(Path tmpDir, int index) {
        return tmpDir.resolve(index + ".part");
    }

    private String extension(String fileName) {
        return StrUtil.blankToDefault(FileUtil.extName(StrUtil.blankToDefault(fileName, "")), "")
                .toLowerCase(Locale.ROOT);
    }

    private String safeName(String fileName) {
        return StrUtil.blankToDefault(fileName, "video.mp4").replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
