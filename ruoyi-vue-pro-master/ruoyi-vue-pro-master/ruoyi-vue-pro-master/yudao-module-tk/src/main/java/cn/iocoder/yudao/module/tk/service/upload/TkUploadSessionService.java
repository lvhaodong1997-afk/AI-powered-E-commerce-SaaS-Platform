package cn.iocoder.yudao.module.tk.service.upload;

import cn.iocoder.yudao.module.tk.controller.admin.upload.vo.TkUploadSessionStatusRespVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkMaterialLibraryDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkUploadSessionDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkUploadSessionMapper;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.tk.enums.ErrorCodeConstants.TK_UPLOAD_SESSION_INVALID;

@Service
@Slf4j
public class TkUploadSessionService {

    @Resource
    private TkUploadSessionMapper sessionMapper;
    @Resource
    private TkDataScopeService dataScopeService;
    @Resource
    private TkGenerationProperties generationProperties;
    @Resource
    private TkLocalUploadStorageService localUploadStorageService;
    @Resource
    private TkOssObjectStorageService ossObjectStorageService;

    public void create(String uploadId, TkMaterialLibraryDO library, String fileName, Long fileSize,
                       String contentType, String storageMode) {
        TkUserScope scope = dataScopeService.getCurrentScope();
        int expireHours = generationProperties.getUpload().getSessionExpireHours() == null
                ? 24 : Math.max(1, generationProperties.getUpload().getSessionExpireHours());
        TkUploadSessionDO session = new TkUploadSessionDO()
                .setUploadId(uploadId)
                .setCompanyId(library.getCompanyId())
                .setLibraryId(library.getId())
                .setFileName(fileName)
                .setFileSize(fileSize)
                .setContentType(contentType)
                .setStorageMode(storageMode)
                .setStatus("UPLOADING")
                .setExpiresAt(LocalDateTime.now().plusHours(expireHours));
        session.setTenantId(library.getTenantId());
        session.setCreator(scope.getUserIdString());
        sessionMapper.insert(session);
    }

    /**
     * Creates a session for a publish-media upload, which is not attached to a material library.
     */
    public void createTiktok(String uploadId, Long companyId, String fileName, Long fileSize, String contentType) {
        createTiktok(uploadId, companyId, fileName, fileSize, contentType, "local");
    }

    public void createTiktok(String uploadId, Long companyId, String fileName, Long fileSize,
                             String contentType, String storageMode) {
        TkUserScope scope = dataScopeService.getCurrentScope();
        int expireHours = generationProperties.getUpload().getSessionExpireHours() == null
                ? 24 : Math.max(1, generationProperties.getUpload().getSessionExpireHours());
        TkUploadSessionDO session = new TkUploadSessionDO()
                .setUploadId(uploadId)
                .setCompanyId(companyId)
                .setLibraryId(null)
                .setFileName(fileName)
                .setFileSize(fileSize)
                .setContentType(contentType)
                .setStorageMode(storageMode)
                .setStatus("UPLOADING")
                .setExpiresAt(LocalDateTime.now().plusHours(expireHours));
        session.setTenantId(scope.getTenantId());
        session.setCreator(scope.getUserIdString());
        sessionMapper.insert(session);
    }

    public TkUploadSessionDO validateAccessible(String uploadId) {
        TkUploadSessionDO session = findAccessible(uploadId);
        if (session == null) {
            throw exception(TK_UPLOAD_SESSION_INVALID);
        }
        return session;
    }

    private TkUploadSessionDO findAccessible(String uploadId) {
        TkUploadSessionDO session = TenantUtils.executeIgnore(() -> sessionMapper.selectByUploadId(uploadId));
        if (session == null) {
            return null;
        }
        dataScopeService.validateReadable(session.getTenantId(), session.getCompanyId(), session.getCreator());
        if (!TkUploadSessionAccessPolicy.canAccess(session, LocalDateTime.now())) {
            return null;
        }
        return session;
    }

    public TkUploadSessionStatusRespVO getStatus(String uploadId) {
        TkUploadSessionDO session = validateAccessible(uploadId);
        TkUploadSessionStatusRespVO respVO = new TkUploadSessionStatusRespVO();
        respVO.setUploadId(uploadId);
        respVO.setFileSize(session.getFileSize());
        respVO.setUploadedSize(0L);
        respVO.setUploadedChunks(Collections.emptySet());
        respVO.setStatus(session.getStatus());
        return respVO;
    }

    public void markCompleted(String uploadId) {
        TkUploadSessionDO session = validateAccessible(uploadId);
        sessionMapper.updateById(new TkUploadSessionDO().setId(session.getId())
                .setStatus("COMPLETED").setCompletedTime(LocalDateTime.now()));
    }

    public void cancel(String uploadId) {
        TkUploadSessionDO session = findAccessible(uploadId);
        if (session == null) {
            return;
        }
        sessionMapper.updateById(new TkUploadSessionDO().setId(session.getId())
                .setStatus("CANCELLED").setCancelledTime(LocalDateTime.now()));
        if ("local".equalsIgnoreCase(session.getStorageMode())) {
            cn.hutool.core.io.FileUtil.del(localUploadStorageService.getTmpDir(uploadId).toFile());
        }
    }

    public int expireSessions() {
        return TenantUtils.executeIgnore(this::expireSessionsIgnoringTenant);
    }

    private int expireSessionsIgnoringTenant() {
        List<TkUploadSessionDO> expired = sessionMapper.selectExpired(LocalDateTime.now(), 200);
        for (TkUploadSessionDO session : expired) {
            sessionMapper.updateById(new TkUploadSessionDO().setId(session.getId()).setStatus("EXPIRED"));
            if ("local".equalsIgnoreCase(session.getStorageMode())) {
                cn.hutool.core.io.FileUtil.del(localUploadStorageService.getTmpDir(session.getUploadId()).toFile());
            } else if ("oss".equalsIgnoreCase(session.getStorageMode()) && session.getLibraryId() == null
                    && ossObjectStorageService != null && ossObjectStorageService.isConfigured()) {
                try {
                    ossObjectStorageService.deleteObject(ossObjectStorageService.buildTiktokObjectKey(session));
                } catch (Exception ex) {
                    log.warn("[expireSessions][failed to delete expired TikTok OSS object, uploadId({})]",
                            session.getUploadId(), ex);
                }
            }
        }
        return expired.size();
    }
}
