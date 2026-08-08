package cn.iocoder.yudao.module.tk.service.reference.ai;

import cn.iocoder.yudao.module.tk.enums.TkApiKeyProviderEnum;
import cn.iocoder.yudao.module.tk.service.config.TkApiKeyConfigService;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkGeminiClient;
import cn.iocoder.yudao.module.tk.service.reference.TkReferenceAnalysisProvider;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class TkGeminiReferenceAnalysisClient implements TkReferenceAiAnalysisClient {

    @Resource
    private TkGeminiClient geminiClient;
    @Resource
    private TkApiKeyConfigService apiKeyConfigService;

    @Override
    public String provider() {
        return TkReferenceAnalysisProvider.GEMINI;
    }

    @Override
    public TkReferenceAiAnalysisResult analyze(TkReferenceAiAnalysisContext context) {
        String content = geminiClient.generateText(context.getPrompt(), context.getImages());
        String model = apiKeyConfigService.getValueOrDefault(
                TkApiKeyProviderEnum.GEMINI.getProvider(), "text-model", "");
        return new TkReferenceAiAnalysisResult(provider(), model, content);
    }
}
