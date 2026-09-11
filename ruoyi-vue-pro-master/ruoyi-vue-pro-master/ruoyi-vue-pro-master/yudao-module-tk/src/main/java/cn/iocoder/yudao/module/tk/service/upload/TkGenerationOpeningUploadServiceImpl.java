package cn.iocoder.yudao.module.tk.service.upload;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkGenerationOpeningUploadCompleteRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.upload.vo.TkUploadSessionRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.upload.vo.TkUploadSessionStatusRespVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkMaterialLibraryDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkUploadSessionDO;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import cn.iocoder.yudao.module.tk.service.material.TkMaterialLibraryService;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.time.Clock;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.tk.enums.ErrorCodeConstants.TK_UPLOAD_FILE_EMPTY;
import static cn.iocoder.yudao.module.tk.enums.ErrorCodeConstants.TK_UPLOAD_FILE_EXTENSION_INVALID;
import static cn.iocoder.yudao.module.tk.enums.ErrorCodeConstants.TK_UPLOAD_FILE_INVALID;
import static cn.iocoder.yudao.module.tk.enums.ErrorCodeConstants.TK_UPLOAD_FILE_TOO_LARGE;
import static cn.iocoder.yudao.module.tk.enums.ErrorCodeConstants.TK_UPLOAD_SESSION_INVALID;

@Service
@Validated
public class TkGenerationOpeningUploadServiceImpl implements TkGenerationOpeningUploadService {

    private static final String[] ALLOWED_EXTENSIONS = {"mp4", "mov", "webm"};

    @Resource
    private TkMaterialLibraryService libraryService;
    @Resource
    private TkDataScopeService dataScopeService;
    @Resource
    private TkUploadSessionService uploadSessionService;
    @Resource
    private TkChunkUploadStorageService chunkStorageService;
    @Resource
    private TkGenerationProperties generationProperties;
    @Resource
    private TkOssObjectStorageService ossObjectStorageService;

    @Override
    public TkUploadSessionRespVO createSession(Long libraryId, String fileName, Long fileSize, String contentType) {
        TkMaterialLibraryDO library = validateLibrary(libraryId);
        validateFileBasics(fileName, fileSize);
        String uploadId = UUID.randomUUID().toString().replace("-", "");
        String normalizedContentType = StrUtil.blankToDefault(contentType, "video/" + extension(fileName));
        if (isOssUploadEnabled()) {
            uploadSessionService.create(uploadId, library, fileName, fileSize, normalizedContentType, "oss");
            return createOssSession(uploadId, library, fileName, fileSize);
        }
        chunkStorageService.createManifest(uploadId, fileName, fileSize, normalizedContentType,
                Collections.singletonMap("libraryId", String.valueOf(libraryId)));
        uploadSessionService.create(uploadId, library, fileName, fileSize, normalizedContentType, "local");
        return buildLocalSession(uploadId, fileSize);
    }

    @Override
    public TkUploadSessionStatusRespVO getSessionStatus(String uploadId) {
        TkUploadSessionDO session = uploadSessionService.validateAccessible(uploadId);
        validateSessionOwner(session, session.getLibraryId());
        if ("oss".equalsIgnoreCase(session.getStorageMode())) {
            return buildOssStatus(session);
        }
        java.util.Properties manifest = chunkStorageService.readManifest(uploadId);
        int totalChunks = Integer.parseInt(manifest.getProperty("totalChunks"));
        java.util.Set<Integer> chunks = chunkStorageService.uploadedChunks(uploadId, totalChunks);
        TkUploadSessionStatusRespVO response = new TkUploadSessionStatusRespVO();
        response.setUploadId(uploadId);
        response.setChunkSize(Integer.parseInt(manifest.getProperty("chunkSize")));
        response.setTotalChunks(totalChunks);
        response.setFileSize(Long.parseLong(manifest.getProperty("fileSize")));
        response.setUploadedChunks(chunks);
        response.setUploadedSize(chunkStorageService.uploadedSize(uploadId, chunks));
        response.setStatus(manifest.getProperty("status", "UPLOADING"));
        return response;
    }

