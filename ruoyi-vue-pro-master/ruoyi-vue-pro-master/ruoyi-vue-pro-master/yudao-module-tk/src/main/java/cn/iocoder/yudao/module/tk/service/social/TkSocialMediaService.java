package cn.iocoder.yudao.module.tk.service.social;

import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.tk.controller.admin.social.vo.TkSocialVideoUploadCompleteReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.social.vo.TkSocialVideoUploadSessionRespVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.social.TkSocialMediaDO;
import cn.iocoder.yudao.module.tk.dal.mysql.social.TkSocialMediaMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.social.TkSocialPublishTaskMapper;
import cn.iocoder.yudao.module.tk.dal.dataobject.social.TkSocialPublishTaskDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkUploadSessionDO;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.LocalDateTime;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;
import cn.iocoder.yudao.module.tk.service.generation.TkGenerationTaskService;
import cn.iocoder.yudao.module.tk.service.upload.TkGenerationOutputStorageService;
import cn.iocoder.yudao.module.tk.service.upload.TkOssObjectStorageService;
import cn.iocoder.yudao.module.tk.service.upload.TkOssPostPolicySigner;
import cn.iocoder.yudao.module.tk.service.upload.TkUploadSessionService;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import javax.annotation.Resource;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.*;
import java.net.URI;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.transaction.annotation.Transactional;
import cn.iocoder.yudao.module.tk.service.upload.TkOssObjectStorageClient.ObjectMetadata;

@Service
public class TkSocialMediaService {
    public static final long MAX_VIDEO_BYTES = 1L * 1024 * 1024 * 1024;
    private static final long MAX_IMAGE_BYTES = 8L * 1024 * 1024;
    private final FileApi files;
    private final TkSocialMediaMapper mapper;
    private final TkDataScopeService scope;
    // FileApi accepts bytes; bound simultaneous allocations for this integration.
    private final Semaphore uploadSlot = new Semaphore(1);
    @Resource private TkSocialVideoInspector videoInspector;
    @Resource private TkGenerationTaskService generationService;
    @Resource private TkGenerationOutputStorageService outputStorage;
    @Resource private TkSocialPublishTaskMapper tasks;
    @Resource private PlatformTransactionManager transactionManager;
    @Resource private TkGenerationProperties generationProperties;
    @Resource private TkOssObjectStorageService ossObjectStorageService;
    @Resource private TkUploadSessionService uploadSessionService;
    @Value("${tk.social.enabled:false}") private boolean enabled;

    public TkSocialMediaService(FileApi files, TkSocialMediaMapper mapper, TkDataScopeService scope) {
        this.files = files; this.mapper = mapper; this.scope = scope;
    }

