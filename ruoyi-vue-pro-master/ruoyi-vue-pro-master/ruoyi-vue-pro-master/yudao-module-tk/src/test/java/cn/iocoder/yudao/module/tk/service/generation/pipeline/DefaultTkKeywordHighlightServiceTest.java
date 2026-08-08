package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultTkKeywordHighlightServiceTest {

    private final DefaultTkKeywordHighlightService service = new DefaultTkKeywordHighlightService();

    @Test
    void resolveKeywordsAddsSocialHookWordsForYellowStoryStyle() {
        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .subtitleStyle("yellow_story")
                .subtitleKeywordEnabled(true)
                .subtitleKeywordMode("auto_manual")
                .build();

        List<String> keywords = service.resolveKeywords(task,
                "Stop wasting time. This secret fix works fast for busy moms.");

        assertTrue(containsIgnoreCase(keywords, "Stop"), "Yellow story captions should highlight hook words");
        assertTrue(containsIgnoreCase(keywords, "secret"), "Yellow story captions should highlight curiosity words");
        assertTrue(containsIgnoreCase(keywords, "fast"), "Yellow story captions should highlight result words");
    }

    @Test
    void resolveKeywordsAddsCommerceWordsForPromoAndPriceStyles() {
        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .subtitleStyle("price_flash")
                .subtitleKeywordEnabled(true)
                .subtitleKeywordMode("auto_manual")
                .build();

        List<String> keywords = service.resolveKeywords(task,
                "Limited offer today: $19.99, 40% off, free shipping, only for this sale.");

        assertTrue(containsIgnoreCase(keywords, "$19.99"), "Price flash should highlight prices");
        assertTrue(containsIgnoreCase(keywords, "40%"), "Price flash should highlight discount percentages");
        assertTrue(containsIgnoreCase(keywords, "Limited"), "Price flash should highlight urgency words");
        assertTrue(containsIgnoreCase(keywords, "offer"), "Price flash should highlight promotion words");
    }

    @Test
    void resolveKeywordsAddsProductExplainerWordsForCleanProductStyle() {
        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .subtitleStyle("clean_product")
                .subtitleKeywordEnabled(true)
                .subtitleKeywordMode("auto_manual")
                .build();

        List<String> keywords = service.resolveKeywords(task,
                "The comfortable breathable support keeps your wrist steady during daily use.");

        assertTrue(containsIgnoreCase(keywords, "comfortable"), "Clean product captions should highlight product benefits");
        assertTrue(containsIgnoreCase(keywords, "breathable"), "Clean product captions should highlight product feature words");
        assertTrue(containsIgnoreCase(keywords, "support"), "Clean product captions should highlight product category or effect words");
    }

    private boolean containsIgnoreCase(List<String> values, String expected) {
        return values.stream().anyMatch(value -> value.equalsIgnoreCase(expected));
    }
}
