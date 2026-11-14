package cn.iocoder.yudao.module.tk.service.media;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkUploadSessionDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokMediaDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkUploadSessionMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenTiktokMediaMapper;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiIds;
import cn.iocoder.yudao.module.tk.service.upload.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import javax.annotation.PreDestroy;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

/** The database is the queue. A bounded executor only runs already leased work, never owns its state. */
@Component @Slf4j
public class TkPhotoConversionWorker {
    private final TkOpenTiktokMediaMapper open;
    private final TkUploadSessionMapper sessions;
    private final TkOssObjectStorageService oss;
    private final TkPhotoNormalizationService codec;
    private final TkPhotoPrivateStorage files;
    private final TkPhotoConversionStore store;
    private final ThreadPoolExecutor executor=new ThreadPoolExecutor(2,2,30,TimeUnit.SECONDS,new SynchronousQueue<>(),r->{
        Thread t=new Thread(r,"tk-photo-conversion");t.setDaemon(true);return t;
    },new ThreadPoolExecutor.AbortPolicy());
    private final ScheduledExecutorService heartbeat=Executors.newSingleThreadScheduledExecutor(r->{
        Thread t=new Thread(r,"tk-photo-lease");t.setDaemon(true);return t;
    });
    private boolean adminFirst;

    public TkPhotoConversionWorker(TkOpenTiktokMediaMapper open,TkUploadSessionMapper sessions,TkOssObjectStorageService oss,
                                   TkPhotoNormalizationService codec,TkPhotoPrivateStorage files,TkPhotoConversionStore store) {
        this.open=open;this.sessions=sessions;this.oss=oss;this.codec=codec;this.files=files;this.store=store;
    }

    @Scheduled(fixedDelayString="${tk.photo.poll-delay-ms:3000}",initialDelayString="${tk.photo.initial-delay-ms:15000}")
    public synchronized void poll() {
        if(executor.isShutdown() || executor.getActiveCount()>=2)return;
        TenantUtils.executeIgnore(()->{
            try {adminFirst=!adminFirst;if(adminFirst){queueAdmin();queueOpen();}else{queueOpen();queueAdmin();}}
            catch(Exception ex){log.warn("[photoConversion][poll failed]",ex);}
        });
    }
    private void queueOpen() {
        for(TkOpenTiktokMediaDO m:open.selectPhotoConversionCandidates()) {
            if(executor.getActiveCount()>=2)return;
            String token=token();if(open.claimPhoto(m.getId(),token)!=1)continue;
            try{executor.execute(()->TenantUtils.executeIgnore(()->processOpen(m,token)));}
            catch(RejectedExecutionException e){open.releasePhotoClaim(m.getId(),token);return;}
        }
    }
    private void queueAdmin() {
        for(TkUploadSessionDO s:sessions.selectPhotoConversionCandidates()) {
            if(executor.getActiveCount()>=2)return;
            String token=token();if(sessions.claimPhoto(s.getId(),token)!=1)continue;
            try{executor.execute(()->TenantUtils.executeIgnore(()->processAdmin(s,token)));}
            catch(RejectedExecutionException e){sessions.releasePhotoClaim(s.getId(),token);return;}
        }
    }

    void processOpen(TkOpenTiktokMediaDO m,String token) {
        Path work=files.session(m.getUploadId()).resolve("work-"+token);String key=null;boolean committed=false;
        ScheduledFuture<?> lease=null;
        TkOssObjectStorageService target=oss.publishStorage();
        try {
            lease=renewLease(()->open.renewPhotoLease(m.getId(),token));
            cleanupPreviousOpen(m,token,target);
            files.createSession(m.getUploadId());Files.createDirectories(work);Path source=work.resolve("source.bin"),result=work.resolve("final.jpg");
            String sourceHash=copyOriginal(m.getUploadId(),m.getClientId(),m.getSourceLocalPath(),m.getSourceObjectKey(),m.getSourceFileSize(),
                    m.getSourceSha256(),m.getSourceEtag(),m.getSourceVersionId(),target,source);
            TkPhotoNormalizationService.Result normalized=codec.normalize(source,result);
            key=openKey(m,token);target.uploadFile(key,result,TkPhotoNormalizationService.MAX_BYTES,"image/jpeg");
            m.setMediaId(TkOpenApiIds.next("media")).setObjectKey(key).setFileUrl(target.unsignedUrl(key)).setMediaType("PHOTO")
                    .setUploadMode("OSS").setFileName(m.getUploadId()+".jpg").setFileSize(normalized.getBytes()).setContentType("image/jpeg")
                    .setSha256(normalized.getSha256()).setSourceSha256(sourceHash).setWidth(normalized.getWidth()).setHeight(normalized.getHeight());
            committed=open.completePhoto(m,token)==1;
        }catch(Exception ex){
            if(!Thread.currentThread().isInterrupted())open.failPhoto(m.getId(),token,reason(ex));
        }finally{
            if(lease!=null)lease.cancel(false);
            if(!committed && key!=null)try{target.deleteObject(key);}catch(Exception ex){log.warn("[photoConversion][orphan output cleanup failed, id({})]",m.getUploadId());}
            try{files.deleteTree(work);}catch(Exception ignored){}
        }
    }

