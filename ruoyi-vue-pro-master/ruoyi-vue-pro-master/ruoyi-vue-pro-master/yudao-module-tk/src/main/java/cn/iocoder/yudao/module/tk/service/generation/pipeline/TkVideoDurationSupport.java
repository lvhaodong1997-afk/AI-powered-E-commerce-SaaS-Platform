package cn.iocoder.yudao.module.tk.service.generation.pipeline;

public final class TkVideoDurationSupport {

    public static final int DEFAULT_TARGET_DURATION = 15;
    public static final int MIN_TARGET_DURATION = 8;
    public static final int MAX_TARGET_DURATION = 60;

    public static int normalize(Integer duration) {
        if (duration == null || duration <= 0) {
            return DEFAULT_TARGET_DURATION;
        }
        return Math.max(MIN_TARGET_DURATION, Math.min(duration, MAX_TARGET_DURATION));
    }

    public static int normalize(Integer duration, Integer maxDuration) {
        int normalized = normalize(duration);
        if (maxDuration != null && maxDuration > 0) {
            normalized = Math.min(normalized, maxDuration);
        }
        return normalized;
    }

    private TkVideoDurationSupport() {
    }

}
