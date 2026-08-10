package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TkVideoDurationSupportTest {

    @Test
    void normalizeShouldSupportUpToOneHundredEightySeconds() {
        assertEquals(180, TkVideoDurationSupport.normalize(180));
        assertEquals(180, TkVideoDurationSupport.normalize(181));
    }

    @Test
    void normalizeShouldStillHonorLowerConfiguredMaximum() {
        assertEquals(120, TkVideoDurationSupport.normalize(180, 120));
    }

}