    private void processAdmin(TkUploadSessionDO s,String token) {
        Path work=files.session(s.getUploadId()).resolve("work-"+token);String key=null;boolean committed=false;
        ScheduledFuture<?> lease=null;
        TkOssObjectStorageService target=oss.publishStorage();
        try {
            lease=renewLease(()->sessions.renewPhotoLease(s.getId(),token));
            if(StrUtil.isNotBlank(s.getConversionClaimToken()) && !token.equals(s.getConversionClaimToken()))
                target.deleteObject(adminKey(s,s.getConversionClaimToken()));
            files.createSession(s.getUploadId());Files.createDirectories(work);Path source=work.resolve("source.bin"),result=work.resolve("final.jpg");
            copyOriginal(s.getUploadId(),null,s.getSourceLocalPath(),s.getSourceObjectKey(),s.getFileSize(),s.getSourceSha256(),s.getSourceEtag(),s.getSourceVersionId(),target,source);
            TkPhotoNormalizationService.Result normalized=codec.normalize(source,result);
            key=adminKey(s,token);target.validateOwnedKey(key,s.getTenantId(),s.getCompanyId());
            target.uploadFile(key,result,TkPhotoNormalizationService.MAX_BYTES,"image/jpeg");
            store.finishAdmin(s,token,target.unsignedUrl(key),normalized);committed=true;
        }catch(TkPhotoConversionStore.StaleClaimException ignored){
            // Cancellation/expiry won. The transaction rolled back the new admin media.
        }catch(Exception ex){if(!Thread.currentThread().isInterrupted())sessions.failPhoto(s.getId(),token,reason(ex));}
        finally {
            if(lease!=null)lease.cancel(false);
            if(!committed && key!=null)try{target.deleteObject(key);}catch(Exception ex){log.warn("[photoConversion][orphan output cleanup failed, id({})]",s.getUploadId());}
            try{files.deleteTree(work);}catch(Exception ignored){}
        }
    }

    private String copyOriginal(String uploadId,String clientId,String local,String objectKey,Long bytes,String expectedSha,String etag,String version,
                                TkOssObjectStorageService storage,Path destination) throws Exception {
        if(bytes==null || bytes<1 || bytes>TkPhotoNormalizationService.MAX_BYTES)throw new IllegalArgumentException("原图片大小无效");
        if(StrUtil.isNotBlank(local)) {
            Path source=files.requireOwned(local);
            if(!source.equals(files.session(uploadId).resolve("original.bin")))throw new IllegalArgumentException("原图片路径与会话不符");
            if(Files.size(source)!=bytes)throw new IllegalArgumentException("原图片大小已改变");
            try(InputStream in=Files.newInputStream(source);OutputStream out=Files.newOutputStream(destination)) {
                if(TkOssObjectStorageService.copyBounded(in,out,TkPhotoNormalizationService.MAX_BYTES)!=bytes)throw new IllegalArgumentException("原图片大小已改变");
            }
        }else{
            String prefix="tk/open-api/"+clientId+"/photo-original/";
            if(clientId==null || objectKey==null || !objectKey.startsWith(prefix) || !storage.hasPrivateObjectAcl(objectKey))
                throw new IllegalArgumentException("原图片归属或私有权限无效");
            TkOssObjectStorageClient.ObjectMetadata expected=new TkOssObjectStorageClient.ObjectMetadata(bytes,null,etag,version);
            TkOssObjectStorageService.requireIdentity(expected,storage.headObject(objectKey));
            storage.downloadToFile(objectKey,destination,expected,TkPhotoNormalizationService.MAX_BYTES);
        }
        String actual=TkPhotoNormalizationService.sha256(destination);
        if(StrUtil.isNotBlank(expectedSha) && !expectedSha.equalsIgnoreCase(actual))throw new IllegalArgumentException("原图片SHA256已改变");
        return actual;
    }

