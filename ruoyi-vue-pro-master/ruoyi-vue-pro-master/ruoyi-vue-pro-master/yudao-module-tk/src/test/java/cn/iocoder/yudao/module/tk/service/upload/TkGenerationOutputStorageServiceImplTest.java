package cn.iocoder.yudao.module.tk.service.upload;

import cn.iocoder.yudao.module.infra.service.file.FileService;
import cn.iocoder.yudao.module.tk.dal.mysql.TkGenerationTaskMapper;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URLDecoder;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TkGenerationOutputStorageServiceImplTest {

    @Test
    void uploadGeneratedAssetStoresGeneratedOutputOnOss() {
        TkGenerationOutputStorageServiceImpl service = new TkGenerationOutputStorageServiceImpl();
        TkGenerationProperties properties = ossProperties();
        FileService fileService = mock(FileService.class);
        TkGenerationOutputStorageServiceImpl.OssObjectUploader uploader =
                mock(TkGenerationOutputStorageServiceImpl.OssObjectUploader.class);
        ReflectionTestUtils.setField(service, "generationProperties", properties);
        ReflectionTestUtils.setField(service, "fileService", fileService);
        ReflectionTestUtils.setField(service, "ossObjectUploader", uploader);
        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .id(42L)
                .companyId(9L)
                .build();
        task.setTenantId(5L);
        byte[] content = new byte[]{1, 2, 3};

        String url = service.uploadGeneratedAsset(task, content, "../generated 42.mp4", "video/mp4");

        assertTrue(url.matches("https://oss\\.example\\.com/tk/5/9/generation-tasks/42/\\d{8}/generated-42\\.mp4"));
        verify(uploader).upload(eq(properties.getUpload().getOss()),
                eq(url.substring("https://oss.example.com/".length())), eq(content), eq("video/mp4"));
        verifyNoInteractions(fileService);
    }

    @Test
    void uploadGeneratedAssetFailsWhenOssStorageIsDisabled() {
        TkGenerationOutputStorageServiceImpl service = new TkGenerationOutputStorageServiceImpl();
        TkGenerationProperties properties = ossProperties();
        properties.getUpload().setStorageType("local");
        ReflectionTestUtils.setField(service, "generationProperties", properties);

        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .id(42L)
                .companyId(9L)
                .build();
        task.setTenantId(5L);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.uploadGeneratedAsset(task, new byte[]{1}, "generated-42.mp4", "video/mp4"));

        assertTrue(ex.getMessage().contains("OSS"));
    }

    @Test
    void uploadGeneratedAssetReturnsSignedReadUrlWhenReadExpireConfigured() throws Exception {
        TkGenerationOutputStorageServiceImpl service = new TkGenerationOutputStorageServiceImpl();
        TkGenerationProperties properties = ossProperties();
        properties.getUpload().getOss().setReadUrlExpireSeconds(3600);
        TkGenerationOutputStorageServiceImpl.OssObjectUploader uploader =
                mock(TkGenerationOutputStorageServiceImpl.OssObjectUploader.class);
        TkGenerationTaskMapper taskMapper = mock(TkGenerationTaskMapper.class);
        AdminUserApi adminUserApi = mock(AdminUserApi.class);
        ReflectionTestUtils.setField(service, "generationProperties", properties);
        ReflectionTestUtils.setField(service, "ossObjectUploader", uploader);
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "adminUserApi", adminUserApi);
        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .id(43L)
                .companyId(9L)
                .build();
        task.setTenantId(5L);
        task.setCreator("166");
        task.setCreateTime(LocalDateTime.of(2026, 8, 7, 10, 30));
        when(taskMapper.selectDailyTaskIds(eq(5L), eq("166"),
                eq(LocalDateTime.of(2026, 8, 7, 0, 0)),
                eq(LocalDateTime.of(2026, 8, 8, 0, 0))))
                .thenReturn(Arrays.asList(41L, 43L));
        AdminUserRespDTO user = new AdminUserRespDTO();
        user.setId(166L);
        user.setNickname("管理员");
        when(adminUserApi.getUser(eq(166L))).thenReturn(user);

        String url = service.uploadGeneratedAsset(task, new byte[]{1}, "generated-43.mp4", "video/mp4");
        String decodedUrl = URLDecoder.decode(url, "UTF-8");

        assertTrue(url.startsWith("https://oss.example.com/tk/5/9/generation-tasks/43/"));
        assertTrue(url.contains("/generated-43.mp4?OSSAccessKeyId=ak&Expires="));
        assertTrue(url.contains("&response-content-disposition="));
        assertTrue(decodedUrl.contains("filename=\"2026-08-07-002.mp4\""));
        assertTrue(decodedUrl.contains(
                "filename*=UTF-8''2026-08-07-%E7%AE%A1%E7%90%86%E5%91%98-002.mp4"));
        assertTrue(url.contains("&Signature="));
    }

    @Test
    void refreshGeneratedAssetReadUrlRebuildsSignedDownloadFileName() throws Exception {
        TkGenerationOutputStorageServiceImpl service = new TkGenerationOutputStorageServiceImpl();
        TkGenerationProperties properties = ossProperties();
        properties.getUpload().getOss().setReadUrlExpireSeconds(3600);
        TkGenerationTaskMapper taskMapper = mock(TkGenerationTaskMapper.class);
        AdminUserApi adminUserApi = mock(AdminUserApi.class);
        ReflectionTestUtils.setField(service, "generationProperties", properties);
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "adminUserApi", adminUserApi);
        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .id(45L)
                .companyId(9L)
                .build();
        task.setTenantId(5L);
        task.setCreator("188");
        task.setCreateTime(LocalDateTime.of(2026, 8, 8, 12, 0));
        when(taskMapper.selectDailyTaskIds(eq(5L), eq("188"),
                eq(LocalDateTime.of(2026, 8, 8, 0, 0)),
                eq(LocalDateTime.of(2026, 8, 9, 0, 0))))
                .thenReturn(Arrays.asList(39L, 40L, 41L, 42L, 43L, 44L, 45L));
        AdminUserRespDTO user = new AdminUserRespDTO();
        user.setId(188L);
        user.setNickname("王曦若");
        when(adminUserApi.getUser(eq(188L))).thenReturn(user);

        String url = service.refreshGeneratedAssetReadUrl(task,
                "https://oss.example.com/tk/5/9/generation-tasks/45/20260808/generated-45.mp4?OSSAccessKeyId=old&Expires=1&response-content-disposition=old&Signature=old");
        String decodedUrl = URLDecoder.decode(url, "UTF-8");

        assertTrue(url.startsWith("https://oss.example.com/tk/5/9/generation-tasks/45/20260808/generated-45.mp4?"));
        assertTrue(decodedUrl.contains("filename=\"2026-08-08-007.mp4\""));
        assertTrue(decodedUrl.contains(
                "filename*=UTF-8''2026-08-08-%E7%8E%8B%E6%9B%A6%E8%8B%A5-007.mp4"));
        assertTrue(url.contains("&Signature="));
    }

    @Test
    void buildGeneratedAssetObjectKeyUsesGenerationTaskPath() {
        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .id(40L)
                .companyId(166L)
                .build();
        task.setTenantId(166L);

        String objectKey = TkGenerationOutputStorageServiceImpl.buildGeneratedAssetObjectKey(
                "tk", task, LocalDate.of(2026, 7, 16), "generated-40.mp4");

        assertEquals("tk/166/166/generation-tasks/40/20260716/generated-40.mp4", objectKey);
    }

    @Test
    void buildGeneratedAssetObjectKeyNormalizesUnsafeFileName() {
        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .id(41L)
                .companyId(7L)
                .build();
        task.setTenantId(3L);

        String objectKey = TkGenerationOutputStorageServiceImpl.buildGeneratedAssetObjectKey(
                "tk/", task, LocalDate.of(2026, 7, 16), "../subtitle 41.ass");

        assertEquals("tk/3/7/generation-tasks/41/20260716/subtitle-41.ass", objectKey);
    }

    private static TkGenerationProperties ossProperties() {
        TkGenerationProperties properties = new TkGenerationProperties();
        properties.getUpload().setStorageType("oss");
        properties.getUpload().getOss().setEnabled(true);
        properties.getUpload().getOss().setBucket("bucket");
        properties.getUpload().getOss().setEndpoint("oss-cn-beijing.aliyuncs.com");
        properties.getUpload().getOss().setPublicBaseUrl("https://oss.example.com");
        properties.getUpload().getOss().setAccessKeyId("ak");
        properties.getUpload().getOss().setAccessKeySecret("sk");
        properties.getUpload().getOss().setReadUrlExpireSeconds(0);
        properties.getUpload().getOss().setUploadPathPrefix("tk");
        return properties;
    }
}
