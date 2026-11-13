package cn.iocoder.yudao.module.tk.service.media;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.tk.controller.open.tiktok.vo.TkOpenTiktokMediaVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokMediaDO;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenTiktokMediaMapper;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import cn.iocoder.yudao.module.tk.framework.openapi.*;
import cn.iocoder.yudao.module.tk.service.upload.*;
import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** PHOTO-specific upload branch. The existing video implementation keeps its original contract. */
@Service
public class TkOpenPhotoMediaService {
    public static final int CHUNK_SIZE=1024*1024;
    private final TkOpenTiktokMediaMapper mapper;
    private final TkGenerationProperties properties;
    private final TkOssObjectStorageService oss;
    private final TkPhotoPrivateStorage files;
    public TkOpenPhotoMediaService(TkOpenTiktokMediaMapper mapper,TkGenerationProperties properties,
                                  TkOssObjectStorageService oss,TkPhotoPrivateStorage files) {
        this.mapper=mapper;this.properties=properties;this.oss=oss;this.files=files;
    }
    public TkOpenTiktokMediaVO.UploadSessionResp create(String name,Long size,String mime,String sha) {
        String normalizedMime=validateMetadata(name,size,mime,sha);
        String client=TkOpenApiContext.getRequiredPrincipal().getClientId();
        if(!client.matches("[A-Za-z0-9_-]{1,64}")) throw TkOpenApiException.badRequest("MEDIA_FILE_INVALID","client scope is invalid");
        String id=TkOpenApiIds.next("upload");
        TkGenerationProperties.Oss config=publishConfig();
        if(!oss.publishStorage().isConfigured()) throw TkOpenApiException.unavailable("OSS_CONFIG_REQUIRED","photo upload OSS is not configured");
        boolean useOss=Boolean.TRUE.equals(config.getEnabled());
        TkOpenTiktokMediaDO m=TkOpenTiktokMediaDO.builder().uploadId(id).clientId(client).mediaType("PHOTO")
                .uploadMode(useOss?"OSS":"LOCAL").sourceFileName(name).sourceFileSize(size).sourceContentType(normalizedMime)
                .sourceSha256(StrUtil.isBlank(sha)?null:sha.toLowerCase(Locale.ROOT)).fileName(name).fileSize(size).contentType(normalizedMime)
                .uploadedSize(0L).uploadedChunks("[]").status("UPLOADING")
                .expireTime(LocalDateTime.now().plusHours(Math.max(1,properties.getUpload().getSessionExpireHours()==null?24:properties.getUpload().getSessionExpireHours()))).build();
        TkOpenTiktokMediaVO.UploadSessionResp r=new TkOpenTiktokMediaVO.UploadSessionResp();
        r.setUploadId(id);r.setUploadMode(m.getUploadMode());r.setMediaType("PHOTO");r.setStatus("UPLOADING");r.setExpireTime(m.getExpireTime());
        if(useOss) {
            String key="tk/open-api/"+client+"/photo-original/"+id+"."+extension(name);
            m.setSourceObjectKey(key);
            TkOssPostPolicySigner.Policy p=TkOssPostPolicySigner.signPrivatePhoto(config.getAccessKeyId(),config.getAccessKeySecret(),key,size,
                    config.getPolicyExpireSeconds()==null?900:config.getPolicyExpireSeconds(),normalizedMime,m.getSourceSha256(),Clock.systemUTC());
            r.setObjectKey(key);r.setUploadUrl(oss.publishStorage().browserUploadUrl());
            TkOpenTiktokMediaVO.OssFields f=new TkOpenTiktokMediaVO.OssFields(p.getPolicy(),p.getAccessKeyId(),p.getSignature(),m.getSourceSha256());
            f.setXOssObjectAcl("private");f.setContentType(normalizedMime);r.setFields(f);
        } else {m.setSourceLocalPath(files.session(id).resolve("original.bin").toString());r.setChunkSize(CHUNK_SIZE);r.setTotalChunks(total(size));}
        mapper.insert(m);return r;
    }

