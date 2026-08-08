package cn.iocoder.yudao.module.tk.service.upload;

import cn.iocoder.yudao.module.tk.controller.admin.upload.vo.TkUploadSessionRespVO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkMaterialLibraryDO;
import cn.iocoder.yudao.module.tk.dal.mysql.TkMaterialLibraryMapper;
import cn.iocoder.yudao.module.tk.dal.mysql.TkMaterialVideoMapper;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import cn.iocoder.yudao.module.tk.service.material.TkMaterialVideoParseService;
import cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TkMaterialOssUploadServiceImplTest {

    @Test
    void formatOssGmtDateUsesTwoDigitDay() {
        String date = TkMaterialOssUploadServiceImpl.formatOssGmtDate(
                Instant.parse("2026-07-07T10:31:42Z"));

        assertEquals("Tue, 07 Jul 2026 10:31:42 GMT", date);
    }

    @Test
    void createMaterialVideoSessionStoresObjectUnderMaterialLibraryPath() {
        TkMaterialOssUploadServiceImpl service = createService();
        TkMaterialLibraryMapper libraryMapper = mock(TkMaterialLibraryMapper.class);
        TkMaterialLibraryDO library = TkMaterialLibraryDO.builder()
                .id(23L)
                .companyId(100L)
                .build();
        library.setTenantId(1L);
        when(libraryMapper.selectById(23L)).thenReturn(library);
        ReflectionTestUtils.setField(service, "libraryMapper", libraryMapper);

        TkUploadSessionRespVO session = service.createMaterialVideoSession(23L, "demo.mp4", 1024L, "video/mp4");

        assertTrue(session.getObjectKey().startsWith(
                "tk/1/100/material-libraries/23/material-videos/"));
        assertTrue(session.getPublicUrl().startsWith(
                "https://cdn.example.com/tk/1/100/material-libraries/23/material-videos/"));
    }

    @Test
    void isManagedUrlKeepsRecognizingOldAndNewMaterialPaths() {
        TkMaterialOssUploadServiceImpl service = createService();

        assertTrue(service.isManagedUrl("https://cdn.example.com/tk/1/100/material-videos/20260805/old.mp4"));
        assertTrue(service.isManagedUrl("https://cdn.example.com/tk/1/100/material-libraries/23/material-videos/20260805/new.mp4"));
    }

    private TkMaterialOssUploadServiceImpl createService() {
        TkMaterialOssUploadServiceImpl service = new TkMaterialOssUploadServiceImpl();
        TkGenerationProperties properties = new TkGenerationProperties();
        properties.getUpload().setStorageType("oss");
        properties.getUpload().getOss().setEnabled(true);
        properties.getUpload().getOss().setBucket("bucket");
        properties.getUpload().getOss().setEndpoint("oss-cn-example.aliyuncs.com");
        properties.getUpload().getOss().setPublicBaseUrl("https://cdn.example.com");
        properties.getUpload().getOss().setAccessKeyId("access-key");
        properties.getUpload().getOss().setAccessKeySecret("access-secret");
        ReflectionTestUtils.setField(service, "generationProperties", properties);
        ReflectionTestUtils.setField(service, "videoMapper", mock(TkMaterialVideoMapper.class));
        ReflectionTestUtils.setField(service, "dataScopeService", mock(TkDataScopeService.class));
        ReflectionTestUtils.setField(service, "materialVideoParseService", mock(TkMaterialVideoParseService.class));
        return service;
    }
}
