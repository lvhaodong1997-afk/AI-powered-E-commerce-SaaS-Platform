package cn.iocoder.yudao.module.tk.service.upload;

import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TkOssObjectStorageServiceTest {

    @Test
    void resolveReadUrlSignsHistoricalPrivateOssUrl() {
        TkOssObjectStorageService service = new TkOssObjectStorageService();
        TkGenerationProperties properties = new TkGenerationProperties();
        properties.getUpload().setStorageType("oss");
        properties.getUpload().getOss().setEnabled(true);
        properties.getUpload().getOss().setBucket("bucket");
        properties.getUpload().getOss().setEndpoint("oss-cn-example.aliyuncs.com");
        properties.getUpload().getOss().setPublicBaseUrl("https://cdn.example.com");
        properties.getUpload().getOss().setAccessKeyId("access-key");
        properties.getUpload().getOss().setAccessKeySecret("access-secret");
        properties.getUpload().getOss().setReadUrlExpireSeconds(3600);
        ReflectionTestUtils.setField(service, "generationProperties", properties);

        String resolved = service.resolveReadUrl(
                "https://cdn.example.com/tk/100/200/generation-openings/opening%20one.mp4");

        assertTrue(resolved.startsWith(
                "https://cdn.example.com/tk/100/200/generation-openings/opening%20one.mp4?OSSAccessKeyId=access-key&Expires="));
        assertTrue(resolved.contains("&Signature="));
    }

    @Test
    void resolveReadUrlLeavesExternalUrlUnchanged() {
        TkOssObjectStorageService service = new TkOssObjectStorageService();

        assertEquals("https://example.com/opening.mp4", service.resolveReadUrl("https://example.com/opening.mp4"));
    }

}
