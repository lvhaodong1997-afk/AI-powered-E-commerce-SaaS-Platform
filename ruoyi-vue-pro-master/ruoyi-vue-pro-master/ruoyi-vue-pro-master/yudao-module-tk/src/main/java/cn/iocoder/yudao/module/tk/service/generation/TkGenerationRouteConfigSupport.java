package cn.iocoder.yudao.module.tk.service.generation;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkLanguageSupport;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class TkGenerationRouteConfigSupport {

    private static final String CLIP_PLAN_MODE_FIELD = "clipPlanMode";
    private static final String SCRIPT_MODE_FIELD = "scriptMode";

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

    public static String buildManualScriptConfig(String clipPlanMode) {
        Map<String, String> config = new LinkedHashMap<>();
        config.put(CLIP_PLAN_MODE_FIELD, normalizeClipPlanMode(clipPlanMode).name());
        config.put(SCRIPT_MODE_FIELD, "MANUAL");
        return JsonUtils.toJsonString(config);
    }

    public static boolean isManualScript(String routeConfig) {
        if (StrUtil.isBlank(routeConfig)) {
            return false;
        }
        JsonNode root = JsonUtils.parseTree(routeConfig);
        return root != null && root.isObject() && "MANUAL".equals(root.path(SCRIPT_MODE_FIELD).asText());
    }

    public static String withManualTranslationConfig(String routeConfig, boolean enabled,
                                                     String originalText, String targetLanguage) {
        JsonNode root = JsonUtils.parseTree(StrUtil.blankToDefault(routeConfig, "{}"));
        ObjectNode config = root != null && root.isObject()
                ? ((ObjectNode) root).deepCopy() : (ObjectNode) JsonUtils.parseTree("{}");
        config.put("manualTranslationEnabled", enabled);
        config.put("manualOriginalText", StrUtil.trimToEmpty(originalText));
        if (enabled) {
            config.put("manualTranslationTargetLanguage", TkLanguageSupport.normalize(targetLanguage));
            config.put("manualTranslationTargetCountry", TkLanguageSupport.targetCountry(targetLanguage));
        } else {
            config.remove("manualTranslationTargetLanguage");
            config.remove("manualTranslationTargetCountry");
        }
        return JsonUtils.toJsonString(config);
    }

    public enum ClipPlanMode {

        SEGMENTED,
        FULL_POOL_RANDOM

    }

}
