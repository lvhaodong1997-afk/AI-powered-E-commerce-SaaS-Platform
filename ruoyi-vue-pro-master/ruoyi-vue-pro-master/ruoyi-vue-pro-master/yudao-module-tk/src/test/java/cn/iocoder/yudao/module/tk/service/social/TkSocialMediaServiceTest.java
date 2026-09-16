package cn.iocoder.yudao.module.tk.service.social;

import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.tk.dal.dataobject.social.TkSocialMediaDO;
import cn.iocoder.yudao.module.tk.dal.mysql.social.TkSocialMediaMapper;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TkSocialMediaServiceTest {
    private final FileApi files = mock(FileApi.class);
    private final TkSocialMediaMapper mapper = mock(TkSocialMediaMapper.class);
    private final TkDataScopeService scope = mock(TkDataScopeService.class);
    private final TkSocialMediaService service = new TkSocialMediaService(files, mapper, scope);

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
