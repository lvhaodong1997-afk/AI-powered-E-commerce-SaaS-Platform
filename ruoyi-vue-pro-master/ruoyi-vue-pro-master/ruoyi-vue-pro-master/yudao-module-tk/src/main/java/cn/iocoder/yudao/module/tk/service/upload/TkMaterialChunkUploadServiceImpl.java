package cn.iocoder.yudao.module.tk.service.upload;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.tk.controller.admin.upload.vo.TkUploadSessionRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.upload.vo.TkUploadSessionStatusRespVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkMaterialLibraryDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkMaterialVideoDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkMaterialLibraryMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.TkMaterialVideoMapper;
import cn.iocoder.yudao.module.tk.enums.TkMaterialSegmentTypeEnum;
import cn.iocoder.yudao.module.tk.enums.TkMaterialUsagePhaseEnum;
import cn.iocoder.yudao.module.tk.enums.TkMaterialVideoStatusEnum;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import cn.iocoder.yudao.module.tk.service.material.TkMaterialVideoParseService;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.tk.enums.ErrorCodeConstants.*;

@Service
@Validated
public class TkMaterialChunkUploadServiceImpl implements TkMaterialChunkUploadService {

    private static final String[] ALLOWED_EXTENSIONS = {"mp4", "mov", "webm"};
    private static final byte[] WEBM_EBML_HEADER = new byte[]{0x1A, 0x45, (byte) 0xDF, (byte) 0xA3};

    @Resource
    private TkMaterialLibraryMapper libraryMapper;
    @Resource
    private TkMaterialVideoMapper videoMapper;
    @Resource
    private TkDataScopeService dataScopeService;
    @Resource
    private TkMaterialVideoParseService materialVideoParseService;
    @Resource
    private TkLocalUploadStorageService storageService;
    @Resource
    private TkGenerationProperties generationProperties;
    @Resource
    private TkUploadSessionService uploadSessionService;

    @Override
    public TkUploadSessionRespVO createMaterialVideoSession(Long libraryId, String fileName, Long fileSize, String contentType) {
        TkMaterialLibraryDO library = validateLibraryWritable(libraryId);
        validateFileBasics(fileName, fileSize);
        String uploadId = UUID.randomUUID().toString().replace("-", "");
        int chunkSize = getChunkSize();
        int totalChunks = (int) Math.ceil(fileSize * 1.0D / chunkSize);
        Path tmpDir = storageService.getTmpDir(uploadId);
        try {
            Files.createDirectories(tmpDir);
            Properties manifest = new Properties();
            manifest.setProperty("uploadId", uploadId);
            manifest.setProperty("libraryId", String.valueOf(libraryId));
            manifest.setProperty("tenantId", String.valueOf(library.getTenantId()));
            manifest.setProperty("companyId", String.valueOf(library.getCompanyId()));
            manifest.setProperty("fileName", fileName);
            manifest.setProperty("fileSize", String.valueOf(fileSize));
            manifest.setProperty("contentType", StrUtil.blankToDefault(contentType, "video/" + extension(fileName)));
            manifest.setProperty("chunkSize", String.valueOf(chunkSize));
            manifest.setProperty("totalChunks", String.valueOf(totalChunks));
            manifest.setProperty("status", "UPLOADING");
            try (OutputStream outputStream = Files.newOutputStream(manifestPath(tmpDir))) {
                manifest.store(outputStream, "TK upload session");
            }
        } catch (IOException ex) {
            throw new IllegalStateException("创建上传会话失败：" + ex.getMessage(), ex);
        }
        if (uploadSessionService != null) {
            uploadSessionService.create(uploadId, library, fileName, fileSize,
                    StrUtil.blankToDefault(contentType, "video/" + extension(fileName)), "local");
        }
        return toSessionResp(uploadId, chunkSize, totalChunks, 0L, Collections.emptySet());
    }

    @Override
    public TkUploadSessionStatusRespVO getSessionStatus(String uploadId) {
        if (uploadSessionService != null) {
            uploadSessionService.validateAccessible(uploadId);
        }
        Properties manifest = readManifest(uploadId);
        Set<Integer> chunks = uploadedChunks(uploadId, Integer.parseInt(manifest.getProperty("totalChunks")));
        TkUploadSessionStatusRespVO respVO = new TkUploadSessionStatusRespVO();
        respVO.setUploadId(uploadId);
        respVO.setChunkSize(Integer.parseInt(manifest.getProperty("chunkSize")));
        respVO.setTotalChunks(Integer.parseInt(manifest.getProperty("totalChunks")));
        respVO.setFileSize(Long.parseLong(manifest.getProperty("fileSize")));
        respVO.setUploadedChunks(chunks);
        respVO.setUploadedSize(uploadedSize(uploadId, chunks));
        respVO.setStatus(manifest.getProperty("status", "UPLOADING"));
        return respVO;
    }