    @Override
    public void uploadChunk(String uploadId, Integer chunkIndex, MultipartFile chunk) {
        TkUploadSessionDO session = uploadSessionService.validateAccessible(uploadId);
        validateSessionOwner(session, session.getLibraryId());
        if (!"local".equalsIgnoreCase(session.getStorageMode())) {
            throw new IllegalArgumentException("OSS 上传会话不支持服务端分片");
        }
        chunkStorageService.saveChunk(uploadId, chunkIndex, chunk);
    }

    @Override
    public TkGenerationOpeningUploadCompleteRespVO complete(String uploadId) {
        TkUploadSessionDO session = uploadSessionService.validateAccessible(uploadId);
        validateSessionOwner(session, session.getLibraryId());
        if ("oss".equalsIgnoreCase(session.getStorageMode())) {
            return completeOss(uploadId, session);
        }
        String relativePath = buildRelativePath(session);
        try {
            chunkStorageService.mergeAndValidate(uploadId, relativePath);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw exception(TK_UPLOAD_FILE_INVALID);
        }
        uploadSessionService.markCompleted(uploadId);
        chunkStorageService.deleteSessionFiles(uploadId);
        return buildCompleteResponse(uploadId, session, chunkStorageService.toPublicUrl(relativePath));
    }

    @Override
    public TkGenerationOpeningUploadCompleteRespVO validateCompletedUpload(String uploadId, Long libraryId) {
        TkUploadSessionDO session = uploadSessionService.validateCompletedAccessible(uploadId);
        validateSessionOwner(session, libraryId);
        if ("oss".equalsIgnoreCase(session.getStorageMode())) {
            String objectKey = buildOssObjectKey(session);
            return buildCompleteResponse(uploadId, session, toOssReadUrl(objectKey));
        }
        String relativePath = buildRelativePath(session);
        try {
            if (!Files.isRegularFile(getLocalPath(relativePath))
                    || Files.size(getLocalPath(relativePath)) != session.getFileSize()) {
                throw exception(TK_UPLOAD_SESSION_INVALID);
            }
        } catch (java.io.IOException ex) {
            throw exception(TK_UPLOAD_SESSION_INVALID);
        }
        return buildCompleteResponse(uploadId, session, chunkStorageService.toPublicUrl(relativePath));
    }

    @Override
    public void cancel(String uploadId) {
        TkUploadSessionDO session = uploadSessionService.validateAccessible(uploadId);
        validateSessionOwner(session, session.getLibraryId());
        if ("oss".equalsIgnoreCase(session.getStorageMode()) && ossObjectStorageService != null
                && ossObjectStorageService.isConfigured()) {
            ossObjectStorageService.deleteObject(buildOssObjectKey(session));
        }
        uploadSessionService.cancel(uploadId);
        chunkStorageService.deleteSessionFiles(uploadId);
    }

    private TkMaterialLibraryDO validateLibrary(Long libraryId) {
        TkMaterialLibraryDO library = libraryService.validateMaterialLibraryReadable(libraryId);
        if (library == null) {
            throw exception(TK_UPLOAD_SESSION_INVALID);
        }
        return library;
    }

    private void validateSessionOwner(TkUploadSessionDO session, Long libraryId) {
        if (session == null || session.getLibraryId() == null || libraryId == null
                || !libraryId.equals(session.getLibraryId())) {
            throw exception(TK_UPLOAD_SESSION_INVALID);
        }
        TkUserScope scope = dataScopeService.getCurrentScope();
        boolean companyMatches = scope != null && (scope.isPlatformAdmin()
                || Objects.equals(scope.getCompanyId(), session.getCompanyId()));
        if (scope == null || !Objects.equals(scope.getTenantId(), session.getTenantId())
                || !companyMatches
                || !Objects.equals(scope.getUserIdString(), session.getCreator())) {
            throw exception(TK_UPLOAD_SESSION_INVALID);
        }
        TkMaterialLibraryDO library = validateLibrary(libraryId);
        if (!Objects.equals(session.getTenantId(), library.getTenantId())
                || !Objects.equals(session.getCompanyId(), library.getCompanyId())) {
            throw exception(TK_UPLOAD_SESSION_INVALID);
        }
    }

