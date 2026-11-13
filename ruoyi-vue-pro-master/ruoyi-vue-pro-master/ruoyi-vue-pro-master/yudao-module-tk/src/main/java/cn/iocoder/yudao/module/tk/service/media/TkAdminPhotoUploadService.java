package cn.iocoder.yudao.module.tk.service.media;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.tk.dal.dataobject.*;
import cn.iocoder.yudao.module.tk.dal.mysql.*;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiIds;
import cn.iocoder.yudao.module.tk.service.scope.*;
import cn.iocoder.yudao.module.tk.service.upload.TkOssObjectStorageService;
import lombok.Data;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import javax.annotation.Resource;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.Objects;

/** Login-owned asynchronous uploads. Never accepts or emits OpenAPI media identifiers. */
@Service
public class TkAdminPhotoUploadService {
    @Resource private TkUploadSessionMapper sessions;
    @Resource private TkTiktokPublishMediaMapper media;
    @Resource private TkDataScopeService scopeService;
    @Resource private TkPhotoPrivateStorage files;
    @Resource private TkOssObjectStorageService oss;

    @Data public static class Status {
        private String uploadId;
        private String status;
        private String failReason;
        private Long mediaId;
        private String fileUrl;
        private Integer width;
        private Integer height;
    }

    public Status create(MultipartFile file) {
        if(file==null || file.isEmpty()) throw new IllegalArgumentException("请选择图片");
        String mime;
        try { mime=TkOpenPhotoMediaService.validateMetadata(file.getOriginalFilename(),file.getSize(),file.getContentType(),null); }
        catch (cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiException e) {
            throw new cn.iocoder.yudao.framework.common.exception.ServiceException(400,e.getMessage());
        }
        TkUserScope scope=scopeService.getCurrentScope();Long company=scopeService.getWritableCompanyId(null);
        if(scope.getTenantId()==null || company==null || scope.getUserId()==null) throw new IllegalArgumentException("请配置用户所属租户和公司");
        if(!oss.publishStorage().isConfigured()) throw new IllegalStateException("图文发布需要配置发布OSS");
        String id=TkOpenApiIds.next("photo");Path source=files.session(id).resolve("original.bin");
        try {
            files.createSession(id);
            try(InputStream in=file.getInputStream();OutputStream out=Files.newOutputStream(source,StandardOpenOption.CREATE_NEW)) {
                if(TkOssObjectStorageService.copyBounded(in,out,TkPhotoNormalizationService.MAX_BYTES)!=file.getSize()) throw new IOException("size mismatch");
            }
            // Quick byte signature rejection; decoding and conversion happen after the response.
            TkPhotoNormalizationService.detectFormat(source);
            TkUploadSessionDO s=new TkUploadSessionDO().setUploadId(id).setPurpose("PHOTO").setCompanyId(company).setLibraryId(null)
                    .setFileName(StrUtil.maxLength(file.getOriginalFilename(),255)).setFileSize(file.getSize()).setContentType(mime)
                    .setStorageMode("photo").setStatus("PROCESSING").setExpiresAt(LocalDateTime.now().plusHours(24))
                    .setSourceLocalPath(source.toString()).setSourceSha256(TkPhotoNormalizationService.sha256(source));
            s.setTenantId(scope.getTenantId());s.setCreator(scope.getUserIdString());sessions.insert(s);return response(s);
        } catch(Exception e) {
            try{Files.deleteIfExists(source);}catch(IOException ignored){}
            if(e instanceof RuntimeException) throw (RuntimeException)e;
            throw new IllegalArgumentException("图片上传失败",e);
        }
    }

    public Status status(String id) {return response(require(id,false));}

    @Transactional(rollbackFor=Exception.class)
    public void cancel(String id) {
        TkUploadSessionDO s=require(id,true);
        if("CANCELLED".equals(s.getStatus())) return;
        if("COMPLETED".equals(s.getStatus())) throw new IllegalArgumentException("图片已转换完成，请从发布列表移除");
        TenantUtils.executeIgnore(()->sessions.cancelPhoto(s.getId()));
    }

    @Transactional(rollbackFor=Exception.class)
    public Status retry(String id) {
        TkUploadSessionDO s=require(id,true);
        if(!"FAILED".equals(s.getStatus()) || s.getExpiresAt().isBefore(LocalDateTime.now())) throw new IllegalArgumentException("该图片无法重试，请重新上传");
        s.setStatus("PROCESSING");s.setFailReason(null);
        if(TenantUtils.executeIgnore(()->sessions.retryPhoto(s.getId()))!=1) throw new IllegalArgumentException("该图片会话已发生变化");return response(s);
    }

    private TkUploadSessionDO require(String id,boolean lock) {
        TkUploadSessionDO s=TenantUtils.executeIgnore(()->lock?sessions.selectByUploadIdForUpdate(id):sessions.selectByUploadId(id));
        TkUserScope scope=scopeService.getCurrentScope();
        if(s==null || !"PHOTO".equals(s.getPurpose()) || !Objects.equals(s.getTenantId(),scope.getTenantId())
                || !Objects.equals(s.getCreator(),scope.getUserIdString())) throw new cn.iocoder.yudao.framework.common.exception.ServiceException(400,"图片上传会话不存在");
        scopeService.validateReadable(s.getTenantId(),s.getCompanyId(),s.getCreator());return s;
    }
    private Status response(TkUploadSessionDO s) {
        Status r=new Status();r.setUploadId(s.getUploadId());r.setStatus("COMPLETED".equals(s.getStatus())?"READY":s.getStatus());
        r.setFailReason(s.getFailReason());r.setWidth(s.getPhotoWidth());r.setHeight(s.getPhotoHeight());
        if("COMPLETED".equals(s.getStatus()) && s.getResultMediaId()!=null) {
            TkTiktokPublishMediaDO m=TenantUtils.executeIgnore(()->media.selectById(s.getResultMediaId()));
            if(m==null || !Objects.equals(m.getTenantId(),s.getTenantId()) || !Objects.equals(m.getCreator(),s.getCreator())
                    || !Objects.equals(m.getCompanyId(),s.getCompanyId())) throw new IllegalStateException("图片转换结果无效");
            r.setMediaId(m.getId());r.setFileUrl(m.getFileUrl());
        }
        return r;
    }
}
