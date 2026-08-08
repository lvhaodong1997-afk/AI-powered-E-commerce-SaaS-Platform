package cn.iocoder.yudao.module.tk.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TkApiKeyProviderEnum {

    GEMINI("GEMINI", "Gemini"),
    DASHSCOPE("DASHSCOPE", "DashScope"),
    MIMO("MIMO", "MiMo");

    private final String provider;
    private final String name;

}
