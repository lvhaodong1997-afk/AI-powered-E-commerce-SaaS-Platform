package cn.iocoder.yudao.module.tk.service.generation;

import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;
import cn.iocoder.yudao.module.tk.enums.TkGenerationStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Collection;

public final class TkGenerationBatchProgressSupport {

    private TkGenerationBatchProgressSupport() {
    }

    public static BatchProgress summarize(Integer expectedCount, Collection<TkGenerationTaskDO> tasks) {
        int expected = expectedCount == null || expectedCount < 0 ? 0 : expectedCount;
        int created = tasks == null ? 0 : tasks.size();
        int success = 0;
        int failed = 0;
        if (tasks != null) {
            for (TkGenerationTaskDO task : tasks) {
                if (TkGenerationStatusEnum.SUCCESS.equals(task.getStatus())) {
                    success++;
                } else if (TkGenerationStatusEnum.FAILED.equals(task.getStatus())) {
                    failed++;
                }
            }
        }
        int finished = success + failed;
        int running = Math.max(0, created - finished);
        int denominator = expected > 0 ? expected : created;
        int progressPercent = denominator <= 0 ? 0 : Math.min(100, Math.round(finished * 100F / denominator));
        return new BatchProgress(expected, created, success, failed, running, finished, progressPercent,
                resolveStatus(expected, created, success, failed, running, finished));
    }

    private static String resolveStatus(int expected, int created, int success, int failed, int running, int finished) {
        if (created <= 0) {
            return "PENDING";
        }
        int target = expected > 0 ? expected : created;
        if (finished >= target && failed <= 0) {
            return "COMPLETED";
        }
        if (finished >= target) {
            return "COMPLETED_WITH_FAILURES";
        }
        return running > 0 || finished > 0 ? "RUNNING" : "PENDING";
    }

    @Data
    @AllArgsConstructor
    public static class BatchProgress {
        private int expectedCount;
        private int createdCount;
        private int successCount;
        private int failedCount;
        private int runningCount;
        private int finishedCount;
        private int progressPercent;
        private String status;
    }
}
