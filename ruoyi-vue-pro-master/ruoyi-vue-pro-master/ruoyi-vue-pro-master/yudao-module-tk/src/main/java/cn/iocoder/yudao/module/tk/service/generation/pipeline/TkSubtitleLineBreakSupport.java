package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntPredicate;

/** Display-only, single-line-first layout. Never changes caption or word timing. */
final class TkSubtitleLineBreakSupport {
    private static final Logger LOG = LoggerFactory.getLogger(TkSubtitleLineBreakSupport.class);
    private static final FontRenderContext CONTEXT = new FontRenderContext(null, true, true);
    private static final Map<String, List<Font>> FONT_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();
    private final Font font;
    private final double emSize;
    private final double safeWidth;

    TkSubtitleLineBreakSupport(String fontsDir, String family, int fontSize, int bold, int italic,
                              int outline, int shadow, int x) {
        // Leave room for outline/box, shadow, and small Java/libass shaping differences.
        this.safeWidth = Math.max(1D, Math.min(900D, 2D * Math.min(x - 80, 1000 - x))
                - 2D * (outline + shadow) - 8D);
        String directory = fontsDir == null ? "" : new File(fontsDir).getAbsolutePath();
        int style = (bold != 0 ? Font.BOLD : Font.PLAIN) | (italic != 0 ? Font.ITALIC : Font.PLAIN);
        Font selected = selectFont(FONT_CACHE.computeIfAbsent(directory, TkSubtitleLineBreakSupport::loadFonts), family, bold);
        Font styled = selected == null ? null : selected.deriveFont(style, (float) fontSize);
        // libass requests FT_SIZE_REQUEST_TYPE_REAL_DIM: ASS size is ascent + descent, not Java em.
        if (styled != null) {
            java.awt.font.LineMetrics line = styled.getLineMetrics("Hg", CONTEXT);
            styled = styled.deriveFont((float) (fontSize * fontSize / (line.getAscent() + line.getDescent())));
        }
        this.font = styled;
        this.emSize = styled == null ? fontSize : styled.getSize2D();
        if (font == null && WARNED.add(directory + ":" + family)) {
            LOG.warn("Subtitle width measurement has no exact font family {} in {}; using conservative glyph widths",
                    family, directory.isEmpty() ? "installed fonts" : directory);
        }
    }

    boolean hasMatchingFont() { return font != null; }
    double safeWidth() { return safeWidth; }

    double width(String text) {
        if (text == null || text.isEmpty()) return 0D;
        if (font == null) return fallbackWidth(text);
        TextLayout layout = new TextLayout(text, font, CONTEXT);
        Rectangle2D bounds = layout.getBounds();
        double width = Math.max(layout.getAdvance(), bounds.getMaxX()) - Math.min(0D, bounds.getMinX());
        // Missing glyphs must not acquire the narrow .notdef advance and incorrectly fit.
        for (int offset = 0; offset < text.length();) {
            int cp = text.codePointAt(offset);
            String glyph = new String(Character.toChars(cp));
            offset += Character.charCount(cp);
            if (!font.canDisplay(cp)) {
                width += Math.max(0D, fallbackWidth(glyph) - font.getStringBounds(glyph, CONTEXT).getWidth());
            }
        }
        return width * 1.01D;
    }

    private double fallbackWidth(String text) {
        return text.codePoints().mapToDouble(cp -> {
            if (cp >= 128) return 1D;
            if (Character.isWhitespace(cp)) return .35D;
            if ("WMwm@%&".indexOf(cp) >= 0) return 1D;
            return Character.isUpperCase(cp) ? .8D : .65D;
        }).sum() * emSize * 1.01D;
    }

    int breakIndex(String text, IntPredicate wordBoundary) {
        if (width(text) <= safeWidth) return text.length();
        IntPredicate safe = TkSubtitleTextSegmentationSupport.safeLineBoundaries(text);
        int fullest = -1;
        int firstOverflow = -1;
        for (int offset = 0; offset < text.length();) {
            offset += Character.charCount(text.codePointAt(offset));
            if (offset >= text.length() || !wordBoundary.test(offset) || !safe.test(offset)) continue;
            if (width(text.substring(0, offset).stripTrailing()) <= safeWidth) {
                fullest = offset;
            } else if (firstOverflow < 0) {
                firstOverflow = offset;
            }
        }
        if (fullest < 0) return firstOverflow > 0 ? firstOverflow : text.length();
        // Prefer a nearby phrase/space/punctuation boundary only after actual overflow.
        double fullWidth = width(text.substring(0, fullest).stripTrailing());
        int best = fullest;
        int priority = TkSubtitleTextSegmentationSupport.lineBoundaryPriority(text, fullest);
        for (int offset = fullest; offset > 0;) {
            offset -= Character.charCount(text.codePointBefore(offset));
            if (offset <= 0 || !wordBoundary.test(offset) || !safe.test(offset)) continue;
            if (fullWidth - width(text.substring(0, offset).stripTrailing()) > emSize * 2D) break;
            int candidate = TkSubtitleTextSegmentationSupport.lineBoundaryPriority(text, offset);
            if (candidate > priority) { best = offset; priority = candidate; }
        }
        return best;
    }

    int scale(List<String> lines) {
        double widest = lines.stream().mapToDouble(this::width).max().orElse(1D);
        return Math.max(1, Math.min(100, (int) Math.floor(safeWidth * 100D / Math.max(1D, widest))));
    }

    private static Font selectFont(List<Font> fonts, String family, int bold) {
        return fonts.stream().filter(candidate -> candidate.getFamily(Locale.ROOT).equalsIgnoreCase(family))
                .min(Comparator.comparingInt(candidate -> {
                    boolean isBold = candidate.isBold() || candidate.getFontName(Locale.ROOT).toLowerCase(Locale.ROOT).contains("bold");
                    return isBold == (bold != 0) ? 0 : 1;
                })).orElse(null);
    }

    private static List<Font> loadFonts(String directory) {
        List<Font> fonts = new ArrayList<>();
        if (!directory.isEmpty()) {
            File[] files = new File(directory).listFiles(file -> file.isFile()
                    && file.getName().toLowerCase(Locale.ROOT).matches(".*\\.(ttf|otf|ttc|otc)"));
            if (files != null) {
                Arrays.sort(files, Comparator.comparing(File::getName));
                for (File file : files) {
                    try { fonts.addAll(Arrays.asList(Font.createFonts(file))); }
                    catch (Exception ex) { LOG.warn("Cannot measure subtitle font {}: {}", file, ex.toString()); }
                }
            }
        }
        // Configured faces have priority. Only exact installed families are considered by selectFont.
        fonts.addAll(Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts()));
        return fonts;
    }
}
