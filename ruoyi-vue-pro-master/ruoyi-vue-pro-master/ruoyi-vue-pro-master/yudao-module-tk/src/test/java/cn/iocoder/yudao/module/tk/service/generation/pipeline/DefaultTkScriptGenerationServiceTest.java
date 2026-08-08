package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkMaterialLibraryDO;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import cn.iocoder.yudao.module.tk.service.config.TkApiKeyConfigService;
import cn.iocoder.yudao.module.tk.service.reference.TkReferenceAnalysisService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultTkScriptGenerationServiceTest {

    @Test
    void generateScriptUsesLeadGenerationManualPromptTextWithoutGeminiRewrite() {
        DefaultTkScriptGenerationService service = new DefaultTkScriptGenerationService();
        ReflectionTestUtils.setField(service, "generationProperties", new TkGenerationProperties());
        ReflectionTestUtils.setField(service, "geminiClient", new TkGeminiClient());
        ReflectionTestUtils.setField(service, "referenceAnalysisService", mock(TkReferenceAnalysisService.class));
        ReflectionTestUtils.setField(service, "apiKeyConfigService", mock(TkApiKeyConfigService.class));

        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .libraryId(10L)
                .materialPurpose(TkGeminiPromptConfig.MATERIAL_PURPOSE_LEAD_GENERATION)
                .promptText("评论关键词，我发你完整方案。")
                .referenceDuration(15)
                .build();
        TkMaterialLibraryDO library = TkMaterialLibraryDO.builder().id(10L).name("引流素材库").build();

        TkGeneratedScript script = service.generateScript(task, library);

        assertEquals("引流素材库 · 手动引流文案", script.getTitle());
        assertEquals("评论关键词，我发你完整方案。", script.getContent());
        assertEquals(15, script.getReferenceDuration());
        assertEquals(15, script.getTargetDuration());
    }

    @Test
    void generateScriptUsesPureMaterialMixForBlankManualLeadGenerationPrompt() {
        DefaultTkScriptGenerationService service = new DefaultTkScriptGenerationService();
        RecordingGeminiClient geminiClient = new RecordingGeminiClient();
        ReflectionTestUtils.setField(service, "generationProperties", new TkGenerationProperties());
        ReflectionTestUtils.setField(service, "geminiClient", geminiClient);
        ReflectionTestUtils.setField(service, "referenceAnalysisService", mock(TkReferenceAnalysisService.class));
        ReflectionTestUtils.setField(service, "apiKeyConfigService", mock(TkApiKeyConfigService.class));

        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .libraryId(10L)
                .sourceUrl("manual-lead-generation://10")
                .materialPurpose(TkGeminiPromptConfig.MATERIAL_PURPOSE_LEAD_GENERATION)
                .promptText("")
                .referenceDuration(15)
                .build();
        TkMaterialLibraryDO library = TkMaterialLibraryDO.builder().id(10L).name("Lead Library").build();

        TkGeneratedScript script = service.generateScript(task, library);

        assertEquals("Lead Library · 纯素材混剪", script.getTitle());
        assertEquals("", script.getContent());
        assertEquals(15, script.getReferenceDuration());
        assertEquals(15, script.getTargetDuration());
        assertEquals(false, geminiClient.called);
    }

    @Test
    void generateScriptAppendsStrictDurationBudgetToConfiguredPrompt() {
        DefaultTkScriptGenerationService service = new DefaultTkScriptGenerationService();
        RecordingGeminiClient geminiClient = new RecordingGeminiClient();
        TkApiKeyConfigService apiKeyConfigService = mock(TkApiKeyConfigService.class);
        when(apiKeyConfigService.getValueOrDefault(TkGeminiPromptConfig.PROVIDER,
                TkGeminiPromptConfig.KEY_GENERATION_SCRIPT_PROMPT,
                TkGeminiPromptConfig.defaultGenerationScriptPrompt(null)))
                .thenReturn("来源：{}，素材库：{}，类目：{}，场景：{}，标签：{}，目标：{}秒，占位：{}秒");
        ReflectionTestUtils.setField(service, "generationProperties", new TkGenerationProperties());
        ReflectionTestUtils.setField(service, "geminiClient", geminiClient);
        ReflectionTestUtils.setField(service, "referenceAnalysisService", mock(TkReferenceAnalysisService.class));
        ReflectionTestUtils.setField(service, "apiKeyConfigService", apiKeyConfigService);
        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .sourceUrl("https://example.com/reference.mp4")
                .referenceDuration(27)
                .clipSeconds(3)
                .build();
        TkMaterialLibraryDO library = TkMaterialLibraryDO.builder()
                .id(10L)
                .name("水蜜桃")
                .category("食品")
                .scene("电商带货")
                .tags("爆汁,新鲜")
                .build();

        service.generateScript(task, library);

        assertTrue(geminiClient.lastPrompt.contains("硬性时长约束"));
        assertTrue(geminiClient.lastPrompt.contains("目标 27 秒"));
        assertTrue(geminiClient.lastPrompt.contains("中文 126-162 字"));
        assertTrue(geminiClient.lastPrompt.contains("英文 63-81 words"));
    }

    private static class RecordingGeminiClient extends TkGeminiClient {

        private boolean called;
        private String lastPrompt;

        @Override
        public String generateText(String prompt) {
            called = true;
            lastPrompt = prompt;
            return "generated";
        }

    }

}
