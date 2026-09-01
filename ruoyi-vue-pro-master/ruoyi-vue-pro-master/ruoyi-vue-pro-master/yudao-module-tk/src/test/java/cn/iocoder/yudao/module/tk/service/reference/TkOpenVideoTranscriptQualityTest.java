package cn.iocoder.yudao.module.tk.service.reference;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TkOpenVideoTranscriptQualityTest {

    @Test
    void rejectsEmptyTranscriptOrTimeline() {
        assertFalse(TkOpenVideoTranscriptQuality.isUsable("", "[]"));
        assertFalse(TkOpenVideoTranscriptQuality.isUsable("有文本", "[]"));
    }

    @Test
    void rejectsRepeatedHallucinatedText() {
        assertFalse(TkOpenVideoTranscriptQuality.isUsable("哈哈哈哈哈哈哈哈", "[{\"text\":\"哈哈哈哈哈哈哈哈\"}]"));
    }

    @Test
    void acceptsNonEmptyTimelineAndNaturalText() {
        assertTrue(TkOpenVideoTranscriptQuality.isUsable("团队今天回归比赛", "[{\"text\":\"团队今天回归比赛\"}]"));
    }

}
