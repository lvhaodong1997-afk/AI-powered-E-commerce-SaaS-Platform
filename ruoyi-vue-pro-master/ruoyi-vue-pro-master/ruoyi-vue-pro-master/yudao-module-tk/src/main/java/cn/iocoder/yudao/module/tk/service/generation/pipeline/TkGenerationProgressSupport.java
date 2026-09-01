package cn.iocoder.yudao.module.tk.service.generation.pipeline;

final class TkGenerationProgressSupport {

    private TkGenerationProgressSupport() {
    }

    static int stageProgress(int start, int end, int completed, int total) {
        int normalizedStart = Math.max(0, Math.min(start, 100));
        int normalizedEnd = Math.max(normalizedStart, Math.min(end, 100));
        if (total <= 0) {
            return normalizedStart;
        }
        int normalizedCompleted = Math.max(0, Math.min(completed, total));
        return normalizedStart + (int) Math.round((normalizedEnd - normalizedStart)
                * (normalizedCompleted / (double) total));
    }

}