    public TkSocialVideoUploadSessionRespVO createDirectVideoUpload(String fileName, Long fileSize, String contentType) {
        validateDirectVideoFile(fileName, fileSize);
        TkUserScope current = scope.getCurrentScope();
        Long companyId = scope.getWritableCompanyId(null);
        if (!current.hasTenantScope() || companyId == null) throw new IllegalArgumentException("请先选择租户");
        validateOssUploadConfig();
        String uploadId = UUID.randomUUID().toString().replace("-", "");
        String objectKey = socialObjectKey(current.getTenantId(), companyId, uploadId);
        TkOssPostPolicySigner.Policy policy = TkOssPostPolicySigner.signExact(
                generationProperties.getUpload().getOss().getAccessKeyId(),
                generationProperties.getUpload().getOss().getAccessKeySecret(), objectKey,
                MAX_VIDEO_BYTES, generationProperties.getUpload().getOss().getPolicyExpireSeconds(), Clock.systemUTC());
        uploadSessionService.createSocial(uploadId, companyId, fileName, fileSize,
                "video/mp4", "social-oss");
        TkSocialVideoUploadSessionRespVO response = new TkSocialVideoUploadSessionRespVO();
        response.setUploadId(uploadId);
        response.setUploadUrl(ossObjectStorageService.browserUploadUrl());
        response.setObjectKey(objectKey);
        response.setPublicUrl(ossObjectStorageService.publicUrlForObjectKey(objectKey));
        response.setAccessKeyId(policy.getAccessKeyId());
        response.setPolicy(policy.getPolicy());
        response.setSignature(policy.getSignature());
        response.setSuccessActionStatus("200");
        response.setExpiration(policy.getExpiration());
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public TkSocialMediaDO completeDirectVideoUpload(TkSocialVideoUploadCompleteReqVO request) {
        if (request == null) throw new IllegalArgumentException("上传信息不能为空");
        validateDirectVideoFile(request.getFileName(), request.getFileSize());
        TkUploadSessionDO session = uploadSessionService.lockSocialCompletion(request.getUploadId());
        TkUserScope current = scope.getCurrentScope();
        Long companyId = scope.getWritableCompanyId(null);
        if (!Objects.equals(session.getTenantId(), current.getTenantId())
                || !Objects.equals(session.getCompanyId(), companyId)
                || !Objects.equals(session.getCreator(), current.getUserIdString())
                || !"social-oss".equals(session.getStorageMode())
                || !Objects.equals(session.getFileName(), request.getFileName())
                || !Objects.equals(session.getFileSize(), request.getFileSize())) {
            throw new IllegalArgumentException("上传会话与文件信息不一致");
        }
        String expectedKey = socialObjectKey(session.getTenantId(), session.getCompanyId(), session.getUploadId());
        if (!expectedKey.equals(request.getObjectKey())) throw new IllegalArgumentException("OSS 上传对象无效");
        TkSocialMediaDO existing = mapper.selectOne(new QueryWrapper<TkSocialMediaDO>()
                .eq("tenant_id", session.getTenantId()).eq("upload_id", session.getUploadId()));
        if (existing != null) {
            if (!Objects.equals(existing.getCompanyId(), companyId) || !Objects.equals(existing.getCreator(), session.getCreator())
                    || !Objects.equals(existing.getObjectKey(), expectedKey)) throw new IllegalArgumentException("上传会话归属不一致");
            return existing;
        }
        if (!"UPLOADING".equals(session.getStatus())) throw new IllegalArgumentException("上传会话已结束");
        validateOssUploadConfig();
        TkOssObjectStorageService.ObjectMetadata metadata = ossObjectStorageService.headObject(expectedKey);
        if (metadata.getContentLength() != request.getFileSize()) throw new IllegalArgumentException("OSS 文件大小和上传记录不一致");
        if (metadata.getEtag() == null || metadata.getEtag().trim().isEmpty()) throw new IllegalArgumentException("OSS 文件标识缺失");
        TkSocialMediaDO media = new TkSocialMediaDO();
        media.setTenantId(session.getTenantId()); media.setCompanyId(session.getCompanyId()); media.setCreator(session.getCreator());
        media.setFileName(request.getFileName().substring(0, Math.min(255, request.getFileName().length())));
        media.setContentType("video/mp4"); media.setMediaType("VIDEO"); media.setFileSize(metadata.getContentLength());
        media.setObjectKey(expectedKey); media.setPublicUrl(ossObjectStorageService.publicUrlForObjectKey(expectedKey));
        media.setUploadId(session.getUploadId());
        media.setStatus("PROCESSING"); media.setMetadataStatus("PENDING");
        media.setSourceFileSize(metadata.getContentLength()); media.setSourceEtag(metadata.getEtag());
        media.setSourceVersionId(metadata.getVersionId()); media.setInspectionAttempts(0);
        media.setInspectionNextRetry(LocalDateTime.now()); media.setNormalized(false);
        mapper.insert(media);
        uploadSessionService.markCompleted(request.getUploadId());
        return media;
    }

    private void validateDirectVideoFile(String fileName, Long fileSize) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (!name.endsWith(".mp4")) throw new IllegalArgumentException("直传仅支持 MP4 视频");
        if (fileSize == null || fileSize <= 0) throw new IllegalArgumentException("文件大小必须大于 0");
        if (fileSize > MAX_VIDEO_BYTES) throw new IllegalArgumentException("视频不能超过 1GB");
    }

