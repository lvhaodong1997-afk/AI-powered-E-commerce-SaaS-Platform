package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TkVideoDurationSupportTest {

    @Test
    void normalizeShouldSupportUpToFiveHundredSeconds() {
        assertEquals(500, TkVideoDurationSupport.normalize(500));
        assertEquals(500, TkVideoDurationSupport.normalize(501));
    }

    @Test
    void normalizeShouldStillHonorLowerConfiguredMaximum() {
        assertEquals(120, TkVideoDurationSupport.normalize(500, 120));
    }

}
