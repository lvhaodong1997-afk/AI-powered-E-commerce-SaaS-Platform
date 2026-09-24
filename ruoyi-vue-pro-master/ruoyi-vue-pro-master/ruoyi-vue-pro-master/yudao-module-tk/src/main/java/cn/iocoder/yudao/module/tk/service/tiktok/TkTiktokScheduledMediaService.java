package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import cn.iocoder.yudao.module.tk.service.upload.TkLocalUploadStorageService;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class TkTiktokScheduledMediaService {

    private static final int DOWNLOAD_TIMEOUT_MILLIS = 10 * 60 * 1000;

    private final TkGenerationProperties generationProperties;
    private final TkLocalUploadStorageService localStorageService;

    public TkTiktokScheduledMediaService(TkGenerationProperties generationProperties,
                                         TkLocalUploadStorageService localStorageService) {
        this.generationProperties = generationProperties;
        this.localStorageService = localStorageService;
    }

    public String persist(Long tenantId, String sourceUrl, Long uploadedVideoId, String mimeType) {
        if (tenantId == null || StrUtil.isBlank(sourceUrl)) {
            throw new IllegalArgumentException("定时发布素材来源不能为空");
        }
        Path root = getRootDir();
        String extension = resolveExtension(sourceUrl, mimeType);
        String fileName = UUID.randomUUID().toString().replace("-", "") + extension;
        Path target = root.resolve(String.valueOf(tenantId)).resolve(fileName).normalize();
        ensureManaged(target);
        try {
            Files.createDirectories(target.getParent());
            Optional<Path> local = localStorageService.resolveLocalPath(sourceUrl);
            if (local.isPresent() && Files.isRegularFile(local.get())) {
                Files.copy(local.get(), target, StandardCopyOption.REPLACE_EXISTING);
            } else {
                download(sourceUrl, target);
            }
            if (!Files.isRegularFile(target) || Files.size(target) <= 0L) {
                throw new IllegalStateException("定时发布素材保存后为空");
            }
            return target.toString();
        } catch (Exception ex) {
            try {
                Files.deleteIfExists(target);
            } catch (Exception ignored) {
                // Preserve the original persistence failure.
            }
            if (ex instanceof IllegalStateException) {
                throw (IllegalStateException) ex;
            }
            throw new IllegalStateException("保存定时发布素材失败：" + ex.getMessage(), ex);
        }
    }

    public Path resolve(String storedPath) {
        if (StrUtil.isBlank(storedPath)) {
            throw new IllegalArgumentException("定时发布素材路径不能为空");
        }
        Path path = Paths.get(storedPath).toAbsolutePath().normalize();
        ensureManaged(path);
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("定时发布素材不存在");
        }
        return path;
    }

    public void cleanup(String storedPath) {
        if (StrUtil.isBlank(storedPath)) {
            return;
        }
        Path path = Paths.get(storedPath).toAbsolutePath().normalize();
        ensureManaged(path);
        try {
            Files.deleteIfExists(path);
            Path parent = path.getParent();
            if (parent != null && !parent.equals(getRootDir()) && Files.isDirectory(parent)) {
                try (java.util.stream.Stream<Path> children = Files.list(parent)) {
                    if (!children.findAny().isPresent()) {
                        Files.deleteIfExists(parent);
                    }
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("清理定时发布素材失败：" + ex.getMessage(), ex);
        }
    }

    private void download(String sourceUrl, Path target) throws Exception {
        if (!StrUtil.startWithIgnoreCase(sourceUrl, "http://")
                && !StrUtil.startWithIgnoreCase(sourceUrl, "https://")) {
            throw new IllegalArgumentException("定时发布素材不是可读取的本地文件或公网地址");
        }
        try (HttpResponse response = HttpRequest.get(sourceUrl).timeout(DOWNLOAD_TIMEOUT_MILLIS).execute()) {
            if (!response.isOk()) {
                throw new IllegalStateException("下载定时发布素材失败，HTTP " + response.getStatus());
            }
            try (InputStream input = response.bodyStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
            String contentLength = response.header("Content-Length");
            if (StrUtil.isNotBlank(contentLength) && Long.parseLong(contentLength) != Files.size(target)) {
                throw new IllegalStateException("定时发布素材大小校验失败");
            }
        }
    }

    private Path getRootDir() {
        String configured = StrUtil.blankToDefault(
                generationProperties.getUpload().getScheduledPublishRootDir(), "/tk-publish-media");
        configured = configured.replace("${java.io.tmpdir}", System.getProperty("java.io.tmpdir"));
        return Paths.get(configured).toAbsolutePath().normalize();
    }

    private void ensureManaged(Path path) {
        if (!path.startsWith(getRootDir())) {
            throw new IllegalArgumentException("定时发布素材路径超出配置根目录");
        }
    }

    private String resolveExtension(String sourceUrl, String mimeType) {
        String normalizedMime = StrUtil.blankToDefault(mimeType, "").toLowerCase(Locale.ROOT);
        if (normalizedMime.contains("quicktime")) {
            return ".mov";
        }
        if (normalizedMime.contains("webm")) {
            return ".webm";
        }
        String cleanUrl = StrUtil.subBefore(sourceUrl, "?", false);
        String extension = FileUtil.extName(cleanUrl).toLowerCase(Locale.ROOT);
        if ("mov".equals(extension) || "webm".equals(extension)) {
            return "." + extension;
        }
        return ".mp4";
    }
}
