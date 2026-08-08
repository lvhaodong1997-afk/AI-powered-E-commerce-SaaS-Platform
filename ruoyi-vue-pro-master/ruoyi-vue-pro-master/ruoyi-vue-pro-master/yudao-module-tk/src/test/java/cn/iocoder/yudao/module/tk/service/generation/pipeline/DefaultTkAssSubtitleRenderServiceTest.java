package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultTkAssSubtitleRenderServiceTest {

    private final DefaultTkAssSubtitleRenderService service = new DefaultTkAssSubtitleRenderService();

    @Test
    void renderWrapsLongEnglishSubtitleAndKeepsSpaces() throws Exception {
        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .subtitleStyle("yellow_keyword")
                .subtitleFontSize("medium")
                .subtitleKeywordColor("#FFD84D")
                .subtitleActiveColor("#35F27A")
                .subtitleKaraokeEnabled(false)
                .build();
        TkSubtitleSegment segment = new TkSubtitleSegment(
                "Sign relax your fingers. The breathable holes keep your hands dry during daily use.",
                0D, 3D, "bottom_center", 540, 1450, Collections.emptyList());
        File file = File.createTempFile("subtitle-wrap", ".ass");

        service.render(task, new TkSubtitleLayout(Collections.singletonList(segment)), file);

        String ass = Files.readString(file.toPath());
        assertTrue(ass.contains("Sign relax your fingers."),
                "English words should keep visible spaces instead of being merged");
        assertTrue(ass.contains("\\N"), "Long subtitle should wrap to a second line inside the safe width");
        assertFalse(ass.contains("\\\\N"), "ASS newline should not be escaped into visible text");
        assertFalse(ass.contains("Signrelaxyourfingers"),
                "English words should not be concatenated");
        assertTrue(ass.contains("WrapStyle: 2"), "ASS renderer should use explicit wrapping mode");
    }

    @Test
    void renderWrapsKaraokeWordsByLine() throws Exception {
        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .subtitleStyle("promo_bold")
                .subtitleFontSize("medium")
                .subtitleKeywordColor("#FFD84D")
                .subtitleActiveColor("#35F27A")
                .subtitleKaraokeEnabled(true)
                .build();
        TkSubtitleSegment segment = new TkSubtitleSegment("只要套上这个提拉带，几秒钟就能轻松抬脚，老人日常走路更省力。",
                0D, 4D, "bottom_center", 540, 1450, Arrays.asList(
                new TkSubtitleWord("只要", 0D, 0.3D, false),
                new TkSubtitleWord("套上", 0.3D, 0.6D, false),
                new TkSubtitleWord("这个", 0.6D, 0.9D, false),
                new TkSubtitleWord("提拉带", 0.9D, 1.4D, true),
                new TkSubtitleWord("，", 1.4D, 1.5D, false),
                new TkSubtitleWord("几秒钟", 1.5D, 2.0D, true),
                new TkSubtitleWord("就能", 2.0D, 2.3D, false),
                new TkSubtitleWord("轻松", 2.3D, 2.7D, false),
                new TkSubtitleWord("抬脚", 2.7D, 3.0D, false),
                new TkSubtitleWord("，老人日常走路更省力。", 3.0D, 4.0D, false)
        ));
        File file = File.createTempFile("subtitle-karaoke-wrap", ".ass");

        service.render(task, new TkSubtitleLayout(Collections.singletonList(segment)), file);

        String ass = Files.readString(file.toPath());
        assertTrue(ass.contains("\\N"), "Karaoke subtitle should wrap by line before applying kf timing");
        assertTrue(ass.contains("{\\kf"), "Karaoke timing should remain enabled after wrapping");
        assertTrue(ass.contains("提拉带"), "Keyword text should remain in the rendered subtitle");
    }

    @Test
    void renderUsesDistinctAssStylesForSubtitlePresets() throws Exception {
        String classic = renderStyleLine("classic_white");
        String yellowKeyword = renderStyleLine("yellow_keyword");
        String tiktokLarge = renderStyleLine("tiktok_large");
        String promoBold = renderStyleLine("promo_bold");
        String cleanProduct = renderStyleLine("clean_product");
        String neonPop = renderStyleLine("neon_pop");
        String yellowStory = renderStyleLine("yellow_story");
        String priceFlash = renderStyleLine("price_flash");
        String stepCard = renderStyleLine("step_card");
        String brandMinimal = renderStyleLine("brand_minimal");
        String commentBubble = renderStyleLine("comment_bubble");

        assertNotEquals(classic, yellowKeyword, "Yellow keyword style should no longer be identical to classic white");
        assertNotEquals(classic, tiktokLarge, "TikTok large style should have a distinct large text treatment");
        assertNotEquals(promoBold, priceFlash, "Promo and price flash should use different commerce treatments");
        assertNotEquals(cleanProduct, stepCard, "Clean product and step card should be separate explanatory styles");
        assertNotEquals(neonPop, yellowStory, "Neon and yellow story should be visually distinct social styles");
        assertNotEquals(classic, brandMinimal, "Brand minimal should not fall back to classic white");
        assertNotEquals(cleanProduct, brandMinimal, "Brand minimal should be distinct from clean product");
        assertNotEquals(stepCard, commentBubble, "Comment bubble should be distinct from step card");

        assertTrue(tiktokLarge.contains(",70,"), "TikTok large should use a larger medium-base font size");
        assertTrue(promoBold.contains("&H0000F2FF"), "Promo bold should use yellow primary text");
        assertTrue(cleanProduct.contains("&H002A1F13"), "Clean product should use a dark text color");
        assertTrue(neonPop.contains("&H00FFF200"), "Neon pop should use cyan primary text");
        assertTrue(yellowStory.contains("&H0037E8FF"), "Yellow story should use warm yellow primary text");
        assertTrue(priceFlash.contains("&H0000FFFF"), "Price flash should use bright yellow primary text");
        assertTrue(stepCard.contains("&H00221A10"), "Step card should use dark readable text");
        assertTrue(brandMinimal.contains("&H00514137"), "Brand minimal should use restrained dark brand text");
        assertTrue(commentBubble.contains("&H00221A10"), "Comment bubble should use dark readable text");
    }

    @Test
    void renderAddsBoxBackgroundForCommerceAndExplainerStyles() throws Exception {
        assertTrue(renderStyleLine("promo_bold").contains(",3,"),
                "Promo bold should use ASS opaque box border style");
        assertTrue(renderStyleLine("price_flash").contains(",3,"),
                "Price flash should use ASS opaque box border style");
        assertTrue(renderStyleLine("step_card").contains(",3,"),
                "Step card should use ASS opaque box border style");
        assertTrue(renderStyleLine("clean_product").contains(",3,"),
                "Clean product should use ASS opaque box border style");
        assertTrue(renderStyleLine("classic_white").contains(",1,4,1,"),
                "Classic style should remain plain outlined text without a box");
    }

    @Test
    void renderUsesPresetSpecificKeywordColors() throws Exception {
        String neon = renderDialogue("neon_pop", new TkSubtitleWord("secret", 0D, 1D, true));
        String price = renderDialogue("price_flash", new TkSubtitleWord("$19.99", 0D, 1D, true));
        String promo = renderDialogue("promo_bold", new TkSubtitleWord("限时", 0D, 1D, true));
        String classic = renderDialogue("classic_white", new TkSubtitleWord("Limited", 0D, 1D, true));

        assertTrue(neon.contains("{\\c&H00C86BFF}") && neon.contains("secret"),
                "Neon pop keywords should use hot pink");
        assertTrue(price.contains("{\\c&H000066FF}") && price.contains("$19.99"),
                "Price flash keywords should use orange/red");
        assertTrue(promo.contains("{\\c&H00FFFFFF}") && promo.contains("限时"),
                "Promo bold keywords should flip to white inside the yellow promo treatment");
        assertTrue(classic.contains("{\\c&H00FFFFFF}") && classic.contains("Limited"),
                "Classic white keywords should stay white instead of turning yellow");
        assertFalse(classic.contains("&H004DD8FF"),
                "Classic white should not render yellow keyword overrides");
    }

    private String renderStyleLine(String subtitleStyle) throws Exception {
        String ass = renderAss(subtitleStyle, Collections.emptyList());
        return Arrays.stream(ass.split("\\R"))
                .filter(line -> line.startsWith("Style: Default,"))
                .findFirst()
                .orElse("");
    }

    private String renderDialogue(String subtitleStyle, TkSubtitleWord word) throws Exception {
        String ass = renderAss(subtitleStyle, Collections.singletonList(word));
        return Arrays.stream(ass.split("\\R"))
                .filter(line -> line.startsWith("Dialogue:"))
                .findFirst()
                .orElse("");
    }

    private String renderAss(String subtitleStyle, java.util.List<TkSubtitleWord> words) throws Exception {
        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .subtitleStyle(subtitleStyle)
                .subtitleFontSize("medium")
                .subtitleKeywordColor("#FFD84D")
                .subtitleActiveColor("#35F27A")
                .subtitleKaraokeEnabled(!words.isEmpty())
                .build();
        TkSubtitleSegment segment = new TkSubtitleSegment("Limited offer today",
                0D, 2D, "bottom_center", 540, 1450, words);
        File file = File.createTempFile("subtitle-style", ".ass");

        service.render(task, new TkSubtitleLayout(Collections.singletonList(segment)), file);

        return Files.readString(file.toPath());
    }
}
