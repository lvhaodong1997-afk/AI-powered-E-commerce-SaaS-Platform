package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TkGenerationProgressSupportTest {

    @Test
    void progressUsesActualCompletedItemCountWithinStageRange() {
        assertEquals(66, TkGenerationProgressSupport.stageProgress(66, 72, 0, 13));
        assertEquals(69, TkGenerationProgressSupport.stageProgress(66, 72, 6, 13));
        assertEquals(72, TkGenerationProgressSupport.stageProgress(66, 72, 13, 13));
    }

    @Test
    void progressFallsBackToStageStartWhenItemTotalIsUnknown() {
        assertEquals(72, TkGenerationProgressSupport.stageProgress(72, 88, 3, 0));
    }

}
