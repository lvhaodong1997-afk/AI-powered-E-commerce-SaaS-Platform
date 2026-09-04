package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Rules shared by the native-opening generation path.
 */
public final class TkNativeOpeningSupport {

    public static final String MODE_NATIVE = "NATIVE";
    public static final String MODE_STANDARD = "STANDARD";
    private static final String INVALID_TIMELINE_MESSAGE = "原生开头模式缺少可用的分段时间轴，无法安全排除黄金开头文案";

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

    public static String resolveNarrationScript(String fullScript, String segmentTimeline, String mode) {
        String original = StrUtil.trimToEmpty(fullScript);
        if (!isNativeMode(mode)) {
            return original;
        }
        if (StrUtil.isBlank(segmentTimeline)) {
            throw new IllegalStateException(INVALID_TIMELINE_MESSAGE);
        }
        try {
            JsonNode root = JsonUtils.parseTree(segmentTimeline);
            if (root == null || !root.isArray() || root.isEmpty()) {
                throw new IllegalStateException(INVALID_TIMELINE_MESSAGE);
            }
            StringBuilder body = new StringBuilder();
            boolean hasHook = false;
            for (JsonNode item : root) {
                if (item == null || !item.isObject()) {
                    throw new IllegalStateException(INVALID_TIMELINE_MESSAGE);
                }
                String segment = item.path("segmentLibrary").asText("");
                if (StrUtil.isBlank(segment)) {
                    throw new IllegalStateException(INVALID_TIMELINE_MESSAGE);
                }
                if ("S1_HOOK".equalsIgnoreCase(segment)) {
                    hasHook = true;
                    continue;
                }
                String scriptLine = StrUtil.trimToEmpty(item.path("scriptLine").asText(""));
                if (StrUtil.isBlank(scriptLine)) {
                    continue;
                }
                if (body.length() > 0) {
                    body.append(' ');
                }
                body.append(scriptLine);
            }
            return hasHook ? body.toString() : original;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ignored) {
            throw new IllegalStateException(INVALID_TIMELINE_MESSAGE, ignored);
        }
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
