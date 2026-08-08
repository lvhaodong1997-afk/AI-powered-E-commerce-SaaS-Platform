package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TkLanguageSupportTest {

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
