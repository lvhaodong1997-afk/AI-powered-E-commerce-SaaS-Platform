package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Service
public class DefaultTkAssSubtitleRenderService implements TkAssSubtitleRenderService {

    private static final int MAX_LINES = 2;
    private static final int MAX_ENGLISH_LINE_WEIGHT = 30;
    private static final int MAX_CJK_LINE_WEIGHT = 18;

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
                .append(toAssColor(style.primaryColor)).append(',').append(toAssColor(style.activeColor)).append(',')
                .append(toAssColor(style.outlineColor)).append(',').append(toAssColor(style.backColor)).append(',')
                .append(style.bold).append(',').append(style.italic).append(",0,0,100,100,0,0,")
                .append(style.borderStyle).append(',').append(style.outline).append(',').append(style.shadow)
                .append(',').append(style.alignment).append(",80,80,80,1\n\n")
                .append("[Events]\n")
                .append("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n");
        for (TkSubtitleSegment segment : layout.getSegments()) {
            ass.append("Dialogue: 0,")
                    .append(formatAssTime(segment.getStart())).append(',')
                    .append(formatAssTime(segment.getEnd())).append(',')
                    .append("Default,,0,0,0,,")
                    .append("{\\pos(").append(segment.getX()).append(',').append(segment.getY()).append(")}")
                    .append(renderText(task, style, segment))
                    .append('\n');
        }
        FileUtil.writeUtf8String(ass.toString(), targetFile);
        return targetFile;
    }

    private String renderText(TkGenerationTaskDO task, SubtitleStyle style, TkSubtitleSegment segment) {
        if (!Boolean.TRUE.equals(task.getSubtitleKaraokeEnabled())
                || !Boolean.TRUE.equals(segment.getWordTimingReliable())
                || segment.getWords() == null || segment.getWords().isEmpty()) {
            return renderPlainText(style, segment);
        }
        StringBuilder text = new StringBuilder();
        List<List<TkSubtitleWord>> lines = splitWordsToLines(segment);
        boolean mostlyEnglish = isMostlyEnglish(segment.getText());
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
                if (word.isKeyword()) {
                    text.append("{\\c").append(toAssColor(style.keywordColor)).append('}');
                }
                text.append("{\\kf").append(centiseconds).append('}')
                        .append(escapeAssText(word.getText()));
                if (word.isKeyword()) {
                    text.append("{\\c").append(toAssColor(style.primaryColor)).append('}');
                }
            }
        }
        return text.toString();
    }

    private List<List<TkSubtitleWord>> splitWordsToLines(TkSubtitleSegment segment) {
        List<List<TkSubtitleWord>> lines = new ArrayList<>();
        List<TkSubtitleWord> line = new ArrayList<>();
        boolean mostlyEnglish = isMostlyEnglish(segment.getText());
        int maxWeight = mostlyEnglish ? MAX_ENGLISH_LINE_WEIGHT : MAX_CJK_LINE_WEIGHT;
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

    private String renderPlainText(SubtitleStyle style, TkSubtitleSegment segment) {
        String text = wrapPlainText(StrUtil.maxLength(segment.getText(), 80));
        if (segment.getWords() == null || segment.getWords().stream().noneMatch(TkSubtitleWord::isKeyword)) {
            return text;
        }
        for (TkSubtitleWord word : segment.getWords()) {
            if (!word.isKeyword() || StrUtil.isBlank(word.getText())) {
                continue;
            }
            String escaped = escapeAssText(word.getText());
            text = text.replace(escaped, "{\\c" + toAssColor(style.keywordColor) + "}" + escaped
                    + "{\\c" + toAssColor(style.primaryColor) + "}");
        }
        return text;
    }

    private String wrapPlainText(String text) {
        List<String> lines = splitTextToLines(text);
        List<String> escapedLines = new ArrayList<>();
        for (String line : lines) {
            escapedLines.add(escapeAssText(line));
        }
        return String.join("\\N", escapedLines);
    }

    private List<String> splitTextToLines(String text) {
        String normalized = StrUtil.blankToDefault(text, "").trim().replaceAll("\\s+", " ");
        List<String> lines = new ArrayList<>();
        if (StrUtil.isBlank(normalized)) {
            lines.add("");
            return lines;
        }
        if (isMostlyEnglish(normalized)) {
            splitEnglishLines(normalized, lines);
        } else {
            splitCjkLines(normalized, lines);
        }
        return lines;
    }

    private void splitEnglishLines(String text, List<String> lines) {
        StringBuilder line = new StringBuilder();
        int lineWeight = 0;
        for (String token : text.split(" ")) {
            int tokenWeight = displayWeight(token);
            int extraSpace = line.length() == 0 ? 0 : 1;
            if (line.length() > 0 && lineWeight + extraSpace + tokenWeight > MAX_ENGLISH_LINE_WEIGHT
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
        StringBuilder line = new StringBuilder();
        int lineWeight = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            int charWeight = charWeight(ch);
            if (line.length() > 0 && lineWeight + charWeight > MAX_CJK_LINE_WEIGHT
                    && lines.size() < MAX_LINES - 1) {
                lines.add(line.toString());
                line.setLength(0);
                lineWeight = 0;
            }
            line.append(ch);
            lineWeight += charWeight;
        }
        lines.add(line.toString());
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

    private SubtitleStyle resolveStyle(TkGenerationTaskDO task) {
        String style = StrUtil.blankToDefault(task.getSubtitleStyle(), "classic_white");
        int fontSize = resolveFontSize(task.getSubtitleFontSize());
        String activeColor = StrUtil.blankToDefault(task.getSubtitleActiveColor(), "#35F27A");
        String keywordColor = StrUtil.blankToDefault(task.getSubtitleKeywordColor(), "#FFD84D");
        if (StrUtil.equals(style, "promo_bold")) {
            return new SubtitleStyle("Arial", fontSize + 4, "#FFF200", "#FF3B30", "#FFFFFF", "#2B0505",
                    "#E02020", 3, 3, 0, 2, -1, 0);
        }
        if (StrUtil.equals(style, "tiktok_large")) {
            return new SubtitleStyle("Arial", fontSize + 8, "#FFFFFF", "#00F2FF", "#FF3B8D", "#080A12",
                    "#000000", 1, 6, 2, 2, -1, 0);
        }
        if (StrUtil.equals(style, "clean_product")) {
            return new SubtitleStyle("Arial", fontSize - 4, "#131F2A", activeColor, "#1677FF", "#D5E4F0",
                    "#F8FBFF", 3, 2, 0, 2, -1, 0);
        }
        if (StrUtil.equals(style, "yellow_keyword")) {
            return new SubtitleStyle("Arial", fontSize + 1, "#FFFFFF", activeColor, keywordColor, "#111111",
                    "#000000", 1, 5, 0, 2, -1, 0);
        }
        if (StrUtil.equals(style, "neon_pop")) {
            return new SubtitleStyle("Arial", fontSize + 5, "#00F2FF", "#35F27A", "#FF6BC8", "#080A12",
                    "#000000", 1, 5, 2, 2, -1, 0);
        }
        if (StrUtil.equals(style, "yellow_story")) {
            return new SubtitleStyle("Arial", fontSize + 4, "#FFE837", "#FFFFFF", "#FF3D00", "#151515",
                    "#000000", 1, 3, 2, 2, -1, -1);
        }
        if (StrUtil.equals(style, "price_flash")) {
            return new SubtitleStyle("Arial", fontSize + 6, "#FFFF00", "#FFFFFF", "#FF6600", "#000000",
                    "#111111", 3, 4, 0, 2, -1, 0);
        }
        if (StrUtil.equals(style, "step_card")) {
            return new SubtitleStyle("Arial", fontSize - 3, "#101A22", activeColor, "#0EA5E9", "#CBD5E1",
                    "#F8FAFC", 3, 2, 0, 2, -1, 0);
        }
        if (StrUtil.equals(style, "brand_minimal")) {
            return new SubtitleStyle("Arial", fontSize - 2, "#374151", "#2563EB", "#2563EB", "#CBD5E1",
                    "#FFFFFF", 3, 2, 0, 2, -1, 0);
        }
        if (StrUtil.equals(style, "comment_bubble")) {
            return new SubtitleStyle("Arial", fontSize - 1, "#101A22", "#1677FF", "#1677FF", "#D6E1EA",
                    "#FFFFFF", 3, 2, 1, 2, -1, 0);
        }
        return new SubtitleStyle("Arial", fontSize, "#FFFFFF", "#FFFFFF", "#FFFFFF", "#000000",
                "#000000", 1, 4, 1, 2, -1, 0);
    }

    private int resolveFontSize(String value) {
        if (StrUtil.equals(value, "small")) {
            return 52;
        }
        if (StrUtil.equals(value, "large")) {
            return 74;
        }
        return 62;
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
        return StrUtil.blankToDefault(text, "")
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