    public static void validateDirectVideoMetadata(Integer width, Integer height, Double durationSeconds, Double frameRate) {
        if (width == null || height == null || width < 1 || height < 1
                || durationSeconds == null || !Double.isFinite(durationSeconds) || durationSeconds <= 0
                || frameRate == null || !Double.isFinite(frameRate) || frameRate < 23 || frameRate > 60) {
            throw new IllegalArgumentException("视频元数据无效，请重新选择视频");
        }
    }

    private void validateOssUploadConfig() {
        TkGenerationProperties.Upload upload = generationProperties == null ? null : generationProperties.getUpload();
        TkGenerationProperties.Oss oss = upload == null ? null : upload.getOss();
        if (upload == null || !"oss".equalsIgnoreCase(upload.getStorageType()) || oss == null
                || !Boolean.TRUE.equals(oss.getEnabled()) || !ossObjectStorageService.isConfigured()) {
            throw new IllegalStateException("OSS 上传未配置，请先完成 OSS 配置");
        }
    }

    private String socialObjectKey(Long tenantId, Long companyId, String uploadId) {
        String prefix = generationProperties.getUpload().getOss().getUploadPathPrefix();
        prefix = prefix == null || prefix.trim().isEmpty() ? "tk" : prefix.replaceAll("/+$", "");
        return prefix + "/" + tenantId + "/" + companyId + "/social-media/" + uploadId + ".mp4";
    }

    public TkSocialMediaDO upload(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择图片或视频");
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        boolean video = name.toLowerCase(Locale.ROOT).endsWith(".mp4");
        if (!video && !name.toLowerCase(Locale.ROOT).matches(".*\\.jpe?g$")) throw new IllegalArgumentException("支持 MP4 视频和 JPEG 图片");
        long limit = video ? MAX_VIDEO_BYTES : MAX_IMAGE_BYTES;
        if (file.getSize() > limit) throw new IllegalArgumentException(video ? "视频不能超过 1GB" : "图片不能超过 8MB");
        if (!uploadSlot.tryAcquire()) throw new IllegalArgumentException("已有媒体正在上传，请稍后重试");
        try (InputStream input = file.getInputStream()) {
            if (video) {
                BufferedInputStream buffered = new BufferedInputStream(input);
                buffered.mark(12);
                byte[] header = new byte[12];
                if (buffered.read(header) != 12 || !"ftyp".equals(new String(header, 4, 4, StandardCharsets.US_ASCII)))
                    throw new IllegalArgumentException("文件不是有效的 MP4 视频");
                buffered.reset();
                return saveVideo(buffered, name, file.getSize());
            }
            return saveImage(readBounded(input, limit), name);
        } catch (IOException ex) {
            throw new IllegalStateException("读取上传文件失败");
        } finally { uploadSlot.release(); }
    }

    public TkSocialMediaDO importGenerated(Long taskId) {
        TkGenerationTaskDO task = generationService.getGenerationTask(taskId);
        if (task == null || !"SUCCESS".equals(task.getStatus()) || task.getOutputUrl() == null) {
            throw new IllegalArgumentException("请先完成视频生成");
        }
        scope.validateReadable(task.getTenantId(), task.getCompanyId(), task.getCreator());
        validateOssUploadConfig();
        String key = ossObjectStorageService.requireOwnedObjectKey(task.getOutputUrl(), task.getTenantId(), task.getCompanyId());
        if (!uploadSlot.tryAcquire()) throw new IllegalArgumentException("已有媒体正在上传，请稍后重试");
        try (TkSocialVideoInspector.Workspace workspace = videoInspector.openWorkspace()) {
            Path source = workspace.getDirectory().resolve("source.mp4");
            ObjectMetadata identity = ossObjectStorageService.headObject(key);
            ossObjectStorageService.downloadToFile(key, source, identity, MAX_VIDEO_BYTES);
            TkOssObjectStorageService.requireIdentity(identity, ossObjectStorageService.headObject(key));
            return saveVideoPath(source, "generation-" + taskId + ".mp4", workspace.getDirectory());
        } catch (IOException ex) {
            throw new IllegalStateException("读取生成视频失败，请稍后重试");
        } finally {
            uploadSlot.release();
        }
    }

