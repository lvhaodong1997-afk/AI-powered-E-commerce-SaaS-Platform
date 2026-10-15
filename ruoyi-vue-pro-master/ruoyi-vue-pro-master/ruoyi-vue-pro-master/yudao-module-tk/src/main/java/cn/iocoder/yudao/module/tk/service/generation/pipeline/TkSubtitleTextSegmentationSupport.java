package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import java.util.HashSet;
import java.util.Set;
import java.util.function.IntPredicate;

/** Shared, local text boundaries for caption chunks and visible lines. */
final class TkSubtitleTextSegmentationSupport {

    static final int CJK_LINE_WEIGHT = 18;
    private static final String SENTENCE_ENDINGS = "。！？!?；;.";
    private static final String PUNCTUATION = "，,、。！？!?；;：:.”’」』）)]}》〉";
    private static final String OPENING_PUNCTUATION = "“‘「『（([{《〈";
    private static final String[] PROTECTED_PHRASES = {
            "系统能力", "执行按钮", "评论提效", "个人经验", "标准流程", "业务逻辑",
            "低水平", "重复劳动", "中等质量", "多维表格", "自动化输出",
            "低质视频", "高质量视频", "30条高质量视频", "多维表格提效", "提效",
            "底层逻辑", "整理了一份", "98块",
            "配料表", "芒果", "任何", "其他", "自然", "高温", "烘干", "厚切",
            "干净", "添加", "成熟", "口感", "嚼劲"
    };
    private static final String[] CLAUSE_STARTS = {
            "加上", "而且", "并且", "但是", "同时", "然后", "所以", "因为", "只要", "也不", "不仅"
    };

    private TkSubtitleTextSegmentationSupport() {
    }

    static int chooseChunkSplitIndex(String text, int maxWeight) {
        return chooseSplitIndex(text, maxWeight, maxWeight, Math.max(1, maxWeight / 2), index -> true);
    }

    static int chooseLineSplitIndex(String text, IntPredicate wordBoundary) {
        int weight = displayWeight(text);
        return chooseSplitIndex(text, CJK_LINE_WEIGHT, Math.min(CJK_LINE_WEIGHT, (weight + 1) / 2),
                weight <= 2 * CJK_LINE_WEIGHT ? Math.max(1, weight - CJK_LINE_WEIGHT) : CJK_LINE_WEIGHT / 2,
                wordBoundary);
    }

    static boolean canFitTwoLines(String text) {
        return canFitTwoLines(text, index -> true);
    }

    static boolean canFitTwoLines(String text, IntPredicate wordBoundary) {
        if (displayWeight(text) <= CJK_LINE_WEIGHT) {
            return true;
        }
        if (displayWeight(text) > 2 * CJK_LINE_WEIGHT) {
            return false;
        }
        int splitIndex = chooseLineSplitIndex(text, wordBoundary);
        return splitIndex < text.length() && displayWeight(text.substring(0, splitIndex)) <= CJK_LINE_WEIGHT
                && displayWeight(text.substring(splitIndex)) <= CJK_LINE_WEIGHT;
    }

    private static int chooseSplitIndex(String text, int maxWeight, int preferredWeight, int minWeight,
                                       IntPredicate wordBoundary) {
        boolean[] protectedBoundaries = protectedBoundaries(text);
        int bestIndex = -1;
        int bestPriority = -1;
        int bestDistance = Integer.MAX_VALUE;
        int fallbackIndex = text.length();
        int fallbackDistance = Integer.MAX_VALUE;
        int weight = 0;
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            weight += codePoint < 128 ? 1 : 2;
            offset += Character.charCount(codePoint);
            if (offset == text.length() || !wordBoundary.test(offset) || protectedBoundaries[offset]) {
                continue;
            }
            int next = text.codePointAt(offset);
            if (PUNCTUATION.indexOf(next) >= 0 || OPENING_PUNCTUATION.indexOf(codePoint) >= 0
                    || isAsciiWord(codePoint) && isAsciiWord(next)) {
                continue;
            }
            int distance = Math.abs(weight - preferredWeight);
            if (distance < fallbackDistance) {
                fallbackIndex = offset;
                fallbackDistance = distance;
            }
            if (weight < minWeight || weight > maxWeight) {
                continue;
            }
            int priority = boundaryPriority(text, offset, codePoint, next);
            if (priority > bestPriority || priority == bestPriority && distance < bestDistance) {
                bestIndex = offset;
                bestPriority = priority;
                bestDistance = distance;
            }
        }
        return bestIndex > 0 ? bestIndex : fallbackIndex;
    }

    private static boolean[] protectedBoundaries(String text) {
        boolean[] boundaries = new boolean[text.length() + 1];
        for (String phrase : PROTECTED_PHRASES) {
            for (int start = text.indexOf(phrase); start >= 0; start = text.indexOf(phrase, start + 1)) {
                for (int offset = start + 1; offset < start + phrase.length(); offset++) {
                    boundaries[offset] = true;
                }
            }
        }
        return boundaries;
    }

    private static int boundaryPriority(String text, int offset, int previous, int next) {
        if (PUNCTUATION.indexOf(previous) >= 0) {
            return 3;
        }
        if (Character.isWhitespace(previous) || Character.isWhitespace(next)) {
            return 2;
        }
        for (String clause : CLAUSE_STARTS) {
            if (text.startsWith(clause, offset)) {
                return 1;
            }
        }
        return 0;
    }

    static boolean isAsciiWord(int codePoint) {
        return codePoint < 128 && (Character.isLetterOrDigit(codePoint)
                || codePoint == '\'' || codePoint == '-' || codePoint == '%');
    }

    static boolean isWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint) || codePoint == '\u0085';
    }

    static int displayWeight(String text) {
        return text == null ? 0 : text.codePoints().map(cp -> cp < 128 ? 1 : 2).sum();
    }

    static int compactLength(String text) {
        return text == null ? 0 : (int) text.codePoints().filter(cp -> !isWhitespace(cp)).count();
    }

    static Set<Integer> sentenceBoundaryOffsets(String script) {
        Set<Integer> offsets = new HashSet<>();
        int cursor = 0;
        if (script == null) {
            return offsets;
        }
        for (int offset = 0; offset < script.length();) {
            int cp = script.codePointAt(offset);
            offset += Character.charCount(cp);
            if (cp == '\n' || cp == '\r' || cp == '\u000B' || cp == '\f'
                    || cp == '\u0085' || cp == '\u2028' || cp == '\u2029') {
                offsets.add(cursor);
            } else if (!isWhitespace(cp)) {
                cursor++;
                if (SENTENCE_ENDINGS.indexOf(cp) >= 0) {
                    offsets.add(cursor);
                }
            }
        }
        return offsets;
    }

    static boolean endsSentence(String text) {
        return text != null && !text.trim().isEmpty()
                && SENTENCE_ENDINGS.indexOf(text.trim().codePointBefore(text.trim().length())) >= 0;
    }
}
