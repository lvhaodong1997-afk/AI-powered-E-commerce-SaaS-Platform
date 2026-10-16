package cn.iocoder.yudao.module.tk.controller.admin.tiktok;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TkOpenTiktokPublishQueueControllerMappingTest {

    @Test
    void exposesReadEndpointsWithQueueQueryPermission() throws Exception {
        assertTrue(Arrays.asList(TkOpenTiktokPublishQueueController.class.getDeclaredMethod(
                "summary", java.time.LocalDate.class, int.class).getAnnotation(GetMapping.class).value())
                .contains("/summary"));
        assertEquals("@ss.hasPermission('tk:open-tiktok-publish:queue-query')",
                TkOpenTiktokPublishQueueController.class.getDeclaredMethod(
                        "details", java.time.LocalDate.class, String.class, int.class)
                        .getAnnotation(PreAuthorize.class).value());
    }

    @Test
    void exposesRecoveryAndCancelEndpoints() throws Exception {
        assertTrue(Arrays.asList(TkOpenTiktokPublishQueueController.class.getDeclaredMethod(
                "resume", int.class).getAnnotation(PostMapping.class).value()).contains("/resume"));
        assertTrue(Arrays.asList(TkOpenTiktokPublishQueueController.class.getDeclaredMethod(
                "sync", int.class).getAnnotation(PostMapping.class).value()).contains("/sync"));
        assertEquals("@ss.hasPermission('tk:open-tiktok-publish:queue-cancel')",
                TkOpenTiktokPublishQueueController.class.getDeclaredMethod(
                        "cancel", String.class, String.class).getAnnotation(PreAuthorize.class).value());
    }
}
