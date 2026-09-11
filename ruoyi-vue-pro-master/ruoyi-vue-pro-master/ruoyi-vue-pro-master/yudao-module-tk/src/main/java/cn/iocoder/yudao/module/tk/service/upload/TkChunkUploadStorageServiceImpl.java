package cn.iocoder.yudao.module.tk.service.upload;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.tk.enums.ErrorCodeConstants.TK_UPLOAD_FILE_EMPTY;
import static cn.iocoder.yudao.module.tk.enums.ErrorCodeConstants.TK_UPLOAD_FILE_INVALID;

@Service
public class TkChunkUploadStorageServiceImpl implements TkChunkUploadStorageService {

    private static final String[] ALLOWED_EXTENSIONS = {"mp4", "mov", "webm"};
    private static final byte[] WEBM_EBML_HEADER = new byte[]{0x1A, 0x45, (byte) 0xDF, (byte) 0xA3};

    @Resource
    private TkLocalUploadStorageService storageService;
    @Resource
    private TkGenerationProperties generationProperties;

    @Override
    public void createManifest(String uploadId, String fileName, Long fileSize, String contentType,
                               Map<String, String> metadata) {
        validateUploadId(uploadId);
        validateFileBasics(fileName, fileSize);
        int chunkSize = getChunkSize();
        int totalChunks = (int) Math.ceil(fileSize * 1.0D / chunkSize);
        Path tmpDir = storageService.getTmpDir(uploadId);
        Properties manifest = new Properties();
        manifest.setProperty("uploadId", uploadId);
        manifest.setProperty("fileName", fileName);
        manifest.setProperty("fileSize", String.valueOf(fileSize));
        manifest.setProperty("contentType", StrUtil.blankToDefault(contentType,
                "video/" + extension(fileName)));
        manifest.setProperty("chunkSize", String.valueOf(chunkSize));
        manifest.setProperty("totalChunks", String.valueOf(totalChunks));
        manifest.setProperty("status", "UPLOADING");
        if (metadata != null) {
            metadata.forEach((key, value) -> {
                if (StrUtil.isNotBlank(key) && value != null) {
                    manifest.setProperty(key, value);
                }
            });
        }
        try {
            Files.createDirectories(tmpDir);
            try (OutputStream outputStream = Files.newOutputStream(manifestPath(tmpDir))) {
                manifest.store(outputStream, "TK chunk upload session");
            }
        } catch (IOException ex) {
            throw new IllegalStateException("创建上传会话失败：" + ex.getMessage(), ex);
        }
    }

    @Override
    public Properties readManifest(String uploadId) {
        validateUploadId(uploadId);
        Path manifestFile = manifestPath(storageService.getTmpDir(uploadId));
        if (!Files.isRegularFile(manifestFile)) {
            throw new IllegalArgumentException("上传会话不存在");
        }
        Properties manifest = new Properties();
        try (InputStream inputStream = Files.newInputStream(manifestFile)) {
            manifest.load(inputStream);
            return manifest;
        } catch (IOException ex) {
            throw new IllegalStateException("读取上传会话失败：" + ex.getMessage(), ex);
        }
    }

    @Override
    public Set<Integer> uploadedChunks(String uploadId, int totalChunks) {
        validateUploadId(uploadId);
        Set<Integer> chunks = new TreeSet<>();
        Path tmpDir = storageService.getTmpDir(uploadId);
        for (int i = 0; i < totalChunks; i++) {
            if (Files.isRegularFile(chunkPath(tmpDir, i))) {
                chunks.add(i);
            }
        }
        return chunks;
    }

    @Override
    public long uploadedSize(String uploadId, Set<Integer> chunks) {
        long total = 0L;
        for (Integer chunk : chunks == null ? Collections.<Integer>emptySet() : chunks) {
            try {
                total += Files.size(chunkPath(storageService.getTmpDir(uploadId), chunk));
            } catch (IOException ignored) {
                // Status is advisory while a chunk is being replaced.
            }
        }
        return total;
    }