    private void validateFileBasics(String fileName, Long fileSize) {
        if (fileSize == null || fileSize <= 0) {
            throw exception(TK_UPLOAD_FILE_EMPTY);
        }
        if (fileSize > chunkStorageService.getMaxFileSize()) {
            throw exception(TK_UPLOAD_FILE_TOO_LARGE);
        }
        if (!Arrays.asList(ALLOWED_EXTENSIONS).contains(extension(fileName))) {
            throw exception(TK_UPLOAD_FILE_EXTENSION_INVALID);
        }
    }

    private TkUploadSessionRespVO buildLocalSession(String uploadId, Long fileSize) {
        TkUploadSessionRespVO response = new TkUploadSessionRespVO();
        response.setUploadId(uploadId);
        response.setUploadMode("local");
        response.setChunkSize(chunkStorageService.getChunkSize());
        response.setTotalChunks((int) Math.ceil(fileSize * 1.0D / chunkStorageService.getChunkSize()));
        response.setUploadedSize(0L);
        response.setUploadedChunks(Collections.emptySet());
        return response;
    }

    private TkUploadSessionRespVO createOssSession(String uploadId, TkMaterialLibraryDO library,
                                                    String fileName, Long fileSize) {
        TkGenerationProperties.Oss oss = generationProperties.getUpload().getOss();
        TkOssPostPolicySigner.Policy policy = TkOssPostPolicySigner.signExact(
                oss.getAccessKeyId(), oss.getAccessKeySecret(),
                buildOssObjectKey(uploadId, library, fileName), chunkStorageService.getMaxFileSize(),
                oss.getPolicyExpireSeconds(), Clock.systemUTC());
        TkUploadSessionRespVO response = new TkUploadSessionRespVO();
        response.setUploadId(uploadId);
        response.setUploadMode("oss");
        response.setChunkSize(chunkStorageService.getChunkSize());
        response.setTotalChunks(1);
        response.setUploadedSize(0L);
        response.setUploadedChunks(Collections.emptySet());
        response.setUploadUrl(buildOssUploadUrl(oss));
        response.setPublicUrl(toOssReadUrl(buildOssObjectKey(uploadId, library, fileName)));
        response.setObjectKey(buildOssObjectKey(uploadId, library, fileName));
        response.setAccessKeyId(policy.getAccessKeyId());
        response.setPolicy(policy.getPolicy());
        response.setSignature(policy.getSignature());
        response.setSuccessActionStatus("200");
        response.setExpiration(policy.getExpiration());
        return response;
    }

    private TkUploadSessionStatusRespVO buildOssStatus(TkUploadSessionDO session) {
        TkUploadSessionStatusRespVO response = new TkUploadSessionStatusRespVO();
        response.setUploadId(session.getUploadId());
        response.setChunkSize(chunkStorageService.getChunkSize());
        response.setTotalChunks(1);
        response.setFileSize(session.getFileSize());
        response.setUploadedChunks(Collections.emptySet());
        response.setUploadedSize(0L);
        response.setStatus(session.getStatus());
        return response;
    }

    private TkGenerationOpeningUploadCompleteRespVO completeOss(String uploadId, TkUploadSessionDO session) {
        if (ossObjectStorageService == null || !ossObjectStorageService.isConfigured()) {
            throw new IllegalStateException("OSS 存储未配置");
        }
        TkOssObjectStorageClient.ObjectMetadata metadata = ossObjectStorageService.headObject(buildOssObjectKey(session));
        if (metadata.getContentLength() != session.getFileSize()) {
            throw exception(TK_UPLOAD_FILE_INVALID);
        }
        uploadSessionService.markCompleted(uploadId);
        return buildCompleteResponse(uploadId, session, toOssReadUrl(buildOssObjectKey(session)));
    }

