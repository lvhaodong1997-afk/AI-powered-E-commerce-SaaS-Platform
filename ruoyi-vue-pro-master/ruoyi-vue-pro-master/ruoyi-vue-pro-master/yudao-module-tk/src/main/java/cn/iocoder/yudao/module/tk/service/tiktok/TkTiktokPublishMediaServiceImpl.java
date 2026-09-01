package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokPublishMediaDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkTiktokPublishMediaMapper;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import cn.iocoder.yudao.module.tk.service.upload.TkLocalUploadStorageService;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

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
        try {
            Path target = storageService.resolveRelativePath(relative);
            Files.createDirectories(target.getParent());
            file.transferTo(target.toFile());
        } catch (Exception ex) {
            throw new IllegalStateException("保存 TikTok 发布视频失败：" + ex.getMessage(), ex);
        }
        TkTiktokPublishMediaDO media = TkTiktokPublishMediaDO.builder()
                .companyId(companyId).fileName(file.getOriginalFilename()).fileUrl(storageService.toPublicUrl(relative))
                .fileSize(file.getSize()).mimeType(file.getContentType()).status("READY").build();
        media.setTenantId(scope.getTenantId());
        mediaMapper.insert(media);
        return media;
    }
}
