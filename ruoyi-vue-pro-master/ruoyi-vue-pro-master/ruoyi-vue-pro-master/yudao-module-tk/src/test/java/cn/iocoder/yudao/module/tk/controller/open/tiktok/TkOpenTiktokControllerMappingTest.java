package cn.iocoder.yudao.module.tk.controller.open.tiktok;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TkOpenTiktokControllerMappingTest {

    @Test
    void authControllerExposesAdminApiPrefix() {
        assertTrue(Arrays.asList(TkOpenTiktokAuthController.class.getAnnotation(RequestMapping.class).value())
                .contains("/admin-api/tk/open/v1/tiktok"));
    }

    @Test
    void mediaControllerExposesAdminApiPrefix() {
        assertTrue(Arrays.asList(TkOpenTiktokMediaController.class.getAnnotation(RequestMapping.class).value())
                .contains("/admin-api/tk/open/v1/tiktok/media"));
    }

    @Test
    void publishControllerExposesAdminApiPrefix() {
        assertTrue(Arrays.asList(TkOpenTiktokPublishController.class.getAnnotation(RequestMapping.class).value())
                .contains("/admin-api/tk/open/v1/tiktok/publish"));
    }
}
