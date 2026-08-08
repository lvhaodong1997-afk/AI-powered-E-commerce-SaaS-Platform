package cn.iocoder.yudao.module.tk.enums;

import cn.hutool.core.util.StrUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;

@Getter
@AllArgsConstructor
public enum TkMaterialSegmentTypeEnum {

    S1_HOOK("S1_HOOK", "黄金3秒", 1),
    S2_PAIN("S2_PAIN", "痛点场景", 2),
    S3_REVEAL("S3_REVEAL", "产品亮相", 3),
    S4_DEMO("S4_DEMO", "使用演示", 4),
    S5_PROOF("S5_PROOF", "效果证明", 5),
    S6_DETAIL("S6_DETAIL", "细节特写", 6),
    S7_LIFESTYLE("S7_LIFESTYLE", "场景融入", 7),
    GENERAL("GENERAL", "通用素材", 99);

    private final String code;
    private final String name;
    private final Integer order;

    public static final List<TkMaterialSegmentTypeEnum> STORY_SEGMENTS = Arrays.asList(
            S1_HOOK, S2_PAIN, S3_REVEAL, S4_DEMO, S5_PROOF, S6_DETAIL, S7_LIFESTYLE);

    public static final List<TkMaterialSegmentTypeEnum> LEAD_GENERATION_SEGMENTS = Arrays.asList(
            S1_HOOK, S2_PAIN, S3_REVEAL, S4_DEMO, S5_PROOF, S6_DETAIL, S7_LIFESTYLE, GENERAL);

    public static TkMaterialSegmentTypeEnum normalize(String code) {
        if (StrUtil.isBlank(code)) {
            return GENERAL;
        }
        return Arrays.stream(values())
                .filter(item -> item.code.equalsIgnoreCase(code.trim()))
                .findFirst()
                .orElse(GENERAL);
    }

}
