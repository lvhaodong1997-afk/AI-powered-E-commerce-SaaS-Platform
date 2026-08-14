package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TkMimoTtsClientTest {

    @Test
    void buildRequestUsesPresetModelAndVoiceForPresetMode() {
        TkMimoTtsClient client = newClient();
        TkVoiceSynthesisRequest request = TkVoiceSynthesisRequest.builder()
                .text("Hello world")
                .targetLanguage("en")
                .mimoVoiceMode("PRESET")
                .mimoVoiceCode("Chloe")
                .build();

        Map<String, Object> payload = client.buildRequest(request);

        assertEquals("mimo-v2.5-tts", payload.get("model"));
        Map<String, Object> audio = cast(payload.get("audio"));
        assertEquals("Chloe", audio.get("voice"));
        List<?> messages = castList(payload.get("messages"));
        assertEquals(2, messages.size());
        assertTrue(messages.toString().contains("Hello world"));
    }

    @Test
    void buildRequestUsesVoiceDesignModelAndPromptForVoiceDesignMode() {
        TkMimoTtsClient client = newClient();
        TkVoiceSynthesisRequest request = TkVoiceSynthesisRequest.builder()
                .text("Make it catchy")
                .targetLanguage("zh-cn")
                .mimoVoiceMode("VOICE_DESIGN")
                .mimoVoicePrompt("Natural, warm, confident seller voice")
                .build();

        Map<String, Object> payload = client.buildRequest(request);

        assertEquals("mimo-v2.5-tts-voicedesign", payload.get("model"));
        Map<String, Object> audio = cast(payload.get("audio"));
        assertEquals(Boolean.FALSE, audio.get("optimize_text_preview"));
        List<?> messages = castList(payload.get("messages"));
        assertTrue(messages.toString().contains("Natural, warm, confident seller voice"));
        assertTrue(messages.toString().contains("Read the provided narration text exactly"));
        assertTrue(messages.toString().contains("Make it catchy"));
    }

    @Test
    void buildRequestKeepsVoiceDesignModelForFinalSynthesisWhenVoiceDesignSelected() {
        TkMimoTtsClient client = newClient();
        TkVoiceSynthesisRequest request = TkVoiceSynthesisRequest.builder()
                .text("Make it catchy")
                .targetLanguage("en")
                .mimoVoiceMode("VOICE_DESIGN")
                .mimoVoicePrompt("Natural, warm, confident seller voice")
                .finalSynthesis(true)
                .build();

        Map<String, Object> payload = client.buildRequest(request);

        assertEquals("mimo-v2.5-tts-voicedesign", payload.get("model"));
        Map<String, Object> audio = cast(payload.get("audio"));
        assertEquals(Boolean.FALSE, audio.get("optimize_text_preview"));
        assertTrue(!audio.containsKey("voice"));
        List<?> messages = castList(payload.get("messages"));
        assertTrue(messages.toString().contains("Natural, warm, confident seller voice"));
        assertTrue(messages.toString().contains("Read the provided narration text exactly"));
        assertTrue(messages.toString().contains("Make it catchy"));
    }

    @Test
    void buildRequestUsesVoiceCloneModelAndSampleForVoiceCloneMode() {
        TkMimoTtsClient client = newClient();
        TkVoiceSynthesisRequest request = TkVoiceSynthesisRequest.builder()
                .text("Use the same style")
                .targetLanguage("zh-cn")
                .mimoVoiceMode("VOICE_CLONE")
                .mimoVoiceSampleUrl("data:audio/wav;base64,AA==")
                .build();

        Map<String, Object> payload = client.buildRequest(request);

        assertEquals("mimo-v2.5-tts-voiceclone", payload.get("model"));
        Map<String, Object> audio = cast(payload.get("audio"));
        assertEquals("data:audio/wav;base64,AA==", audio.get("voice"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<?> castList(Object value) {
        return (List<?>) value;
    }

    private static TkMimoTtsClient newClient() {
        TkMimoTtsClient client = new TkMimoTtsClient();
        ReflectionTestUtils.setField(client, "generationProperties", new cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties());
        cn.iocoder.yudao.module.tk.service.config.TkApiKeyConfigService apiKeyConfigService =
                mock(cn.iocoder.yudao.module.tk.service.config.TkApiKeyConfigService.class);
        when(apiKeyConfigService.getValueOrDefault(anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(2));
        ReflectionTestUtils.setField(client, "apiKeyConfigService", apiKeyConfigService);
        return client;
    }
}
