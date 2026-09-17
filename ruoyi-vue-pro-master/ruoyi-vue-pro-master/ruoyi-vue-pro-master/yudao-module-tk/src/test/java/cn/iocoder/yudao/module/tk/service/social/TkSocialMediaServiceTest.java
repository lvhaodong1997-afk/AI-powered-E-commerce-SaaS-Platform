package cn.iocoder.yudao.module.tk.service.social;

import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.tk.dal.dataobject.social.TkSocialMediaDO;
import cn.iocoder.yudao.module.tk.dal.mysql.social.TkSocialMediaMapper;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import cn.iocoder.yudao.module.tk.controller.admin.social.vo.TkSocialVideoUploadCompleteReqVO;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import cn.iocoder.yudao.module.tk.service.upload.TkUploadSessionService;
import cn.iocoder.yudao.module.tk.service.upload.TkOssObjectStorageService;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkUploadSessionDO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TkSocialMediaServiceTest {
    @Test void historicalOwnedUnpublishedVideoIsRequeuedForTrueInspection() {
        TkSocialMediaDO historical = prepareHistorical(0L);
        TkSocialMediaDO result = service.getReadable(1L);
        assertEquals("PROCESSING", result.getStatus());
        assertEquals("PENDING", result.getMetadataStatus());
        assertNull(result.getFrameRate());
        assertEquals("\"etag-1\"", result.getSourceEtag());
        assertEquals(1024L, result.getSourceFileSize());
        assertThrows(IllegalArgumentException.class, () -> service.requireReadable(1L));
    }
    @Test void referencedHistoricalVideoRemainsUnchangedForExistingPublications() {
        TkSocialMediaDO historical = prepareHistorical(1L);
        assertSame(historical, service.getReadable(1L));
        assertEquals("READY", historical.getStatus()); assertEquals("UNVERIFIED", historical.getMetadataStatus());
        verify(mapper, never()).update(isNull(), any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
    }
    @Test void historicalForeignOssKeyCannotBeProbed() {
        TkSocialMediaDO historical = prepareHistorical(0L);
        historical.setObjectKey("tk/999/999/social-media/foreign.mp4");
        TkOssObjectStorageService oss = (TkOssObjectStorageService) org.springframework.test.util.ReflectionTestUtils.getField(service, "ossObjectStorageService");
        doThrow(new IllegalArgumentException("wrong owner")).when(oss).validateOwnedKey(historical.getObjectKey(),100L,100L);
        assertEquals("READY", service.getReadable(1L).getStatus());
        verify(oss, never()).headObject(anyString());
        verify(mapper, never()).update(isNull(), any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
    }
    private TkSocialMediaDO prepareHistorical(long references) {
        TkSocialVideoUploadCompleteReqVO request = directRequest(); prepareDirect(request);
        TkSocialMediaDO value = prepareCleanup(references);
        value.setCompanyId(100L); value.setCreator("7"); value.setMediaType("VIDEO"); value.setMetadataStatus("UNVERIFIED");
        value.setObjectKey(request.getObjectKey()); value.setFileSize(1024L); value.setFrameRate(30D);
        when(mapper.selectById(1L)).thenReturn(value);
        return value;
    }
    @Test void completionIgnoresFabricatedClientFrameRateAndQueuesProbe() {
        cn.iocoder.yudao.module.tk.controller.admin.social.vo.TkSocialVideoUploadCompleteReqVO request = directRequest();
        prepareDirect(request);
        TkSocialMediaDO result = service.completeDirectVideoUpload(request);
        assertEquals("PROCESSING", result.getStatus());
        assertNull(result.getFrameRate(), "Browser-supplied 30fps is not evidence");
        assertNull(result.getWidth());
        verify(mapper).insert(any(TkSocialMediaDO.class));
    }
    @Test void completionAcceptsAbsentBrowserMetadata() {
        TkSocialVideoUploadCompleteReqVO request = directRequest(); prepareDirect(request);
        request.setWidth(null); request.setHeight(null); request.setFrameRate(null); request.setDurationSeconds(null);
        assertEquals("PROCESSING", service.completeDirectVideoUpload(request).getStatus());
    }
    @Test void repeatedCompletionReturnsSameMediaWithoutInsertingAgain() {
        TkSocialVideoUploadCompleteReqVO request = directRequest(); prepareDirect(request);
        TkSocialMediaDO existing = new TkSocialMediaDO();
        existing.setId(20L); existing.setTenantId(100L); existing.setCompanyId(100L); existing.setCreator("7");
        existing.setObjectKey(request.getObjectKey()); existing.setStatus("PROCESSING");
        when(mapper.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(existing);
        assertSame(existing, service.completeDirectVideoUpload(request));
        verify(mapper, never()).insert(any(TkSocialMediaDO.class));
    }
    @Test void completionRejectsObjectSizeMismatch() {
        TkSocialVideoUploadCompleteReqVO request = directRequest(); TkOssObjectStorageService oss = prepareDirect(request);
        when(oss.headObject(request.getObjectKey())).thenReturn(
                new cn.iocoder.yudao.module.tk.service.upload.TkOssObjectStorageClient.ObjectMetadata(999L, null));
        assertThrows(IllegalArgumentException.class, () -> service.completeDirectVideoUpload(request));
        verify(mapper, never()).insert(any(TkSocialMediaDO.class));
    }
    @Test void completionRejectsDifferentUploaderEvenWithinCompany() {
        TkSocialVideoUploadCompleteReqVO request = directRequest(); prepareDirect(request);
        when(scope.getCurrentScope()).thenReturn(new TkUserScope(8L, 100L, "USER", 100L));
        assertThrows(IllegalArgumentException.class, () -> service.completeDirectVideoUpload(request));
        verify(mapper, never()).insert(any(TkSocialMediaDO.class));
    }
    @Test void historicalUnverifiedVideoCannotStartNewPublication() {
        TkSocialMediaDO media = new TkSocialMediaDO();
        media.setId(1L); media.setTenantId(100L); media.setCompanyId(100L); media.setCreator("7");
        media.setMediaType("VIDEO"); media.setStatus("READY");
        when(mapper.selectById(1L)).thenReturn(media);
        assertThrows(IllegalArgumentException.class, () -> service.requireReadable(1L));
    }
    private cn.iocoder.yudao.module.tk.controller.admin.social.vo.TkSocialVideoUploadCompleteReqVO directRequest() {
        TkSocialVideoUploadCompleteReqVO request = new TkSocialVideoUploadCompleteReqVO();
        request.setUploadId("abc123"); request.setFileName("movie.mp4"); request.setFileSize(1024L);
        request.setObjectKey("tk/100/100/social-media/abc123.mp4");
        request.setWidth(1080); request.setHeight(1920); request.setDurationSeconds(10D); request.setFrameRate(30D);
        return request;
    }
    private cn.iocoder.yudao.module.tk.service.upload.TkOssObjectStorageService prepareDirect(
            cn.iocoder.yudao.module.tk.controller.admin.social.vo.TkSocialVideoUploadCompleteReqVO request) {
        TkGenerationProperties properties = new TkGenerationProperties();
        properties.getUpload().setStorageType("oss"); properties.getUpload().getOss().setEnabled(true);
        TkUploadSessionService sessions = mock(TkUploadSessionService.class);
        TkUploadSessionDO session = new TkUploadSessionDO();
        session.setUploadId(request.getUploadId()); session.setFileName(request.getFileName());
        session.setFileSize(request.getFileSize()); session.setTenantId(100L); session.setCompanyId(100L);
        session.setCreator("7"); session.setStorageMode("social-oss"); session.setStatus("UPLOADING");
        when(sessions.lockSocialCompletion(request.getUploadId())).thenReturn(session);
        TkOssObjectStorageService oss = mock(TkOssObjectStorageService.class);
        when(oss.isConfigured()).thenReturn(true);
        when(oss.headObject(request.getObjectKey())).thenReturn(
                new cn.iocoder.yudao.module.tk.service.upload.TkOssObjectStorageClient.ObjectMetadata(1024L, null, "\"etag-1\"", null));
        when(oss.publicUrlForObjectKey(request.getObjectKey())).thenReturn("https://assets.example.com/abc123.mp4");
        when(scope.getCurrentScope()).thenReturn(new TkUserScope(7L, 100L, "USER", 100L));
        when(scope.getWritableCompanyId(null)).thenReturn(100L);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "generationProperties", properties);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "uploadSessionService", sessions);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "ossObjectStorageService", oss);
        return oss;
    }
    private final FileApi files = mock(FileApi.class);
    private final TkSocialMediaMapper mapper = mock(TkSocialMediaMapper.class);
    private final TkDataScopeService scope = mock(TkDataScopeService.class);
    private final TkSocialMediaService service = new TkSocialMediaService(files, mapper, scope);

    @Test void acceptsVideoLimitOfOneGiB() {
        assertEquals(1L * 1024 * 1024 * 1024, TkSocialMediaService.MAX_VIDEO_BYTES);
    }

    @Test void rejectsIncompleteDirectUploadMetadata() {
        assertThrows(IllegalArgumentException.class,
                () -> TkSocialMediaService.validateDirectVideoMetadata(0, 1080, 10D, 30D));
        assertThrows(IllegalArgumentException.class,
                () -> TkSocialMediaService.validateDirectVideoMetadata(1080, 1920, 10D, 10D));
    }

    @Test void rejectsFakeJpegBeforeWritingStorage() {
        assertThrows(IllegalArgumentException.class, () -> service.upload(
                new MockMultipartFile("file", "fake.jpg", "image/jpeg", new byte[]{1,2,3})));
        verifyNoInteractions(files, mapper);
    }
    @Test void rejectsFakeMp4BeforeWritingStorage() {
        assertThrows(IllegalArgumentException.class, () -> service.upload(
                new MockMultipartFile("file", "movie.mp4", "video/mp4", new byte[]{1,2,3})));
        verifyNoInteractions(files, mapper);
    }
    @Test void storesJpegUnderTenantNamespaceWithTrustedReadUrl() throws Exception {
        when(scope.getCurrentScope()).thenReturn(new TkUserScope(7L, 100L, "USER", 100L));
        when(scope.getWritableCompanyId(null)).thenReturn(100L);
        when(files.createFile(any(), anyString(), eq("tk/100/100/social-media"), eq("image/jpeg")))
                .thenReturn("https://assets.example.com/social/a.jpg");
        when(files.presignGetUrl(anyString(), eq(86400))).thenReturn("https://assets.example.com/social/a.jpg?sig=test");
        doAnswer(call -> { ((TkSocialMediaDO)call.getArgument(0)).setId(12L); return 1; })
                .when(mapper).insert(any(TkSocialMediaDO.class));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(640, 800, BufferedImage.TYPE_INT_RGB), "jpg", bytes);
        TkSocialMediaDO media = service.upload(new MockMultipartFile("file", "photo.jpg", "image/jpeg", bytes.toByteArray()));
        assertEquals(12L, media.getId());
        assertEquals(100L, media.getTenantId());
        assertEquals("7", media.getCreator());
        assertEquals("IMAGE", media.getMediaType());
        assertEquals(640, media.getWidth());
    }

    @Test void fallsBackToOriginalUrlWhenStorageDoesNotSupportPresigning() throws Exception {
        when(scope.getCurrentScope()).thenReturn(new TkUserScope(7L, 100L, "USER", 100L));
        when(scope.getWritableCompanyId(null)).thenReturn(100L);
        when(files.createFile(any(), anyString(), eq("tk/100/100/social-media"), eq("image/jpeg")))
                .thenReturn("https://assets.example.com/social/local.jpg");
        when(files.presignGetUrl(anyString(), eq(86400)))
                .thenThrow(new UnsupportedOperationException("不支持的操作"));
        doAnswer(call -> { ((TkSocialMediaDO)call.getArgument(0)).setId(13L); return 1; })
                .when(mapper).insert(any(TkSocialMediaDO.class));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(640, 800, BufferedImage.TYPE_INT_RGB), "jpg", bytes);

        TkSocialMediaDO media = service.upload(new MockMultipartFile("file", "local.jpg", "image/jpeg", bytes.toByteArray()));

        assertEquals(13L, media.getId());
        assertEquals("https://assets.example.com/social/local.jpg", media.getPublicUrl());
        verify(mapper).insert(any(TkSocialMediaDO.class));
    }
    @Test void rejectsUnusableStorageUrls() {
        for (String url : new String[]{"/uploads/a.jpg", "http://example.com/a.jpg", "https://localhost/a.jpg",
                "https://127.0.0.1/a.jpg", "https://user:pass@example.com/a.jpg"}) {
            assertThrows(IllegalArgumentException.class, () -> TkSocialMediaService.validateReadUrl(url));
        }
        assertDoesNotThrow(() -> TkSocialMediaService.validateReadUrl("https://assets.example.com/a.jpg?sig=test"));
    }
    @Test void enforcesOwnershipBeforeResolvingMediaUrl() {
        TkSocialMediaDO media = new TkSocialMediaDO();
        media.setId(1L); media.setTenantId(200L); media.setCompanyId(200L); media.setCreator("99");
        when(mapper.selectById(1L)).thenReturn(media);
        doThrow(new IllegalArgumentException("forbidden")).when(scope).validateReadable(200L, 200L, "99");
        assertThrows(IllegalArgumentException.class, () -> service.requireReadable(1L));
        verifyNoInteractions(files);
    }
    @Test void referencedSnapshotCannotBeDeleted() {
        prepareCleanup(1L);
        service.cleanupUnreferenced(1L,100L);
        verify(files,never()).deleteFileByUrl(anyString());
    }
    @Test void unreferencedSnapshotIsDeletedAndCannotBeClaimed() {
        TkSocialMediaDO value=prepareCleanup(0L);
        service.cleanupUnreferenced(1L,100L);
        verify(files).deleteFileByUrl("https://assets.example.com/social/a.mp4");
        value.setStatus("DELETING");
        assertThrows(IllegalArgumentException.class,() -> service.lockReady(1L,100L));
    }
    private TkSocialMediaDO prepareCleanup(long references) {
        cn.iocoder.yudao.module.tk.dal.mysql.social.TkSocialPublishTaskMapper tasks=
                mock(cn.iocoder.yudao.module.tk.dal.mysql.social.TkSocialPublishTaskMapper.class);
        org.springframework.transaction.PlatformTransactionManager manager=mock(org.springframework.transaction.PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new org.springframework.transaction.support.SimpleTransactionStatus());
        org.springframework.test.util.ReflectionTestUtils.setField(service,"tasks",tasks);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"transactionManager",manager);
        TkSocialMediaDO value=new TkSocialMediaDO(); value.setId(1L); value.setTenantId(100L);
        value.setStatus("READY"); value.setPublicUrl("https://assets.example.com/social/a.mp4");
        when(mapper.lockMedia(1L,100L)).thenReturn(value);
        when(tasks.selectCount(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(references);
        when(mapper.update(isNull(),any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(1);
        return value;
    }
}
