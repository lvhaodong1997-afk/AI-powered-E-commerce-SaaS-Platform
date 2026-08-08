package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TkRenderMediaSupportTest {

    @Test
    void shouldKeepOriginalNarrationAudioWhenDurationDifferenceIsSmallButNoticeable() {
        assertFalse(TkRenderMediaSupport.shouldAdaptAudio(20.0D, 21.624D));
        assertFalse(TkRenderMediaSupport.shouldAdaptAudio(20.0D, 18.8D));
    }

    @Test
    void shouldNotAdaptAudioWhenDifferenceIsTooLargeOrNegligible() {
        assertFalse(TkRenderMediaSupport.shouldAdaptAudio(20.0D, 25.0D));
        assertFalse(TkRenderMediaSupport.shouldAdaptAudio(20.0D, 20.08D));
        assertFalse(TkRenderMediaSupport.shouldAdaptAudio(0D, 20.0D));
    }

    @Test
    void buildAtempoFilterSpeedsAudioToTargetVideoDuration() {
        assertEquals("atempo=1.0812", TkRenderMediaSupport.buildAtempoFilter(20.0D, 21.624D));
        assertEquals("atempo=0.94", TkRenderMediaSupport.buildAtempoFilter(20.0D, 18.8D));
    }

    @Test
    void buildVideoSpeedFilterCompressesWholeSectionToTargetDuration() {
        assertEquals("setpts=PTS/1.142857", TkRenderMediaSupport.buildVideoSpeedFilter(8.0D, 7.0D));
        assertEquals("setpts=PTS/1.333333", TkRenderMediaSupport.buildVideoSpeedFilter(8.0D, 6.0D));
    }

    @Test
    void sourceCacheFileNameIsStableForSameUrlAndKeepsSafeName() {
        String first = TkRenderMediaSupport.sourceCacheFileName("https://example.com/a/video.mov?token=1", "my/video.mov");
        String second = TkRenderMediaSupport.sourceCacheFileName("https://example.com/a/video.mov?token=1", "my/video.mov");
        String third = TkRenderMediaSupport.sourceCacheFileName("https://example.com/a/video.mov?token=2", "my/video.mov");

        assertEquals(first, second);
        assertTrue(first.startsWith("source-cache-"));
        assertTrue(first.endsWith("-my_video.mov"));
        assertFalse(first.equals(third));
    }

}
