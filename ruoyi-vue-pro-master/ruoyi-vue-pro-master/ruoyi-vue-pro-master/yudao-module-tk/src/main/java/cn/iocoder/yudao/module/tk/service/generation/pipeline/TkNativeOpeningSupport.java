package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import cn.hutool.core.util.StrUtil;

/**
 * Rules shared by the native-opening generation path.
 */
public final class TkNativeOpeningSupport {

    public static final String MODE_NATIVE = "NATIVE";
    public static final String MODE_STANDARD = "STANDARD";

    private TkNativeOpeningSupport() {
    }

    public static String normalizeMode(String mode) {
        return MODE_NATIVE.equalsIgnoreCase(StrUtil.trimToEmpty(mode)) ? MODE_NATIVE : MODE_STANDARD;
    }

    public static boolean isNativeMode(String mode) {
        return MODE_NATIVE.equals(normalizeMode(mode));
    }

    public static double resolveEffectiveDuration(double targetDuration, double openingDuration,
                                                  double bodyAudioDuration) {
        double target = Math.max(0D, targetDuration);
        double opening = Math.max(0D, openingDuration);
        double audio = Math.max(0D, bodyAudioDuration);
        return Math.max(target, opening + audio);
    }

    public static double resolveBodyDuration(double targetDuration, double openingDuration,
                                             double bodyAudioDuration) {
        double effectiveDuration = resolveEffectiveDuration(targetDuration, openingDuration, bodyAudioDuration);
        return Math.max(0D, effectiveDuration - Math.max(0D, openingDuration));
    }

    /**
     * The native opening supplies its own media boundary, so voiceover and subtitles always use the full script.
     * The legacy parameters remain in the signature for source compatibility.
     */
    public static String resolveNarrationScript(String fullScript, String segmentTimeline, String mode) {
        return StrUtil.trimToEmpty(fullScript);
    }

    public static void shiftTimeline(TkSubtitleTimeline timeline, double offsetSeconds) {
        if (timeline == null || offsetSeconds <= 0D) {
            return;
        }
        timeline.setAudioDuration(timeline.getAudioDuration() + offsetSeconds);
        if (timeline.getSegments() == null) {
            return;
        }
        for (TkSubtitleSegment segment : timeline.getSegments()) {
            segment.setStart(segment.getStart() + offsetSeconds);
            segment.setEnd(segment.getEnd() + offsetSeconds);
            if (segment.getWords() == null) {
                continue;
            }
            for (TkSubtitleWord word : segment.getWords()) {
                word.setStart(word.getStart() + offsetSeconds);
                word.setEnd(word.getEnd() + offsetSeconds);
            }
        }
    }

}