    @Scheduled(fixedDelayString="${tk.photo.cleanup-delay-ms:60000}",initialDelayString="${tk.photo.cleanup-initial-delay-ms:60000}")
    public void cleanupExpiredOriginals() {
        TenantUtils.executeIgnore(()->{
            for(TkOpenTiktokMediaDO m:open.selectExpiredPhotoSources())try {
                if(open.expirePhoto(m.getId())!=1)continue;
                if(m.getSourceObjectKey()!=null) {
                    if(!m.getSourceObjectKey().startsWith("tk/open-api/"+m.getClientId()+"/photo-original/"))throw new IllegalArgumentException("invalid source scope");
                    oss.publishStorage().deleteObject(m.getSourceObjectKey());
                }
                files.deleteTree(files.session(m.getUploadId()));
                open.updateById(new TkOpenTiktokMediaDO().setId(m.getId()).setSourceCleanedAt(LocalDateTime.now()));
            }catch(Exception ex){log.warn("[photoConversion][original cleanup failed, id({})]",m.getUploadId());}
            for(TkUploadSessionDO s:sessions.selectExpiredPhotoSources())try {
                if(sessions.expirePhoto(s.getId())!=1)continue;
                files.deleteTree(files.session(s.getUploadId()));
                sessions.updateById(new TkUploadSessionDO().setId(s.getId()).setSourceCleanedAt(LocalDateTime.now()));
            }catch(Exception ex){log.warn("[photoConversion][original cleanup failed, id({})]",s.getUploadId());}
        });
    }
    private void cleanupPreviousOpen(TkOpenTiktokMediaDO m,String token,TkOssObjectStorageService target) {
        if(StrUtil.isNotBlank(m.getConversionClaimToken()) && !token.equals(m.getConversionClaimToken()))target.deleteObject(openKey(m,m.getConversionClaimToken()));
    }
    private String openKey(TkOpenTiktokMediaDO m,String token) {
        if(m.getClientId()==null || !m.getClientId().matches("[A-Za-z0-9_-]{1,64}"))throw new IllegalArgumentException("invalid client scope");
        validateToken(token);return "tk/open-api/"+m.getClientId()+"/photo-final/"+m.getUploadId()+"-"+token+".jpg";
    }
    private String adminKey(TkUploadSessionDO s,String token) {
        validateToken(token);if(s.getTenantId()==null || s.getCompanyId()==null)throw new IllegalArgumentException("invalid admin scope");
        String prefix=StrUtil.removeSuffix(StrUtil.blankToDefault(oss.publishConfig().getUploadPathPrefix(),"tk"),"/");
        return prefix+"/"+s.getTenantId()+"/"+s.getCompanyId()+"/tiktok-photo/"+s.getUploadId()+"-"+token+".jpg";
    }
    private static String token(){return UUID.randomUUID().toString().replace("-","");}
    private ScheduledFuture<?> renewLease(Runnable renew) {
        return heartbeat.scheduleAtFixedRate(()->{
            try{TenantUtils.executeIgnore(renew);}catch(Exception ex){log.warn("[photoConversion][lease heartbeat failed]");}
        },30,30,TimeUnit.SECONDS);
    }
    private static void validateToken(String token){if(token==null || !token.matches("[0-9a-f]{32}"))throw new IllegalArgumentException("invalid conversion token");}
    private static String reason(Exception e){return StrUtil.maxLength(StrUtil.blankToDefault(e.getMessage(),"图片转换失败，请重新上传"),1000);}
    @PreDestroy public void stop(){executor.shutdownNow();heartbeat.shutdownNow();}
}
