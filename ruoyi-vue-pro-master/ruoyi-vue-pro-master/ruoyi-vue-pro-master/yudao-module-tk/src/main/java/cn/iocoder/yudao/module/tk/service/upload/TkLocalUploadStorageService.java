package cn.iocoder.yudao.module.tk.service.upload;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

@Service
public class TkLocalUploadStorageService {

    private final TkGenerationProperties generationProperties;

    public TkLocalUploadStorageService(TkGenerationProperties generationProperties) {
        this.generationProperties = generationProperties;
    }

    public Path getRootDir() {
        String rootDir = StrUtil.blankToDefault(generationProperties.getUpload().getRootDir(),
                "${java.io.tmpdir}/tk-uploads");
        rootDir = rootDir.replace("${java.io.tmpdir}", System.getProperty("java.io.tmpdir"));
        return Paths.get(rootDir).toAbsolutePath().normalize();
    }

    public Path getTmpDir(String uploadId) {
        return getRootDir().resolve("tmp").resolve(uploadId).normalize();
    }

    public Path resolveRelativePath(String relativePath) {
        Path root = getRootDir();
        Path path = root.resolve(relativePath).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Invalid upload path");
        }
        return path;
    }

    public String toPublicUrl(String relativePath) {
        String publicBaseUrl = StrUtil.blankToDefault(generationProperties.getUpload().getPublicBaseUrl(), "/uploads");
        return StrUtil.removeSuffix(publicBaseUrl, "/") + "/" + relativePath.replace('\\', '/');
    }

    public Optional<Path> resolveLocalPath(String publicUrl) {
        String publicBaseUrl = StrUtil.blankToDefault(generationProperties.getUpload().getPublicBaseUrl(), "/uploads");
        String cleanUrl = StrUtil.subBefore(StrUtil.blankToDefault(publicUrl, ""), "?", false);
        String relative = null;
        if (StrUtil.startWith(cleanUrl, publicBaseUrl)) {
            relative = StrUtil.removePrefix(cleanUrl, StrUtil.removeSuffix(publicBaseUrl, "/"));
        } else if (StrUtil.startWith(publicBaseUrl, "http://") || StrUtil.startWith(publicBaseUrl, "https://")) {
            try {
                URI baseUri = URI.create(publicBaseUrl);
                URI urlUri = URI.create(cleanUrl);
                if (StrUtil.equalsIgnoreCase(baseUri.getHost(), urlUri.getHost())
                        && StrUtil.startWith(urlUri.getPath(), baseUri.getPath())) {
                    relative = StrUtil.removePrefix(urlUri.getPath(), StrUtil.removeSuffix(baseUri.getPath(), "/"));
                }
            } catch (IllegalArgumentException ignored) {
                return Optional.empty();
            }
        }
        if (StrUtil.isBlank(relative)) {
            return Optional.empty();
        }
        relative = StrUtil.removePrefix(relative, "/");
        Path path = resolveRelativePath(relative);
        return Optional.of(path);
    }

}
