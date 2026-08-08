package cn.iocoder.yudao.module.tk.service.generation;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.tk.enums.TkMaterialSegmentTypeEnum;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.EnumMap;
import java.util.Map;

public final class TkSegmentDurationConfigSupport {

    private TkSegmentDurationConfigSupport() {
    }

    public static boolean hasConfig(String config) {
        return StrUtil.isNotBlank(config);
    }

    public static Map<TkMaterialSegmentTypeEnum, Integer> parseTargets(String config) {
        Map<TkMaterialSegmentTypeEnum, Integer> targets = new EnumMap<>(TkMaterialSegmentTypeEnum.class);
        if (StrUtil.isBlank(config)) {
            return targets;
        }
        JsonNode root = JsonUtils.parseTree(config);
        if (root == null || !root.isArray()) {
            return targets;
        }
        for (JsonNode item : root) {
            TkMaterialSegmentTypeEnum segment = TkMaterialSegmentTypeEnum.normalize(item.path("segmentType").asText());
            if (!TkMaterialSegmentTypeEnum.STORY_SEGMENTS.contains(segment)) {
                segment = TkMaterialSegmentTypeEnum.normalize(item.path("segmentLibrary").asText());
            }
            if (!TkMaterialSegmentTypeEnum.STORY_SEGMENTS.contains(segment)) {
                continue;
            }
            int duration = Math.max(0, item.path("duration").asInt(0));
            targets.merge(segment, duration, Integer::sum);
        }
        targets.entrySet().removeIf(entry -> entry.getValue() <= 0);
        return targets;
    }

    public static int totalDuration(Map<TkMaterialSegmentTypeEnum, Integer> targets) {
        return targets.values().stream().mapToInt(Integer::intValue).sum();
    }

}
