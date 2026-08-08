package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import cn.hutool.core.util.StrUtil;

import java.util.Locale;

public final class TkLanguageSupport {

    public static final String DEFAULT_LANGUAGE = "zh-cn";
    public static final String LANGUAGE_AUTO = "auto";
    public static final String LANGUAGE_ZH_CN = "zh-cn";
    public static final String LANGUAGE_EN = "en";
    public static final String LANGUAGE_EN_US = "en-us";
    public static final String LANGUAGE_DE = "de";
    public static final String LANGUAGE_ES = "es";
    public static final String LANGUAGE_FR = "fr";
    public static final String LANGUAGE_NL = "nl";

    private TkLanguageSupport() {
    }

    public static String normalize(String language) {
        String normalized = StrUtil.blankToDefault(language, DEFAULT_LANGUAGE).trim()
                .replace('_', '-')
                .toLowerCase(Locale.ROOT);
        if (StrUtil.equalsAny(normalized, "zh", "zh-cn", "cn", "chinese", "中文")) {
            return LANGUAGE_ZH_CN;
        }
        if (StrUtil.equalsAny(normalized, "en-us", "us", "american", "american-english", "美式英语")) {
            return LANGUAGE_EN_US;
        }
        if (StrUtil.equalsAny(normalized, "en", "en-gb", "english", "英语")) {
            return LANGUAGE_EN;
        }
        if (StrUtil.equalsAny(normalized, "de", "de-de", "german", "deutsch", "德语")) {
            return LANGUAGE_DE;
        }
        if (StrUtil.equalsAny(normalized, "es", "es-es", "es-mx", "spanish", "espanol", "español", "西班牙语")) {
            return LANGUAGE_ES;
        }
        if (StrUtil.equalsAny(normalized, "fr", "fr-fr", "french", "francais", "français", "法语")) {
            return LANGUAGE_FR;
        }
        if (StrUtil.equalsAny(normalized, "nl", "nl-nl", "dutch", "nederlands", "荷兰语")) {
            return LANGUAGE_NL;
        }
        return DEFAULT_LANGUAGE;
    }

    public static String displayName(String language) {
        String normalized = normalize(language);
        if (LANGUAGE_EN_US.equals(normalized)) {
            return "美式英语";
        }
        if (LANGUAGE_EN.equals(normalized)) {
            return "英语";
        }
        if (LANGUAGE_DE.equals(normalized)) {
            return "德语";
        }
        if (LANGUAGE_ES.equals(normalized)) {
            return "西班牙语";
        }
        if (LANGUAGE_FR.equals(normalized)) {
            return "法语";
        }
        if (LANGUAGE_NL.equals(normalized)) {
            return "荷兰语";
        }
        return "中文";
    }

    public static String ttsLanguageHint(String language) {
        String normalized = normalize(language);
        if (LANGUAGE_DE.equals(normalized)) {
            return "de";
        }
        if (LANGUAGE_ES.equals(normalized)) {
            return "es";
        }
        if (LANGUAGE_FR.equals(normalized)) {
            return "fr";
        }
        if (LANGUAGE_NL.equals(normalized)) {
            return "nl";
        }
        if (LANGUAGE_EN.equals(normalized) || LANGUAGE_EN_US.equals(normalized)) {
            return "en";
        }
        return "zh";
    }

    public static String promptInstruction(String language) {
        String normalized = normalize(language);
        if (LANGUAGE_EN_US.equals(normalized)) {
            return "目标文案语言：美式英语。所有 title、points、scriptText 必须使用自然的 American English，适合 TikTok 电商口播，不要输出中文。";
        }
        if (LANGUAGE_EN.equals(normalized)) {
            return "目标文案语言：英语。所有 title、points、scriptText 必须使用自然的 English，适合 TikTok 电商口播，不要输出中文。";
        }
        if (LANGUAGE_DE.equals(normalized)) {
            return "目标文案语言：德语。所有 title、points、scriptText 必须使用自然的 Deutsch，适合 TikTok 电商口播，不要输出中文或英文。";
        }
        if (LANGUAGE_ES.equals(normalized)) {
            return "目标文案语言：西班牙语。所有 title、points、scriptText 必须使用自然的 Spanish，适合 TikTok 电商口播，不要输出中文。";
        }
        if (LANGUAGE_FR.equals(normalized)) {
            return "目标文案语言：法语。所有 title、points、scriptText 必须使用自然的 French，适合 TikTok 电商口播，不要输出中文。";
        }
        if (LANGUAGE_NL.equals(normalized)) {
            return "目标文案语言：荷兰语。所有 title、points、scriptText 必须使用自然的 Dutch，适合 TikTok 电商口播，不要输出中文。";
        }
        return "目标文案语言：中文。所有 title、points、scriptText 必须使用自然中文，适合 TikTok 电商口播。";
    }

    public static String ttsInstruction(String language) {
        if (StrUtil.isBlank(language)) {
            return "";
        }
        String normalized = normalize(language);
        if (LANGUAGE_EN_US.equals(normalized)) {
            return "请使用自然清晰的美式英语口音朗读，语调适合 TikTok 电商短视频。";
        }
        if (LANGUAGE_EN.equals(normalized)) {
            return "请使用自然清晰的英语朗读，语调适合 TikTok 电商短视频。";
        }
        if (LANGUAGE_DE.equals(normalized)) {
            return "请使用自然清晰的德语朗读，语调适合 TikTok 电商短视频。";
        }
        if (LANGUAGE_ES.equals(normalized)) {
            return "请使用自然清晰的西班牙语朗读，语调适合 TikTok 电商短视频。";
        }
        if (LANGUAGE_FR.equals(normalized)) {
            return "请使用自然清晰的法语朗读，语调适合 TikTok 电商短视频。";
        }
        if (LANGUAGE_NL.equals(normalized)) {
            return "请使用自然清晰的荷兰语朗读，语调适合 TikTok 电商短视频。";
        }
        return "请使用自然清晰的中文朗读，语调适合 TikTok 电商短视频。";
    }

}
