package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TkGeminiPromptConfigTest {

    @Test
    void defaultPromptsShouldKeepSupportedSchemaAndRuntimePlaceholders() {
        assertFront6sRules(TkGeminiPromptConfig.DEFAULT_ANALYSIS_PROMPT);
        assertFront6sRules(TkGeminiPromptConfig.DEFAULT_SCRIPT_REGENERATION_PROMPT);
        assertFront6sRules(TkGeminiPromptConfig.DEFAULT_GENERATION_SCRIPT_PROMPT);
        assertSegmentTimelineRules(TkGeminiPromptConfig.DEFAULT_ANALYSIS_PROMPT);
        assertSegmentTimelineRules(TkGeminiPromptConfig.DEFAULT_SCRIPT_REGENERATION_PROMPT);

        assertEquals(10, countPlaceholders(TkGeminiPromptConfig.DEFAULT_ANALYSIS_PROMPT));
        assertEquals(13, countPlaceholders(TkGeminiPromptConfig.DEFAULT_SCRIPT_REGENERATION_PROMPT));
        assertEquals(7, countPlaceholders(TkGeminiPromptConfig.DEFAULT_GENERATION_SCRIPT_PROMPT));

        assertUnsupportedJsonFieldsAbsent(TkGeminiPromptConfig.DEFAULT_ANALYSIS_PROMPT);
        assertUnsupportedJsonFieldsAbsent(TkGeminiPromptConfig.DEFAULT_SCRIPT_REGENERATION_PROMPT);
        assertUnsupportedJsonFieldsAbsent(TkGeminiPromptConfig.DEFAULT_GENERATION_SCRIPT_PROMPT);
    }

    @Test
    void generationPromptShouldDescribeOptionalHookAndRandomWholeMaterialsWithoutTrimming() {
        String prompt = TkGeminiPromptConfig.DEFAULT_GENERATION_SCRIPT_PROMPT;

        assertTrue(prompt.contains("未上传"));
        assertTrue(prompt.contains("S1_HOOK"));
        assertTrue(prompt.contains("随机"));
        assertTrue(prompt.contains("完整视频"));
        assertTrue(prompt.contains("压缩到目标时长"));
        assertFalse(prompt.contains("固定使用用户上传视频"));
        assertFalse(prompt.contains("每段裁剪"));
        assertFalse(prompt.contains("裁剪{}秒"));
        assertFalse(prompt.contains("截取"));
    }

    @Test
    void promptsShouldDescribeLongFormDurationBudgetsThroughOneHundredEightySeconds() {
        assertLongFormDurationBudgets(TkGeminiPromptConfig.DEFAULT_ANALYSIS_PROMPT);
        assertLongFormDurationBudgets(TkGeminiPromptConfig.DEFAULT_SCRIPT_REGENERATION_PROMPT);
        assertLongFormDurationBudgets(TkGeminiPromptConfig.DEFAULT_GENERATION_SCRIPT_PROMPT);
    }

    @Test
    void promptKeysShouldRouteByMaterialPurpose() {
        assertEquals(TkGeminiPromptConfig.MATERIAL_PURPOSE_ECOMMERCE,
                TkGeminiPromptConfig.normalizeMaterialPurpose(null));
        assertEquals(TkGeminiPromptConfig.MATERIAL_PURPOSE_ECOMMERCE,
                TkGeminiPromptConfig.normalizeMaterialPurpose(""));
        assertEquals(TkGeminiPromptConfig.MATERIAL_PURPOSE_ECOMMERCE,
                TkGeminiPromptConfig.normalizeMaterialPurpose("unknown"));
        assertEquals(TkGeminiPromptConfig.MATERIAL_PURPOSE_LEAD_GENERATION,
                TkGeminiPromptConfig.normalizeMaterialPurpose("lead_generation"));

        assertEquals(TkGeminiPromptConfig.KEY_ANALYSIS_PROMPT,
                TkGeminiPromptConfig.analysisPromptKey(TkGeminiPromptConfig.MATERIAL_PURPOSE_ECOMMERCE));
        assertEquals(TkGeminiPromptConfig.KEY_SCRIPT_REGENERATION_PROMPT,
                TkGeminiPromptConfig.scriptRegenerationPromptKey(TkGeminiPromptConfig.MATERIAL_PURPOSE_ECOMMERCE));
        assertEquals(TkGeminiPromptConfig.KEY_GENERATION_SCRIPT_PROMPT,
                TkGeminiPromptConfig.generationScriptPromptKey(TkGeminiPromptConfig.MATERIAL_PURPOSE_ECOMMERCE));

        assertEquals(TkGeminiPromptConfig.KEY_ANALYSIS_PROMPT_LEAD_GENERATION,
                TkGeminiPromptConfig.analysisPromptKey(TkGeminiPromptConfig.MATERIAL_PURPOSE_LEAD_GENERATION));
        assertEquals(TkGeminiPromptConfig.KEY_SCRIPT_REGENERATION_PROMPT_LEAD_GENERATION,
                TkGeminiPromptConfig.scriptRegenerationPromptKey(TkGeminiPromptConfig.MATERIAL_PURPOSE_LEAD_GENERATION));
        assertEquals(TkGeminiPromptConfig.KEY_GENERATION_SCRIPT_PROMPT_LEAD_GENERATION,
                TkGeminiPromptConfig.generationScriptPromptKey(TkGeminiPromptConfig.MATERIAL_PURPOSE_LEAD_GENERATION));
    }

    private static void assertFront6sRules(String prompt) {
        assertTrue(prompt.contains("0-3 秒"));
        assertTrue(prompt.contains("3-6 秒"));
        assertTrue(prompt.contains("前 6 秒"));
        assertTrue(prompt.contains("用户目标时长") || prompt.contains("目标成片时长"));
    }

    private static void assertUnsupportedJsonFieldsAbsent(String prompt) {
        assertFalse(prompt.contains("\"audiencePainPoints\""));
        assertFalse(prompt.contains("\"first6sBreakdown\""));
    }

    private static void assertSegmentTimelineRules(String prompt) {
        assertTrue(prompt.contains("\"segmentTimeline\""));
        assertTrue(prompt.contains("S1_HOOK"));
        assertTrue(prompt.contains("S7_LIFESTYLE"));
        assertTrue(prompt.contains("S8_CTA"));
    }

    private static void assertLongFormDurationBudgets(String prompt) {
        assertTrue(prompt.contains("90秒"));
        assertTrue(prompt.contains("120秒"));
        assertTrue(prompt.contains("180秒"));
    }

    private static int countPlaceholders(String prompt) {
        int count = 0;
        int index = 0;
        while ((index = prompt.indexOf("{}", index)) >= 0) {
            count++;
            index += 2;
        }
        return count;
    }

}