    private TkSocialMediaDO saveVideo(InputStream input, String name, long expectedSize) throws IOException {
        try (TkSocialVideoInspector.Workspace workspace = videoInspector.openWorkspace()) {
            Path source = workspace.getDirectory().resolve("source.mp4");
            try (OutputStream output = Files.newOutputStream(source)) {
                if (TkOssObjectStorageService.copyBounded(input, output, MAX_VIDEO_BYTES) != expectedSize)
                    throw new IllegalArgumentException("上传文件大小不一致");
            }
            return saveVideoPath(source, name, workspace.getDirectory());
        }
    }

    private TkSocialMediaDO saveVideoPath(Path source, String name, Path workspace) throws IOException {
        try (InputStream input = Files.newInputStream(source)) {
            byte[] header = new byte[12];
            if (input.read(header) != 12 || !"ftyp".equals(new String(header, 4, 4, StandardCharsets.US_ASCII)))
                throw new IllegalArgumentException("文件不是有效的 MP4 视频");
        }
        validateOssUploadConfig();
        TkUserScope current = scope.getCurrentScope();
        Long companyId = scope.getWritableCompanyId(null);
        if (!current.hasTenantScope() || companyId == null) throw new IllegalArgumentException("请先选择租户");
        TkSocialMediaDO media = new TkSocialMediaDO();
        media.setTenantId(current.getTenantId()); media.setCompanyId(companyId); media.setCreator(current.getUserIdString());
        media.setFileName(name.substring(0, Math.min(255, name.length()))); media.setMediaType("VIDEO"); media.setContentType("video/mp4");
        media.setObjectKey(socialObjectKey(current.getTenantId(), companyId, UUID.randomUUID().toString().replace("-", "")));
        try {
            ObjectMetadata original = ossObjectStorageService.uploadFile(media.getObjectKey(), source, MAX_VIDEO_BYTES);
            media.setSourceFileSize(original.getContentLength()); media.setSourceEtag(original.getEtag()); media.setSourceVersionId(original.getVersionId());
            preparePublishCopy(media, source, workspace);
            media.setInspectionAttempts(1);
            mapper.insert(media);
            return media;
        } catch (RuntimeException ex) {
            deleteOwnedObjects(media); throw ex;
        }
    }

    /** Invoked by the lease worker under explicit tenant context and with a scoped DB record. */
    void inspectOwnedSource(TkSocialMediaDO media) {
        ossObjectStorageService.validateOwnedKey(media.getObjectKey(), media.getTenantId(), media.getCompanyId());
        try (TkSocialVideoInspector.Workspace workspace = videoInspector.openWorkspace()) {
            Path source = workspace.getDirectory().resolve("source.mp4");
            ObjectMetadata expected = sourceIdentity(media);
            TkOssObjectStorageService.requireIdentity(expected, ossObjectStorageService.headObject(media.getObjectKey()));
            ossObjectStorageService.downloadToFile(media.getObjectKey(), source, expected, MAX_VIDEO_BYTES);
            preparePublishCopy(media, source, workspace.getDirectory());
        }
    }

