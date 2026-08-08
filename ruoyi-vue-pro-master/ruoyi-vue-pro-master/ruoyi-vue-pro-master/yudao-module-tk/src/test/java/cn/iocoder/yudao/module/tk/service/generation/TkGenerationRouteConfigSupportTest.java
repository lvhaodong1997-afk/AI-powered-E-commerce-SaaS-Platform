package cn.iocoder.yudao.module.tk.service.generation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TkGenerationRouteConfigSupportTest {

    @Test
    void resolveClipPlanModeDefaultsToSegmentedForBlankConfig() {
        assertEquals(TkGenerationRouteConfigSupport.ClipPlanMode.SEGMENTED,
                TkGenerationRouteConfigSupport.resolveClipPlanMode(null));
        assertEquals(TkGenerationRouteConfigSupport.ClipPlanMode.SEGMENTED,
                TkGenerationRouteConfigSupport.resolveClipPlanMode(""));
        assertEquals(TkGenerationRouteConfigSupport.ClipPlanMode.SEGMENTED,
                TkGenerationRouteConfigSupport.resolveClipPlanMode("   "));
    }

    @Test
    void resolveClipPlanModeReadsFullPoolRandomMode() {
        assertEquals(TkGenerationRouteConfigSupport.ClipPlanMode.FULL_POOL_RANDOM,
                TkGenerationRouteConfigSupport.resolveClipPlanMode("{\"clipPlanMode\":\"FULL_POOL_RANDOM\"}"));
    }

    @Test
    void buildClipPlanModeConfigWritesSelectedMode() {
        String routeConfig = TkGenerationRouteConfigSupport.buildClipPlanModeConfig("FULL_POOL_RANDOM");

        assertEquals(TkGenerationRouteConfigSupport.ClipPlanMode.FULL_POOL_RANDOM,
                TkGenerationRouteConfigSupport.resolveClipPlanMode(routeConfig));
    }

    @Test
    void normalizeClipPlanModeDefaultsInvalidSelectionToSegmented() {
        assertEquals(TkGenerationRouteConfigSupport.ClipPlanMode.SEGMENTED,
                TkGenerationRouteConfigSupport.normalizeClipPlanMode(null));
        assertEquals(TkGenerationRouteConfigSupport.ClipPlanMode.SEGMENTED,
                TkGenerationRouteConfigSupport.normalizeClipPlanMode("bad-mode"));
    }
}
