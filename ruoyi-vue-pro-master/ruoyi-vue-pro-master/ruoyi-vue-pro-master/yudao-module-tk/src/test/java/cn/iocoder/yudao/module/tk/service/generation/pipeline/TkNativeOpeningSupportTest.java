package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TkNativeOpeningSupportTest {

    @Test
    void nativeModeUsesActualOpeningAndBodyAudioDurations() {
        assertEquals(30D, TkNativeOpeningSupport.resolveEffectiveDuration(30D, 3D, 26D), 0.001D);
        assertEquals(32D, TkNativeOpeningSupport.resolveEffectiveDuration(30D, 3D, 29D), 0.001D);
        assertEquals(30D, TkNativeOpeningSupport.resolveEffectiveDuration(30D, 3.2D, 26.8D), 0.001D);
        assertEquals(35D, TkNativeOpeningSupport.resolveEffectiveDuration(30D, 31D, 4D), 0.001D);
    }

    @Test
    void nativeModeUsesActualOpeningDurationForBodyVideo() {
        assertEquals(29D, TkNativeOpeningSupport.resolveBodyDuration(30D, 3D, 29D), 0.001D);
        assertEquals(27.4D, TkNativeOpeningSupport.resolveBodyDuration(30D, 2.6D, 27.4D), 0.001D);
        assertEquals(26.8D, TkNativeOpeningSupport.resolveBodyDuration(30D, 3.2D, 26.8D), 0.001D);
    }

    @Test
    void nativeModeKeepsCompleteScriptWithoutTimeline() {
        assertEquals("Hook line Body line", TkNativeOpeningSupport.resolveNarrationScript(
                "Hook line Body line", null, TkNativeOpeningSupport.MODE_NATIVE));
    }

    @Test
    void nativeModeKeepsCompleteScriptWhenTimelineContainsS1Hook() {
        String timeline = "["
                + "{\"timeWindow\":\"0-3s\",\"segmentLibrary\":\"S1_HOOK\",\"scriptLine\":\"Hook line\"},"
                + "{\"timeWindow\":\"3-6s\",\"segmentLibrary\":\"S2_PAIN\",\"scriptLine\":\"Body line\"}"
                + "]";

        assertEquals("Hook line Body line", TkNativeOpeningSupport.resolveNarrationScript(
                "Hook line Body line", timeline, TkNativeOpeningSupport.MODE_NATIVE));
    }

    @Test
    void nativeModeIgnoresMissingOrMalformedTimeline() {
        assertEquals("Hook line Body line", TkNativeOpeningSupport.resolveNarrationScript(
                "Hook line Body line", "not-json", TkNativeOpeningSupport.MODE_NATIVE));
        assertEquals("Hook line Body line", TkNativeOpeningSupport.resolveNarrationScript(
                "Hook line Body line", "{\"segmentLibrary\":\"S1_HOOK\"}", TkNativeOpeningSupport.MODE_NATIVE));
        assertEquals("Hook line Body line", TkNativeOpeningSupport.resolveNarrationScript(
                "Hook line Body line", "[]", TkNativeOpeningSupport.MODE_NATIVE));
    }

    @Test
    void standardModeKeepsCompleteNarrationScript() {
        assertEquals("Hook line Body line", TkNativeOpeningSupport.resolveNarrationScript(
                "Hook line Body line", "[]", TkNativeOpeningSupport.MODE_STANDARD));
        assertFalse(TkNativeOpeningSupport.isNativeMode(null));
        assertTrue(TkNativeOpeningSupport.isNativeMode(TkNativeOpeningSupport.MODE_NATIVE));
    }

    @Test
    void nativeModeOffsetsSubtitleTimelineAndWordTimingAfterOpening() {
        TkSubtitleWord word = new TkSubtitleWord("Body", 0D, 0.8D, false);
        TkSubtitleSegment segment = new TkSubtitleSegment("Body", 0D, 0.8D, null, 0, 0,
                java.util.Collections.singletonList(word));
        TkSubtitleTimeline timeline = new TkSubtitleTimeline("en-US", 0.8D,
                java.util.Collections.singletonList(segment));

        TkNativeOpeningSupport.shiftTimeline(timeline, 3.2D);

        assertEquals(4.0D, timeline.getAudioDuration(), 0.001D);
        assertEquals(3.2D, segment.getStart(), 0.001D);
        assertEquals(4.0D, segment.getEnd(), 0.001D);
        assertEquals(3.2D, word.getStart(), 0.001D);
        assertEquals(4.0D, word.getEnd(), 0.001D);
    }

}
