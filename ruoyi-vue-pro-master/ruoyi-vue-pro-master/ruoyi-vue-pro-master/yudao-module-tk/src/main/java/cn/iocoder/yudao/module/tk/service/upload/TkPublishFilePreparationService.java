package cn.iocoder.yudao.module.tk.service.upload;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkPublishFileCopyDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokMediaDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkPublishFileCopyMapper;
import cn.iocoder.yudao.module.tk.service.upload.TkOssObjectStorageClient.ObjectMetadata;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Called in existing publish workers, after record authorization and before the init fence. */
@Service
public class TkPublishFilePreparationService {
    @Resource private TkOssObjectStorageService storage;
    @Resource private TkPublishFileCopyMapper mapper;
    @Resource private cn.iocoder.yudao.module.tk.service.open.tiktok.TkOpenTiktokMediaService mediaService;
    public boolean isEnabled() { return storage.isPublishEnabled(); }

    public String prepareOwned(String url, Long tenantId, Long companyId) {
        if (!isEnabled()) return url;
        TkOssObjectStorageService source = storage.storageForUrl(url);
        String key = source.requireOwnedObjectKey(url, tenantId, companyId);
        return prepare(source, key);
    }

    /** The media row must have been loaded by clientId; paths must additionally match that client. */
    public String prepareOpen(TkOpenTiktokMediaDO media, String clientId) {
        if (!isEnabled()) return media.getFileUrl();
        if (media == null || StrUtil.isBlank(clientId) || !clientId.equals(media.getClientId())
                || !"READY".equals(media.getStatus()))
            throw new IllegalArgumentException("发布文件必须是当前 API 客户端已校验的 OSS 文件");
        TkOssObjectStorageService source;
        try { source = storage.storageForUrl(media.getFileUrl()); }
        catch (IllegalArgumentException external) {
            if (!"REMOTE".equalsIgnoreCase(media.getUploadMode()) && !"LOCAL".equalsIgnoreCase(media.getUploadMode())) throw external;
            return prepareOpenStaged(media, clientId);
        }
        String key = source.objectKeyForUrl(media.getFileUrl());
        // Historical client-authorized records may have private object ACLs even in the public bucket.
        if (source.bucket().equals(storage.publishStorage().bucket())) return prepare(source, key);
        if (!"OSS".equalsIgnoreCase(media.getUploadMode()) || !key.startsWith("tk/open-api/" + clientId + "/") || !key.equals(media.getObjectKey()))
            throw new IllegalArgumentException("发布源文件不属于当前 API 客户端");
        return prepare(source, key);
    }

    private String prepareOpenStaged(TkOpenTiktokMediaDO media, String clientId) {
        if (StrUtil.isBlank(media.getMediaId()) || !clientId.matches("[A-Za-z0-9_-]+"))
            throw new IllegalArgumentException("发布文件缺少有效的 API 客户端或媒体编号");
        // Existing staging validates remote DNS/IP/redirects and caps bytes; it never grants private OSS access.
        mediaService.prepareForScheduledPublish(media);
        java.nio.file.Path path = java.nio.file.Paths.get(media.getScheduledLocalPath()).toAbsolutePath().normalize();
        try (java.io.InputStream input = java.nio.file.Files.newInputStream(path)) {
            String hash = DigestUtil.sha256Hex(input);
            String extension = cn.hutool.core.io.FileUtil.extName(media.getFileName()).toLowerCase(java.util.Locale.ROOT);
            if (!java.util.Arrays.asList("mp4", "mov", "webm").contains(extension)) throw new IllegalArgumentException("发布视频格式无效");
            String key = "tk/open-api/" + clientId + "/publish-copies/" + hash + "." + extension;
            TkOssObjectStorageService target = storage.publishStorage();
            target.uploadFile(key, path, 1_000_000_000L, "mov".equals(extension) ? "video/quicktime" : "video/" + extension);
            return target.unsignedUrl(key);
        } catch (java.io.IOException ex) { throw new IllegalStateException("发布远程文件准备失败，请重试", ex); }
    }

    private String prepare(TkOssObjectStorageService source, String key) {
        String extension = cn.hutool.core.io.FileUtil.extName(key).toLowerCase(java.util.Locale.ROOT);
        if (!java.util.Arrays.asList("mp4", "mov", "webm", "jpg", "jpeg", "webp").contains(extension))
            throw new IllegalArgumentException("只能准备视频或发布图片，内部附件禁止公开");
        TkOssObjectStorageService target = storage.publishStorage();
        if (source.bucket().equals(target.bucket()) && !source.hasPrivateObjectAcl(key)) return target.unsignedUrl(key);
        ObjectMetadata identity = source.headObject(key);
        if (identity.getContentLength() <= 0 || identity.getContentLength() > 4_000_000_000L
                || StrUtil.isBlank(identity.getEtag())) throw new IllegalArgumentException("发布源文件无效");
        String id = DigestUtil.sha256Hex(source.bucket() + "\n" + key + "\n" + identity.getEtag()
                + "\n" + StrUtil.blankToDefault(identity.getVersionId(), "") + "\n" + identity.getContentLength()
                + "\n" + target.bucket());
        // Keep the authorized scope path for downstream ownership and cleanup checks.
        String targetKey = key.substring(0, key.lastIndexOf('/') + 1) + "publish-copies/" + id + "." + extension;
        TkPublishFileCopyDO row = new TkPublishFileCopyDO(); row.setId(id); row.setSourceBucket(source.bucket());
        row.setSourceKey(key); row.setSourceEtag(identity.getEtag()); row.setSourceVersion(identity.getVersionId());
        row.setSourceSize(identity.getContentLength()); row.setTargetUrl(target.unsignedUrl(targetKey));
        mapper.insertIfMissing(row);
        long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(3);
        while (System.nanoTime() < deadline) {
            TkPublishFileCopyDO existing = mapper.find(id);
            if (existing != null && "READY".equals(existing.getStatus())) {
                try {
                    ObjectMetadata actual = target.headObject(targetKey);
                    if (actual.getContentLength() == identity.getContentLength()
                            && Objects.equals(existing.getTargetEtag(), actual.getEtag())) return row.getTargetUrl();
                } catch (RuntimeException ignored) { /* Expired/deleted copies are rebuilt on demand. */ }
                mapper.invalidate(id, existing.getTargetEtag());
            }
            String token = UUID.randomUUID().toString();
            if (mapper.claim(id, token, java.time.LocalDateTime.now().plusHours(2)) == 1) {
                try {
                    ObjectMetadata copied = target.copyFrom(source, key, targetKey, identity);
                    if (mapper.ready(id, token, copied.getEtag()) != 1)
                        throw new IllegalStateException("发布副本准备租约已改变，请重试");
                    return row.getTargetUrl();
                } catch (RuntimeException ex) {
                    mapper.failed(id, token);
                    throw new IllegalStateException("发布文件准备失败，请重试：" + ex.getMessage(), ex);
                }
            }
            try { Thread.sleep(200); } catch (InterruptedException ex) {
                Thread.currentThread().interrupt(); throw new IllegalStateException("发布文件准备已中断", ex);
            }
        }
        throw new IllegalStateException("发布文件仍在准备中，请稍后重试");
    }
}
