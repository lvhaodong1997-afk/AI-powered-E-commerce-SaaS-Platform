package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

class TkNarrationDurationSupportTest {
    private TkGenerationTaskDO task(String config) {
        return TkGenerationTaskDO.builder().targetDuration(35).generationRouteConfig(config).build();
    }

    @Test void optInUsesNarrationEvenWhenShorterThanMinimumVideoDuration() {
        DefaultTkGenerationPipelineService pipeline = new DefaultTkGenerationPipelineService();
        ReflectionTestUtils.setField(pipeline, "generationProperties", new TkGenerationProperties());
        assertEquals(8, pipeline.resolveEffectiveTargetDuration(task("{\"durationMode\":\"FOLLOW_NARRATION\"}"), 4.125));
        assertEquals(4.125, TkNarrationDurationSupport.renderDuration(
                task("{\"durationMode\":\"FOLLOW_NARRATION\"}"), 4.125, 500));
    }

    @Test void defaultWebDurationStillUsesLongerOfTargetAndVoice() {
        DefaultTkGenerationPipelineService pipeline = new DefaultTkGenerationPipelineService();
        ReflectionTestUtils.setField(pipeline, "generationProperties", new TkGenerationProperties());
        assertEquals(35, pipeline.resolveEffectiveTargetDuration(task(null), 4.125));
        assertEquals(35, TkNarrationDurationSupport.renderDuration(task(null), 4.125, 500));
    }

    @Test void missingNonFiniteAndOverLimitNarrationMustFailInsteadOfTruncating() {
        TkGenerationTaskDO task = task("{\"durationMode\":\"FOLLOW_NARRATION\"}");
        assertThrows(IllegalStateException.class, () -> TkNarrationDurationSupport.renderDuration(task, null, 500));
        assertThrows(IllegalStateException.class, () -> TkNarrationDurationSupport.renderDuration(task, Double.NaN, 500));
        assertThrows(IllegalStateException.class, () -> TkNarrationDurationSupport.renderDuration(task, 35.01, 35));
    }

    @Test void nativeOpeningIsIncludedSoBodyNarrationCannotBeTruncated() {
        TkGenerationTaskDO task = task("{\"durationMode\":\"FOLLOW_NARRATION\"}");
        task.setOpeningProcessMode("NATIVE");
        task.setOpeningVideoUrl("opening.mp4");
        task.setOpeningDurationMs(10000L);
        assertEquals(30D, TkNarrationDurationSupport.renderDuration(task, 20D, 500));
        assertThrows(IllegalStateException.class, () -> TkNarrationDurationSupport.renderDuration(task, 25D, 30));
        task.setOpeningDurationMs(null);
        assertThrows(IllegalStateException.class, () -> TkNarrationDurationSupport.renderDuration(task, 20D, 500));
    }
}
