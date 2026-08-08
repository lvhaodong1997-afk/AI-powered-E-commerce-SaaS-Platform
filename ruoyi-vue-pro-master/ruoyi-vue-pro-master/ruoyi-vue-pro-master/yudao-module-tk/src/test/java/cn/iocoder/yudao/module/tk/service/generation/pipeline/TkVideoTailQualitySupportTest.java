package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TkVideoTailQualitySupportTest {

    @Test
    void detectsLowDynamicTailWhenFrameHashesBarelyChange() {
        assertTrue(TkVideoTailQualitySupport.isLowDynamicTail(Arrays.asList(
                "a", "a", "a", "a", "a", "b", "a", "a", "a", "a"
        ), 0.30D));
    }

    @Test
    void acceptsTailWhenFrameHashesKeepChanging() {
        assertFalse(TkVideoTailQualitySupport.isLowDynamicTail(Arrays.asList(
                "a", "b", "c", "d", "e", "f", "g", "h", "i", "j"
        ), 0.30D));
    }

    @Test
    void detectsSubtitleAudioMismatchBeyondTolerance() {
        assertTrue(TkVideoTailQualitySupport.hasSubtitleAudioMismatch(42.2D, 39.8D, 1.0D));
        assertFalse(TkVideoTailQualitySupport.hasSubtitleAudioMismatch(42.2D, 41.5D, 1.0D));
    }

    @Test
    void detectsVideoShorterThanAudio() {
        assertTrue(TkVideoTailQualitySupport.isVideoShorterThanAudio(35.0D, 42.2D, 0.5D));
        assertFalse(TkVideoTailQualitySupport.isVideoShorterThanAudio(43.0D, 42.2D, 0.5D));
    }

}
