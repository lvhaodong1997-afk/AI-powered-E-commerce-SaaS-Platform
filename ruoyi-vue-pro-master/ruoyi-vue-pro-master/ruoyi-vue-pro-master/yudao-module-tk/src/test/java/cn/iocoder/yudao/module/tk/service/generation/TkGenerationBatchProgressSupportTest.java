package cn.iocoder.yudao.module.tk.service.generation;

import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;
import cn.iocoder.yudao.module.tk.enums.TkGenerationStatusEnum;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TkGenerationBatchProgressSupportTest {

    @Test
    void summarizeShouldCountTerminalAndRunningTasks() {
        TkGenerationBatchProgressSupport.BatchProgress progress = TkGenerationBatchProgressSupport.summarize(
                5,
                Arrays.asList(
                        task(TkGenerationStatusEnum.SUCCESS),
                        task(TkGenerationStatusEnum.SUCCESS),
                        task(TkGenerationStatusEnum.FAILED),
                        task(TkGenerationStatusEnum.RENDERING)
                ));

        assertEquals(5, progress.getExpectedCount());
        assertEquals(4, progress.getCreatedCount());
        assertEquals(2, progress.getSuccessCount());
        assertEquals(1, progress.getFailedCount());
        assertEquals(1, progress.getRunningCount());
        assertEquals(3, progress.getFinishedCount());
        assertEquals(60, progress.getProgressPercent());
        assertEquals("RUNNING", progress.getStatus());
    }

    @Test
    void summarizeShouldMarkCompletedWhenAllCreatedTasksFinish() {
        TkGenerationBatchProgressSupport.BatchProgress progress = TkGenerationBatchProgressSupport.summarize(
                2,
                Arrays.asList(task(TkGenerationStatusEnum.SUCCESS), task(TkGenerationStatusEnum.FAILED)));

        assertEquals(100, progress.getProgressPercent());
        assertEquals("COMPLETED_WITH_FAILURES", progress.getStatus());
    }

    private TkGenerationTaskDO task(String status) {
        return TkGenerationTaskDO.builder().status(status).build();
    }
}
