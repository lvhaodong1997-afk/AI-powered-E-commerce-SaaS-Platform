package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import cn.iocoder.yudao.module.tk.service.upload.TkLocalUploadStorageService;
import cn.iocoder.yudao.module.tk.service.upload.TkOssObjectStorageClient.ObjectMetadata;
import cn.iocoder.yudao.module.tk.service.upload.TkOssObjectStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        TkTiktokScheduledMediaService service = new TkTiktokScheduledMediaService(properties, storage,
                mock(TkOssObjectStorageService.class));
        Path source = storage.resolveRelativePath("tk/8/video.mp4");
        Files.createDirectories(source.getParent());
        byte[] content = "scheduled-video".getBytes(StandardCharsets.UTF_8);
        Files.write(source, content);

        String persisted = service.persist(8L, 20L, "/uploads/tk/8/video.mp4", 10L, "video/mp4");
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

    @Test
    void rejectsRootAndFilesNotOwnedByScheduledMediaService() throws Exception {
        TkGenerationProperties properties = new TkGenerationProperties();
        Path scheduledRoot = tempDir.resolve("scheduled");
        properties.getUpload().setScheduledPublishRootDir(scheduledRoot.toString());
        TkTiktokScheduledMediaService service = new TkTiktokScheduledMediaService(properties,
                new TkLocalUploadStorageService(properties), mock(TkOssObjectStorageService.class));
        Files.createDirectories(scheduledRoot);
        Path arbitrary = scheduledRoot.resolve("8").resolve("manual-name.mp4");
        Files.createDirectories(arbitrary.getParent());
        Files.write(arbitrary, "do-not-delete".getBytes(StandardCharsets.UTF_8));

        assertThrows(IllegalArgumentException.class, () -> service.cleanup(scheduledRoot.toString()));
        assertThrows(IllegalArgumentException.class, () -> service.cleanup(arbitrary.toString()));
        assertTrue(Files.exists(scheduledRoot));
        assertTrue(Files.exists(arbitrary));
    }

    @Test
    void scheduledMediaRootMustBeExplicitlyConfigured() {
        TkGenerationProperties properties = new TkGenerationProperties();

        String configured = properties.getUpload().getScheduledPublishRootDir();

        assertTrue(configured == null || configured.trim().isEmpty());
    }

    @Test
    void persistsOwnedPrivateOssVideoWithAuthenticatedDownloadAndAtomicMove() throws Exception {
        TkGenerationProperties properties = ossProperties();
        Path scheduledRoot = tempDir.resolve("scheduled-oss");
        properties.getUpload().setScheduledPublishRootDir(scheduledRoot.toString());
        TkOssObjectStorageService ossStorage = mock(TkOssObjectStorageService.class);
        TkTiktokScheduledMediaService service = new TkTiktokScheduledMediaService(properties,
                new TkLocalUploadStorageService(properties), ossStorage);
        String url = "https://cdn.example.com/tk/8/20/tiktok-publish/video.mp4";
        String objectKey = "tk/8/20/tiktok-publish/video.mp4";
        byte[] content = "private-oss-video".getBytes(StandardCharsets.UTF_8);
        ObjectMetadata identity = new ObjectMetadata(content.length, null, "etag-1", "version-1");
        when(ossStorage.isOwnedObjectUrl(url)).thenReturn(true);
        when(ossStorage.requireOwnedObjectKey(url, 8L, 20L)).thenReturn(objectKey);
        when(ossStorage.headObject(objectKey)).thenReturn(identity, identity);
        doAnswer(invocation -> {
            Path destination = invocation.getArgument(1);
            assertTrue(destination.getFileName().toString().endsWith(".part"));
            Files.write(destination, content);
            return null;
        }).when(ossStorage).downloadToFile(eq(objectKey), any(Path.class), eq(identity), eq(1_000_000_000L));

        Path persisted = Paths.get(service.persist(8L, 20L, url, 40L, "video/mp4"));

        assertTrue(Files.isRegularFile(persisted));
        assertArrayEquals(content, Files.readAllBytes(persisted));
        assertFalse(Files.exists(Paths.get(persisted + ".part")));
        verify(ossStorage).requireOwnedObjectKey(url, 8L, 20L);
        verify(ossStorage).downloadToFile(eq(objectKey), any(Path.class), eq(identity), eq(1_000_000_000L));
        verify(ossStorage, org.mockito.Mockito.times(2)).headObject(objectKey);
    }

    @Test
    void removesPartialAndTargetFilesWhenOwnedOssIdentityChanges() throws Exception {
        TkGenerationProperties properties = ossProperties();
        Path scheduledRoot = tempDir.resolve("scheduled-oss-changed");
        properties.getUpload().setScheduledPublishRootDir(scheduledRoot.toString());
        TkOssObjectStorageService ossStorage = mock(TkOssObjectStorageService.class);
        TkTiktokScheduledMediaService service = new TkTiktokScheduledMediaService(properties,
                new TkLocalUploadStorageService(properties), ossStorage);
        String url = "https://cdn.example.com/tk/8/20/tiktok-publish/video.mp4";
        String objectKey = "tk/8/20/tiktok-publish/video.mp4";
        byte[] content = "private-oss-video".getBytes(StandardCharsets.UTF_8);
        ObjectMetadata before = new ObjectMetadata(content.length, null, "etag-1", "version-1");
        ObjectMetadata after = new ObjectMetadata(content.length, null, "etag-2", "version-2");
        when(ossStorage.isOwnedObjectUrl(url)).thenReturn(true);
        when(ossStorage.requireOwnedObjectKey(url, 8L, 20L)).thenReturn(objectKey);
        when(ossStorage.headObject(objectKey)).thenReturn(before, after);
        doAnswer(invocation -> {
            Files.write(invocation.getArgument(1), content);
            return null;
        }).when(ossStorage).downloadToFile(eq(objectKey), any(Path.class), eq(before), eq(1_000_000_000L));

        assertThrows(IllegalStateException.class,
                () -> service.persist(8L, 20L, url, 40L, "video/mp4"));

        if (Files.exists(scheduledRoot)) {
            try (Stream<Path> paths = Files.walk(scheduledRoot)) {
                assertFalse(paths.anyMatch(Files::isRegularFile));
            }
        }
    }

    private TkGenerationProperties ossProperties() {
        TkGenerationProperties properties = new TkGenerationProperties();
        properties.getUpload().setStorageType("oss");
        properties.getUpload().setMaxFileSizeBytes(1_000_000_000L);
        properties.getUpload().getOss().setEnabled(true);
        properties.getUpload().getOss().setBucket("bucket");
        properties.getUpload().getOss().setEndpoint("oss-cn-example.aliyuncs.com");
        properties.getUpload().getOss().setPublicBaseUrl("https://cdn.example.com");
        properties.getUpload().getOss().setAccessKeyId("access-key");
        properties.getUpload().getOss().setAccessKeySecret("access-secret");
        properties.getUpload().getOss().setUploadPathPrefix("tk");
        return properties;
    }
}