    private void preparePublishCopy(TkSocialMediaDO media, Path source, Path workspace) {
        Path publish = videoInspector.prepareForPublish(source, workspace.resolve("publish.mp4"));
        TkSocialVideoInspector.Metadata result = videoInspector.inspect(publish);
        TkOssObjectStorageService.requireIdentity(sourceIdentity(media), ossObjectStorageService.headObject(media.getObjectKey()));
        String key = socialObjectKey(media.getTenantId(), media.getCompanyId(), UUID.randomUUID().toString().replace("-", ""))
                .replace("/social-media/", "/social-media-publish/");
        // Always copy to a server-only key, even when audio is unchanged: browser POST policies cannot overwrite it.
        media.setPublishObjectKey(key);
        ObjectMetadata published = ossObjectStorageService.uploadFile(key, publish, MAX_VIDEO_BYTES);
        media.setPublishEtag(published.getEtag()); media.setPublishVersionId(published.getVersionId());
        media.setFileSize(published.getContentLength()); media.setNormalized(!source.equals(publish));
        media.setWidth(result.getWidth()); media.setHeight(result.getHeight());
        media.setDurationSeconds(result.getDurationSeconds()); media.setFrameRate(result.getFrameRate());
        media.setVideoCodec(result.getVideoCodec()); media.setAudioCodec(result.getAudioCodec());
        media.setVideoBitrate(result.getVideoBitrate()); media.setAudioBitrate(result.getAudioBitrate()); media.setAudioSampleRate(result.getAudioSampleRate());
        media.setPublicUrl(ossObjectStorageService.publicUrlForObjectKey(key)); media.setStatus("READY");
        media.setMetadataStatus("VERIFIED"); media.setMetadataSource("FFPROBE"); media.setMetadataError(null);
        media.setInspectedAt(LocalDateTime.now());
    }

    private ObjectMetadata sourceIdentity(TkSocialMediaDO media) {
        return new ObjectMetadata(media.getSourceFileSize() == null ? 0 : media.getSourceFileSize(), null, media.getSourceEtag(), media.getSourceVersionId());
    }
    void deletePublishCopy(TkSocialMediaDO media) {
        if (media.getPublishObjectKey() != null) {
            try { ossObjectStorageService.deleteObject(media.getPublishObjectKey()); } catch (RuntimeException ignored) { }
        }
    }
    private void deleteOwnedObjects(TkSocialMediaDO media) {
        deletePublishCopy(media);
        if (media.getObjectKey() != null) try { ossObjectStorageService.deleteObject(media.getObjectKey()); } catch (RuntimeException ignored) { }
    }

    private TkSocialMediaDO saveImage(byte[] bytes, String name) {
        int[] size = inspectJpeg(bytes);
        TkUserScope current = scope.getCurrentScope();
        Long companyId = scope.getWritableCompanyId(null);
        if (!current.hasTenantScope() || companyId == null) throw new IllegalArgumentException("请先选择租户");
        String type = "image/jpeg";
        String url = files.createFile(bytes, UUID.randomUUID() + ".jpg",
                "tk/" + current.getTenantId() + "/" + companyId + "/social-media", type);
        try {
            validateReadUrl(resolveReadableUrl(url));
            TkSocialMediaDO media = new TkSocialMediaDO();
            media.setTenantId(current.getTenantId()); media.setCompanyId(companyId); media.setCreator(current.getUserIdString());
            media.setFileName(name.substring(0, Math.min(255, name.length()))); media.setContentType(type);
            media.setMediaType("IMAGE"); media.setFileSize((long)bytes.length);
            media.setWidth(size[0]); media.setHeight(size[1]); media.setDurationSeconds(0D);
            media.setFrameRate(0D); media.setMetadataStatus("NOT_REQUIRED");
            media.setPublicUrl(url); media.setStatus("READY");
            mapper.insert(media);
            return media;
        } catch (RuntimeException ex) {
            try { files.deleteFileByUrl(url); } catch (RuntimeException cleanup) { ex.addSuppressed(cleanup); }
            throw ex;
        }
    }

    public TkSocialMediaDO requireReadable(Long id) {
        TkSocialMediaDO media = getReadable(id);
        if ("VIDEO".equals(media.getMediaType()) && !"VERIFIED".equals(media.getMetadataStatus()))
            throw new IllegalArgumentException("PROCESSING".equals(media.getStatus()) ? "视频检测中，请稍后重试"
                    : "视频尚未通过服务端检查；请重新上传后发布");
        if (!"READY".equals(media.getStatus())) throw new IllegalArgumentException("媒体尚未就绪");
        return media;
    }