    @Override
    public void uploadChunk(String uploadId, Integer chunkIndex, MultipartFile chunk) {
        if (chunk == null || chunk.isEmpty()) {
            throw exception(TK_UPLOAD_FILE_EMPTY);
        }
        if (uploadSessionService != null) {
            uploadSessionService.validateAccessible(uploadId);
        }
        Properties manifest = readManifest(uploadId);
        int totalChunks = Integer.parseInt(manifest.getProperty("totalChunks"));
        if (chunkIndex == null || chunkIndex < 0 || chunkIndex >= totalChunks) {
            throw new IllegalArgumentException("分片序号无效");
        }
        Path tmpDir = storageService.getTmpDir(uploadId);
        try {
            Files.createDirectories(tmpDir);
            Files.copy(chunk.getInputStream(), chunkPath(tmpDir, chunkIndex), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new IllegalStateException("保存上传分片失败：" + ex.getMessage(), ex);
        }
    }

    @Override
    public Long completeMaterialVideoUpload(String uploadId, String tags, String usagePhase, String segmentType) {
        if (uploadSessionService != null) {
            uploadSessionService.validateAccessible(uploadId);
        }
        Properties manifest = readManifest(uploadId);
        Long libraryId = Long.valueOf(manifest.getProperty("libraryId"));
        TkMaterialLibraryDO library = validateLibraryWritable(libraryId);
        long fileSize = Long.parseLong(manifest.getProperty("fileSize"));
        String originalFilename = manifest.getProperty("fileName");
        String ext = extension(originalFilename);
        Path tmpDir = storageService.getTmpDir(uploadId);
        Set<Integer> chunks = uploadedChunks(uploadId, Integer.parseInt(manifest.getProperty("totalChunks")));
        if (chunks.size() != Integer.parseInt(manifest.getProperty("totalChunks"))) {
            throw new IllegalStateException("上传分片不完整");
        }
        String relativePath = StrUtil.format("tk/{}/{}/material-videos/{}-{}",
                library.getTenantId(), library.getCompanyId(), uploadId, safeName(originalFilename));
        Path finalPath = storageService.resolveRelativePath(relativePath);
        try {
            Files.createDirectories(finalPath.getParent());
            mergeChunks(tmpDir, chunks, finalPath);
            if (Files.size(finalPath) != fileSize) {
                Files.deleteIfExists(finalPath);
                throw new IllegalStateException("合并后文件大小不一致");
            }
            if (!isValidVideoContainer(finalPath, ext)) {
                Files.deleteIfExists(finalPath);
                throw exception(TK_UPLOAD_FILE_INVALID);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("合并上传文件失败：" + ex.getMessage(), ex);
        }

        TkMaterialUsagePhaseEnum normalizedPhase = TkMaterialUsagePhaseEnum.normalize(usagePhase);
        TkMaterialSegmentTypeEnum normalizedSegment = TkMaterialSegmentTypeEnum.normalize(segmentType);
        TkMaterialVideoDO video = TkMaterialVideoDO.builder()
                .companyId(library.getCompanyId())
                .libraryId(libraryId)
                .fileName(originalFilename)
                .fileUrl(storageService.toPublicUrl(relativePath))
                .size(fileSize)
                .format(ext)
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
                .setTotalSize((library.getTotalSize() == null ? 0L : library.getTotalSize()) + fileSize));
        materialVideoParseService.submit(library.getTenantId(), video.getId());
        FileUtil.del(tmpDir.toFile());
        if (uploadSessionService != null) {
            uploadSessionService.markCompleted(uploadId);
        }
        return video.getId();
    }

    @Override
    public void cancel(String uploadId) {
        if (uploadSessionService != null) {
            uploadSessionService.cancel(uploadId);
            return;
        }
        FileUtil.del(storageService.getTmpDir(uploadId).toFile());
    }

    TkLocalUploadStorageService getStorageService() {
        return storageService;
    }

    private TkMaterialLibraryDO validateLibraryWritable(Long libraryId) {
        TkMaterialLibraryDO library = libraryMapper.selectById(libraryId);
        if (library == null) {
            throw exception(TK_MATERIAL_LIBRARY_NOT_EXISTS);
        }
        dataScopeService.validateWritable(library.getTenantId(), library.getCompanyId());
        return library;
    }

    private void validateFileBasics(String fileName, Long fileSize) {
        if (fileSize == null || fileSize <= 0) {
            throw exception(TK_UPLOAD_FILE_EMPTY);
        }
        if (fileSize > getMaxFileSize()) {
            throw exception(TK_UPLOAD_FILE_TOO_LARGE);
        }
        if (!Arrays.asList(ALLOWED_EXTENSIONS).contains(extension(fileName))) {
            throw exception(TK_UPLOAD_FILE_EXTENSION_INVALID);
        }
    }

    private int getChunkSize() {
        Integer chunkSize = generationProperties.getUpload().getChunkSizeBytes();
        return chunkSize == null || chunkSize <= 0 ? 8 * 1024 * 1024 : chunkSize;
    }

    private long getMaxFileSize() {
        Long maxFileSize = generationProperties.getUpload().getMaxFileSizeBytes();
        return maxFileSize == null || maxFileSize <= 0 ? 100L * 1024 * 1024 : maxFileSize;
    }

    private Properties readManifest(String uploadId) {
        Path manifestPath = manifestPath(storageService.getTmpDir(uploadId));
        if (!Files.isRegularFile(manifestPath)) {
            throw new IllegalArgumentException("上传会话不存在");
        }
        Properties manifest = new Properties();
        try (InputStream inputStream = Files.newInputStream(manifestPath)) {
            manifest.load(inputStream);
            return manifest;
        } catch (IOException ex) {
            throw new IllegalStateException("读取上传会话失败：" + ex.getMessage(), ex);
        }
    }

    private Path manifestPath(Path tmpDir) {
        return tmpDir.resolve("manifest.properties");
    }

    private Path chunkPath(Path tmpDir, int index) {
        return tmpDir.resolve(index + ".part");
    }

    private Set<Integer> uploadedChunks(String uploadId, int totalChunks) {
        Path tmpDir = storageService.getTmpDir(uploadId);
        Set<Integer> chunks = new TreeSet<>();
        for (int i = 0; i < totalChunks; i++) {
            if (Files.isRegularFile(chunkPath(tmpDir, i))) {
                chunks.add(i);
            }
        }
        return chunks;
    }

    private long uploadedSize(String uploadId, Set<Integer> chunks) {
        Path tmpDir = storageService.getTmpDir(uploadId);
        long total = 0;
        for (Integer chunk : chunks) {
            try {
                total += Files.size(chunkPath(tmpDir, chunk));
            } catch (IOException ignored) {
                // Ignore transient reads; status is advisory.
            }
        }
        return total;
    }

    private TkUploadSessionRespVO toSessionResp(String uploadId, Integer chunkSize, Integer totalChunks,
                                                Long uploadedSize, Set<Integer> uploadedChunks) {
        TkUploadSessionRespVO respVO = new TkUploadSessionRespVO();
        respVO.setUploadId(uploadId);
        respVO.setUploadMode("local");
        respVO.setChunkSize(chunkSize);
        respVO.setTotalChunks(totalChunks);
        respVO.setUploadedSize(uploadedSize);
        respVO.setUploadedChunks(uploadedChunks);
        return respVO;
    }

    private void mergeChunks(Path tmpDir, Set<Integer> chunks, Path target) throws IOException {
        try (OutputStream outputStream = Files.newOutputStream(target)) {
            for (Integer chunk : chunks) {
                Files.copy(chunkPath(tmpDir, chunk), outputStream);
            }
        }
    }

    private boolean isValidVideoContainer(Path path, String extension) throws IOException {
        if ("webm".equals(extension)) {
            byte[] header = new byte[WEBM_EBML_HEADER.length];
            try (InputStream inputStream = Files.newInputStream(path)) {
                int read = inputStream.read(header);
                return read == WEBM_EBML_HEADER.length && Arrays.equals(header, WEBM_EBML_HEADER);
            }
        }
        return containsMarker(path, "ftyp") && containsMarker(path, "moov");
    }

    private boolean containsMarker(Path path, String markerText) throws IOException {
        byte[] marker = markerText.getBytes(StandardCharsets.US_ASCII);
        byte[] buffer = new byte[8192];
        byte[] overlap = new byte[marker.length - 1];
        int overlapLength = 0;
        try (InputStream inputStream = Files.newInputStream(path)) {
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                byte[] candidate = new byte[overlapLength + read];
                System.arraycopy(overlap, 0, candidate, 0, overlapLength);
                System.arraycopy(buffer, 0, candidate, overlapLength, read);
                if (indexOf(candidate, marker) >= 0) {
                    return true;
                }
                overlapLength = Math.min(overlap.length, candidate.length);
                System.arraycopy(candidate, candidate.length - overlapLength, overlap, 0, overlapLength);
            }
        }
        return false;
    }

    private int indexOf(byte[] content, byte[] marker) {
        for (int i = 0; i <= content.length - marker.length; i++) {
            boolean matched = true;
            for (int j = 0; j < marker.length; j++) {
                if (content[i + j] != marker[j]) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                return i;
            }
        }
        return -1;
    }

    private String extension(String fileName) {
        return StrUtil.blankToDefault(FileUtil.extName(StrUtil.blankToDefault(fileName, "")), "")
                .toLowerCase(Locale.ROOT);
    }

    private String safeName(String fileName) {
        String name = StrUtil.blankToDefault(fileName, "video.mp4");
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

}