    private TkGenerationOpeningUploadCompleteRespVO buildCompleteResponse(String uploadId, TkUploadSessionDO session,
                                                                           String fileUrl) {
        TkGenerationOpeningUploadCompleteRespVO response = new TkGenerationOpeningUploadCompleteRespVO();
        response.setUploadId(uploadId);
        response.setFileName(session.getFileName());
        response.setFileUrl(fileUrl);
        response.setFileSize(session.getFileSize());
        response.setStatus("COMPLETED");
        return response;
    }

    private String buildRelativePath(TkUploadSessionDO session) {
        return StrUtil.format("tk/{}/{}/generation-openings/{}-{}",
                session.getTenantId(), session.getCompanyId(), session.getUploadId(), safeName(session.getFileName()));
    }

    private java.nio.file.Path getLocalPath(String relativePath) {
        return chunkStorageService.resolveRelativePath(relativePath);
    }

    private String buildOssObjectKey(TkUploadSessionDO session) {
        return buildOssObjectKey(session.getUploadId(), session.getTenantId(), session.getCompanyId(), session.getFileName());
    }

    private String buildOssObjectKey(String uploadId, TkMaterialLibraryDO library, String fileName) {
        return buildOssObjectKey(uploadId, library.getTenantId(), library.getCompanyId(), fileName);
    }

    private String buildOssObjectKey(String uploadId, Long tenantId, Long companyId, String fileName) {
        TkGenerationProperties.Oss oss = generationProperties.getUpload().getOss();
        String prefix = StrUtil.removeSuffix(StrUtil.blankToDefault(oss.getUploadPathPrefix(), "tk"), "/");
        return StrUtil.format("{}/{}/{}/generation-openings/{}.{}", prefix, tenantId, companyId,
                uploadId, extension(fileName));
    }

    private String buildOssUploadUrl(TkGenerationProperties.Oss oss) {
        String endpoint = StrUtil.removePrefix(StrUtil.removePrefix(oss.getEndpoint(), "https://"), "http://");
        return endpoint.startsWith(oss.getBucket() + ".") ? "https://" + endpoint : "https://" + oss.getBucket() + "." + endpoint;
    }

    private String toOssPublicUrl(String objectKey) {
        return StrUtil.removeSuffix(generationProperties.getUpload().getOss().getPublicBaseUrl(), "/") + "/" + objectKey;
    }

    private String toOssReadUrl(String objectKey) {
        String publicUrl = toOssPublicUrl(objectKey);
        return ossObjectStorageService == null ? publicUrl : ossObjectStorageService.resolveReadUrl(publicUrl);
    }

    private boolean isOssUploadEnabled() {
        TkGenerationProperties.Upload upload = generationProperties.getUpload();
        TkGenerationProperties.Oss oss = upload == null ? null : upload.getOss();
        return upload != null && "oss".equalsIgnoreCase(StrUtil.blankToDefault(upload.getStorageType(), "local"))
                && oss != null && Boolean.TRUE.equals(oss.getEnabled())
                && !StrUtil.hasBlank(oss.getBucket(), oss.getEndpoint(), oss.getPublicBaseUrl(),
                oss.getAccessKeyId(), oss.getAccessKeySecret());
    }

    private String extension(String fileName) {
        return StrUtil.blankToDefault(FileUtil.extName(StrUtil.blankToDefault(fileName, "")), "")
                .toLowerCase(Locale.ROOT);
    }

    private String safeName(String fileName) {
        return FileUtil.getName(StrUtil.blankToDefault(fileName, "opening.mp4"))
                .replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
