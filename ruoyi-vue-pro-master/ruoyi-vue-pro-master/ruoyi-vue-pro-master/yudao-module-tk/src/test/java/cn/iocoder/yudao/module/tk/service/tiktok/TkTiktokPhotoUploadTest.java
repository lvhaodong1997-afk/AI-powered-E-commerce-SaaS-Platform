package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokPublishMediaDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkTiktokPublishMediaMapper;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import cn.iocoder.yudao.module.tk.service.upload.TkOssObjectStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TkTiktokPhotoUploadTest {
    @Test void savesValidatedJpegWithServerDetectedMimeAndTenantOwnedKey() throws Exception {
        TkTiktokPublishMediaServiceImpl service = new TkTiktokPublishMediaServiceImpl();
        TkDataScopeService scope = mock(TkDataScopeService.class);
        when(scope.getCurrentScope()).thenReturn(new TkUserScope(7L, 100L, "USER", 200L));
        when(scope.getWritableCompanyId(null)).thenReturn(200L);
        TkOssObjectStorageService oss = mock(TkOssObjectStorageService.class);
        when(oss.isConfigured()).thenReturn(true);
        when(oss.publishStorage()).thenReturn(oss);
        when(oss.publishConfig()).thenReturn(new TkGenerationProperties.Oss());
        when(oss.unsignedUrl(anyString())).thenAnswer(i -> "https://cdn.example/" + i.getArgument(0));
        TkTiktokPublishMediaMapper mapper = mock(TkTiktokPublishMediaMapper.class);
        ReflectionTestUtils.setField(service, "dataScopeService", scope);
        ReflectionTestUtils.setField(service, "ossObjectStorageService", oss);
        ReflectionTestUtils.setField(service, "generationProperties", new TkGenerationProperties());
        ReflectionTestUtils.setField(service, "mediaMapper", mapper);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB), "jpg", bytes);
        TkTiktokPublishMediaDO media = ReflectionTestUtils.invokeMethod(service, "uploadPhoto",
                new MockMultipartFile("file", "photo.jpg", "application/octet-stream", bytes.toByteArray()));
        assertEquals("image/jpeg", media.getMimeType());
        assertEquals(100L, media.getTenantId());
        assertTrue(media.getFileUrl().startsWith("https://cdn.example/tk/100/200/tiktok-photo/"));
        verify(mapper).insert(media);
        assertTrue(mockingDetails(oss).getInvocations().stream().anyMatch(i -> i.getMethod().getName().equals("uploadFile")
                && i.getArguments().length == 4 && "image/jpeg".equals(i.getArgument(3))));
    }

    @Test void rejectsFakeImageAndPngBeforeWritingStorage() {
        TkTiktokPublishMediaServiceImpl service = new TkTiktokPublishMediaServiceImpl();
        assertThrows(IllegalArgumentException.class, () -> ReflectionTestUtils.invokeMethod(service, "uploadPhoto",
                new MockMultipartFile("file", "fake.jpg", "image/jpeg", new byte[]{1, 2, 3})));
        assertThrows(IllegalArgumentException.class, () -> ReflectionTestUtils.invokeMethod(service, "uploadPhoto",
                new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1, 2, 3})));
    }
}