    public TkSocialMediaDO getReadable(Long id) {
        TkSocialMediaDO media = mapper.selectById(id);
        if (media == null) throw new IllegalArgumentException("媒体不存在");
        scope.validateReadable(media.getTenantId(), media.getCompanyId(), media.getCreator());
        if ("DELETED".equals(media.getStatus()) || "DELETING".equals(media.getStatus())) throw new IllegalArgumentException("媒体已清理");
        return requeueHistorical(media);
    }

    private TkSocialMediaDO requeueHistorical(TkSocialMediaDO media) {
        if (!historical(media) || ossObjectStorageService == null || !ossObjectStorageService.isConfigured()) return media;
        String key;
        try {
            key = media.getObjectKey() == null ? ossObjectStorageService.requireOwnedObjectKey(media.getPublicUrl(), media.getTenantId(), media.getCompanyId())
                    : media.getObjectKey();
            if (key == null) return media;
            ossObjectStorageService.validateOwnedKey(key, media.getTenantId(), media.getCompanyId());
        } catch (IllegalArgumentException ex) { return media; }
        final String ownedKey = key;
        return new TransactionTemplate(transactionManager).execute(status -> {
            TkSocialMediaDO current = mapper.lockMedia(media.getId(), media.getTenantId());
            if (current == null || !historical(current) || !Objects.equals(current.getCompanyId(), media.getCompanyId())) return media;
            // Never change a media snapshot referenced by any existing publication.
            if (tasks.selectCount(new QueryWrapper<TkSocialPublishTaskDO>().eq("tenant_id", current.getTenantId()).eq("media_id", current.getId())) > 0)
                return current;
            ObjectMetadata identity;
            try {
                identity = ossObjectStorageService.headObject(ownedKey);
                if (identity.getContentLength() <= 0 || identity.getContentLength() > MAX_VIDEO_BYTES
                        || !Objects.equals(current.getFileSize(), identity.getContentLength())
                        || identity.getEtag() == null || identity.getEtag().isEmpty()) return current;
            } catch (RuntimeException ex) { return current; }
            LocalDateTime now = LocalDateTime.now();
            int queued = mapper.update(null, new UpdateWrapper<TkSocialMediaDO>()
                    .eq("id", current.getId()).eq("tenant_id", current.getTenantId()).eq("company_id", current.getCompanyId())
                    .eq("status", "READY").and(q -> q.isNull("metadata_status").or().eq("metadata_status", "UNVERIFIED"))
                    .notInSql("id", "SELECT media_id FROM tk_social_publish_task WHERE media_id IS NOT NULL")
                    .set("status", "PROCESSING").set("metadata_status", "PENDING").set("metadata_source", null).set("metadata_error", null)
                    .set("object_key", ownedKey).set("source_file_size", identity.getContentLength())
                    .set("source_etag", identity.getEtag()).set("source_version_id", identity.getVersionId())
                    .set("width", null).set("height", null).set("duration_seconds", null).set("frame_rate", null)
                    .set("video_codec", null).set("audio_codec", null).set("video_bitrate", null).set("audio_bitrate", null).set("audio_sample_rate", null)
                    .set("inspection_attempts", 0).set("inspection_next_retry", now).set("inspection_lease_token", null).set("inspection_lease_until", null));
            if (queued == 1) {
                current.setStatus("PROCESSING"); current.setMetadataStatus("PENDING"); current.setMetadataSource(null); current.setMetadataError(null);
                current.setObjectKey(ownedKey); current.setSourceFileSize(identity.getContentLength()); current.setSourceEtag(identity.getEtag());
                current.setSourceVersionId(identity.getVersionId()); current.setWidth(null); current.setHeight(null); current.setDurationSeconds(null);
                current.setFrameRate(null); current.setVideoCodec(null); current.setAudioCodec(null); current.setVideoBitrate(null);
                current.setAudioBitrate(null); current.setAudioSampleRate(null); current.setInspectionAttempts(0); current.setInspectionNextRetry(now);
            }
            return current;
        });
    }
    private static boolean historical(TkSocialMediaDO media) {
        return "VIDEO".equals(media.getMediaType()) && "READY".equals(media.getStatus())
                && (media.getMetadataStatus() == null || "UNVERIFIED".equals(media.getMetadataStatus()));
    }

