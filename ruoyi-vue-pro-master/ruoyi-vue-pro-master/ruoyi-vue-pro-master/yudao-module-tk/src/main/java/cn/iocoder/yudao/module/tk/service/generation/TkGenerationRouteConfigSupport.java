package cn.iocoder.yudao.module.tk.service.generation;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Locale;

public final class TkGenerationRouteConfigSupport {

    private static final String CLIP_PLAN_MODE_FIELD = "clipPlanMode";

    private TkGenerationRouteConfigSupport() {
    }

    public static ClipPlanMode resolveClipPlanMode(String routeConfig) {
        if (StrUtil.isBlank(routeConfig)) {
            return ClipPlanMode.SEGMENTED;
        }
        JsonNode root = JsonUtils.parseTree(routeConfig);
        if (root == null || !root.isObject()) {
            return ClipPlanMode.SEGMENTED;
        }
        String value = StrUtil.trimToEmpty(root.path(CLIP_PLAN_MODE_FIELD).asText());
        if (StrUtil.isBlank(value)) {
            return ClipPlanMode.SEGMENTED;
        }
        try {
            return ClipPlanMode.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return ClipPlanMode.SEGMENTED;
        }
    }

    public static boolean isFullPoolRandom(String routeConfig) {
        return resolveClipPlanMode(routeConfig) == ClipPlanMode.FULL_POOL_RANDOM;
    }

    public static ClipPlanMode normalizeClipPlanMode(String clipPlanMode) {
        String value = StrUtil.trimToEmpty(clipPlanMode);
        if (StrUtil.isBlank(value)) {
            return ClipPlanMode.SEGMENTED;
        }
        try {
            return ClipPlanMode.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return ClipPlanMode.SEGMENTED;
        }
    }

    public static String buildClipPlanModeConfig(String clipPlanMode) {
        return "{\"clipPlanMode\":\"" + normalizeClipPlanMode(clipPlanMode).name() + "\"}";
    }

    public enum ClipPlanMode {

        SEGMENTED,
        FULL_POOL_RANDOM

    }

}