    @Override
    public void saveChunk(String uploadId, Integer chunkIndex, MultipartFile chunk) {
        if (chunk == null || chunk.isEmpty()) {
            throw exception(TK_UPLOAD_FILE_EMPTY);
        }
        Properties manifest = readManifest(uploadId);
        int totalChunks = Integer.parseInt(manifest.getProperty("totalChunks"));
        int chunkSize = Integer.parseInt(manifest.getProperty("chunkSize"));
        long fileSize = Long.parseLong(manifest.getProperty("fileSize"));
        if (chunkIndex == null || chunkIndex < 0 || chunkIndex >= totalChunks) {
            throw new IllegalArgumentException("分片序号无效");
        }
        long expectedSize = Math.min(chunkSize, fileSize - ((long) chunkIndex * chunkSize));
        if (chunk.getSize() != expectedSize) {
            throw new IllegalArgumentException("分片大小无效");
        }
        try {
            Files.copy(chunk.getInputStream(), chunkPath(storageService.getTmpDir(uploadId), chunkIndex),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new IllegalStateException("保存上传分片失败：" + ex.getMessage(), ex);
        }
    }

    @Override
    public Path mergeAndValidate(String uploadId, String relativePath) {
        Properties manifest = readManifest(uploadId);
        int totalChunks = Integer.parseInt(manifest.getProperty("totalChunks"));
        Set<Integer> chunks = uploadedChunks(uploadId, totalChunks);
        if (chunks.size() != totalChunks) {
            throw new IllegalStateException("上传分片不完整");
        }
        long fileSize = Long.parseLong(manifest.getProperty("fileSize"));
        String extension = extension(manifest.getProperty("fileName"));
        Path finalPath = storageService.resolveRelativePath(relativePath);
        try {
            Files.createDirectories(finalPath.getParent());
            try (OutputStream outputStream = Files.newOutputStream(finalPath)) {
                for (Integer chunk : chunks) {
                    Files.copy(chunkPath(storageService.getTmpDir(uploadId), chunk), outputStream);
                }
            }
            if (Files.size(finalPath) != fileSize || !isValidVideoContainer(finalPath, extension)) {
                Files.deleteIfExists(finalPath);
                throw exception(TK_UPLOAD_FILE_INVALID);
            }
            return finalPath;
        } catch (IOException ex) {
            throw new IllegalStateException("合并上传文件失败：" + ex.getMessage(), ex);
        }
    }

    @Override
    public void deleteSessionFiles(String uploadId) {
        validateUploadId(uploadId);
        FileUtil.del(storageService.getTmpDir(uploadId).toFile());
    }

    @Override
    public int getChunkSize() {
        Integer chunkSize = generationProperties.getUpload().getChunkSizeBytes();
        return chunkSize == null || chunkSize <= 0 ? 1 * 1024 * 1024 : chunkSize;
    }

    @Override
    public long getMaxFileSize() {
        Long maxFileSize = generationProperties.getUpload().getMaxFileSizeBytes();
        return maxFileSize == null || maxFileSize <= 0 ? 1_000_000_000L : maxFileSize;
    }

    @Override
    public String toPublicUrl(String relativePath) {
        return storageService.toPublicUrl(relativePath);
    }

    @Override
    public Path resolveRelativePath(String relativePath) {
        return storageService.resolveRelativePath(relativePath);
    }

    TkLocalUploadStorageService getStorageService() {
        return storageService;
    }

    private void validateFileBasics(String fileName, Long fileSize) {
        if (fileSize == null || fileSize <= 0) {
            throw exception(TK_UPLOAD_FILE_EMPTY);
        }
        if (fileSize > getMaxFileSize()) {
            throw exception(cn.iocoder.yudao.module.tk.enums.ErrorCodeConstants.TK_UPLOAD_FILE_TOO_LARGE);
        }
        if (!Arrays.asList(ALLOWED_EXTENSIONS).contains(extension(fileName))) {
            throw exception(cn.iocoder.yudao.module.tk.enums.ErrorCodeConstants.TK_UPLOAD_FILE_EXTENSION_INVALID);
        }
    }

    private void validateUploadId(String uploadId) {
        if (StrUtil.isBlank(uploadId) || !uploadId.matches("[A-Za-z0-9_-]{8,64}")) {
            throw new IllegalArgumentException("上传会话编号无效");
        }
    }

    private boolean isValidVideoContainer(Path path, String extension) throws IOException {
        if ("webm".equals(extension)) {
            byte[] header = new byte[WEBM_EBML_HEADER.length];
            try (InputStream inputStream = Files.newInputStream(path)) {
                return inputStream.read(header) == header.length && Arrays.equals(header, WEBM_EBML_HEADER);
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

    private Path manifestPath(Path tmpDir) {
        return tmpDir.resolve("manifest.properties");
    }

    private Path chunkPath(Path tmpDir, int index) {
        return tmpDir.resolve(index + ".part");
    }

    private String extension(String fileName) {
        return StrUtil.blankToDefault(FileUtil.extName(StrUtil.blankToDefault(fileName, "")), "")
                .toLowerCase(Locale.ROOT);
    }
}