    /** Must run in the task-creation transaction; serializes publication with cleanup. */
    public void lockReady(Long id,Long tenantId) {
        TkSocialMediaDO source=mapper.lockMedia(id,tenantId);
        if (source==null || !"READY".equals(source.getStatus())) throw new IllegalArgumentException("媒体已过期或清理中，请重新上传");
        if ("VIDEO".equals(source.getMediaType()) && !"VERIFIED".equals(source.getMetadataStatus()))
            throw new IllegalArgumentException("视频尚未通过服务端检查");
    }

    public void cleanupUnreferenced(Long id,Long tenantId) {
        TkSocialMediaDO candidate=new TransactionTemplate(transactionManager).execute(status -> {
            TkSocialMediaDO source=mapper.lockMedia(id,tenantId);
            if (source==null || !Arrays.asList("READY","FAILED","DELETING").contains(source.getStatus())) return null;
            if (tasks.selectCount(new QueryWrapper<TkSocialPublishTaskDO>().eq("tenant_id",tenantId).eq("media_id",id))>0) return null;
            mapper.update(null,new UpdateWrapper<TkSocialMediaDO>().eq("id",id).eq("tenant_id",tenantId).set("status","DELETING"));
            return source;
        });
        if (candidate==null) return;
        // Claim is committed before storage I/O. A crashed deletion is retried by the sweep.
        if (candidate.getObjectKey() != null && candidate.getSourceEtag() != null) {
            ossObjectStorageService.validateOwnedKey(candidate.getObjectKey(), candidate.getTenantId(), candidate.getCompanyId());
            if (candidate.getPublishObjectKey() != null) {
                ossObjectStorageService.validateOwnedKey(candidate.getPublishObjectKey(), candidate.getTenantId(), candidate.getCompanyId());
                ossObjectStorageService.deleteObject(candidate.getPublishObjectKey());
            }
            ossObjectStorageService.deleteObject(candidate.getObjectKey());
        } else files.deleteFileByUrl(candidate.getPublicUrl());
        mapper.update(null,new UpdateWrapper<TkSocialMediaDO>().eq("id",id).eq("tenant_id",tenantId).eq("status","DELETING")
                .set("status","DELETED").set("deleted",true));
    }

    @Scheduled(fixedDelay=3600000,initialDelay=120000)
    public void cleanupAbandonedMedia() {
        if (!enabled) return;
        List<TkSocialMediaDO> candidates=TenantUtils.executeIgnore(() -> mapper.selectList(
                new QueryWrapper<TkSocialMediaDO>().and(q -> q.eq("status","DELETING")
                        .or(s -> s.in("status","READY","FAILED").lt("create_time",LocalDateTime.now().minusHours(24))))
                        .notInSql("id","SELECT media_id FROM tk_social_publish_task WHERE media_id IS NOT NULL AND deleted=0")
                        .orderByAsc("id").last("LIMIT 20")));
        for (TkSocialMediaDO candidate:candidates) {
            try { TenantUtils.execute(candidate.getTenantId(),() -> cleanupUnreferenced(candidate.getId(),candidate.getTenantId())); }
            catch (RuntimeException ignored) { /* Keep the row for the next bounded sweep; never log signed URLs. */ }
        }
    }

    public String readUrl(TkSocialMediaDO media) {
        if (media == null) return null;
        if ("VERIFIED".equals(media.getMetadataStatus()) && media.getPublishObjectKey() != null) {
            ossObjectStorageService.validateOwnedKey(media.getPublishObjectKey(), media.getTenantId(), media.getCompanyId());
            TkOssObjectStorageService.requireIdentity(new ObjectMetadata(media.getFileSize(), null, media.getPublishEtag(), media.getPublishVersionId()),
                    ossObjectStorageService.headObject(media.getPublishObjectKey()));
            return ossObjectStorageService.publicUrlForObjectKey(media.getPublishObjectKey());
        }
        String url = resolveReadableUrl(media.getPublicUrl());
        validateReadUrl(url);
        return url;
    }

