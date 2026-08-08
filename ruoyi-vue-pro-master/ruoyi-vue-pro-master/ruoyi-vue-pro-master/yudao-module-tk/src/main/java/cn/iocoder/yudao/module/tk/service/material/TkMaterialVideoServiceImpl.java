package cn.iocoder.yudao.module.tk.service.material;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.tk.controller.admin.material.vo.TkMaterialVideoPageReqVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkMaterialLibraryDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkMaterialVideoDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkMaterialLibraryMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.TkMaterialVideoMapper;
import cn.iocoder.yudao.module.tk.enums.TkMaterialSegmentTypeEnum;
import cn.iocoder.yudao.module.tk.enums.TkMaterialUsagePhaseEnum;
import cn.iocoder.yudao.module.tk.enums.TkMaterialVideoStatusEnum;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.upload.TkLocalUploadStorageService;
import cn.iocoder.yudao.module.tk.service.upload.TkMaterialOssUploadService;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.tk.enums.ErrorCodeConstants.*;

@Service
@Validated
public class TkMaterialVideoServiceImpl implements TkMaterialVideoService {

    private static final long MAX_SIZE = 100L * 1024 * 1024;
    private static final String[] ALLOWED_EXTENSIONS = {"mp4", "mov", "webm"};
    private static final byte[] WEBM_EBML_HEADER = new byte[]{0x1A, 0x45, (byte) 0xDF, (byte) 0xA3};

    @Resource
    private TkMaterialVideoMapper videoMapper;
    @Resource
    private TkMaterialLibraryMapper libraryMapper;
    @Resource
    private TkDataScopeService dataScopeService;
    @Resource
    private FileApi fileApi;
    @Resource
    private TkMaterialVideoParseService materialVideoParseService;
    @Resource
    private TkLocalUploadStorageService localUploadStorageService;
    @Resource
    private TkMaterialOssUploadService materialOssUploadService;

    @Override
    public Long uploadMaterialVideo(Long libraryId, MultipartFile file, String tags, String usagePhase, String segmentType) {
        TkMaterialLibraryDO library = libraryMapper.selectById(libraryId);
        if (library == null) {
            throw exception(TK_MATERIAL_LIBRARY_NOT_EXISTS);
        }
        dataScopeService.validateWritable(library.getTenantId(), library.getCompanyId());
        if (file.isEmpty()) {
            throw exception(TK_UPLOAD_FILE_EMPTY);
        }
        if (file.getSize() > MAX_SIZE) {
            throw exception(TK_UPLOAD_FILE_TOO_LARGE);
        }
        String originalFilename = file.getOriginalFilename() == null ? "video.mp4" : file.getOriginalFilename();
        String extension = StrUtil.blankToDefault(cn.hutool.core.io.FileUtil.extName(originalFilename), "").toLowerCase(Locale.ROOT);
        if (!Arrays.asList(ALLOWED_EXTENSIONS).contains(extension)) {
            throw exception(TK_UPLOAD_FILE_EXTENSION_INVALID);
        }

        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException ex) {
            throw exception(TK_UPLOAD_FILE_EMPTY);
        }
        if (!isValidVideoContainer(fileBytes, extension)) {
            throw exception(TK_UPLOAD_FILE_INVALID);
        }

        String fileUrl;
        try {
            String directory = StrUtil.format("tk/{}/{}/material-videos", library.getTenantId(), library.getCompanyId());
            fileUrl = fileApi.createFile(fileBytes, originalFilename, directory, file.getContentType());
        } catch (Exception ex) {
            throw exception(TK_UPLOAD_FILE_EMPTY);
        }

        TkMaterialUsagePhaseEnum normalizedPhase = TkMaterialUsagePhaseEnum.normalize(usagePhase);
        TkMaterialSegmentTypeEnum normalizedSegment = TkMaterialSegmentTypeEnum.normalize(segmentType);

        TkMaterialVideoDO video = TkMaterialVideoDO.builder()
                .companyId(library.getCompanyId())
                .libraryId(libraryId)
                .fileName(originalFilename)
                .fileUrl(fileUrl)
                .size(file.getSize())
                .format(extension)
                .tags(tags)
                .usagePhase(normalizedPhase.getCode())
                .segmentType(normalizedSegment.getCode())
                .status(TkMaterialVideoStatusEnum.PARSING)
                .build();
        video.setTenantId(library.getTenantId());
        videoMapper.insert(video);

