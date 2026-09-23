package cn.iocoder.yudao.module.tk.service.open.tiktok;

import cn.iocoder.yudao.module.tk.dal.dataobject.openapi.TkOpenTiktokMediaDO;
import cn.iocoder.yudao.module.tk.dal.mysql.openapi.TkOpenTiktokMediaMapper;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiContext;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiException;
import cn.iocoder.yudao.module.tk.framework.openapi.TkOpenApiPrincipal;
import cn.iocoder.yudao.module.tk.service.upload.TkLocalUploadStorageService;
import cn.iocoder.yudao.module.tk.service.upload.TkOssObjectStorageClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TkOpenTiktokMediaServiceTest {

    @AfterEach
    void clearContext() {
        TkOpenApiContext.clear();
    }

    @Test
    void shouldNormalizeSupportedVideoExtensions() {
        assertEquals("mp4", TkOpenTiktokMediaService.normalizeExtension("demo.MP4"));
        assertEquals("mov", TkOpenTiktokMediaService.normalizeExtension("demo.mov"));
        assertEquals("webm", TkOpenTiktokMediaService.normalizeExtension("demo.webm"));
        assertThrows(IllegalArgumentException.class,
                () -> TkOpenTiktokMediaService.normalizeExtension("../payload.exe"));
    }

    @Test
    void shouldBuildClientScopedOssObjectKey() {
        String objectKey = TkOpenTiktokMediaService.buildObjectKey("client_b", "upload_123", "video.mp4", "20260901");

        assertEquals("tk/open-api/client_b/20260901/upload_123.mp4", objectKey);
        assertFalse(objectKey.contains(".."));
    }

    @Test
    void shouldRejectInvalidContentTypeAndSha256() {
        TkOpenTiktokMediaService service = new TkOpenTiktokMediaService(mock(TkOpenTiktokMediaMapper.class),
                null, new TkGenerationProperties(), mock(TkOssObjectStorageClient.class));
        TkOpenApiContext.set(new TkOpenApiPrincipal("client_b", "B", "media"), "req-metadata");

        TkOpenApiException contentTypeError = assertThrows(TkOpenApiException.class,
                () -> service.create("video.mp4", 10L, "text/plain", null));
        TkOpenApiException shaError = assertThrows(TkOpenApiException.class,
                () -> service.create("video.mp4", 10L, "video/mp4", "not-a-sha256"));

        assertEquals("MEDIA_FILE_INVALID", contentTypeError.getCode());
        assertEquals("MEDIA_FILE_INVALID", shaError.getCode());
    }

    @Test
    void shouldRejectInsecureRemoteVideoUrls() {
        assertThrows(IllegalArgumentException.class,
                () -> TkOpenTiktokMediaService.validateRemoteVideoUrl("http://example.com/video.mp4"));
        assertThrows(IllegalArgumentException.class,
                () -> TkOpenTiktokMediaService.validateRemoteVideoUrl("https://127.0.0.1/video.mp4"));
    }

    @Test
    void shouldRejectOssCompletionWhenObjectMetadataDoesNotMatch() {
        TkOpenTiktokMediaMapper mapper = mock(TkOpenTiktokMediaMapper.class);
        TkOssObjectStorageClient oss = mock(TkOssObjectStorageClient.class);
        TkOpenTiktokMediaDO media = ossMedia();
        when(mapper.selectByClientAndUploadId("client_b", "upload_1")).thenReturn(media);
        when(oss.headObject("tk/open-api/client_b/upload_1.mp4"))
                .thenReturn(new TkOssObjectStorageClient.ObjectMetadata(9L, "sha-1"));
        TkOpenTiktokMediaService service = new TkOpenTiktokMediaService(mapper, null,
                new TkGenerationProperties(), oss);
        TkOpenApiContext.set(new TkOpenApiPrincipal("client_b", "B", "media"), "req-1");

        TkOpenApiException error = assertThrows(TkOpenApiException.class,
                () -> service.complete("upload_1", 10L, "sha-1", 1000L));

        assertEquals("MEDIA_FILE_INVALID", error.getCode());
        verify(mapper, never()).updateById(any(TkOpenTiktokMediaDO.class));
    }

    @Test
    void shouldStoreRawLocalChunk(@TempDir Path tempDir) throws Exception {
        TkOpenTiktokMediaMapper mapper = mock(TkOpenTiktokMediaMapper.class);
        TkOpenTiktokMediaDO media = TkOpenTiktokMediaDO.builder().id(1L).uploadId("upload_2")
                .clientId("client_b").uploadMode("LOCAL").fileName("video.mp4").fileSize(3L)
                .status("UPLOADING").expireTime(LocalDateTime.now().plusMinutes(5)).build();
        when(mapper.selectByClientAndUploadId("client_b", "upload_2")).thenReturn(media);
        TkGenerationProperties properties = new TkGenerationProperties();
        properties.getUpload().setChunkSizeBytes(3);
        properties.getUpload().setRootDir(tempDir.toString());
        TkLocalUploadStorageService storage = new TkLocalUploadStorageService(properties);
        TkOpenTiktokMediaService service = new TkOpenTiktokMediaService(mapper, storage, properties,
                mock(TkOssObjectStorageClient.class));
        TkOpenApiContext.set(new TkOpenApiPrincipal("client_b", "B", "media"), "req-2");

        service.uploadChunk("upload_2", 0, new byte[]{1, 2, 3});

        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(tempDir.resolve("tmp/upload_2/0.part")));
        verify(mapper).updateById(any(TkOpenTiktokMediaDO.class));
    }

    @Test
    void shouldRejectUploadMutationAfterSessionIsReady() {
        TkOpenTiktokMediaMapper mapper = mock(TkOpenTiktokMediaMapper.class);
        TkOpenTiktokMediaDO media = TkOpenTiktokMediaDO.builder().id(1L).uploadId("upload_ready")
                .clientId("client_b").uploadMode("LOCAL").fileName("video.mp4").fileSize(3L)
                .status("READY").expireTime(LocalDateTime.now().plusMinutes(5)).build();
        when(mapper.selectByClientAndUploadId("client_b", "upload_ready")).thenReturn(media);
        TkOpenTiktokMediaService service = new TkOpenTiktokMediaService(mapper, null,
                new TkGenerationProperties(), mock(TkOssObjectStorageClient.class));
        TkOpenApiContext.set(new TkOpenApiPrincipal("client_b", "B", "media"), "req-ready");

        TkOpenApiException chunkError = assertThrows(TkOpenApiException.class,
                () -> service.uploadChunk("upload_ready", 0, new byte[]{1, 2, 3}));
        TkOpenApiException completeError = assertThrows(TkOpenApiException.class,
                () -> service.complete("upload_ready", 3L, null, null));
        TkOpenApiException cancelError = assertThrows(TkOpenApiException.class,
                () -> service.cancel("upload_ready"));

        assertEquals("MEDIA_UPLOAD_STATUS_INVALID", chunkError.getCode());
        assertEquals("MEDIA_UPLOAD_STATUS_INVALID", completeError.getCode());
        assertEquals("MEDIA_UPLOAD_STATUS_INVALID", cancelError.getCode());
        verify(mapper, never()).updateById(any(TkOpenTiktokMediaDO.class));
    }

    @Test
    void shouldPersistMediaForScheduledPublish(@TempDir Path tempDir) throws Exception {
        TkOpenTiktokMediaMapper mapper = mock(TkOpenTiktokMediaMapper.class);
        TkGenerationProperties properties = new TkGenerationProperties();
        properties.getUpload().setRootDir(tempDir.resolve("uploads").toString());
        properties.getUpload().setScheduledPublishRootDir(tempDir.resolve("scheduled").toString());
        TkLocalUploadStorageService storage = new TkLocalUploadStorageService(properties);
        Path source = storage.resolveRelativePath("open-api/client_b/media_1/video.mp4");
        Files.createDirectories(source.getParent());
        Files.write(source, new byte[]{1, 2, 3, 4});
        TkOpenTiktokMediaDO media = TkOpenTiktokMediaDO.builder().id(9L).mediaId("media_1")
                .clientId("client_b").fileName("video.mp4").fileUrl(storage.toPublicUrl("open-api/client_b/media_1/video.mp4"))
                .contentType("video/mp4").fileSize(4L).status("READY").build();
        TkOpenTiktokMediaService service = new TkOpenTiktokMediaService(mapper, storage, properties,
                mock(TkOssObjectStorageClient.class));

        service.prepareForScheduledPublish(media);

        assertNotNull(media.getScheduledLocalPath());
        assertArrayEquals(new byte[]{1, 2, 3, 4}, Files.readAllBytes(java.nio.file.Paths.get(media.getScheduledLocalPath())));
        assertEquals("READY", media.getScheduledDownloadStatus());
        verify(mapper).updateById(any(TkOpenTiktokMediaDO.class));
    }

    @Test
    void shouldCleanupScheduledMediaInsideManagedRoot(@TempDir Path tempDir) throws Exception {
        TkOpenTiktokMediaMapper mapper = mock(TkOpenTiktokMediaMapper.class);
        TkGenerationProperties properties = new TkGenerationProperties();
        Path root = tempDir.resolve("scheduled");
        properties.getUpload().setScheduledPublishRootDir(root.toString());
        Path file = root.resolve("client_b/media_1/video.mp4");
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[]{1, 2, 3});
        TkOpenTiktokMediaDO media = TkOpenTiktokMediaDO.builder().id(9L).mediaId("media_1")
                .clientId("client_b").scheduledLocalPath(file.toString()).scheduledDownloadStatus("READY").build();
        TkOpenTiktokMediaService service = new TkOpenTiktokMediaService(mapper, null, properties,
                mock(TkOssObjectStorageClient.class));

        service.cleanupScheduledPublishMedia(media);

        assertFalse(Files.exists(file));
        assertFalse(Files.exists(file.getParent()));
        assertTrue(Files.isDirectory(root.resolve("client_b")));
        ArgumentCaptor<TkOpenTiktokMediaDO> update = ArgumentCaptor.forClass(TkOpenTiktokMediaDO.class);
        verify(mapper).updateById(update.capture());
        assertEquals("CLEANED", update.getValue().getScheduledDownloadStatus());
        assertDoesNotThrow(() -> service.cleanupScheduledPublishMedia(media));
        verify(mapper, times(2)).updateById(any(TkOpenTiktokMediaDO.class));
    }

    @Test
    void shouldPreserveOtherFilesInScheduledMediaDirectory(@TempDir Path root) throws Exception {
        TkOpenTiktokMediaMapper mapper = mock(TkOpenTiktokMediaMapper.class);
        TkGenerationProperties properties = new TkGenerationProperties();
        properties.getUpload().setScheduledPublishRootDir(root.toString());
        Path file = root.resolve("client_b/media_1/video.mp4");
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[]{1});
        Path other = file.resolveSibling("other.mp4");
        Files.write(other, new byte[]{2, 3});
        TkOpenTiktokMediaDO media = TkOpenTiktokMediaDO.builder().id(9L).mediaId("media_1")
                .clientId("client_b").scheduledLocalPath(file.toString()).build();
        TkOpenTiktokMediaService service = new TkOpenTiktokMediaService(mapper, null, properties,
                mock(TkOssObjectStorageClient.class));

        service.cleanupScheduledPublishMedia(media);

        assertFalse(Files.exists(file));
        assertArrayEquals(new byte[]{2, 3}, Files.readAllBytes(other));
        ArgumentCaptor<TkOpenTiktokMediaDO> update = ArgumentCaptor.forClass(TkOpenTiktokMediaDO.class);
        verify(mapper).updateById(update.capture());
        assertEquals("CLEANED", update.getValue().getScheduledDownloadStatus());
    }

    @Test
    void shouldNotRemoveParentForLegacyScheduledPath(@TempDir Path root) throws Exception {
        TkOpenTiktokMediaMapper mapper = mock(TkOpenTiktokMediaMapper.class);
        TkGenerationProperties properties = new TkGenerationProperties();
        properties.getUpload().setScheduledPublishRootDir(root.toString());
        Path file = root.resolve("client_b/video.mp4");
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[]{1});
        TkOpenTiktokMediaDO media = TkOpenTiktokMediaDO.builder().id(9L).mediaId("media_1")
                .clientId("client_b").scheduledLocalPath(file.toString()).build();
        TkOpenTiktokMediaService service = new TkOpenTiktokMediaService(mapper, null, properties,
                mock(TkOssObjectStorageClient.class));

        service.cleanupScheduledPublishMedia(media);

        assertFalse(Files.exists(file));
        assertTrue(Files.isDirectory(file.getParent()));
        assertTrue(Files.isDirectory(root));
    }

    @Test
    void shouldRejectCompletionWithoutFileSize() {
        TkOpenTiktokMediaMapper mapper = mock(TkOpenTiktokMediaMapper.class);
        when(mapper.selectByClientAndUploadId("client_b", "upload_1")).thenReturn(ossMedia());
        TkOpenTiktokMediaService service = new TkOpenTiktokMediaService(mapper, null,
                new TkGenerationProperties(), mock(TkOssObjectStorageClient.class));
        TkOpenApiContext.set(new TkOpenApiPrincipal("client_b", "B", "media"), "req-size");

        TkOpenApiException error = assertThrows(TkOpenApiException.class,
                () -> service.complete("upload_1", null, "sha-1", null));

        assertEquals("MEDIA_FILE_INVALID", error.getCode());
        verify(mapper, never()).updateById(any(TkOpenTiktokMediaDO.class));
    }

    private TkOpenTiktokMediaDO ossMedia() {
        return TkOpenTiktokMediaDO.builder().id(1L).uploadId("upload_1").clientId("client_b")
                .uploadMode("OSS").fileName("video.mp4").fileSize(10L).sha256("sha-1")
                .objectKey("tk/open-api/client_b/upload_1.mp4").status("UPLOADING")
                .expireTime(LocalDateTime.now().plusMinutes(5)).build();
    }
}
