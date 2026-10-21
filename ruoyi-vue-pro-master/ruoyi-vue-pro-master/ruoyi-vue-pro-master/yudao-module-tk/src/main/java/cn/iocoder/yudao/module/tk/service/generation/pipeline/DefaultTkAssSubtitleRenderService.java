package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class DefaultTkAssSubtitleRenderService implements TkAssSubtitleRenderService {

    private static final int MAX_LINES = 2;
    private static final int MAX_ENGLISH_LINE_WEIGHT = 30;
    private static final int MAX_CJK_LINE_WEIGHT = TkSubtitleTextSegmentationSupport.CJK_LINE_WEIGHT;

    @Override
    public File render(TkGenerationTaskDO task, TkSubtitleLayout layout, File targetFile) {
        StringBuilder ass = new StringBuilder();
        SubtitleStyle style = resolveStyle(task);
        ass.append("[Script Info]\n")
                .append("ScriptType: v4.00+\n")
                .append("PlayResX: 1080\n")
                .append("PlayResY: 1920\n")
                .append("ScaledBorderAndShadow: yes\n")
                .append("WrapStyle: 2\n\n")
                .append("[V4+ Styles]\n")
                .append("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, ")
                .append("Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, ")
                .append("Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\n")
                .append("Style: Default,").append(style.fontName).append(',').append(style.fontSize).append(',')
                .append(toAssColor(Boolean.TRUE.equals(task.getSubtitleKaraokeEnabled()) ? style.activeColor : style.primaryColor))
                .append(',').append(toAssColor(style.primaryColor)).append(',')
                .append(toAssColor(style.outlineColor)).append(',').append(toAssColor(style.backColor)).append(',')
                .append(style.bold).append(',').append(style.italic).append(",0,0,100,100,0,0,")
                .append(style.borderStyle).append(',').append(style.outline).append(',').append(style.shadow)
                .append(',').append(style.alignment).append(",80,80,80,1\n\n")
                .append("[Events]\n")
                .append("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n");
        List<TkSubtitleSegment> displaySegments = new ArrayList<>();
        for (TkSubtitleSegment original : layout.getSegments()) {
            displaySegments.addAll(splitLegacyEnglish(original, style.fontSize));
        }
        for (TkSubtitleSegment segment : displaySegments) {
            ass.append("Dialogue: 0,")
                    .append(formatAssTime(segment.getStart())).append(',')
                    .append(formatAssTime(segment.getEnd())).append(',')
                    .append("Default,,0,0,0,,")
                    .append("{\\pos(").append(segment.getX()).append(',').append(segment.getY()).append(")}")
                    .append(horizontalFit(style, segment))
                    .append(renderText(task, style, segment))
                    .append('\n');
        }
        FileUtil.writeUtf8String(ass.toString(), targetFile);
        return targetFile;
    }

    private String renderText(TkGenerationTaskDO task, SubtitleStyle style, TkSubtitleSegment segment) {
        if (!Boolean.TRUE.equals(task.getSubtitleKaraokeEnabled()) || !canRenderKaraoke(segment)) {
            return "{\\1c" + toAssColor(style.primaryColor) + "}" + renderPlainText(task, style, segment);
        }
        StringBuilder text = new StringBuilder();
        List<List<TkSubtitleWord>> lines = splitWordsToLines(segment, style.fontSize);
        boolean mostlyEnglish = isMostlyEnglish(segment.getText());
        long karaokeCursor = 0L;
        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            if (lineIndex > 0) {
                text.append("\\N");
            }
            List<TkSubtitleWord> line = lines.get(lineIndex);
            for (int wordIndex = 0; wordIndex < line.size(); wordIndex++) {
                TkSubtitleWord word = line.get(wordIndex);
                if (mostlyEnglish && shouldInsertSpace(line, wordIndex)) {
                    text.append(' ');
                }
                int centiseconds = Math.max(1, (int) Math.round((word.getEnd() - word.getStart()) * 100));
                boolean keyword = !Boolean.FALSE.equals(task.getSubtitleKeywordEnabled()) && word.isKeyword();
                String spokenColor = keyword ? style.keywordColor : style.activeColor;
                String waitingColor = keyword ? style.keywordColor : style.primaryColor;
                text.append("{\\1c").append(toAssColor(spokenColor))
                        .append("\\2c").append(toAssColor(waitingColor)).append('}');
                String wordText = word.getText() == null ? "" : word.getText();
                if (!mostlyEnglish) {
                    if (wordIndex == 0) {
                        wordText = wordText.stripLeading();
                    }
                    if (wordIndex == line.size() - 1) {
                        wordText = wordText.stripTrailing();
                    }
                }
                // Empty k syllables preserve silence on libass < 0.17, where kt is unsupported.
                long start = Math.max(karaokeCursor, Math.round((word.getStart() - segment.getStart()) * 100));
                if (start > karaokeCursor) {
                    text.append("{\\k").append(start - karaokeCursor).append('}');
                }
                karaokeCursor = start + centiseconds;
                text.append("{\\kf").append(centiseconds).append('}')
                        .append(escapeAssText(wordText));
            }
        }
        return text.toString();
    }

    private List<TkSubtitleSegment> splitLegacyEnglish(TkSubtitleSegment original, int fontSize) {
        if (!isMostlyEnglish(StrUtil.blankToDefault(original.getText(), ""))) {
            return List.of(original);
        }
        int limit = englishLineWeight(fontSize);
        List<String> chunks = new ArrayList<>();
        StringBuilder chunk = new StringBuilder();
        int lines = 1;
        int width = 0;
        for (String token : normalizeDisplayText(original.getText()).split(" ")) {
            int nextWidth = width + (width > 0 ? 1 : 0) + token.length();
            if (width > 0 && nextWidth > limit) {
                lines++;
                width = 0;
            }
            if (lines > MAX_LINES) {
                chunks.add(chunk.toString());
                chunk.setLength(0);
                lines = 1;
            }
            if (chunk.length() > 0) {
                chunk.append(' ');
            }
            chunk.append(token);
            width += token.length() + (width > 0 ? 1 : 0);
        }
        if (chunk.length() > 0) {
            chunks.add(chunk.toString());
        }
        if (chunks.size() <= 1) {
            return List.of(original);
        }
        boolean actual = canRenderKaraoke(original);
        StringBuilder joined = new StringBuilder();
        if (original.getWords() != null) {
            for (TkSubtitleWord word : original.getWords()) {
                joined.append(word.getText());
            }
        }
        actual &= normalizeDisplayText(original.getText()).replace(" ", "").equals(joined.toString().replace(" ", ""));
        List<TkSubtitleSegment> result = new ArrayList<>();
        int wordCursor = 0;
        int textCursor = 0;
        int totalLength = Math.max(1, normalizeDisplayText(original.getText()).length());
        for (int index = 0; index < chunks.size(); index++) {
            String text = chunks.get(index);
            List<TkSubtitleWord> words = new ArrayList<>();
            if (actual) {
                int targetLength = text.replace(" ", "").length();
                int length = 0;
                while (wordCursor < original.getWords().size() && length < targetLength) {
                    TkSubtitleWord word = original.getWords().get(wordCursor++);
                    words.add(word);
                    length += word.getText().replace(" ", "").length();
                }
                if (length != targetLength) {
                    actual = false;
                }
            }
            double duration = original.getEnd() - original.getStart();
            double start = original.getStart() + duration * textCursor / totalLength;
            textCursor += text.length() + (index < chunks.size() - 1 ? 1 : 0);
            double end = index == chunks.size() - 1 ? original.getEnd()
                    : original.getStart() + duration * textCursor / totalLength;
            if (actual && !words.isEmpty()) {
                start = index == 0 ? original.getStart() : words.get(0).getStart();
                end = index == chunks.size() - 1 ? original.getEnd() : words.get(words.size() - 1).getEnd();
            }
            TkSubtitleSegment segment = new TkSubtitleSegment(text, start, end, original.getPosition(),
                    original.getX(), original.getY(), words);
            segment.setWordTimingReliable(actual);
            result.add(segment);
        }
        return result;
    }

    // Shared with diagnostics: exactly the same decision must describe what was rendered.
    static boolean canRenderKaraoke(TkSubtitleSegment segment) {
        if (!Boolean.TRUE.equals(segment.getWordTimingReliable()) || segment.getWords() == null
                || segment.getWords().isEmpty() || !Double.isFinite(segment.getStart())
                || !Double.isFinite(segment.getEnd()) || segment.getEnd() <= segment.getStart()) {
            return false;
        }
        double previousEnd = segment.getStart();
        for (TkSubtitleWord word : segment.getWords()) {
            if (!Double.isFinite(word.getStart()) || !Double.isFinite(word.getEnd())
                    || word.getStart() < previousEnd - 0.011D || word.getEnd() <= word.getStart()
                    || word.getEnd() > segment.getEnd() + 0.011D) {
                return false;
            }
            previousEnd = word.getEnd();
        }
        return true;
    }

    private int englishLineWeight(int fontSize) {
        return fontSize <= 82 ? MAX_ENGLISH_LINE_WEIGHT
                : Math.max(10, (int) (860D / (fontSize * 0.65D)));
    }

    private String horizontalFit(SubtitleStyle style, TkSubtitleSegment segment) {
        // Legacy tasks may contain a caption too long for two lines. Keep the exact text visible.
        String visible = wrapPlainText(segment, style.fontSize).replaceAll("\\\\[{}]", "");
        double maxWidth = 1D;
        for (String line : visible.split("\\\\N")) {
            double width = line.codePoints().mapToDouble(cp -> isCjk((char) cp) ? 1D : 0.65D).sum() * style.fontSize;
            maxWidth = Math.max(maxWidth, width);
        }
        double safeWidth = Math.min(900D, Math.min(segment.getX() - 80, 1000 - segment.getX()) * 2D);
        int scale = Math.max(1, Math.min(100, (int) Math.floor(safeWidth * 100D / maxWidth)));
        return scale < 100 ? "{\\fscx" + scale + "\\fscy" + scale + "}" : "";
    }

    private List<List<TkSubtitleWord>> splitWordsToLines(TkSubtitleSegment segment, int fontSize) {
        if (!isMostlyEnglish(segment.getText())) {
            return splitCjkWordsToLines(restoreCjkWordSpacing(segment));
        }
        List<List<TkSubtitleWord>> lines = new ArrayList<>();
        List<String> visibleLines = splitTextToLines(segment.getText(), fontSize);
        if (visibleLines.size() == 2) {
            int boundary = visibleLines.get(0).replace(" ", "").length();
            int cursor = 0;
            List<TkSubtitleWord> first = new ArrayList<>();
            List<TkSubtitleWord> second = new ArrayList<>();
            for (TkSubtitleWord word : segment.getWords()) {
                (cursor < boundary ? first : second).add(word);
                cursor += StrUtil.blankToDefault(word.getText(), "").replace(" ", "").length();
            }
            if (!first.isEmpty() && !second.isEmpty()) {
                lines.add(first);
                lines.add(second);
                return lines;
            }
        }
        List<TkSubtitleWord> line = new ArrayList<>();
        boolean mostlyEnglish = isMostlyEnglish(segment.getText());
        int maxWeight = mostlyEnglish ? englishLineWeight(fontSize) : MAX_CJK_LINE_WEIGHT;
        int lineWeight = 0;
        for (TkSubtitleWord word : segment.getWords()) {
            String wordText = StrUtil.blankToDefault(word.getText(), "");
            int wordWeight = displayWeight(wordText);
            int extraSpace = mostlyEnglish && !line.isEmpty() && needsLeadingSpace(wordText) ? 1 : 0;
            if (!line.isEmpty() && lineWeight + extraSpace + wordWeight > maxWeight
                    && lines.size() < MAX_LINES - 1) {
                lines.add(line);
                line = new ArrayList<>();
                lineWeight = 0;
                extraSpace = 0;
            }
            line.add(word);
            lineWeight += extraSpace + wordWeight;
        }
        lines.add(line);
        return lines;
    }

    private List<TkSubtitleWord> restoreCjkWordSpacing(TkSubtitleSegment segment) {
        String source = normalizeDisplayText(segment.getText());
        List<TkSubtitleWord> displayWords = new ArrayList<>();
        int cursor = 0;
        for (TkSubtitleWord word : segment.getWords()) {
            String wordText = word.getText() == null ? "" : word.getText();
            int start = cursor;
            for (int offset = 0; offset < wordText.length();) {
                int cp = wordText.codePointAt(offset);
                offset += Character.charCount(cp);
                if (TkSubtitleTextSegmentationSupport.isWhitespace(cp)) {
                    continue;
                }
                while (cursor < source.length()
                        && TkSubtitleTextSegmentationSupport.isWhitespace(source.codePointAt(cursor))) {
                    cursor += Character.charCount(source.codePointAt(cursor));
                }
                if (cursor >= source.length() || source.codePointAt(cursor) != cp) {
                    return segment.getWords();
                }
                cursor += Character.charCount(cp);
            }
            displayWords.add(new TkSubtitleWord(source.substring(start, cursor), word.getStart(), word.getEnd(),
                    word.isKeyword()));
        }
        return cursor == source.length() ? displayWords : segment.getWords();
    }

    private List<List<TkSubtitleWord>> splitCjkWordsToLines(List<TkSubtitleWord> words) {
        StringBuilder text = new StringBuilder();
        Set<Integer> wordEnds = new HashSet<>();
        int compactCursor = 0;
        for (TkSubtitleWord word : words) {
            text.append(word.getText() == null ? "" : word.getText());
            compactCursor += TkSubtitleTextSegmentationSupport.compactLength(word.getText());
            wordEnds.add(compactCursor);
        }
        List<List<TkSubtitleWord>> lines = new ArrayList<>();
        if (TkSubtitleTextSegmentationSupport.displayWeight(text.toString()) <= MAX_CJK_LINE_WEIGHT) {
            lines.add(words);
            return lines;
        }
        String source = text.toString();
        int splitIndex = TkSubtitleTextSegmentationSupport.chooseLineSplitIndex(source, offset -> wordEnds.contains(
                TkSubtitleTextSegmentationSupport.compactLength(source.substring(0, offset))));
        int compactSplitIndex = TkSubtitleTextSegmentationSupport.compactLength(source.substring(0, splitIndex));
        List<TkSubtitleWord> line = new ArrayList<>();
        int cursor = 0;
        for (TkSubtitleWord word : words) {
            if (cursor >= compactSplitIndex && lines.isEmpty() && !line.isEmpty()) {
                lines.add(line);
                line = new ArrayList<>();
            }
            line.add(word);
            cursor += TkSubtitleTextSegmentationSupport.compactLength(word.getText());
        }
        lines.add(line);
        return lines;
    }

    private boolean shouldInsertSpace(List<TkSubtitleWord> line, int wordIndex) {
        if (wordIndex <= 0) {
            return false;
        }
        String current = StrUtil.blankToDefault(line.get(wordIndex).getText(), "");
        String previous = StrUtil.blankToDefault(line.get(wordIndex - 1).getText(), "");
        return needsLeadingSpace(current) && endsWithAlphaNumeric(previous);
    }

    private boolean needsLeadingSpace(String text) {
        return StrUtil.isNotBlank(text) && startsWithAlphaNumeric(text);
    }

    private boolean startsWithAlphaNumeric(String text) {
        char ch = text.charAt(0);
        return Character.isLetterOrDigit(ch);
    }

    private boolean endsWithAlphaNumeric(String text) {
        if (StrUtil.isBlank(text)) {
            return false;
        }
        char ch = text.charAt(text.length() - 1);
        return Character.isLetterOrDigit(ch);
    }

    private String renderPlainText(TkGenerationTaskDO task, SubtitleStyle style, TkSubtitleSegment segment) {
        String source = normalizeDisplayText(segment.getText());
        List<int[]> ranges = new ArrayList<>();
        if (!Boolean.FALSE.equals(task.getSubtitleKeywordEnabled()) && segment.getWords() != null) {
            Set<String> keywords = new HashSet<>();
            for (TkSubtitleWord word : segment.getWords()) {
                if (word.isKeyword() && StrUtil.isNotBlank(word.getText())) {
                    keywords.add(word.getText());
                }
            }
            // Search visible source only: generated ASS colors must never become match candidates.
            for (String keyword : keywords) {
                for (int from = source.indexOf(keyword); from >= 0; from = source.indexOf(keyword, from + 1)) {
                    ranges.add(new int[]{from, from + keyword.length()});
                }
            }
        }
        ranges.sort(java.util.Comparator.comparingInt(range -> range[0]));
        List<int[]> merged = new ArrayList<>();
        for (int[] range : ranges) {
            if (!merged.isEmpty() && range[0] <= merged.get(merged.size() - 1)[1]) {
                int[] previous = merged.get(merged.size() - 1);
                previous[1] = Math.max(previous[1], range[1]);
            } else {
                merged.add(range);
            }
        }
        List<String> renderedLines = new ArrayList<>();
        int sourceCursor = 0;
        for (String line : plainTextLines(segment, style.fontSize)) {
            int lineStart = source.indexOf(line, sourceCursor);
            int lineEnd = lineStart + line.length();
            StringBuilder rendered = new StringBuilder();
            int cursor = lineStart;
            for (int[] range : merged) {
                int start = Math.max(lineStart, range[0]);
                int end = Math.min(lineEnd, range[1]);
                if (start >= end) {
                    continue;
                }
                rendered.append(escapeAssText(source.substring(cursor, start)))
                        .append("{\\c").append(toAssColor(style.keywordColor)).append('}')
                        .append(escapeAssText(source.substring(start, end)))
                        .append("{\\c").append(toAssColor(style.primaryColor)).append('}');
                cursor = end;
            }
            rendered.append(escapeAssText(source.substring(cursor, lineEnd)));
            renderedLines.add(rendered.toString());
            sourceCursor = lineEnd;
        }
        return String.join("\\N", renderedLines);
    }

    private String wrapPlainText(TkSubtitleSegment segment, int fontSize) {
        List<String> escapedLines = new ArrayList<>();
        for (String line : plainTextLines(segment, fontSize)) {
            escapedLines.add(escapeAssText(line));
        }
        return String.join("\\N", escapedLines);
    }

    private List<String> plainTextLines(TkSubtitleSegment segment, int fontSize) {
        String source = StrUtil.blankToDefault(segment.getText(), "");
        if (isMostlyEnglish(source) || segment.getWords() == null || segment.getWords().isEmpty()) {
            return splitTextToLines(source, fontSize);
        }
        List<TkSubtitleWord> displayWords = restoreCjkWordSpacing(segment);
        StringBuilder wordText = new StringBuilder();
        for (TkSubtitleWord word : displayWords) {
            wordText.append(word.getText() == null ? "" : word.getText());
        }
        if (!normalizeDisplayText(source).equals(wordText.toString())) {
            return splitTextToLines(source, fontSize);
        }
        List<String> lines = new ArrayList<>();
        for (List<TkSubtitleWord> words : splitCjkWordsToLines(displayWords)) {
            StringBuilder line = new StringBuilder();
            for (TkSubtitleWord word : words) {
                line.append(word.getText() == null ? "" : word.getText());
            }
            lines.add(line.toString().trim());
        }
        return lines;
    }

    private List<String> splitTextToLines(String text, int fontSize) {
        String normalized = normalizeDisplayText(text);
        List<String> lines = new ArrayList<>();
        if (StrUtil.isBlank(normalized)) {
            lines.add("");
            return lines;
        }
        if (isMostlyEnglish(normalized)) {
            splitEnglishLines(normalized, lines, englishLineWeight(fontSize));
        } else {
            splitCjkLines(normalized, lines);
        }
        return lines;
    }

    private String normalizeDisplayText(String text) {
        return StrUtil.blankToDefault(text, "").trim().replaceAll("\\s+", " ");
    }

    private void splitEnglishLines(String text, List<String> lines, int maxWeight) {
        if (text.length() > maxWeight) {
            int split = -1;
            int best = Integer.MAX_VALUE;
            for (int index = text.indexOf(' '); index >= 0; index = text.indexOf(' ', index + 1)) {
                int difference = Math.abs(index - (text.length() - index - 1));
                if (index <= maxWeight && text.length() - index - 1 <= maxWeight && difference < best) {
                    split = index;
                    best = difference;
                }
            }
            if (split >= 0) {
                lines.add(text.substring(0, split));
                lines.add(text.substring(split + 1));
                return;
            }
        }
        StringBuilder line = new StringBuilder();
        int lineWeight = 0;
        for (String token : text.split(" ")) {
            int tokenWeight = displayWeight(token);
            int extraSpace = line.length() == 0 ? 0 : 1;
            if (line.length() > 0 && lineWeight + extraSpace + tokenWeight > maxWeight
                    && lines.size() < MAX_LINES - 1) {
                lines.add(line.toString());
                line.setLength(0);
                lineWeight = 0;
                extraSpace = 0;
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(token);
            lineWeight += extraSpace + tokenWeight;
        }
        lines.add(line.toString());
    }

    private void splitCjkLines(String text, List<String> lines) {
        if (TkSubtitleTextSegmentationSupport.displayWeight(text) <= MAX_CJK_LINE_WEIGHT) {
            lines.add(text);
            return;
        }
        int splitIndex = TkSubtitleTextSegmentationSupport.chooseLineSplitIndex(text, index -> true);
        lines.add(text.substring(0, splitIndex).trim());
        if (splitIndex < text.length()) {
            lines.add(text.substring(splitIndex).trim());
        }
    }

    private boolean isMostlyEnglish(String text) {
        int latin = 0;
        int cjk = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z')) {
                latin++;
            } else if (isCjk(ch)) {
                cjk++;
            }
        }
        return latin >= cjk;
    }

    private int displayWeight(String text) {
        int weight = 0;
        for (int i = 0; i < text.length(); i++) {
            weight += charWeight(text.charAt(i));
        }
        return weight;
    }

    private int charWeight(char ch) {
        return isCjk(ch) ? 2 : 1;
    }

    private boolean isCjk(char ch) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(ch);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.HIRAGANA
                || block == Character.UnicodeBlock.KATAKANA
                || block == Character.UnicodeBlock.HANGUL_SYLLABLES;
    }

    private static SubtitleStyle resolveStyle(TkGenerationTaskDO task) {
        String style = StrUtil.blankToDefault(task.getSubtitleStyle(), "classic_white");
        int fontSize = resolveFontSize(task.getSubtitleFontSize());
        String activeColor = StrUtil.blankToDefault(task.getSubtitleActiveColor(), "#35F27A");
        String keywordColor = StrUtil.blankToDefault(task.getSubtitleKeywordColor(), "#FFD84D");
        if (StrUtil.equals(style, "promo_bold")) {
            return new SubtitleStyle("Noto Sans CJK SC", fontSize, "#FFF200", "#FF3B30", "#FFFFFF", "#2B0505",
                    "#E02020", 3, 3, 0, 2, -1, 0);
        }
        if (StrUtil.equals(style, "tiktok_large")) {
            return new SubtitleStyle("Noto Sans CJK SC", fontSize, "#FFFFFF", "#00F2FF", "#FF3B8D", "#080A12",
                    "#000000", 1, 6, 2, 2, -1, 0);
        }
        if (StrUtil.equals(style, "clean_product")) {
            return new SubtitleStyle("Noto Sans CJK SC", fontSize, "#131F2A", activeColor, "#1677FF", "#D5E4F0",
                    "#F8FBFF", 3, 2, 0, 2, -1, 0);
        }
        if (StrUtil.equals(style, "yellow_keyword")) {
            return new SubtitleStyle("Noto Sans CJK SC", fontSize, "#FFFFFF", activeColor, keywordColor, "#111111",
                    "#000000", 1, 5, 0, 2, -1, 0);
        }
        if (StrUtil.equals(style, "neon_pop")) {
            return new SubtitleStyle("Noto Sans CJK SC", fontSize, "#00F2FF", "#35F27A", "#FF6BC8", "#080A12",
                    "#000000", 1, 5, 2, 2, -1, 0);
        }
        if (StrUtil.equals(style, "yellow_story")) {
            return new SubtitleStyle("Noto Serif CJK SC", fontSize, "#FFE837", "#FFFFFF", "#FF3D00", "#151515",
                    "#000000", 1, 3, 2, 2, -1, -1);
        }
        if (StrUtil.equals(style, "price_flash")) {
            return new SubtitleStyle("Noto Sans CJK SC", fontSize, "#FFFF00", "#FFFFFF", "#FF6600", "#000000",
                    "#111111", 3, 4, 0, 2, -1, 0);
        }
        if (StrUtil.equals(style, "step_card")) {
            return new SubtitleStyle("Noto Sans CJK SC", fontSize, "#101A22", activeColor, "#0EA5E9", "#CBD5E1",
                    "#F8FAFC", 3, 2, 0, 2, -1, 0);
        }
        if (StrUtil.equals(style, "brand_minimal")) {
            return new SubtitleStyle("Noto Serif CJK SC", fontSize, "#374151", "#2563EB", "#2563EB", "#CBD5E1",
                    "#FFFFFF", 3, 2, 0, 2, 0, 0);
        }
        if (StrUtil.equals(style, "comment_bubble")) {
            return new SubtitleStyle("Noto Sans CJK SC", fontSize, "#101A22", "#1677FF", "#1677FF", "#D6E1EA",
                    "#FFFFFF", 3, 2, 1, 2, -1, 0);
        }
        return new SubtitleStyle("Noto Sans CJK SC", fontSize, "#FFFFFF", activeColor, keywordColor, "#000000",
                "#000000", 1, 4, 1, 2, -1, 0);
    }

    static int fontSize(TkGenerationTaskDO task) {
        return resolveStyle(task).fontSize;
    }

    private static int resolveFontSize(String value) {
        if (StrUtil.equals(value, "small")) {
            return 58;
        }
        if (StrUtil.equals(value, "large")) {
            return 86;
        }
        if (StrUtil.equals(value, "extra_large")) {
            return 96;
        }
        return 68;
    }

    private String toAssColor(String hexColor) {
        String normalized = StrUtil.blankToDefault(hexColor, "#FFFFFF").replace("#", "");
        if (normalized.length() == 6) {
            String rr = normalized.substring(0, 2);
            String gg = normalized.substring(2, 4);
            String bb = normalized.substring(4, 6);
            return "&H00" + bb + gg + rr;
        }
        return "&H00FFFFFF";
    }

    private String formatAssTime(double seconds) {
        int totalCentiseconds = Math.max(0, (int) Math.round(seconds * 100));
        int hour = totalCentiseconds / 360000;
        int minute = totalCentiseconds % 360000 / 6000;
        int second = totalCentiseconds % 6000 / 100;
        int centisecond = totalCentiseconds % 100;
        return String.format("%d:%02d:%02d.%02d", hour, minute, second, centisecond);
    }

    private String escapeAssText(String text) {
        return (text == null ? "" : text)
                .replace("\\", "\\\\")
                .replace("{", "\\{")
                .replace("}", "\\}")
                .replace("\n", "\\N");
    }

    private static class SubtitleStyle {

        private final String fontName;
        private final int fontSize;
        private final String primaryColor;
        private final String activeColor;
        private final String keywordColor;
        private final String outlineColor;
        private final String backColor;
        private final int borderStyle;
        private final int outline;
        private final int shadow;
        private final int alignment;
        private final int bold;
        private final int italic;

        private SubtitleStyle(String fontName, int fontSize, String primaryColor, String activeColor, String keywordColor,
                              String outlineColor, String backColor, int borderStyle, int outline, int shadow,
                              int alignment, int bold, int italic) {
            this.fontName = fontName;
            this.fontSize = fontSize;
            this.primaryColor = primaryColor;
            this.activeColor = activeColor;
            this.keywordColor = keywordColor;
            this.outlineColor = outlineColor;
            this.backColor = backColor;
            this.borderStyle = borderStyle;
            this.outline = outline;
            this.shadow = shadow;
            this.alignment = alignment;
            this.bold = bold;
            this.italic = italic;
        }
    }

}