        libraryMapper.updateById(new TkMaterialLibraryDO()
                .setId(libraryId)
                .setVideoCount((library.getVideoCount() == null ? 0 : library.getVideoCount()) + 1)
                .setTotalSize((library.getTotalSize() == null ? 0L : library.getTotalSize()) + file.getSize()));
        materialVideoParseService.submit(library.getTenantId(), video.getId());
        return video.getId();
    }

    private boolean isValidVideoContainer(byte[] content, String extension) {
        if (content == null || content.length < 16) {
            return false;
        }
        if ("webm".equals(extension)) {
            return startsWith(content, WEBM_EBML_HEADER);
        }
        return hasMp4Atom(content, "ftyp") && hasMp4Atom(content, "moov");
    }

    private boolean startsWith(byte[] content, byte[] prefix) {
        if (content.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (content[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean hasMp4Atom(byte[] content, String atomType) {
        byte[] marker = atomType.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        for (int i = 4; i <= content.length - marker.length; i++) {
            if (content[i] == marker[0]
                    && content[i + 1] == marker[1]
                    && content[i + 2] == marker[2]
                    && content[i + 3] == marker[3]) {
                return true;
            }
        }
        return false;
    }

    @Override
    public PageResult<TkMaterialVideoDO> getMaterialVideoPage(TkMaterialVideoPageReqVO pageReqVO) {
        if (pageReqVO.getLibraryId() != null) {
            TkMaterialLibraryDO library = libraryMapper.selectById(pageReqVO.getLibraryId());
            if (library == null) {
                throw exception(TK_MATERIAL_LIBRARY_NOT_EXISTS);
            }
            dataScopeService.validateReadable(library.getTenantId(), library.getCompanyId(), null);
        }
        return videoMapper.selectPage(pageReqVO, dataScopeService.getCurrentScope());
    }

    @Override
    public TkMaterialVideoDO getMaterialVideo(Long id) {
        TkMaterialVideoDO video = videoMapper.selectById(id);
        if (video == null) {
            throw exception(TK_MATERIAL_VIDEO_NOT_EXISTS);
        }
        dataScopeService.validateReadable(video.getTenantId(), video.getCompanyId(), null);
        return video;
    }

    @Override
    public Map<String, Long> getSegmentSummary(Long libraryId) {
        TkMaterialLibraryDO library = libraryMapper.selectById(libraryId);
        if (library == null) {
            throw exception(TK_MATERIAL_LIBRARY_NOT_EXISTS);
        }
        dataScopeService.validateReadable(library.getTenantId(), library.getCompanyId(), null);
        Map<String, Long> summary = new HashMap<>();
        for (TkMaterialVideoDO video : videoMapper.selectListByLibraryId(libraryId)) {
            String segmentType = TkMaterialSegmentTypeEnum.normalize(video.getSegmentType()).getCode();
            summary.put(segmentType, summary.getOrDefault(segmentType, 0L) + 1);
        }
        return summary;
    }

    @Override
    public void updateUsagePhase(List<Long> ids, String usagePhase) {
        TkMaterialUsagePhaseEnum normalizedPhase = TkMaterialUsagePhaseEnum.normalize(usagePhase);
        for (Long id : ids) {
            TkMaterialVideoDO video = videoMapper.selectById(id);
            if (video == null) {
                throw exception(TK_MATERIAL_VIDEO_NOT_EXISTS);
            }
            dataScopeService.validateWritable(video.getTenantId(), video.getCompanyId());
            videoMapper.updateById(TkMaterialVideoDO.builder()
                    .id(id)
                    .usagePhase(normalizedPhase.getCode())
                    .build());
        }
    }

    @Override
    public void updateSegmentType(List<Long> ids, String segmentType) {
        TkMaterialSegmentTypeEnum normalizedSegment = TkMaterialSegmentTypeEnum.normalize(segmentType);
        for (Long id : ids) {
            TkMaterialVideoDO video = videoMapper.selectById(id);
            if (video == null) {
                throw exception(TK_MATERIAL_VIDEO_NOT_EXISTS);
            }
            dataScopeService.validateWritable(video.getTenantId(), video.getCompanyId());
            videoMapper.updateById(TkMaterialVideoDO.builder()
                    .id(id)
                    .segmentType(normalizedSegment.getCode())
                    .usagePhase(toUsagePhase(normalizedSegment).getCode())
                    .build());
        }
    }

    private TkMaterialUsagePhaseEnum toUsagePhase(TkMaterialSegmentTypeEnum segmentType) {
        if (segmentType == TkMaterialSegmentTypeEnum.S1_HOOK || segmentType == TkMaterialSegmentTypeEnum.S2_PAIN) {
            return TkMaterialUsagePhaseEnum.ATTENTION;
        }
        if (segmentType == TkMaterialSegmentTypeEnum.S5_PROOF) {
            return TkMaterialUsagePhaseEnum.RESULT_EFFECT;
        }
        return TkMaterialUsagePhaseEnum.PRODUCT_SHOW;
    }

    @Override
    public void deleteMaterialVideo(Long id) {
        TkMaterialVideoDO video = videoMapper.selectById(id);
        if (video == null) {
            throw exception(TK_MATERIAL_VIDEO_NOT_EXISTS);
        }
        dataScopeService.validateWritable(video.getTenantId(), video.getCompanyId());
        deleteFileIfPresent(video.getFileUrl());
        deleteFileIfPresent(video.getCoverUrl());
        videoMapper.deleteById(id);
        TkMaterialLibraryDO library = libraryMapper.selectById(video.getLibraryId());
        if (library != null) {
            libraryMapper.updateById(new TkMaterialLibraryDO()
                    .setId(video.getLibraryId())
                    .setVideoCount(Math.max((library.getVideoCount() == null ? 0 : library.getVideoCount()) - 1, 0))
                    .setTotalSize(Math.max((library.getTotalSize() == null ? 0L : library.getTotalSize()) - (video.getSize() == null ? 0L : video.getSize()), 0L)));
        }
    }

    private void deleteFileIfPresent(String url) {
        if (StrUtil.isBlank(url)) {
            return;
        }
        if (materialOssUploadService != null && materialOssUploadService.isEnabled()
                && materialOssUploadService.isManagedUrl(url)) {
            try {
                materialOssUploadService.deleteByUrl(url);
            } catch (RuntimeException ignored) {
                // OSS cleanup must not block deleting the material record.
            }
            return;
        }
        deleteLocalUploadFileIfPresent(url);
        fileApi.deleteFileByUrl(url);
    }

    private void deleteLocalUploadFileIfPresent(String url) {
        if (localUploadStorageService == null) {
            return;
        }
        Optional<Path> localPath = localUploadStorageService.resolveLocalPath(url);
        if (!localPath.isPresent()) {
            return;
        }
        try {
            Files.deleteIfExists(localPath.get());
        } catch (IOException ex) {
            throw new IllegalStateException("删除素材文件失败：" + localPath.get(), ex);
        }
    }

}
