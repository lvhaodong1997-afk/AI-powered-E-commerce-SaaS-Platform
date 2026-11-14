package cn.iocoder.yudao.module.tk.service.automation;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkGenerationTaskCreateReqVO;
import cn.iocoder.yudao.module.tk.service.generation.TkGenerationTaskServiceImpl;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkNarrationDurationSupport;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

class TkAutomationSnapshotTest {
    @Test void durationSurvivesCopyAndRouteSnapshotWithoutChangingOldRequests() {
        TkGenerationTaskServiceImpl service = new TkGenerationTaskServiceImpl();
        TkGenerationTaskCreateReqVO request = new TkGenerationTaskCreateReqVO();
        request.setMaterialPurpose("ECOMMERCE");
        request.setScriptMode("MANUAL");
        request.setClipPlanMode("FULL_POOL_RANDOM");
        request.setManualOriginalText("完整正文");
        request.setManualTranslationEnabled(false);
        request.setDurationMode("FOLLOW_NARRATION");
        TkGenerationTaskCreateReqVO copy = ReflectionTestUtils.invokeMethod(service, "copyCreateReqVO", request);
        String config = ReflectionTestUtils.invokeMethod(service, "resolveGenerationRouteConfig", copy, "ECOMMERCE", null);
        assertTrue(TkNarrationDurationSupport.followsNarration(TkGenerationTaskDO.builder().generationRouteConfig(config).build()));
        assertEquals("完整正文", JsonUtils.parseTree(config).path("manualOriginalText").asText());
        assertEquals("MANUAL", JsonUtils.parseTree(config).path("scriptMode").asText());
        request.setDurationMode(null);
        String oldConfig = ReflectionTestUtils.invokeMethod(service, "resolveGenerationRouteConfig", request, "ECOMMERCE", null);
        assertFalse(JsonUtils.parseTree(oldConfig).has("durationMode"));
    }
}
