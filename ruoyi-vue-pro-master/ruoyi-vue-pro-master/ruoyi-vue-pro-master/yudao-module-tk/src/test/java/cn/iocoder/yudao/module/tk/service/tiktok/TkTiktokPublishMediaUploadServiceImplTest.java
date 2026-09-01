package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.iocoder.yudao.module.tk.controller.admin.upload.vo.TkUploadSessionRespVO;
import cn.iocoder.yudao.module.tk.controller.admin.upload.vo.TkUploadSessionStatusRespVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokPublishMediaDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkTiktokPublishMediaMapper;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import cn.iocoder.yudao.module.tk.service.upload.TkLocalUploadStorageService;
import cn.iocoder.yudao.module.tk.service.upload.TkUploadSessionService;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkUploadSessionDO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TkTiktokPublishMediaUploadServiceImplTest {

    @TempDir
    Path tempDir;

    @Test
    void defaultUploadLimitSupportsVideosBelowOneGb() {
        TkGenerationProperties properties = new TkGenerationProperties();

        assertTrue(properties.getUpload().getMaxFileSizeBytes() >= 1_000_000_000L);
    }

    @Test
    void ossStorageReturnsDirectUploadSession() {
        TkDataScopeService dataScopeService = mock(TkDataScopeService.class);
        when(dataScopeService.getCurrentScope()).thenReturn(new TkUserScope(7L, 100L, "USER", 200L));
        TkGenerationProperties properties = new TkGenerationProperties();
        properties.getUpload().setRootDir(tempDir.toString());
        properties.getUpload().setStorageType("oss");
        properties.getUpload().getOss().setEnabled(true);
        properties.getUpload().getOss().setBucket("clipforge");
        properties.getUpload().getOss().setEndpoint("oss-cn-hangzhou.aliyuncs.com");
        properties.getUpload().getOss().setPublicBaseUrl("https://cdn.example.com");
        properties.getUpload().getOss().setAccessKeyId("test-access-key");
        properties.getUpload().getOss().setAccessKeySecret("test-access-secret");

        TkTiktokPublishMediaUploadServiceImpl service = new TkTiktokPublishMediaUploadServiceImpl();
        ReflectionTestUtils.setField(service, "storageService", new TkLocalUploadStorageService(properties));
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        ReflectionTestUtils.setField(service, "generationProperties", properties);

        TkUploadSessionRespVO session = service.createSession("demo.mp4", 8L, "video/mp4");

        assertEquals("oss", session.getUploadMode());
        assertNotNull(session.getUploadUrl());
        assertNotNull(session.getObjectKey());
        assertNotNull(session.getPolicy());
        assertNotNull(session.getSignature());
        String policyJson = new String(Base64.getDecoder().decode(session.getPolicy()), StandardCharsets.UTF_8);
        assertTrue(policyJson.contains("[\"eq\",\"$key\",\"" + session.getObjectKey() + "\"]"));
        properties.getUpload().getOss().setReadUrlExpireSeconds(600);
        assertTrue(service.refreshReadUrl("https://cdn.example.com/" + session.getObjectKey()).contains("Expires="));
    }

    @Test
    void chunksAreTrackedAndCompletedIntoPublishMedia() throws Exception {
        TkTiktokPublishMediaMapper mediaMapper = mock(TkTiktokPublishMediaMapper.class);
        TkDataScopeService dataScopeService = mock(TkDataScopeService.class);
        TkUserScope scope = new TkUserScope(7L, 100L, "USER", 200L);
        when(dataScopeService.getCurrentScope()).thenReturn(scope);
        when(dataScopeService.getWritableCompanyId(null)).thenReturn(200L);
        doAnswer(invocation -> {
            TkTiktokPublishMediaDO media = invocation.getArgument(0);
            media.setId(88L);
            return 1;
        }).when(mediaMapper).insert(any(TkTiktokPublishMediaDO.class));

        TkGenerationProperties properties = new TkGenerationProperties();
        properties.getUpload().setRootDir(tempDir.toString());
        properties.getUpload().setPublicBaseUrl("https://tkassetplant.fnn.net.cn/uploads");
        properties.getUpload().setChunkSizeBytes(4);
        TkTiktokPublishMediaUploadServiceImpl service = new TkTiktokPublishMediaUploadServiceImpl();
        ReflectionTestUtils.setField(service, "mediaMapper", mediaMapper);
        ReflectionTestUtils.setField(service, "storageService", new TkLocalUploadStorageService(properties));
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        ReflectionTestUtils.setField(service, "generationProperties", properties);

        TkUploadSessionRespVO session = service.createSession("demo.mp4", 8L, "video/mp4");
        assertEquals(2, session.getTotalChunks());
        assertEquals(4, session.getChunkSize());

        service.uploadChunk(session.getUploadId(), 0,
                new MockMultipartFile("chunk", "0.part", "application/octet-stream", "ftyp".getBytes(StandardCharsets.US_ASCII)));
        service.uploadChunk(session.getUploadId(), 1,
                new MockMultipartFile("chunk", "1.part", "application/octet-stream", "moov".getBytes(StandardCharsets.US_ASCII)));

        TkUploadSessionStatusRespVO status = service.getSessionStatus(session.getUploadId());
        assertEquals(2, status.getUploadedChunks().size());
        assertEquals(8L, status.getUploadedSize());

        TkTiktokPublishMediaDO media = service.complete(session.getUploadId(), null);
        assertEquals(88L, media.getId());
        assertEquals(8L, media.getFileSize());
        assertEquals("READY", media.getStatus());
        assertNotNull(media.getFileUrl());
    }

    @Test
    void tenantUserWithoutExplicitCompanyUsesWritableTenantScope() {
        TkDataScopeService dataScopeService = mock(TkDataScopeService.class);
        when(dataScopeService.getCurrentScope()).thenReturn(new TkUserScope(7L, 100L, "TENANT_USER", null));
        when(dataScopeService.getWritableCompanyId(null)).thenReturn(100L);
        TkGenerationProperties properties = new TkGenerationProperties();
        properties.getUpload().setRootDir(tempDir.toString());
        properties.getUpload().setChunkSizeBytes(4);
        TkTiktokPublishMediaUploadServiceImpl service = new TkTiktokPublishMediaUploadServiceImpl();
        ReflectionTestUtils.setField(service, "storageService", new TkLocalUploadStorageService(properties));
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        ReflectionTestUtils.setField(service, "generationProperties", properties);

        TkUploadSessionRespVO session = service.createSession("tenant.mp4", 4L, "video/mp4");

        assertEquals(1, session.getTotalChunks());
    }

    @Test
    void cancelRemovesTemporarySessionFiles() throws Exception {
        TkDataScopeService dataScopeService = mock(TkDataScopeService.class);
        when(dataScopeService.getCurrentScope()).thenReturn(new TkUserScope(7L, 100L, "USER", 200L));
        TkGenerationProperties properties = new TkGenerationProperties();
        properties.getUpload().setRootDir(tempDir.toString());
        properties.getUpload().setChunkSizeBytes(4);
        TkTiktokPublishMediaUploadServiceImpl service = new TkTiktokPublishMediaUploadServiceImpl();
        ReflectionTestUtils.setField(service, "storageService", new TkLocalUploadStorageService(properties));
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        ReflectionTestUtils.setField(service, "generationProperties", properties);

        TkUploadSessionRespVO session = service.createSession("demo.mp4", 4L, "video/mp4");
        Path tmp = new TkLocalUploadStorageService(properties).getTmpDir(session.getUploadId());
        assertTrue(Files.isDirectory(tmp));
        service.cancel(session.getUploadId());
        assertFalse(Files.exists(tmp));
    }

    @Test
    void sessionCannotBeReadFromAnotherCompany() {
        TkDataScopeService dataScopeService = mock(TkDataScopeService.class);
        when(dataScopeService.getCurrentScope()).thenReturn(new TkUserScope(7L, 100L, "USER", 200L));
        TkGenerationProperties properties = new TkGenerationProperties();
        properties.getUpload().setRootDir(tempDir.toString());
        TkTiktokPublishMediaUploadServiceImpl service = new TkTiktokPublishMediaUploadServiceImpl();
        ReflectionTestUtils.setField(service, "storageService", new TkLocalUploadStorageService(properties));
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        ReflectionTestUtils.setField(service, "generationProperties", properties);

        TkUploadSessionRespVO session = service.createSession("demo.mp4", 4L, "video/mp4");
        when(dataScopeService.getCurrentScope()).thenReturn(new TkUserScope(9L, 100L, "USER", 201L));
        assertThrows(IllegalArgumentException.class, () -> service.getSessionStatus(session.getUploadId()));
    }

    @Test
    void missingManifestIsRecoveredFromPersistedUploadSession() throws Exception {
        TkDataScopeService dataScopeService = mock(TkDataScopeService.class);
        when(dataScopeService.getCurrentScope()).thenReturn(new TkUserScope(7L, 100L, "USER", 200L));
        TkUploadSessionService uploadSessionService = mock(TkUploadSessionService.class);
        TkGenerationProperties properties = new TkGenerationProperties();
        properties.getUpload().setRootDir(tempDir.toString());
        properties.getUpload().setChunkSizeBytes(4);
        TkTiktokPublishMediaUploadServiceImpl service = new TkTiktokPublishMediaUploadServiceImpl();
        TkLocalUploadStorageService storageService = new TkLocalUploadStorageService(properties);
        ReflectionTestUtils.setField(service, "storageService", storageService);
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        ReflectionTestUtils.setField(service, "generationProperties", properties);
        ReflectionTestUtils.setField(service, "uploadSessionService", uploadSessionService);

        TkUploadSessionRespVO session = service.createSession("recover.mp4", 4L, "video/mp4");
        TkUploadSessionDO persisted = new TkUploadSessionDO()
                .setUploadId(session.getUploadId())
                .setCompanyId(200L)
                .setFileName("recover.mp4")
                .setFileSize(4L)
                .setContentType("video/mp4")
                .setStorageMode("local")
                .setStatus("UPLOADING");
        persisted.setTenantId(100L);
        when(uploadSessionService.validateAccessible(session.getUploadId())).thenReturn(persisted);
        Files.delete(storageService.getTmpDir(session.getUploadId()).resolve("manifest.properties"));

        assertDoesNotThrow(() -> service.uploadChunk(session.getUploadId(), 0,
                new MockMultipartFile("chunk", "0.part", "application/octet-stream",
                        "ftyp".getBytes(StandardCharsets.US_ASCII))));
        assertTrue(Files.isRegularFile(storageService.getTmpDir(session.getUploadId()).resolve("manifest.properties")));
    }

}
