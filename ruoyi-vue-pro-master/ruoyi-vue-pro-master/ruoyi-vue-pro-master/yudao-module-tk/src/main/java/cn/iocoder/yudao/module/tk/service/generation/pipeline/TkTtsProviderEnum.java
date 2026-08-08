package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import cn.hutool.core.util.StrUtil;

public final class TkTtsProviderEnum {

    public static final String DASHSCOPE = "DASHSCOPE";
    public static final String MIMO = "MIMO";

    private TkTtsProviderEnum() {
    }

    public static String normalize(String provider) {
        String value = StrUtil.blankToDefault(provider, DASHSCOPE).trim().toUpperCase();
        if (!DASHSCOPE.equals(value) && !MIMO.equals(value)) {
            throw new IllegalStateException("Unsupported voice provider: " + value);
        }
        return value;
    }
}
