package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TkVoiceProviderRouterTest {

    @Test
    void resolveDefaultsToDashScopeWhenProviderMissing() {
        TkVoiceTtsClient dashScope = new FakeClient("DASHSCOPE");
        TkVoiceTtsClient mimo = new FakeClient("MIMO");
        TkVoiceProviderRouter router = new TkVoiceProviderRouter(Arrays.asList(dashScope, mimo));

        assertEquals(dashScope, router.resolve(null));
        assertEquals(dashScope, router.resolve(" "));
    }

    @Test
    void resolveMatchesMiMoProviderCaseInsensitively() {
        TkVoiceTtsClient dashScope = new FakeClient("DASHSCOPE");
        TkVoiceTtsClient mimo = new FakeClient("MIMO");
        TkVoiceProviderRouter router = new TkVoiceProviderRouter(Arrays.asList(dashScope, mimo));

        assertEquals(mimo, router.resolve("mimo"));
        assertEquals(mimo, router.resolve("MiMo"));
    }

    @Test
    void resolveRejectsUnknownProvider() {
        TkVoiceProviderRouter router = new TkVoiceProviderRouter(Collections.singletonList(new FakeClient("DASHSCOPE")));

        assertThrows(IllegalStateException.class, () -> router.resolve("unknown"));
    }

    private static final class FakeClient implements TkVoiceTtsClient {
        private final String provider;

        private FakeClient(String provider) {
            this.provider = provider;
        }

        @Override
        public String provider() {
            return provider;
        }

        @Override
        public String audioFormat() {
            return "mp3";
        }

        @Override
        public byte[] synthesize(TkVoiceSynthesisRequest request) {
            return new byte[0];
        }
    }
}
