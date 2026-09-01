package cn.iocoder.yudao.module.tk.service.tiktok;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TkTiktokUploadPlannerTest {

    @Test
    void usesWholeFileForVideosAtMost64Mb() {
        TkTiktokUploadPlanner.UploadPlan plan = TkTiktokUploadPlanner.plan(64_000_000L);

        assertEquals(64_000_000L, plan.getChunkSize());
        assertEquals(1, plan.getTotalChunkCount());
        assertEquals(64_000_000L, plan.chunkLength(0));
    }

    @Test
    void usesFloorChunkCountAndMergesRemainderIntoFinalChunk() {
        TkTiktokUploadPlanner.UploadPlan plan = TkTiktokUploadPlanner.plan(200_000_000L);

        assertEquals(32_000_000L, plan.getChunkSize());
        assertEquals(6, plan.getTotalChunkCount());
        assertEquals(32_000_000L, plan.chunkLength(0));
        assertEquals(40_000_000L, plan.chunkLength(5));
        assertEquals(200_000_000L, plan.getVideoSize());
    }

    @Test
    void keepsEveryChunkWithinTikTokLimitsForOneGbVideo() {
        TkTiktokUploadPlanner.UploadPlan plan = TkTiktokUploadPlanner.plan(999_999_999L);

        assertEquals(31, plan.getTotalChunkCount());
        assertEquals(999_999_999L, plan.totalChunkBytes());
        for (int index = 0; index < plan.getTotalChunkCount(); index++) {
            long length = plan.chunkLength(index);
            assertTrue(length >= 5_000_000L);
            assertTrue(length <= 64_000_000L);
        }
    }

    @Test
    void rejectsEmptyOrOversizedVideo() {
        assertThrowsIllegalArgument(() -> TkTiktokUploadPlanner.plan(0));
        assertThrowsIllegalArgument(() -> TkTiktokUploadPlanner.plan(4_000_000_001L));
    }

    private void assertThrowsIllegalArgument(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Expected IllegalArgumentException");
    }
}