    public String originalUrl(TkSocialMediaDO media) {
        if (media.getObjectKey() == null) return readUrl(media);
        ossObjectStorageService.validateOwnedKey(media.getObjectKey(), media.getTenantId(), media.getCompanyId());
        return ossObjectStorageService.publicUrlForObjectKey(media.getObjectKey());
    }

    private String resolveReadableUrl(String url) {
        String ossUrl = ossObjectStorageService == null ? url : ossObjectStorageService.resolveReadUrl(url);
        if (!Objects.equals(ossUrl, url)) return ossUrl;
        try {
            String readableUrl = files.presignGetUrl(url, 86400);
            return readableUrl == null || readableUrl.trim().isEmpty() ? url : readableUrl;
        } catch (UnsupportedOperationException ex) {
            return url;
        }
    }

    public static void validateForPlatform(TkSocialMediaDO media,String platform) {
        if (media==null || !"VIDEO".equals(media.getMediaType())) return;
        Integer w=media.getWidth(),h=media.getHeight();
        Double seconds=media.getDurationSeconds(),fps=media.getFrameRate();
        if (w==null || h==null || seconds==null || fps==null || !Double.isFinite(seconds) || !Double.isFinite(fps)
                || w<1 || h<1 || fps<23 || fps>60) {
            throw new IllegalArgumentException("视频帧率需为 23–60fps，请重新上传符合要求的视频");
        }
        if ("FACEBOOK_PAGE".equals(platform)) {
            if (w<540 || h<960 || Math.abs((double)w/h-9.0/16)>0.002 || seconds<4 || seconds>60) {
                throw new IllegalArgumentException("Facebook Reels 视频需为 9:16 竖屏、至少 540×960、时长 4–60 秒");
            }
        } else if ("INSTAGRAM".equals(platform) && (w>1920 || seconds<3 || seconds>900)) {
            throw new IllegalArgumentException("Instagram 视频宽度不超过 1920 像素，时长 3 秒至 15 分钟");
        }
    }

    static byte[] readBounded(InputStream input, long limit) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[65536]; int count;
        while ((count = input.read(chunk)) != -1) {
            if ((long)buffer.size() + count > limit) throw new IllegalArgumentException("文件超过大小限制");
            buffer.write(chunk, 0, count);
        }
        return buffer.toByteArray();
    }

    static int[] inspectJpeg(byte[] bytes) {
        if (bytes.length < 3 || (bytes[0] & 255) != 255 || (bytes[1] & 255) != 216) throw new IllegalArgumentException("请上传有效的 JPEG 图片");
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("无法识别 JPEG 图片");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                int w = reader.getWidth(0), h = reader.getHeight(0);
                if ((long)w * h > 40_000_000 || w < 1 || h < 1 || (double)w / h < 0.8 || (double)w / h > 1.91) {
                    throw new IllegalArgumentException("图片宽高比需为 4:5 至 1.91:1，且不超过 4000 万像素");
                }
                reader.read(0);
                return new int[]{w,h};
            } finally { reader.dispose(); }
        } catch (IOException ex) { throw new IllegalArgumentException("无法解析 JPEG 图片"); }
    }

    public static void validateReadUrl(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || uri.getUserInfo() != null
                    || uri.getFragment() != null || host.equalsIgnoreCase("localhost") || host.endsWith(".local")
                    || host.contains(":") || host.matches("[0-9.]+")) {
                throw new IllegalArgumentException("媒体存储需要配置公网 HTTPS 域名");
            }
        } catch (NullPointerException ex) { throw new IllegalArgumentException("媒体存储地址未配置"); }
    }
}
