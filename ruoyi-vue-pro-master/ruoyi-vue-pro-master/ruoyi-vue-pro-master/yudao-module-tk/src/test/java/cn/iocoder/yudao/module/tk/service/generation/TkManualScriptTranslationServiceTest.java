package cn.iocoder.yudao.module.tk.service.generation;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkManualScriptTranslationReqVO;
import cn.iocoder.yudao.module.tk.controller.admin.generation.vo.TkManualScriptTranslationRespVO;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkDeepSeekClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TkManualScriptTranslationServiceTest {

    @Test
    void trimsSourceAndOutputAndReturnsLanguageForEverySupportedLanguage() throws Exception {
        for (String language : new String[] {"zh-cn", "en", "en-us", "de", "es", "fr", "nl"}) {
            AtomicReference<String> sentSource = new AtomicReference<>();
            AtomicReference<String> sentLanguage = new AtomicReference<>();
            TkManualScriptTranslationService service = service("  translated text  ", new AtomicInteger(), sentSource, sentLanguage);

            TkManualScriptTranslationRespVO response = service.translate(request("  原始文案  ", " " + language + " "));

            assertEquals("原始文案", response.getSourceText());
            assertEquals(language, response.getTargetLanguage());
            assertEquals("translated text", response.getTranslatedText());
            assertEquals("原始文案", sentSource.get());
            assertEquals(language, sentLanguage.get());
        }
    }

    @Test
    void rejectsBlankOrOversizedSourceBeforeCallingDeepSeek() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        TkManualScriptTranslationService service = service("translated", calls, new AtomicReference<>(), new AtomicReference<>());
        for (String source : new String[] {null, "", " \n\t ", repeat('a', 3001)}) {
            TkManualScriptTranslationReqVO request = request(source, "en");
            ServiceException error = assertThrows(ServiceException.class, () -> service.translate(request));
            assertEquals(400, error.getCode());
        }
        assertEquals(0, calls.get());
    }

    @Test
    void rejectsUnknownLanguageInsteadOfFallingBackToChinese() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        TkManualScriptTranslationService service = service("translated", calls, new AtomicReference<>(), new AtomicReference<>());
        for (String language : new String[] {null, "", "xx", "zh", "en-uk", "EN", "en_US", "français"}) {
            TkManualScriptTranslationReqVO request = request("文案", language);
            ServiceException error = assertThrows(ServiceException.class, () -> service.translate(request));
            assertEquals(400, error.getCode());
        }
        assertEquals(0, calls.get());
    }

    @Test
    void acceptsExactly3000TrimmedCharacters() throws Exception {
        String text = repeat('a', 3000);
        TkManualScriptTranslationService service = service(" " + text + " ", new AtomicInteger(), new AtomicReference<>(), new AtomicReference<>());
        TkManualScriptTranslationRespVO response = service.translate(request(" " + text + " ", "en"));
        assertEquals(text, response.getSourceText());
        assertEquals(text, response.getTranslatedText());
    }

    @Test
    void rejectsBlankOrOversizedTranslationWithoutTruncating() throws Exception {
        for (String output : new String[] {null, "", " \n ", repeat('a', 3001)}) {
            TkManualScriptTranslationService service = service(output, new AtomicInteger(), new AtomicReference<>(), new AtomicReference<>());
            ServiceException error = assertThrows(ServiceException.class, () -> service.translate(request("文案", "en")));
            assertEquals(502, error.getCode());
            assertTrue(error.getMessage().contains(output != null && output.length() > 3000 ? "3000" : "有效译文"));
        }
    }

    @Test
    void returnsActionableTranslationErrorWithoutExposingUpstreamDetails() {
        TkManualScriptTranslationService service = new TkManualScriptTranslationService();
        ReflectionTestUtils.setField(service, "deepSeekClient", new TkDeepSeekClient() {
            @Override
            public String translateText(String sourceText, String targetLanguage) {
                throw new IllegalStateException("DeepSeek internal config detail");
            }
        });
        ServiceException error = assertThrows(ServiceException.class, () -> service.translate(request("文案", "en")));
        assertEquals(502, error.getCode());
        assertTrue(error.getMessage().contains("翻译失败"));
        assertFalse(error.getMessage().contains("internal config"));
    }

    private TkManualScriptTranslationService service(String output, AtomicInteger calls, AtomicReference<String> source,
                           AtomicReference<String> language) {
        TkManualScriptTranslationService service = new TkManualScriptTranslationService();
        TkDeepSeekClient client = new TkDeepSeekClient() {
            @Override
            public String translateText(String sourceText, String targetLanguage) {
                calls.incrementAndGet();
                source.set(sourceText);
                language.set(targetLanguage);
                return output;
            }
        };
        ReflectionTestUtils.setField(service, "deepSeekClient", client);
        return service;
    }

    private TkManualScriptTranslationReqVO request(String source, String language) {
        TkManualScriptTranslationReqVO request = new TkManualScriptTranslationReqVO();
        request.setSourceText(source);
        request.setTargetLanguage(language);
        return request;
    }

    private String repeat(char character, int count) {
        char[] value = new char[count];
        java.util.Arrays.fill(value, character);
        return new String(value);
    }
}
