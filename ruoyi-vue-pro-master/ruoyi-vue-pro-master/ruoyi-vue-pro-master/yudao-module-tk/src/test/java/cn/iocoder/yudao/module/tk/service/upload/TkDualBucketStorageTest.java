package cn.iocoder.yudao.module.tk.service.upload;

import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

class TkDualBucketStorageTest {
    @Test void independentPublishBucketIsDisabledByDefault() {
        Object config = ReflectionTestUtils.getField(new TkGenerationProperties().getUpload(), "publishOss");
        assertNotNull(config, "independent publish OSS configuration is missing");
        assertEquals(false, ReflectionTestUtils.getField(config, "enabled"));
    }
    @Test void publishRouteMustNotChangeMaterialRoute() throws Exception {
        TkGenerationProperties props = configured();
        TkGenerationProperties.Oss publish = oss("tk-video-create", "https://tkpublish.fnn.net.cn");
        ReflectionTestUtils.setField(props.getUpload(), "publishOss", publish);
        TkOssObjectStorageService material = new TkOssObjectStorageService();
        ReflectionTestUtils.setField(material, "generationProperties", props);
        TkOssObjectStorageService target = (TkOssObjectStorageService) ReflectionTestUtils.invokeMethod(material, "publishStorage");
        assertEquals("https://tk-video-create.oss-cn-beijing.aliyuncs.com", target.browserUploadUrl());
        assertEquals("https://tk-material-factory.oss-cn-beijing.aliyuncs.com", material.browserUploadUrl());
        String old = material.resolveReadUrl("https://material.example/tk/1/2/old.mp4");
        assertTrue(old.contains("OSSAccessKeyId=tk-material-factory-key"));
        assertEquals("https://tkpublish.fnn.net.cn/tk/1/2/new.mp4", material.resolveReadUrl("https://tkpublish.fnn.net.cn/tk/1/2/new.mp4"));
    }
    @Test void enabledIncompletePublishConfigNeverFallsBack() {
        TkGenerationProperties props = configured();
        TkGenerationProperties.Oss publish = new TkGenerationProperties.Oss(); publish.setEnabled(true);
        ReflectionTestUtils.setField(props.getUpload(), "publishOss", publish);
        TkOssObjectStorageService service = new TkOssObjectStorageService();
        ReflectionTestUtils.setField(service, "generationProperties", props);
        assertThrows(IllegalStateException.class, () -> ReflectionTestUtils.invokeMethod(service, "publishStorage"));
    }
    @Test void publishSessionPinsBucketAcrossConfigurationToggle() {
        TkGenerationProperties props = configured();
        props.getUpload().setPublishOss(oss("tk-video-create", "https://tkpublish.fnn.net.cn"));
        TkOssObjectStorageService storage = new TkOssObjectStorageService();
        ReflectionTestUtils.setField(storage, "generationProperties", props);
        cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService scope = org.mockito.Mockito.mock(cn.iocoder.yudao.module.tk.service.scope.TkDataScopeService.class);
        org.mockito.Mockito.when(scope.getCurrentScope()).thenReturn(new cn.iocoder.yudao.module.tk.service.scope.TkUserScope(7L, 1L, "USER", 2L));
        cn.iocoder.yudao.module.tk.dal.mysql.TkUploadSessionMapper mapper = org.mockito.Mockito.mock(cn.iocoder.yudao.module.tk.dal.mysql.TkUploadSessionMapper.class);
        TkUploadSessionService service = new TkUploadSessionService();
        ReflectionTestUtils.setField(service, "generationProperties", props);
        ReflectionTestUtils.setField(service, "ossObjectStorageService", storage);
        ReflectionTestUtils.setField(service, "dataScopeService", scope);
        ReflectionTestUtils.setField(service, "sessionMapper", mapper);
        service.createTiktok("abc", 2L, "movie.mp4", 100L, "video/mp4", "oss");
        org.mockito.ArgumentCaptor<cn.iocoder.yudao.module.tk.dal.dataobject.TkUploadSessionDO> captured = org.mockito.ArgumentCaptor.forClass(cn.iocoder.yudao.module.tk.dal.dataobject.TkUploadSessionDO.class);
        org.mockito.Mockito.verify(mapper).insert(captured.capture());
        assertEquals("https://tkpublish.fnn.net.cn/tk/1/2/tiktok-publish-media/abc.mp4", captured.getValue().getStorageUrl());
        props.getUpload().getPublishOss().setEnabled(false);
        assertEquals("tk-video-create", storage.storageForSession(captured.getValue()).bucket());
    }
    static TkGenerationProperties configured() { TkGenerationProperties p = new TkGenerationProperties(); p.getUpload().setOss(oss("tk-material-factory", "https://material.example")); p.getUpload().getOss().setReadUrlExpireSeconds(3600); return p; }
    static TkGenerationProperties.Oss oss(String bucket, String base) { TkGenerationProperties.Oss o = new TkGenerationProperties.Oss(); o.setEnabled(true); o.setBucket(bucket); o.setEndpoint("oss-cn-beijing.aliyuncs.com"); o.setRegion("cn-beijing"); o.setPublicBaseUrl(base); o.setAccessKeyId(bucket + "-key"); o.setAccessKeySecret("secret"); return o; }
}
