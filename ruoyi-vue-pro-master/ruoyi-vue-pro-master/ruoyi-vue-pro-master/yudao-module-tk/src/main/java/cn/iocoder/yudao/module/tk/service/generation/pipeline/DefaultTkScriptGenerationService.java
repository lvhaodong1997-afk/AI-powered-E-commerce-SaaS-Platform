package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkMaterialLibraryDO;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkReferenceScriptOptionDO;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import cn.iocoder.yudao.module.tk.service.reference.TkReferenceAnalysisService;
import cn.iocoder.yudao.module.tk.service.config.TkApiKeyConfigService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class DefaultTkScriptGenerationService implements TkScriptGenerationService {

    private static final String MANUAL_LEAD_GENERATION_SOURCE_PREFIX = "manual-lead-generation://";

    @Resource
    private TkGenerationProperties generationProperties;
    @Resource
    private TkGeminiClient geminiClient;
    @Resource
    private TkReferenceAnalysisService referenceAnalysisService;
    @Resource
    private TkApiKeyConfigService apiKeyConfigService;

    @Override
    public TkGeneratedScript generateScript(TkGenerationTaskDO task, TkMaterialLibraryDO library) {
        int targetDuration = TkVideoDurationSupport.normalize(task.getReferenceDuration(),
                generationProperties.getFfmpeg().getMaxTargetDuration());
        if (task.getScriptOptionId() != null) {
            TkReferenceScriptOptionDO option = referenceAnalysisService.validateScriptOptionForGeneration(
                    task.getScriptOptionId(), task.getTenantId(), task.getCompanyId(), task.getLibraryId(),
                    task.getReferenceAnalysisId());
            String scriptText = StrUtil.blankToDefault(option.getScriptText(), option.getTitle());
            return new TkGeneratedScript(option.getTitle(), scriptText, option.getSegmentTimeline(), targetDuration, targetDuration);
        }
        if (TkGeminiPromptConfig.isLeadGeneration(task.getMaterialPurpose())) {
            if (StrUtil.isNotBlank(task.getPromptText())) {
                return new TkGeneratedScript(StrUtil.format("{} \u00b7 \u624b\u52a8\u5f15\u6d41\u6587\u6848", library.getName()),
                        task.getPromptText().trim(), null, targetDuration, targetDuration);
            }
            if (isManualLeadGenerationSource(task.getSourceUrl())) {
                return new TkGeneratedScript(StrUtil.format("{} \u00b7 \u7eaf\u7d20\u6750\u6df7\u526a", library.getName()),
                        "", null, targetDuration, targetDuration);
            }
        }
        String title = StrUtil.format("{} · 对标爆款混剪", library.getName());
        String prompt = buildPrompt(task, library, targetDuration);
        String script = geminiClient.generateText(prompt);
        return new TkGeneratedScript(title, script, null, targetDuration, targetDuration);
    }

    private String buildPrompt(TkGenerationTaskDO task, TkMaterialLibraryDO library, int targetDuration) {
        String promptTemplate = apiKeyConfigService.getValueOrDefault(TkGeminiPromptConfig.PROVIDER,
                        TkGeminiPromptConfig.generationScriptPromptKey(task.getMaterialPurpose()),
                TkGeminiPromptConfig.defaultGenerationScriptPrompt(task.getMaterialPurpose()))
                + "\n\n" + TkLanguageSupport.promptInstruction(task.getTargetLanguage()) + "\n";
        return StrUtil.format(promptTemplate,
                task.getSourceUrl(),
                library.getName(),
                StrUtil.blankToDefault(library.getCategory(), "未分类"),
                StrUtil.blankToDefault(library.getScene(), "带货混剪"),
                StrUtil.blankToDefault(library.getTags(), "产品卖点"),
                targetDuration,
                task.getClipSeconds() == null ? 3 : task.getClipSeconds())
                + "\n" + buildStrictDurationBudget(targetDuration);
    }

    private String buildStrictDurationBudget(int targetDuration) {
        int normalizedDuration = TkVideoDurationSupport.normalize(targetDuration);
        int chineseMin = Math.max(20, (int) Math.floor(normalizedDuration * 140D / 30D));
        int chineseMax = Math.max(chineseMin, (int) Math.ceil(normalizedDuration * 180D / 30D));
        int englishMin = Math.max(12, (int) Math.floor(normalizedDuration * 70D / 30D));
        int englishMax = Math.max(englishMin, (int) Math.ceil(normalizedDuration * 90D / 30D));
        return StrUtil.format("硬性时长约束：目标 {} 秒。中文 {}-{} 字，英文 {}-{} words；"
                        + "只输出可直接配音的短句文案，不要超出该字数预算；如果内容过多，优先删减解释、铺垫和重复卖点。",
                normalizedDuration, chineseMin, chineseMax, englishMin, englishMax);
    }

    private boolean isManualLeadGenerationSource(String sourceUrl) {
        return StrUtil.startWith(sourceUrl, MANUAL_LEAD_GENERATION_SOURCE_PREFIX);
    }
}
