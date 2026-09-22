package cn.iocoder.yudao.module.tk.controller.open.tiktok;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;

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

    @Test
    void publishControllerExposesTaskMetricsEndpoint() throws Exception {
        GetMapping mapping = TkOpenTiktokPublishController.class
                .getDeclaredMethod("metrics", String.class).getAnnotation(GetMapping.class);

        assertTrue(Arrays.asList(mapping.value()).contains("/tasks/{taskId}/metrics"));
    }

    @Test
    void publishControllerExposesBatchTaskMetricsEndpoint() throws Exception {
        PostMapping mapping = TkOpenTiktokPublishController.class
                .getDeclaredMethod("batchMetrics", Class.forName(
                        "cn.iocoder.yudao.module.tk.controller.open.tiktok.vo.TkOpenTiktokPublishVO$MetricsBatchReq"))
                .getAnnotation(PostMapping.class);

        assertTrue(Arrays.asList(mapping.value()).contains("/tasks/metrics/batch"));
    }

    @Test
    void publishControllerExposesScheduleMutationEndpoints() throws Exception {
        PostMapping reschedule = TkOpenTiktokPublishController.class
                .getDeclaredMethod("reschedule", String.class,
                        Class.forName("cn.iocoder.yudao.module.tk.controller.open.tiktok.vo.TkOpenTiktokPublishVO$ScheduleReq"),
                        String.class)
                .getAnnotation(PostMapping.class);
        PostMapping cancel = TkOpenTiktokPublishController.class
                .getDeclaredMethod("cancel", String.class)
                .getAnnotation(PostMapping.class);

        assertTrue(Arrays.asList(reschedule.value()).contains("/tasks/{taskId}/reschedule"));
        assertTrue(Arrays.asList(cancel.value()).contains("/tasks/{taskId}/cancel"));
    }

    @Test
    void authControllerExposesProfileRefreshEndpoint() throws Exception {
        PostMapping mapping = TkOpenTiktokAuthController.class
                .getDeclaredMethod("refreshProfile", String.class)
                .getAnnotation(PostMapping.class);

        assertTrue(Arrays.asList(mapping.value()).contains("/connections/{connectionId}/profile/refresh"));
    }
}
