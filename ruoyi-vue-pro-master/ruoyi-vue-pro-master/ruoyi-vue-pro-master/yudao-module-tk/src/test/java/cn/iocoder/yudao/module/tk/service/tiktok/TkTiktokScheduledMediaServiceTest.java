package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import cn.iocoder.yudao.module.tk.service.upload.TkLocalUploadStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TkTiktokScheduledMediaServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void persistsUploadedLocalVideoAndCleansOnlyManagedPath() throws Exception {
        TkGenerationProperties properties = new TkGenerationProperties();
        Path uploadRoot = tempDir.resolve("uploads");
        Path scheduledRoot = tempDir.resolve("scheduled");
        properties.getUpload().setRootDir(uploadRoot.toString());
        properties.getUpload().setPublicBaseUrl("/uploads");
        properties.getUpload().setScheduledPublishRootDir(scheduledRoot.toString());
        TkLocalUploadStorageService storage = new TkLocalUploadStorageService(properties);
        TkTiktokScheduledMediaService service = new TkTiktokScheduledMediaService(properties, storage);
        Path source = storage.resolveRelativePath("tk/8/video.mp4");
        Files.createDirectories(source.getParent());
        byte[] content = "scheduled-video".getBytes(StandardCharsets.UTF_8);
        Files.write(source, content);

        String persisted = service.persist(8L, "/uploads/tk/8/video.mp4", 10L, "video/mp4");
        Path persistedPath = Paths.get(persisted);

        assertTrue(persistedPath.startsWith(scheduledRoot.toAbsolutePath().normalize()));
        assertArrayEquals(content, Files.readAllBytes(persistedPath));
        service.cleanup(persisted);
        assertFalse(Files.exists(persistedPath));

        Path outside = tempDir.resolve("outside.mp4");
        Files.write(outside, content);
        assertThrows(IllegalArgumentException.class, () -> service.cleanup(outside.toString()));
        assertTrue(Files.exists(outside));
    }
}
