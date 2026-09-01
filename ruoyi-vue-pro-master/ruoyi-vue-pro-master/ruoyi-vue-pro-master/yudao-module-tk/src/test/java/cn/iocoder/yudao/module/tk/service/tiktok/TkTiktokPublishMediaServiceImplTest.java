package cn.iocoder.yudao.module.tk.service.tiktok;

import cn.iocoder.yudao.module.tk.dal.dataobject.TkTiktokPublishMediaDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkTiktokPublishMediaMapper;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import cn.iocoder.yudao.module.tk.service.scope.TkUserScope;
import cn.iocoder.yudao.module.tk.service.upload.TkLocalUploadStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TkTiktokPublishMediaServiceImplTest {

    @TempDir
    Path tempDir;

    @Test
    void acceptsVideoAtOneGbLimitOnLegacyUploadEndpoint() throws Exception {
        TkTiktokPublishMediaMapper mediaMapper = mock(TkTiktokPublishMediaMapper.class);
        TkDataScopeService dataScopeService = mock(TkDataScopeService.class);
        when(dataScopeService.getCurrentScope()).thenReturn(new TkUserScope(7L, 100L, "USER", 200L));
        when(dataScopeService.getWritableCompanyId(null)).thenReturn(200L);
        doAnswer(invocation -> {
            TkTiktokPublishMediaDO media = invocation.getArgument(0);
            media.setId(88L);
            return 1;
        }).when(mediaMapper).insert(any(TkTiktokPublishMediaDO.class));

        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(1_000_000_000L);
        when(file.getOriginalFilename()).thenReturn("demo.mp4");
        when(file.getContentType()).thenReturn("video/mp4");
        doAnswer(invocation -> {
            File target = invocation.getArgument(0);
            Files.write(target.toPath(), new byte[] {0});
            return null;
        }).when(file).transferTo(any(File.class));

        TkTiktokPublishMediaServiceImpl service = new TkTiktokPublishMediaServiceImpl();
        ReflectionTestUtils.setField(service, "mediaMapper", mediaMapper);
        ReflectionTestUtils.setField(service, "storageService", new TkLocalUploadStorageService(createProperties(tempDir)));
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);

        TkTiktokPublishMediaDO media = service.uploadVideo(file);

        assertNotNull(media);
        assertEquals(88L, media.getId());
        assertEquals(1_000_000_000L, media.getFileSize());
    }

    private TkGenerationProperties createProperties(Path rootDir) {
        TkGenerationProperties properties = new TkGenerationProperties();
        properties.getUpload().setRootDir(rootDir.toString());
        properties.getUpload().setPublicBaseUrl("/uploads");
        return properties;
    }
}
