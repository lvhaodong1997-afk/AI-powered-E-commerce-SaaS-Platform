package cn.iocoder.yudao.module.tk.enums;

import cn.hutool.core.util.StrUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

@Getter
@AllArgsConstructor
public enum TkMaterialUsagePhaseEnum {

    ATTENTION("ATTENTION", "吸引注意", 1),
    PRODUCT_SHOW("PRODUCT_SHOW", "产品展示", 2),
    RESULT_EFFECT("RESULT_EFFECT", "使用效果", 3),
    GENERAL("GENERAL", "通用素材", 9);

    private final String code;
    private final String name;
    private final Integer order;

    public static TkMaterialUsagePhaseEnum normalize(String code) {
        if (StrUtil.isBlank(code)) {
            return GENERAL;
        }
        return Arrays.stream(values())
                .filter(item -> item.code.equalsIgnoreCase(code.trim()))
                .findFirst()
                .orElse(GENERAL);
    }

}
