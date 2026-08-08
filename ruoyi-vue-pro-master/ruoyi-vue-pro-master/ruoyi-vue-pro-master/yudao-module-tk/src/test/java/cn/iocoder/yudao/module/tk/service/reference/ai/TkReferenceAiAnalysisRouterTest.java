package cn.iocoder.yudao.module.tk.service.reference.ai;

import cn.iocoder.yudao.module.tk.service.reference.TkReferenceAnalysisProvider;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TkReferenceAiAnalysisRouterTest {

    @Test
    void routesOnlyToExplicitProvider() {
        RecordingClient gemini = new RecordingClient(TkReferenceAnalysisProvider.GEMINI);
        RecordingClient dashscope = new RecordingClient(TkReferenceAnalysisProvider.DASHSCOPE_VIDEO);
        TkReferenceAiAnalysisRouter router = new TkReferenceAiAnalysisRouter(Arrays.asList(gemini, dashscope));
        TkReferenceAiAnalysisContext context = new TkReferenceAiAnalysisContext(
                "prompt", "https://cdn.example/video.mp4", Collections.emptyList());

        assertEquals(TkReferenceAnalysisProvider.GEMINI, router.analyze(null, context).getProvider());
        assertEquals(1, gemini.calls);
        assertEquals(0, dashscope.calls);

        assertEquals(TkReferenceAnalysisProvider.DASHSCOPE_VIDEO,
                router.analyze(TkReferenceAnalysisProvider.DASHSCOPE_VIDEO, context).getProvider());
        assertEquals(1, gemini.calls);
        assertEquals(1, dashscope.calls);
    }

    private static class RecordingClient implements TkReferenceAiAnalysisClient {
        private final String provider;
        private int calls;

        private RecordingClient(String provider) {
            this.provider = provider;
        }

        @Override
        public String provider() {
            return provider;
        }

        @Override
        public TkReferenceAiAnalysisResult analyze(TkReferenceAiAnalysisContext context) {
            calls++;
            return new TkReferenceAiAnalysisResult(provider, "model", "{}");
        }
    }
}