    public void uploadChunk(TkOpenTiktokMediaDO m,Integer index,byte[] bytes) {
        if(!"UPLOADING".equals(m.getStatus()) || !"LOCAL".equals(m.getUploadMode()))
            throw TkOpenApiException.badRequest("MEDIA_UPLOAD_STATUS_INVALID","session is not accepting local chunks");
        int count=total(m.getSourceFileSize());
        long expected=index==null || index<0 || index>=count?-1:(index==count-1?m.getSourceFileSize()-(long)index*CHUNK_SIZE:CHUNK_SIZE);
        if(bytes==null || bytes.length!=expected) throw TkOpenApiException.badRequest("MEDIA_FILE_INVALID","chunk size or index is invalid");
        try {
            Path folder=files.createSession(m.getUploadId()).resolve("chunks");Files.createDirectories(folder);
            Files.write(folder.resolve(index+".part"),bytes);
            long uploaded=0;List<Integer> complete=new ArrayList<>();
            for(int i=0;i<count;i++){Path p=folder.resolve(i+".part");if(Files.isRegularFile(p)){uploaded+=Files.size(p);complete.add(i);}}
            mapper.updateById(new TkOpenTiktokMediaDO().setId(m.getId()).setUploadedSize(uploaded)
                    .setUploadedChunks(cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(complete)));
        }catch(IOException ex){throw TkOpenApiException.unavailable("MEDIA_UPLOAD_FAILED","cannot store private image chunk");}
    }

    public TkOpenTiktokMediaVO.MediaResp complete(TkOpenTiktokMediaDO m,Long size,String sha) {
        if(Arrays.asList("PROCESSING","READY").contains(m.getStatus())) return response(m);
        if(!Arrays.asList("UPLOADING","FAILED").contains(m.getStatus()))
            throw TkOpenApiException.badRequest("MEDIA_UPLOAD_STATUS_INVALID","session cannot be completed");
        boolean omittedRetrySha="FAILED".equals(m.getStatus()) && StrUtil.isBlank(sha);
        if(size==null || !size.equals(m.getSourceFileSize()) || (!omittedRetrySha && StrUtil.isNotBlank(m.getSourceSha256()) && !m.getSourceSha256().equalsIgnoreCase(StrUtil.blankToDefault(sha,""))))
            throw TkOpenApiException.badRequest("MEDIA_FILE_INVALID","original metadata does not match");
        if(m.getExpireTime().isBefore(LocalDateTime.now())) throw TkOpenApiException.badRequest("MEDIA_UPLOAD_EXPIRED","session has expired");
        if("OSS".equals(m.getUploadMode())) {
            TkOssObjectStorageService storage=oss.publishStorage();
            TkOssObjectStorageClient.ObjectMetadata meta=storage.headObject(m.getSourceObjectKey());
            if(meta.getContentLength()!=size || StrUtil.isBlank(meta.getEtag()) || !storage.hasPrivateObjectAcl(m.getSourceObjectKey())
                    || !m.getSourceContentType().equalsIgnoreCase(StrUtil.blankToDefault(meta.getContentType(),""))
                    || (StrUtil.isNotBlank(m.getSourceSha256()) && !m.getSourceSha256().equalsIgnoreCase(meta.getSha256())))
                throw TkOpenApiException.badRequest("MEDIA_FILE_INVALID","original must be private and match declared metadata");
            if(StrUtil.isNotBlank(m.getSourceEtag())) {
                TkOssObjectStorageService.requireIdentity(new TkOssObjectStorageClient.ObjectMetadata(size,null,m.getSourceEtag(),m.getSourceVersionId()),meta);
            }
            m.setSourceEtag(meta.getEtag()).setSourceVersionId(meta.getVersionId());
        } else {
            Path source=files.session(m.getUploadId()).resolve("original.bin");
            try {
                files.createSession(m.getUploadId());
                if(!Files.isRegularFile(source)) {
                    Path staging=source.resolveSibling("original-merging.bin");
                    try(OutputStream out=Files.newOutputStream(staging)) {
                        for(int i=0;i<total(size);i++) {
                            Path chunk=source.getParent().resolve("chunks").resolve(i+".part");
                            if(!Files.isRegularFile(chunk)) throw new IOException("chunks incomplete");
                            Files.copy(chunk,out);
                        }
                    }
                    Files.move(staging,source,StandardCopyOption.REPLACE_EXISTING);
                }
                String actual=TkPhotoNormalizationService.sha256(source);
                if(Files.size(source)!=size || (StrUtil.isNotBlank(m.getSourceSha256()) && !m.getSourceSha256().equalsIgnoreCase(actual)))
                    throw new IOException("original size/hash mismatch");
                m.setSourceSha256(actual).setSourceLocalPath(source.toString());
            }catch(Exception e){throw TkOpenApiException.badRequest("MEDIA_FILE_INVALID","local photo chunks are incomplete or invalid");}
        }
        m.setStatus("PROCESSING").setUploadedSize(size).setFailReason(null); mapper.updateById(m);mapper.resetPhotoClaim(m.getId());return response(m);
    }

