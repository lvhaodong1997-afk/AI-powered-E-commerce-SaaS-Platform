package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokPublishMediaDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkTiktokPublishMediaMapper;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import cn.iocoder.yudao.module.tk.service.upload.TkLocalUploadStorageService;
import cn.iocoder.yudao.module.tk.service.upload.TkOssObjectStorageService;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import java.util.Iterator;
import java.util.concurrent.Semaphore;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.tk.enums.ErrorCodeConstants.*;

@Service
@Validated
public class TkTiktokPublishMediaServiceImpl implements TkTiktokPublishMediaService {
    private static final long MAX_SIZE = 1_000_000_000L;
    private static final String[] EXTENSIONS = {"mp4", "mov", "webm"};
    @Resource private TkTiktokPublishMediaMapper mediaMapper;
    @Resource private TkLocalUploadStorageService storageService;
    @Resource private TkDataScopeService dataScopeService;
    @Resource private TkOssObjectStorageService ossObjectStorageService;
    @Resource private TkGenerationProperties generationProperties;
    private static final long MAX_PHOTO_SIZE = 20L * 1024 * 1024;
    private final Semaphore photoUploadSlot = new Semaphore(2);

    /** Browsers normalize JPEG/WebP to JPEG and resize before upload; revalidate the actual bytes here. */
    @Override
    public TkTiktokPublishMediaDO uploadPhoto(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_PHOTO_SIZE) {
            throw new IllegalArgumentException("请选择不超过 20MB 的图片");
        }
        String ext = FileUtil.extName(StrUtil.blankToDefault(file.getOriginalFilename(), "")).toLowerCase(Locale.ROOT);
        if (!Arrays.asList("jpg", "jpeg").contains(ext)) {
            throw new IllegalArgumentException("请通过图文上传入口将图片转换为 JPEG 后上传");
        }
        if (!photoUploadSlot.tryAcquire()) throw new IllegalArgumentException("图片上传繁忙，请稍后重试");
        Path temp = null;
        try {
            temp = Files.createTempFile("tk-photo-", ".jpg");
            try (InputStream input = file.getInputStream(); OutputStream output = Files.newOutputStream(temp)) {
                if (TkOssObjectStorageService.copyBounded(input, output, MAX_PHOTO_SIZE) != file.getSize()) {
                    throw new IllegalArgumentException("图片大小与上传信息不一致");
                }
            }
            validateJpeg(temp);
            TkUserScope scope = dataScopeService.getCurrentScope();
            Long companyId = dataScopeService.getWritableCompanyId(null);
            if (scope.getTenantId() == null || companyId == null) throw exception(TK_USER_SCOPE_NOT_CONFIGURED);
            TkOssObjectStorageService target = ossObjectStorageService.publishStorage();
            if (!target.isConfigured()) throw new IllegalStateException("图文发布需要先配置 OSS 和 TikTok 已验证的拉取域名");
            String prefix = StrUtil.removeSuffix(StrUtil.blankToDefault(ossObjectStorageService.publishConfig().getUploadPathPrefix(), "tk"), "/");
            String key = prefix + "/" + scope.getTenantId() + "/" + companyId + "/tiktok-photo/" + UUID.randomUUID() + ".jpg";
            target.validateOwnedKey(key, scope.getTenantId(), companyId);
            try {
                target.uploadFile(key, temp, MAX_PHOTO_SIZE, "image/jpeg");
                TkTiktokPublishMediaDO media = TkTiktokPublishMediaDO.builder().companyId(companyId)
                        .fileName(StrUtil.maxLength(file.getOriginalFilename(), 255))
                        .fileUrl(target.unsignedUrl(key)).fileSize(file.getSize())
                        .mimeType("image/jpeg").status("READY").build();
                media.setTenantId(scope.getTenantId());
                mediaMapper.insert(media);
                return media;
            } catch (RuntimeException ex) {
                try { target.deleteObject(key); } catch (RuntimeException cleanup) { ex.addSuppressed(cleanup); }
                throw ex;
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("图片文件无效或无法读取", ex);
        } finally {
            if (temp != null) try { Files.deleteIfExists(temp); } catch (IOException ignored) { }
            photoUploadSlot.release();
        }
    }

    private static void validateJpeg(Path path) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(path.toFile())) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("图片无法解码");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (!"JPEG".equalsIgnoreCase(reader.getFormatName()) || width < 1 || height < 1
                        || Math.max(width, height) > 1920 || Math.min(width, height) > 1080) {
                    throw new IllegalArgumentException("图片必须为 JPEG，分辨率不得超过 1080p");
                }
                if (reader.read(0) == null) throw new IllegalArgumentException("图片无法解码");
            } finally { reader.dispose(); }
        }
    }

    @Override
    public TkTiktokPublishMediaDO uploadVideo(MultipartFile file) {
        if (file == null || file.isEmpty()) throw exception(TK_UPLOAD_FILE_EMPTY);
        if (file.getSize() > MAX_SIZE) throw exception(TK_TIKTOK_PUBLISH_MEDIA_TOO_LARGE);
        String extension = FileUtil.extName(StrUtil.blankToDefault(file.getOriginalFilename(), "")).toLowerCase(Locale.ROOT);
        if (!Arrays.asList(EXTENSIONS).contains(extension)) throw exception(TK_UPLOAD_FILE_EXTENSION_INVALID);
        TkUserScope scope = dataScopeService.getCurrentScope();
        Long companyId = dataScopeService.getWritableCompanyId(null);
        if (scope.getTenantId() == null || companyId == null) throw exception(TK_USER_SCOPE_NOT_CONFIGURED);
        String name = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        String relative = StrUtil.format("tk/{}/{}/tiktok-publish-media/{}", scope.getTenantId(), companyId, name);
        String uploadedUrl = storageService.toPublicUrl(relative);
        String normalizedMime = TkPublishVideoMime.normalize(file.getOriginalFilename(), file.getContentType());
        try {
            Path target = storageService.resolveRelativePath(relative);
            Files.createDirectories(target.getParent());
            file.transferTo(target.toFile());
            if (ossObjectStorageService != null && ossObjectStorageService.isPublishEnabled()) {
                TkOssObjectStorageService publish = ossObjectStorageService.publishStorage();
                publish.uploadFile(relative, target, MAX_SIZE, normalizedMime);
                uploadedUrl = publish.unsignedUrl(relative); Files.deleteIfExists(target);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("保存 TikTok 发布视频失败：" + ex.getMessage(), ex);
        }
        TkTiktokPublishMediaDO media = TkTiktokPublishMediaDO.builder()
                .companyId(companyId).fileName(file.getOriginalFilename()).fileUrl(uploadedUrl)
                .fileSize(file.getSize()).mimeType(normalizedMime).status("READY").build();
        media.setTenantId(scope.getTenantId());
        mediaMapper.insert(media);
        return media;
    }
}
