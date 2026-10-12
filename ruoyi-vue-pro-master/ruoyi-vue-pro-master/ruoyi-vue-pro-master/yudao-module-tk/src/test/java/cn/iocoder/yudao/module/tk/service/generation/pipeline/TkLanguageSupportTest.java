package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TkLanguageSupportTest {
    @Test
    void countryVariantsSurviveAndHaveBaseSpeechCodes() {
        for (String language : new String[] {"zh-cn", "en-us", "en-gb", "ja-jp", "ko-kr", "de-de", "fr-fr", "es-es", "it-it", "pt-br", "es-mx", "nl-nl", "tr-tr", "pt-pt", "vi-vn", "th-th"}) {
            assertEquals(language, TkLanguageSupport.normalize(language));
            assertEquals(language.substring(0, 2), TkLanguageSupport.ttsLanguageHint(language));
            assertEquals(language.substring(0, 2), TkLanguageSupport.asrLanguageCode(language));
            assertEquals(language.substring(3).toUpperCase(), TkLanguageSupport.targetCountry(language));
            assertTrue(TkLanguageSupport.isSupportedTranslationLanguage(language));
            assertTrue(TkLanguageSupport.translationLanguageName(language).contains(TkLanguageSupport.displayName(language).replace("美式英语", "美国英语").replace("中文", "简体中文")));
        }
        assertEquals("US", TkLanguageSupport.targetCountry("en"));
        assertEquals("ja-jp", TkLanguageSupport.normalize("ja_JP"));
        assertEquals("pt-br", TkLanguageSupport.normalize("pt_BR"));
        assertEquals("pt-pt", TkLanguageSupport.normalize("pt_PT"));
        assertTrue(TkLanguageSupport.ttsInstruction("en-gb").contains("英国"));
        assertTrue(TkLanguageSupport.promptInstruction("es-mx").contains("墨西哥"));
    }

    @Test
    void normalizeShouldSupportSpanishFrenchAndDutchAliases() {
        assertEquals("es", TkLanguageSupport.normalize("es"));
        assertEquals("es", TkLanguageSupport.normalize("es_ES"));
        assertEquals("es", TkLanguageSupport.normalize("spanish"));
        assertEquals("es", TkLanguageSupport.normalize("西班牙语"));

        assertEquals("fr", TkLanguageSupport.normalize("fr"));
        assertEquals("fr", TkLanguageSupport.normalize("fr_FR"));
        assertEquals("fr", TkLanguageSupport.normalize("french"));
        assertEquals("fr", TkLanguageSupport.normalize("法语"));

        assertEquals("nl", TkLanguageSupport.normalize("nl"));
        assertEquals("nl", TkLanguageSupport.normalize("nl_NL"));
        assertEquals("nl", TkLanguageSupport.normalize("dutch"));
        assertEquals("nl", TkLanguageSupport.normalize("荷兰语"));
    }

    @Test
    void ttsLanguageHintShouldReturnNewLanguageCodes() {
        assertEquals("es", TkLanguageSupport.ttsLanguageHint("spanish"));
        assertEquals("fr", TkLanguageSupport.ttsLanguageHint("french"));
        assertEquals("nl", TkLanguageSupport.ttsLanguageHint("dutch"));
    }

    @Test
    void instructionsShouldMentionNewTargetLanguages() {
        assertTrue(TkLanguageSupport.promptInstruction("es").contains("Spanish"));
        assertTrue(TkLanguageSupport.promptInstruction("fr").contains("French"));
        assertTrue(TkLanguageSupport.promptInstruction("nl").contains("Dutch"));

        assertTrue(TkLanguageSupport.ttsInstruction("es").contains("西班牙语"));
        assertTrue(TkLanguageSupport.ttsInstruction("fr").contains("法语"));
        assertTrue(TkLanguageSupport.ttsInstruction("nl").contains("荷兰语"));
    }

}