    public void cancel(TkOpenTiktokMediaDO m) {
        if("CANCELLED".equals(m.getStatus())) return;
        if(!Arrays.asList("UPLOADING","PROCESSING","FAILED").contains(m.getStatus()))
            throw TkOpenApiException.badRequest("MEDIA_UPLOAD_STATUS_INVALID","completed photo cannot be cancelled");
        mapper.cancelPhoto(m.getId());
    }

    public static TkOpenTiktokMediaVO.MediaResp response(TkOpenTiktokMediaDO m) {
        TkOpenTiktokMediaVO.MediaResp r=new TkOpenTiktokMediaVO.MediaResp();r.setMediaId(m.getMediaId());r.setUploadId(m.getUploadId());
        r.setFileName(m.getFileName());r.setFileSize(m.getFileSize());r.setContentType(m.getContentType());r.setStatus(m.getStatus());
        r.setMediaType("PHOTO");r.setFailReason(m.getFailReason());r.setWidth(m.getWidth());r.setHeight(m.getHeight());return r;
    }
    public static String validateMetadata(String name,Long size,String mime,String sha) {
        String ext=extension(name);
        String expected=ext.equals("jpg") || ext.equals("jpeg")?"image/jpeg":ext.equals("heif")?"image/heif":ext.equals("heic")?"image/heic":"image/"+ext;
        if(size==null || size<1 || size>TkPhotoNormalizationService.MAX_BYTES) throw TkOpenApiException.badRequest("MEDIA_FILE_INVALID","photo size must be 1 byte to 20MiB");
        if(StrUtil.isNotBlank(mime) && !"application/octet-stream".equalsIgnoreCase(mime) && !expected.equalsIgnoreCase(mime))
            throw TkOpenApiException.badRequest("MEDIA_FILE_INVALID","photo MIME type does not match extension");
        if(StrUtil.isNotBlank(sha) && !sha.matches("(?i)[0-9a-f]{64}")) throw TkOpenApiException.badRequest("MEDIA_FILE_INVALID","sha256 must contain 64 hex characters");
        return expected;
    }
    private static String extension(String name) {
        String ext=name==null?"":name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT);
        if(name==null || name.length()>512 || !Arrays.asList("jpg","jpeg","png","gif","webp","heic","heif").contains(ext))
            throw TkOpenApiException.badRequest("MEDIA_FILE_INVALID","supported photo types: JPEG WebP PNG GIF HEIC HEIF");
        return ext;
    }
    private int total(long size){return (int)((size+CHUNK_SIZE-1)/CHUNK_SIZE);}
    private TkGenerationProperties.Oss publishConfig() {
        TkGenerationProperties.Oss p=properties.getUpload().getPublishOss();
        if(Boolean.TRUE.equals(p.getEnabled())){TkOssObjectStorageService.validatePublishConfig(p,properties.getUpload().getOss());return p;}
        return properties.getUpload().getOss();
    }
}
