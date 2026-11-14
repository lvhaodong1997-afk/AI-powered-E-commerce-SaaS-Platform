package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;
import com.fasterxml.jackson.databind.JsonNode;

/** Opt-in only: legacy web tasks keep their target-duration semantics. */
public final class TkNarrationDurationSupport {
    private TkNarrationDurationSupport() { }

    public static boolean followsNarration(TkGenerationTaskDO task) {
        if (task.getGenerationRouteConfig() == null) return false;
        JsonNode config = JsonUtils.parseTree(task.getGenerationRouteConfig());
        return config != null && "FOLLOW_NARRATION".equals(config.path("durationMode").asText());
    }

    public static double renderDuration(TkGenerationTaskDO task, Double audioDuration, Integer configuredLimit) {
        if (!followsNarration(task)) return TkVideoDurationSupport.normalize(task.getTargetDuration());
        int limit = configuredLimit == null || configuredLimit <= 0 ? TkVideoDurationSupport.MAX_TARGET_DURATION
                : Math.min(configuredLimit, TkVideoDurationSupport.MAX_TARGET_DURATION);
        if (audioDuration == null || !Double.isFinite(audioDuration) || audioDuration <= 0) {
            throw new IllegalStateException("无法识别完整配音时长，已停止生成以避免截断口播");
        }
        double totalDuration = audioDuration;
        if (TkNativeOpeningSupport.isNativeMode(task.getOpeningProcessMode())) {
            if (task.getOpeningDurationMs() == null || task.getOpeningDurationMs() <= 0L) {
                throw new IllegalStateException("无法识别黄金开头视频时长，已停止生成以避免截断口播");
            }
            totalDuration += task.getOpeningDurationMs() / 1000D;
        }
        if (totalDuration > limit) {
            throw new IllegalStateException("完整配音超过系统上限 " + limit + " 秒，请缩短正文后重试");
        }
        return totalDuration;
    }
}
