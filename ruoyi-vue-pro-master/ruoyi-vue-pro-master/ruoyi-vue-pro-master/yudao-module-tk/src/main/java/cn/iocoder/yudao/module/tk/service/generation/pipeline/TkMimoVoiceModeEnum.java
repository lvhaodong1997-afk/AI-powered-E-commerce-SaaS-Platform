package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import cn.hutool.core.util.StrUtil;

public final class TkMimoVoiceModeEnum {

    public static final String PRESET = "PRESET";
    public static final String VOICE_DESIGN = "VOICE_DESIGN";
    public static final String VOICE_CLONE = "VOICE_CLONE";

    private TkMimoVoiceModeEnum() {
    }

    public static String normalize(String mode) {
        String value = StrUtil.blankToDefault(mode, PRESET).trim().toUpperCase();
        value = value.replace('-', '_');
        if (!PRESET.equals(value) && !VOICE_DESIGN.equals(value) && !VOICE_CLONE.equals(value)) {
            throw new IllegalStateException("Unsupported MiMo voice mode: " + value);
        }
        return value;
    }
}
