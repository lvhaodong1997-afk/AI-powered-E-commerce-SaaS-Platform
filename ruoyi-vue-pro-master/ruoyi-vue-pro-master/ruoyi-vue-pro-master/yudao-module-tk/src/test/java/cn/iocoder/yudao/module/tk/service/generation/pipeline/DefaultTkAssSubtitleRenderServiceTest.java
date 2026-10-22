package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultTkAssSubtitleRenderServiceTest {

    private final DefaultTkAssSubtitleRenderService service = new DefaultTkAssSubtitleRenderService();

    @BeforeEach
    void useProductionFontsWhenSupplied() {
        String fonts = System.getProperty("tk.subtitle.fonts");
        if (fonts != null) {
            cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties properties =
                    new cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties();
            properties.getSubtitle().setFontsDir(fonts);
            ReflectionTestUtils.setField(service, "generationProperties", properties);
        }
    }

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
        assertTrue(ass.replace("\\N", " ").contains("Sign relax your fingers."),
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
    void renderFallsBackToStableCaptionWhenWordTimingIsEstimated() throws Exception {
        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .subtitleStyle("classic_white")
                .subtitleFontSize("medium")
                .subtitleKaraokeEnabled(true)
                .build();
        TkSubtitleSegment segment = new TkSubtitleSegment("Limited offer today",
                0D, 2D, "bottom_center", 540, 1450, Arrays.asList(
                new TkSubtitleWord("Limited", 0D, 0.8D, false),
                new TkSubtitleWord("offer", 0.8D, 1.4D, false),
                new TkSubtitleWord("today", 1.4D, 2D, false)
        ));
        segment.setWordTimingReliable(false);
        File file = File.createTempFile("subtitle-stable", ".ass");

        service.render(task, new TkSubtitleLayout(Collections.singletonList(segment)), file);

        String ass = Files.readString(file.toPath());
        assertFalse(ass.contains("{\\kf"),
                "Estimated word timing must render as a stable caption instead of a visibly incorrect karaoke animation");
        assertTrue(ass.contains("Limited offer today"));
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

        assertTrue(tiktokLarge.contains(",68,"), "Preset styling should retain the selected medium font size");
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

        assertTrue(neon.contains("{\\1c&H00C86BFF\\2c&H00C86BFF}") && neon.contains("secret"),
                "Neon pop keywords should use hot pink");
        assertTrue(price.contains("{\\1c&H000066FF\\2c&H000066FF}") && price.contains("$19.99"),
                "Price flash keywords should use orange/red");
        assertTrue(promo.contains("{\\1c&H00FFFFFF\\2c&H00FFFFFF}") && promo.contains("限时"),
                "Promo bold keywords should flip to white inside the yellow promo treatment");
        assertTrue(classic.contains("{\\1c&H004DD8FF\\2c&H004DD8FF}") && classic.contains("Limited"),
                "Configured keyword color must take priority even in classic white");
        assertTrue(classic.contains("&H004DD8FF"),
                "Classic white respects the configured keyword color");
    }

    private String renderStyleLine(String subtitleStyle) throws Exception {
        String ass = renderAss(subtitleStyle, Collections.emptyList());
        return Arrays.stream(ass.split("\\R"))
                .filter(line -> line.startsWith("Style: Default,"))
                .findFirst()
                .orElse("");
    }

    @Test
    void screenshotCaptionUsesOneLineWheneverTheSelectedSizeFits() throws Exception {
        for (boolean reliable : List.of(false, true)) {
            assertEquals("试试这个厚切芒果干。", renderChineseCaption("试试这个厚切芒果干。", reliable),
                    "A fitting screenshot caption must not be balanced into two short lines");
        }
    }

    @Test
    void overflowingCaptionFillsTheFirstLineBeforeWrapping() throws Exception {
        String source = "甲乙丙丁戊己庚辛壬癸甲乙丙丁戊己庚辛壬癸";
        for (boolean reliable : List.of(false, true)) {
            String visible = renderChineseCaption(source, reliable);
            String[] lines = visible.split("\\\\N");
            assertEquals(2, lines.length);
            assertTrue(lines[0].codePointCount(0, lines[0].length()) >= 10,
                    "The first line should fill the measured safe width instead of balancing at 9 CJK: " + visible);
            assertEquals(source, visible.replace("\\N", ""));
        }
    }

    @Test
    void plainAndKaraokeCaptionsUseTheSameReadableChineseLineBreaks() throws Exception {
        for (String text : Arrays.asList("买芒果干 先看配料表", "也不额外添加其他任何东西",
                "每一口都是自然的芒果香", "只选用成熟芒果厚切加上高温烘干", "配料表 只有芒果不加任何糖")) {
            String plain = renderChineseCaption(text, false);
            String karaoke = renderChineseCaption(text, true);

            assertEquals(plain, karaoke, "Timing reliability must not change the visible line breaks");
            assertEquals(text.replace(" ", ""), plain.replace("\\N", "").replace(" ", ""));
            assertFalse(plain.contains("配料\\N表") || plain.contains("任\\N何")
                    || plain.contains("芒\\N果") || plain.contains("自\\N然"));
            String[] lines = plain.replace("\\N", "\n").split("\n");
            assertTrue(lines.length <= 2);
            TkSubtitleLineBreakSupport metrics = new TkSubtitleLineBreakSupport(System.getProperty("tk.subtitle.fonts"),
                    "Noto Sans CJK SC", 86, -1, 0, 4, 1, 540);
            int scale = metrics.scale(List.of(lines));
            for (String line : lines) {
                assertTrue(metrics.width(line) * scale / 100D <= metrics.safeWidth(), line);
            }
        }
        assertEquals("配料表只有芒果不加任何糖", renderChineseCaption("配料表 只有芒果不加任何糖", true).replace("\\N", "").replace(" ", ""));
    }

    @Test
    void plainChineseCaptionKeepsLatinWordsAndSupplementaryCharactersIntact() throws Exception {
        String text = "新品 Mango 芒果干真好吃🤩";
        String visible = renderChineseCaption(text, false);

        assertEquals(text.replace(" ", ""), visible.replace("\\N", "").replace(" ", ""));
        assertTrue(visible.contains("Mango"));
        assertTrue(visible.contains("🤩"));
    }

    @Test
    void plainAndKaraokeChineseCaptionsKeepCompleteLatinTokens() throws Exception {
        String[] texts = {"这个品牌叫Tik-Tok芒果干很好", "配料含量为100%芒果无需添加", "你知道吗用it's来形容芒果干"};
        String[] tokens = {"Tik-Tok", "100%", "it's"};
        for (int i = 0; i < texts.length; i++) {
            String plain = renderChineseCaption(texts[i], false);
            String karaoke = renderChineseCaption(texts[i], true);

            assertEquals(plain, karaoke);
            assertTrue(plain.contains(tokens[i]), "A Latin token must stay on one line: " + plain);
            assertEquals(texts[i], plain.replace("\\N", ""));
        }
    }

    @Test
    void overlongChineseCaptionUsesTheAvailableFirstLineWidth() throws Exception {
        String visible = renderChineseCaption("只要套上这个提拉带，几秒钟就能轻松抬脚，老人日常走路更省力。", true);
        String firstLine = visible.replace("\\N", "\n").split("\n")[0];

        assertTrue(firstLine.codePointCount(0, firstLine.length()) >= 6,
                "An oversized legacy caption should not collapse its first line to one character");
    }

    @Test
    void chineseCaptionWithoutAnEqualWidthPhraseBoundaryUsesANearbyBreak() throws Exception {
        String text = "只选用成熟芒果再厚切高温烘干口感很好";
        for (boolean reliable : Arrays.asList(false, true)) {
            String visible = renderChineseCaption(text, reliable);
            String firstLine = visible.replace("\\N", "\n").split("\n")[0];

            assertTrue(firstLine.codePointCount(0, firstLine.length()) >= 6);
            assertTrue(firstLine.codePointCount(0, firstLine.length()) >= 9, "Use the available font width: " + visible);
            assertTrue(visible.contains("厚切"));
            assertEquals(text, visible.replace("\\N", ""));
        }
    }

    private String renderChineseCaption(String text, boolean reliableTiming) throws Exception {
        TkGenerationTaskDO task = TkGenerationTaskDO.builder()
                .subtitleStyle("classic_white").subtitleFontSize("large").subtitleKaraokeEnabled(true).build();
        List<TkSubtitleWord> words = ReflectionTestUtils.invokeMethod(new DefaultTkSubtitleTimelineService(),
                "buildWords", text, 0D, 2D, Collections.emptyList());
        TkSubtitleSegment caption = new TkSubtitleSegment(text, 0D, 2D,
                "bottom_center", 540, 1450, words);
        caption.setWordTimingReliable(reliableTiming);
        File file = File.createTempFile("subtitle-readable", ".ass");
        service.render(task, new TkSubtitleLayout(Collections.singletonList(caption)), file);
        String dialogue = Files.readString(file.toPath()).lines()
                .filter(line -> line.startsWith("Dialogue:")).findFirst().orElseThrow();
        assertEquals(reliableTiming, dialogue.contains("{\\kf"));
        return dialogue.split(",", 10)[9].replaceAll("\\{[^}]*}", "");
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

    @Test
    void priceKeywordsNeverRewriteGeneratedAssColorsInPlainOrEstimatedKaraoke() throws Exception {
        for (boolean karaoke : List.of(false, true)) {
            String text = renderHighlighted("一斤装 39.8", List.of("39.", "8"), karaoke, false);
            assertTrue(text.contains("一斤装 {\\c&H004DD8FF}39.8{\\c&H00FFFFFF}"), text);
            assertFalse(text.contains("{\\kf"));
        }
    }

    @Test
    void repeatedAndOverlappingKeywordsEmitOneHighlightPerVisibleRange() throws Exception {
        String text = renderHighlighted("banana banana", List.of("ana", "banana", "banana", "nan"), false, false);
        assertTrue(text.endsWith("{\\c&H004DD8FF}banana{\\c&H00FFFFFF} "
                + "{\\c&H004DD8FF}banana{\\c&H00FFFFFF}"), text);
        assertEquals(2, text.split("\\Q{\\c&H004DD8FF}\\E", -1).length - 1);
    }

    @Test
    void hexLikeKeywordsMatchOnlyVisibleTextAndEscapeAssSensitiveCharactersOnce() throws Exception {
        String text = renderHighlighted("8FF {D8} \\N 39.8", List.of("8", "D8", "8FF", "{D8}", "\\N"), false, false);
        assertTrue(text.endsWith("{\\c&H004DD8FF}8FF{\\c&H00FFFFFF} "
                + "{\\c&H004DD8FF}\\{D8\\}{\\c&H00FFFFFF} "
                + "{\\c&H004DD8FF}\\\\N{\\c&H00FFFFFF} 39."
                + "{\\c&H004DD8FF}8{\\c&H00FFFFFF}"), text);
    }

    @Test
    void highlightingPreservesChineseAndEnglishWrapping() throws Exception {
        for (String source : List.of("配料表 只有芒果不加任何糖", "Fresh mango delicious fruit today")) {
            String highlighted = renderHighlighted(source, List.of("果", "芒果", "mango", "a", "8"), true, false);
            String plain = renderHighlighted(source, Collections.emptyList(), true, false);
            assertEquals(plain, highlighted.replace("{\\c&H004DD8FF}", "").replace("{\\c&H00FFFFFF}", ""));
            assertEquals(source.replace(" ", ""), plain.replaceAll("\\\\N", "").replaceAll("\\{[^}]*}", "").replace(" ", ""));
        }
    }

    @Test
    void reliablePriceKaraokeKeepsTimingAndUncorruptedColors() throws Exception {
        String text = renderHighlighted("一斤装 39.8", List.of("一斤装 ", "39.", "8"), true, true);
        assertTrue(text.contains("{\\kf50}"));
        assertTrue(text.contains("{\\1c&H004DD8FF\\2c&H004DD8FF}{\\kf50} 39."));
        assertTrue(text.endsWith("{\\1c&H004DD8FF\\2c&H004DD8FF}{\\kf50}8"), text);
    }

    private String renderHighlighted(String source, List<String> keywords, boolean karaoke, boolean reliable) throws Exception {
        TkGenerationTaskDO task = TkGenerationTaskDO.builder().subtitleStyle("classic_white")
                .subtitleFontSize("medium").subtitleKeywordColor("#FFD84D")
                .subtitleKaraokeEnabled(karaoke).subtitleKeywordEnabled(true).build();
        java.util.ArrayList<TkSubtitleWord> words = new java.util.ArrayList<>();
        for (int i = 0; i < keywords.size(); i++) {
            words.add(new TkSubtitleWord(keywords.get(i), i * .5, (i + 1) * .5, true));
        }
        TkSubtitleSegment segment = new TkSubtitleSegment(source, 0, 3, "bottom_center", 540, 1450, words);
        segment.setWordTimingReliable(reliable);
        File file = File.createTempFile("subtitle-highlight", ".ass");
        service.render(task, new TkSubtitleLayout(List.of(segment)), file);
        return Files.readString(file.toPath()).lines().filter(line -> line.startsWith("Dialogue:"))
                .findFirst().orElseThrow().split(",", 10)[9];
    }
}
