package cn.iocoder.yudao.module.tk.service.upload;

import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public interface TkChunkUploadStorageService {

    void createManifest(String uploadId, String fileName, Long fileSize, String contentType,
                        Map<String, String> metadata);

    Properties readManifest(String uploadId);

    Set<Integer> uploadedChunks(String uploadId, int totalChunks);

    long uploadedSize(String uploadId, Set<Integer> chunks);

    void saveChunk(String uploadId, Integer chunkIndex, MultipartFile chunk);

    Path mergeAndValidate(String uploadId, String relativePath);

    void deleteSessionFiles(String uploadId);

    int getChunkSize();

    long getMaxFileSize();

    String toPublicUrl(String relativePath);

    Path resolveRelativePath(String relativePath);
}
