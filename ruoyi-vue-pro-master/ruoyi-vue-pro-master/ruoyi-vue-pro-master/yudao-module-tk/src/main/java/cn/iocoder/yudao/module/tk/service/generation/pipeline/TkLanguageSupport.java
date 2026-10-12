package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import cn.hutool.core.util.StrUtil;

import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;

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

    private static final Map<String, String[]> COUNTRIES = new LinkedHashMap<>();
    static {
        country("CN", "zh-cn", "简体中文", "Chinese");
        country("US", "en-us", "美国英语", "American English");
        country("GB", "en-gb", "英国英语", "British English");
        country("JP", "ja-jp", "日本日语", "Japanese");
        country("KR", "ko-kr", "韩国韩语", "Korean");
        country("DE", "de-de", "德国德语", "German");
        country("FR", "fr-fr", "法国法语", "French");
        country("ES", "es-es", "西班牙西班牙语", "Spanish as used in Spain");
        country("IT", "it-it", "意大利语", "Italian");
        country("BR", "pt-br", "巴西葡萄牙语", "Brazilian Portuguese");
        country("MX", "es-mx", "墨西哥西班牙语", "Mexican Spanish");
        country("NL", "nl-nl", "荷兰荷兰语", "Dutch");
        country("TR", "tr-tr", "土耳其语", "Turkish");
        country("PT", "pt-pt", "葡萄牙葡萄牙语", "European Portuguese");
        country("VN", "vi-vn", "越南语", "Vietnamese");
        country("TH", "th-th", "泰语", "Thai");
    }

    private static void country(String code, String locale, String name, String englishName) {
        COUNTRIES.put(locale, new String[] {code, name, englishName});
    }

    public static boolean isSupportedTranslationLanguage(String language) {
        return COUNTRIES.containsKey(language) || StrUtil.equalsAny(language, "en", "de", "es", "fr", "nl");
    }

    public static String translationLanguageName(String language) {
        if (!isSupportedTranslationLanguage(language)) throw new IllegalArgumentException("Unsupported translation language");
        String[] country = COUNTRIES.get(language);
        return country == null ? displayName(language) : country[1]
                + "，使用当地惯用词汇、拼写和表达，忠实保留原意，不增删内容";
    }

    public static String targetCountry(String language) {
        String locale = language;
        if ("en".equals(locale)) locale = "en-us";
        else if (StrUtil.equalsAny(locale, "de", "es", "fr", "nl")) locale = locale + "-" + locale;
        String[] country = COUNTRIES.get(locale);
        return country == null ? "" : country[0];
    }

    public static String asrLanguageCode(String language) {
        return LANGUAGE_AUTO.equals(language) || StrUtil.isBlank(language) ? "" : ttsLanguageHint(language);
    }

    private TkLanguageSupport() {
    }

    public static String normalize(String language) {
        if (language != null && COUNTRIES.containsKey(language.trim().toLowerCase(Locale.ROOT))) {
            return language.trim().toLowerCase(Locale.ROOT);
        }
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
        return COUNTRIES.containsKey(normalized) ? normalized : DEFAULT_LANGUAGE;
    }

    public static String displayName(String language) {
        String normalized = normalize(language);
        if (COUNTRIES.containsKey(normalized) && !StrUtil.equalsAny(normalized, "zh-cn", "en-us")) {
            return COUNTRIES.get(normalized)[1];
        }
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
        if (COUNTRIES.containsKey(normalized)) return normalized.substring(0, 2);
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
        String[] country = COUNTRIES.get(normalized);
        if (country != null && !StrUtil.equalsAny(normalized, "zh-cn", "en-us")) {
            return "目标文案语言：" + country[1] + "。所有 title、points、scriptText 必须使用自然的 "
                    + country[2] + "，使用该国家惯用词汇和拼写，适合 TikTok 电商口播。";
        }
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
        String[] country = COUNTRIES.get(normalized);
        if (country != null && !StrUtil.equalsAny(normalized, "zh-cn", "en-us")) {
            return "请使用自然清晰的" + country[1] + "当地口音朗读，语调适合 TikTok 电商短视频。";
        }
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
