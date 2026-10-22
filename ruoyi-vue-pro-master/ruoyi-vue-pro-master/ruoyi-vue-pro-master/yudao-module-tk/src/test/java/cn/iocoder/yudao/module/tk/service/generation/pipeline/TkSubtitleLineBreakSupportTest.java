package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class TkSubtitleLineBreakSupportTest {
    @TempDir Path emptyFonts;

    private TkSubtitleLineBreakSupport metrics(int size, int x) {
        return new TkSubtitleLineBreakSupport(emptyFonts.toString(), "Unavailable test font", size, -1, 0, 4, 1, x);
    }

    @Test void fittingScreenshotCaptionStaysWholeEvenWithoutAnInstalledFont() {
        String text = "试试这个厚切芒果干。";
        TkSubtitleLineBreakSupport metrics = metrics(86, 540);
        assertFalse(metrics.hasMatchingFont());
        assertEquals(text.length(), metrics.breakIndex(text, i -> true));
        assertEquals(100, metrics.scale(List.of(text)));
    }

    @Test void missingExactFontWarnsOncePerDirectoryAndFamily() {
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger)
                org.slf4j.LoggerFactory.getLogger(TkSubtitleLineBreakSupport.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> events =
                new ch.qos.logback.core.read.ListAppender<>();
        events.start();
        logger.addAppender(events);
        try {
            assertFalse(metrics(68, 540).hasMatchingFont());
            assertFalse(metrics(86, 540).hasMatchingFont());
            assertEquals(1, events.list.size());
            assertEquals(ch.qos.logback.classic.Level.WARN, events.list.get(0).getLevel());
            assertTrue(events.list.get(0).getFormattedMessage().contains("Unavailable test font"));
            assertTrue(events.list.get(0).getFormattedMessage().contains("using conservative glyph widths"));
        } finally {
            logger.detachAppender(events);
            events.stop();
        }
    }

    @Test void overflowUsesMeasuredWidthAndPositionInsteadOfCharacterCount() {
        String text = "甲乙丙丁戊己庚辛壬癸甲乙丙丁";
        TkSubtitleLineBreakSupport centered = metrics(68, 540);
        int split = centered.breakIndex(text, i -> true);
        assertEquals(12, split);
        assertTrue(centered.width(text.substring(0, split)) <= centered.safeWidth());
        assertTrue(centered.width(text.substring(0, split + 1)) > centered.safeWidth());
        assertTrue(metrics(68, 420).breakIndex(text, i -> true) < split);
        assertTrue(metrics(96, 540).breakIndex(text, i -> true) < split);
    }

    @Test void breaksRetreatOnlyToNearbyProtectedBoundariesAndKeepUnicodeTokens() {
        TkSubtitleLineBreakSupport metrics = metrics(68, 540);
        for (String token : List.of("Mango", "Tik-Tok", "it's", "100%", "39.8", "🤩", "👩🏽‍💻", "🇨🇳", "é", "\uD840\uDC00")) {
            String text = "甲乙丙丁戊己庚辛壬癸" + token + "甲乙丙丁戊己";
            int split = metrics.breakIndex(text, i -> true);
            assertFalse(split > 0 && Character.isLowSurrogate(text.charAt(split)));
            assertFalse(split > text.indexOf(token) && split < text.indexOf(token) + token.length(), text);
        }
        String phrase = "甲乙丙丁戊己庚辛壬癸甲芒果乙丙丁";
        int split = metrics.breakIndex(phrase, i -> true);
        assertFalse(split > phrase.indexOf("芒果") && split < phrase.indexOf("芒果") + 2);
    }

    @Test void originalTimedWordBoundariesAreNeverSplitEvenForAnOversizedWord() {
        String text = "甲乙丙丁戊己庚辛壬癸甲乙丙丁戊己庚辛壬癸";
        TkSubtitleLineBreakSupport metrics = metrics(86, 540);
        assertEquals(14, metrics.breakIndex(text, i -> i == 14));
        assertTrue(metrics.scale(List.of(text.substring(0, 14), text.substring(14))) < 100);
        assertEquals(text.length(), metrics.breakIndex(text, i -> false));
    }

    @Test void whitespaceCannotHideClosingOrOpeningPunctuationAtVisibleLineEdges() {
        String text = "甲乙丙丁戊己庚辛壬癸甲乙 。丙丁戊己庚辛";
        TkSubtitleLineBreakSupport metrics = metrics(68, 540);
        assertEquals(11, metrics.breakIndex(text, i -> true));
        assertEquals(11, metrics.breakIndex(text, i -> i == 11 || i == 12));
        assertFalse(TkSubtitleTextSegmentationSupport.safeLineBoundaries(text).test(12));
        String opening = "甲乙丙丁戊己庚辛壬癸（ 乙丙丁戊己庚辛";
        assertFalse(TkSubtitleTextSegmentationSupport.safeLineBoundaries(opening).test(12));
    }

    @Test void realConfiguredFontFamilyIsUsedWithoutLogicalFontSubstitution() {
        String fonts = System.getProperty("tk.subtitle.fonts");
        assumeTrue(fonts != null, "Supply -Dtk.subtitle.fonts with the production TTC fixtures");
        TkSubtitleLineBreakSupport metrics = new TkSubtitleLineBreakSupport(fonts, "Noto Sans CJK SC", 68, -1, 0, 4, 1, 540);
        assertTrue(metrics.hasMatchingFont(), "The supplied production TTC must load its exact SC face");
        assertTrue(metrics.width("iiiiiiii") < metrics.width("WWWWWWWW"));
        String text = "试试这个厚切芒果干。";
        assertEquals(text.length(), metrics.breakIndex(text, i -> true));
    }
}
