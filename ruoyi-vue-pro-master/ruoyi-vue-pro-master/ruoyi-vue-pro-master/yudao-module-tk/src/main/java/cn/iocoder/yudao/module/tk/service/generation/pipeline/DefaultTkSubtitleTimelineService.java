package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.tk.dal.dataobject.TkGenerationTaskDO;
import cn.iocoder.yudao.module.tk.framework.config.TkGenerationProperties;
import cn.iocoder.yudao.module.tk.framework.ffmpeg.TkFfmpegExecutableResolver;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DefaultTkSubtitleTimelineService implements TkSubtitleTimelineService {

    private static final double MIN_SEGMENT_SECONDS = 1.2D;
    private static final int MAX_ENGLISH_SEGMENT_CHARS = 52;
    private static final int MAX_ENGLISH_SEGMENT_WORDS = 8;
    private static final int MAX_CJK_SEGMENT_WEIGHT = 28;
    private static final int SOCIAL_ENGLISH_SEGMENT_CHARS = 34;
    private static final int SOCIAL_ENGLISH_SEGMENT_WORDS = 5;
    private static final int SOCIAL_CJK_SEGMENT_WEIGHT = 20;
    private static final double MIN_DYNAMIC_SEGMENT_SECONDS = 0.35D;
    private static final double MAX_REASONABLE_FIRST_SUBTITLE_START_SECONDS = 1.0D;
    private static final double MAX_SUBTITLE_TAIL_GAP_SECONDS = 2.0D;
    private static final double MAX_SUBTITLE_TAIL_GAP_RATIO = 0.08D;
    private static final String CJK_SOFT_PUNCTUATION = "，,、：:";
    private static final String CJK_LEADING_PUNCTUATION = "，,、。！？!?；;：:";
    private static final Pattern SILENCE_START_PATTERN = Pattern.compile("silence_start:\\s*([0-9.]+)");
    private static final Pattern SILENCE_END_PATTERN = Pattern.compile("silence_end:\\s*([0-9.]+)");
    private static final String[] PROTECTED_CJK_PHRASES = {
            "系统能力", "执行按钮", "评论提效", "个人经验", "标准流程", "业务逻辑",
            "低水平", "重复劳动", "中等质量", "多维表格", "自动化输出",
            "低质视频", "高质量视频", "30条高质量视频", "多维表格提效", "提效",
            "底层逻辑", "整理了一份", "98块"
    };

    @Resource
    private TkGenerationProperties generationProperties;

    @Override
    public TkSubtitleTimeline buildTimeline(TkGenerationTaskDO task, String scriptText, File audioFile, List<String> keywords) {
        TkSubtitleTimeline asrTimeline = buildTimelineByAsr(task, scriptText, audioFile, keywords);
        if (isAsrTimelineTextAcceptable(asrTimeline, scriptText, minAsrTextSimilarity())) {
            TkSubtitleTimeline exactTimeline = rebuildExactTimelineWithScriptText(task, scriptText, asrTimeline, keywords);
            SubtitleQuality exactQuality = inspectSubtitleQuality(exactTimeline, audioFile);
            if (exactQuality.acceptable) {
                writeSubtitleQualityReport(audioFile, "ASR_EXACT", exactQuality, null);
                return exactTimeline;
            }
            TkSubtitleTimeline fallbackTimeline = buildEstimatedTimeline(task, scriptText, audioFile, keywords,
                    resolveTimelineDuration(exactTimeline));
            SubtitleQuality fallbackQuality = inspectSubtitleQuality(fallbackTimeline, audioFile);
            writeSubtitleQualityReport(audioFile, "ESTIMATED_FALLBACK", fallbackQuality, exactQuality.reason);
            return fallbackTimeline;
        }
        TkSubtitleTimeline timeline = buildEstimatedTimeline(task, scriptText, audioFile, keywords, 0D);
        String fallbackMode = asrTimeline == null ? "ESTIMATED_ASR_UNAVAILABLE" : "ESTIMATED_ASR_MISMATCH";
        writeSubtitleQualityReport(audioFile, fallbackMode, inspectSubtitleQuality(timeline, audioFile),
                "ASR 时间轴不可用或与 AI 文案不匹配，已降级为估算字幕");
        return timeline;
    }

    private TkSubtitleTimeline buildTimelineByAsr(TkGenerationTaskDO task, String scriptText, File audioFile, List<String> keywords) {
        TkGenerationProperties.Asr asr = generationProperties.getSubtitle().getAsr();
        if (asr == null || !Boolean.TRUE.equals(asr.getEnabled()) || StrUtil.isBlank(asr.getScriptPath())) {
            return null;
        }
        File scriptFile = resolvePath(asr.getScriptPath());
        if (!scriptFile.isFile()) {
            return null;
        }
        try {
            List<String> command = new ArrayList<>(Arrays.asList(
                    StrUtil.blankToDefault(asr.getPython(), "py"),
                    scriptFile.getAbsolutePath(),
                    "--audio", audioFile.getAbsolutePath(),
                    "--language", StrUtil.blankToDefault(task.getTargetLanguage(), ""),
                    "--text", StrUtil.blankToDefault(scriptText, ""),
                    "--keywords", JsonUtils.toJsonString(keywords),
                    "--model", StrUtil.blankToDefault(asr.getModel(), "small")
            ));
            String output = runCommand(command, asr.getTimeoutSeconds());
            writeDiagnostic(audioFile, "asr-raw.json", output);
            return JsonUtils.parseObject(output, TkSubtitleTimeline.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private TkSubtitleTimeline buildEstimatedTimeline(TkGenerationTaskDO task, String scriptText, File audioFile,
                                                      List<String> keywords, double preferredDuration) {
        List<String> sentences = splitSentences(task, scriptText);
        double duration = preferredDuration > 0D ? preferredDuration : probeDuration(audioFile);
        if (duration <= 0) {
            duration = Math.max(sentences.size() * 3D, TkVideoDurationSupport.normalize(task.getTargetDuration()));
        }
        TkSubtitleTimeline timeline = new TkSubtitleTimeline();
        timeline.setLanguage(task.getTargetLanguage());
        timeline.setAudioDuration(duration);
        timeline.setSegments(buildSegments(sentences, duration, keywords));
        applySubtitleLead(timeline, resolveSubtitleLead(audioFile));
        return timeline;
    }

    private TkSubtitleTimeline rebuildExactTimelineWithScriptText(TkGenerationTaskDO task, String scriptText,
                                                                  TkSubtitleTimeline timingTimeline, List<String> keywords) {
        if (StrUtil.isBlank(scriptText)) {
            markKeywords(timingTimeline, keywords);
            return timingTimeline;
        }
        List<String> sentences = splitSentences(task, scriptText);
        double duration = resolveTimelineDuration(timingTimeline);
        if (duration <= 0D) {
            duration = Math.max(sentences.size() * 3D, TkVideoDurationSupport.normalize(task.getTargetDuration()));
        }
        TkSubtitleTimeline timeline = new TkSubtitleTimeline();
        timeline.setLanguage(task.getTargetLanguage());
        timeline.setAudioDuration(duration);
        List<TkSubtitleWord> exactWords = buildExactWords(scriptText, timingTimeline, keywords);
        if (exactWords.isEmpty()) {
            throw new IllegalStateException("字幕精准对齐失败：ASR 未返回可用逐字时间轴");
        }
        List<TkSubtitleSegment> segments = buildExactSegments(sentences, exactWords);
        timeline.setSegments(segments);
        return timeline;
    }

    private double resolveTimelineDuration(TkSubtitleTimeline timeline) {
        if (timeline == null) {
            return 0D;
        }
        if (timeline.getAudioDuration() > 0D) {
            return timeline.getAudioDuration();
        }
        double duration = 0D;
        if (timeline.getSegments() != null) {
            for (TkSubtitleSegment segment : timeline.getSegments()) {
                duration = Math.max(duration, segment.getEnd());
            }
        }
        return duration;
    }

    private SubtitleQuality inspectSubtitleQuality(TkSubtitleTimeline timeline, File audioFile) {
        if (timeline == null || timeline.getSegments() == null || timeline.getSegments().isEmpty()) {
            return new SubtitleQuality(false, 0D, 0D, 0D, "EMPTY_TIMELINE");
        }
        double audioDuration = resolveTimelineDuration(timeline);
        if (audioDuration <= 0D) {
            audioDuration = probeDuration(audioFile);
        }
        double firstStart = Double.MAX_VALUE;
        double lastEnd = 0D;
        for (TkSubtitleSegment segment : timeline.getSegments()) {
            firstStart = Math.min(firstStart, segment.getStart());
            lastEnd = Math.max(lastEnd, segment.getEnd());
        }
        if (firstStart == Double.MAX_VALUE) {
            firstStart = 0D;
        }
        if (audioDuration > 3D && firstStart > MAX_REASONABLE_FIRST_SUBTITLE_START_SECONDS) {
            return new SubtitleQuality(false, audioDuration, firstStart, lastEnd, "FIRST_SUBTITLE_TOO_LATE");
        }
        double allowedTailGap = Math.max(MAX_SUBTITLE_TAIL_GAP_SECONDS, audioDuration * MAX_SUBTITLE_TAIL_GAP_RATIO);
        if (audioDuration > 3D && lastEnd < audioDuration - allowedTailGap) {
            return new SubtitleQuality(false, audioDuration, firstStart, lastEnd, "SUBTITLE_COVERAGE_TOO_SHORT");
        }
        return new SubtitleQuality(true, audioDuration, firstStart, lastEnd, "OK");
    }

    private void writeSubtitleQualityReport(File audioFile, String mode, SubtitleQuality quality, String fallbackReason) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("mode", mode);
        report.put("acceptable", quality.acceptable);
        report.put("reason", quality.reason);
        report.put("fallbackReason", fallbackReason);
        report.put("audioDuration", quality.audioDuration);
        report.put("firstSubtitleStart", quality.firstStart);
        report.put("lastSubtitleEnd", quality.lastEnd);
        writeDiagnostic(audioFile, "subtitle-quality.json", JsonUtils.toJsonString(report));
    }

    private void writeDiagnostic(File audioFile, String fileName, String content) {
        if (audioFile == null || audioFile.getParentFile() == null || content == null) {
            return;
        }
        try {
            Files.write(audioFile.getParentFile().toPath().resolve(fileName), content.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // Diagnostics must never interrupt video generation.
        }
    }

    private List<TkSubtitleWord> buildExactWords(String scriptText, TkSubtitleTimeline timingTimeline,
                                                 List<String> keywords) {
        List<String> scriptUnits = buildSubtitleUnits(scriptText);
        List<TimingUnit> timingUnits = collectTimingUnits(timingTimeline);
        List<TkSubtitleWord> words = new ArrayList<>();
        if (scriptUnits.isEmpty() || timingUnits.isEmpty()) {
            return words;
        }
        if (scriptUnits.size() == timingUnits.size()) {
            for (int i = 0; i < scriptUnits.size(); i++) {
                TimingUnit timing = timingUnits.get(i);
                words.add(new TkSubtitleWord(scriptUnits.get(i), round(timing.start), round(timing.end),
                        isKeyword(scriptUnits.get(i), keywords)));
            }
            markKeywordPhrases(words, keywords);
            return words;
        }
        int[] timingIndexes = alignScriptUnitsToTimingUnits(scriptUnits, timingUnits);
        if (countMatched(timingIndexes) <= 0) {
            return words;
        }
        words.addAll(buildAlignedWords(scriptUnits, timingUnits, timingIndexes, keywords));
        markKeywordPhrases(words, keywords);
        return words;
    }

    private List<TkSubtitleWord> buildAlignedWords(List<String> scriptUnits, List<TimingUnit> timingUnits,
                                                   int[] timingIndexes, List<String> keywords) {
        List<TkSubtitleWord> words = new ArrayList<>();
        int i = 0;
        while (i < scriptUnits.size()) {
            int matchedIndex = timingIndexes[i];
            if (matchedIndex >= 0) {
                TimingUnit timing = timingUnits.get(matchedIndex);
                words.add(new TkSubtitleWord(scriptUnits.get(i), round(timing.start), round(timing.end),
                        isKeyword(scriptUnits.get(i), keywords)));
                i++;
                continue;
            }
            int runStart = i;
            while (i < scriptUnits.size() && timingIndexes[i] < 0) {
                i++;
            }
            addEstimatedWordsForUnmatchedRun(words, scriptUnits, timingUnits, timingIndexes, runStart, i, keywords);
        }
        return words;
    }

    private void addEstimatedWordsForUnmatchedRun(List<TkSubtitleWord> words, List<String> scriptUnits,
                                                  List<TimingUnit> timingUnits, int[] timingIndexes,
                                                  int runStart, int runEnd, List<String> keywords) {
        int previousScriptIndex = previousMatchedIndex(timingIndexes, runStart);
        int nextScriptIndex = nextMatchedIndex(timingIndexes, runEnd);
        double timelineStart = timingUnits.get(0).start;
        double timelineEnd = timingUnits.get(timingUnits.size() - 1).end;
        double start = previousScriptIndex >= 0 ? timingUnits.get(timingIndexes[previousScriptIndex]).end : timelineStart;
        double end = nextScriptIndex >= 0 ? timingUnits.get(timingIndexes[nextScriptIndex]).start : timelineEnd;
        int count = Math.max(1, runEnd - runStart);
        if (end <= start) {
            if (nextScriptIndex >= 0) {
                end = timingUnits.get(timingIndexes[nextScriptIndex]).start;
                start = Math.max(timelineStart, end - count * 0.03D);
            } else {
                end = Math.min(timelineEnd, start + count * 0.03D);
            }
        }
        double duration = Math.max(0.03D * count, end - start);
        int totalWeight = 0;
        for (int index = runStart; index < runEnd; index++) {
            totalWeight += textWeight(scriptUnits.get(index));
        }
        double cursor = start;
        for (int index = runStart; index < runEnd; index++) {
            String unit = scriptUnits.get(index);
            double unitEnd = index == runEnd - 1 ? start + duration
                    : cursor + duration * textWeight(unit) / Math.max(totalWeight, 1);
            words.add(new TkSubtitleWord(unit, round(cursor), round(Math.max(cursor + 0.03D, unitEnd)),
                    isKeyword(unit, keywords)));
            cursor = unitEnd;
        }
    }

    private int previousMatchedIndex(int[] timingIndexes, int beforeIndex) {
        for (int i = beforeIndex - 1; i >= 0; i--) {
            if (timingIndexes[i] >= 0) {
                return i;
            }
        }
        return -1;
    }

    private int nextMatchedIndex(int[] timingIndexes, int fromIndex) {
        for (int i = fromIndex; i < timingIndexes.length; i++) {
            if (timingIndexes[i] >= 0) {
                return i;
            }
        }
        return -1;
    }

    private int[] alignScriptUnitsToTimingUnits(List<String> scriptUnits, List<TimingUnit> timingUnits) {
        int scriptSize = scriptUnits.size();
        int timingSize = timingUnits.size();
        int[][] dp = new int[scriptSize + 1][timingSize + 1];
        for (int i = 1; i <= scriptSize; i++) {
            for (int j = 1; j <= timingSize; j++) {
                if (sameSpeechUnit(scriptUnits.get(i - 1), timingUnits.get(j - 1).text)) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }
        int[] matches = new int[scriptSize];
        Arrays.fill(matches, -1);
        int i = scriptSize;
        int j = timingSize;
        while (i > 0 && j > 0) {
            if (sameSpeechUnit(scriptUnits.get(i - 1), timingUnits.get(j - 1).text)) {
                matches[i - 1] = j - 1;
                i--;
                j--;
            } else if (dp[i - 1][j] >= dp[i][j - 1]) {
                i--;
            } else {
                j--;
            }
        }
        return matches;
    }

    private int countMatched(int[] timingIndexes) {
        int count = 0;
        for (int index : timingIndexes) {
            if (index >= 0) {
                count++;
            }
        }
        return count;
    }

    private boolean sameSpeechUnit(String left, String right) {
        String normalizedLeft = normalizeSpeechText(left);
        String normalizedRight = normalizeSpeechText(right);
        return StrUtil.isNotBlank(normalizedLeft) && normalizedLeft.equals(normalizedRight);
    }

    private List<TimingUnit> collectTimingUnits(TkSubtitleTimeline timingTimeline) {
        List<TimingUnit> units = new ArrayList<>();
        if (timingTimeline == null || timingTimeline.getSegments() == null) {
            return units;
        }
        for (TkSubtitleSegment segment : timingTimeline.getSegments()) {
            if (segment.getWords() != null && !segment.getWords().isEmpty()) {
                for (TkSubtitleWord word : segment.getWords()) {
                    addTimingUnits(units, word.getText(), word.getStart(), word.getEnd());
                }
            } else {
                addTimingUnits(units, segment.getText(), segment.getStart(), segment.getEnd());
            }
        }
        return units;
    }

    private void addTimingUnits(List<TimingUnit> units, String text, double start, double end) {
        if (StrUtil.isBlank(text) || end <= start) {
            return;
        }
        List<String> values = buildSubtitleUnits(text);
        if (values.isEmpty()) {
            return;
        }
        if (values.size() == 1) {
            units.add(new TimingUnit(values.get(0), start, end));
            return;
        }
        double cursor = start;
        double duration = end - start;
        int totalWeight = values.stream().mapToInt(this::textWeight).sum();
        for (int i = 0; i < values.size(); i++) {
            String value = values.get(i);
            double valueEnd = i == values.size() - 1 ? end
                    : Math.min(end, cursor + duration * textWeight(value) / Math.max(totalWeight, 1));
            units.add(new TimingUnit(value, round(cursor), round(valueEnd)));
            cursor = valueEnd;
        }
    }

    private List<TkSubtitleSegment> buildExactSegments(List<String> sentences, List<TkSubtitleWord> exactWords) {
        List<TkSubtitleSegment> segments = new ArrayList<>();
        int cursor = 0;
        for (int i = 0; i < sentences.size() && cursor < exactWords.size(); i++) {
            String sentence = sentences.get(i);
            int count = buildSubtitleUnits(sentence).size();
            if (count <= 0) {
                continue;
            }
            int endExclusive = i == sentences.size() - 1 ? exactWords.size() : Math.min(exactWords.size(), cursor + count);
            List<TkSubtitleWord> words = new ArrayList<>(exactWords.subList(cursor, endExclusive));
            if (words.isEmpty()) {
                continue;
            }
            TkSubtitleSegment segment = new TkSubtitleSegment();
            segment.setText(sentence);
            segment.setStart(words.get(0).getStart());
            segment.setEnd(words.get(words.size() - 1).getEnd());
            segment.setWords(words);
            segments.add(segment);
            cursor = endExclusive;
        }
        if (segments.isEmpty() && !exactWords.isEmpty()) {
            TkSubtitleSegment segment = new TkSubtitleSegment();
            segment.setText(joinWords(exactWords));
            segment.setStart(exactWords.get(0).getStart());
            segment.setEnd(exactWords.get(exactWords.size() - 1).getEnd());
            segment.setWords(exactWords);
            segments.add(segment);
        }
        return segments;
    }

    private String joinWords(List<TkSubtitleWord> words) {
        StringBuilder text = new StringBuilder();
        for (TkSubtitleWord word : words) {
            text.append(word.getText());
        }
        return text.toString();
    }

    private static class TimingUnit {
        private final String text;
        private final double start;
        private final double end;

        private TimingUnit(String text, double start, double end) {
            this.text = text;
            this.start = start;
            this.end = end;
        }
    }

    private String runCommand(List<String> command, Integer timeoutSeconds) throws Exception {
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        boolean finished = process.waitFor(timeoutSeconds == null ? 300 : timeoutSeconds, TimeUnit.SECONDS);
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
        }
            if (!finished) {
                process.destroyForcibly();
                return null;
            }
            if (process.exitValue() != 0) {
                return null;
            }
            return output.toString();
    }

    private double resolveSubtitleLead(File audioFile) {
        TkGenerationProperties.Subtitle subtitle = generationProperties.getSubtitle();
        double fixedLead = positive(subtitle.getLeadSeconds());
        double leadingSilence = Boolean.TRUE.equals(subtitle.getDetectLeadingSilenceEnabled())
                ? detectLeadingSilence(audioFile, subtitle) : 0D;
        double maxLead = positive(subtitle.getMaxLeadSeconds());
        if (maxLead <= 0D) {
            return fixedLead + leadingSilence;
        }
        return Math.min(maxLead, fixedLead + leadingSilence);
    }

    private double detectLeadingSilence(File audioFile, TkGenerationProperties.Subtitle subtitle) {
        if (audioFile == null || !audioFile.isFile()) {
            return 0D;
        }
        try {
            String ffmpeg = TkFfmpegExecutableResolver.ffmpeg(generationProperties.getFfmpeg().getFfmpegPath());
            String silenceDetect = StrUtil.format("silencedetect=noise={}dB:d={}",
                    defaultDouble(subtitle.getSilenceNoiseDb(), -35D),
                    defaultDouble(subtitle.getSilenceMinDurationSeconds(), 0.08D));
            String output = runCommand(Arrays.asList(ffmpeg, "-hide_banner", "-nostats", "-i",
                    audioFile.getAbsolutePath(), "-af", silenceDetect, "-f", "null", "-"), 30);
            return parseLeadingSilenceEnd(output);
        } catch (Exception ignored) {
            return 0D;
        }
    }

    static double parseLeadingSilenceEnd(String output) {
        if (StrUtil.isBlank(output)) {
            return 0D;
        }
        Matcher startMatcher = SILENCE_START_PATTERN.matcher(output);
        if (!startMatcher.find()) {
            return 0D;
        }
        double firstStart = parseDouble(startMatcher.group(1));
        if (firstStart > 0.05D) {
            return 0D;
        }
        Matcher endMatcher = SILENCE_END_PATTERN.matcher(output);
        return endMatcher.find() ? parseDouble(endMatcher.group(1)) : 0D;
    }

    static void applySubtitleLead(TkSubtitleTimeline timeline, double leadSeconds) {
        if (timeline == null || timeline.getSegments() == null || leadSeconds <= 0D) {
            return;
        }
        for (TkSubtitleSegment segment : timeline.getSegments()) {
            segment.setStart(shiftEarlier(segment.getStart(), leadSeconds));
            segment.setEnd(Math.max(segment.getStart() + 0.05D, shiftEarlier(segment.getEnd(), leadSeconds)));
            if (timeline.getAudioDuration() > 0D) {
                segment.setEnd(Math.min(timeline.getAudioDuration(), segment.getEnd()));
            }
            segment.setStart(roundStatic(segment.getStart()));
            segment.setEnd(roundStatic(segment.getEnd()));
            if (segment.getWords() == null) {
                continue;
            }
            for (TkSubtitleWord word : segment.getWords()) {
                word.setStart(roundStatic(shiftEarlier(word.getStart(), leadSeconds)));
                word.setEnd(roundStatic(Math.max(word.getStart() + 0.03D, shiftEarlier(word.getEnd(), leadSeconds))));
                if (word.getEnd() > segment.getEnd()) {
                    word.setEnd(segment.getEnd());
                }
            }
        }
    }

    static boolean isAsrTimelineTextAcceptable(TkSubtitleTimeline timeline, String scriptText, double minSimilarity) {
        if (timeline == null || timeline.getSegments() == null || timeline.getSegments().isEmpty()) {
            return false;
        }
        String expected = normalizeForSimilarity(scriptText);
        if (StrUtil.isBlank(expected)) {
            return true;
        }
        StringBuilder actualBuilder = new StringBuilder();
        for (TkSubtitleSegment segment : timeline.getSegments()) {
            actualBuilder.append(StrUtil.blankToDefault(segment.getText(), ""));
        }
        String actual = normalizeForSimilarity(actualBuilder.toString());
        if (StrUtil.isBlank(actual)) {
            return false;
        }
        if (actual.contains(expected)) {
            return true;
        }
        if (expected.contains(actual)) {
            return actual.length() * 1.0D / Math.max(expected.length(), 1) >= minSimilarity;
        }
        return lcsLength(expected, actual) * 1.0D / Math.max(expected.length(), 1) >= minSimilarity;
    }

    private double minAsrTextSimilarity() {
        TkGenerationProperties.Asr asr = generationProperties.getSubtitle().getAsr();
        return defaultDouble(asr == null ? null : asr.getMinTextSimilarity(), 0.55D);
    }

    private static String normalizeForSimilarity(String text) {
        if (StrUtil.isBlank(text)) {
            return "";
        }
        return normalizeSpeechText(text); /*
                .replaceAll("[\\s\\p{Punct}，。！？、；：：“”‘’《》【】（）￥…—·]+", "");
    }

        */
    }

    private static String normalizeSpeechText(String text) {
        if (StrUtil.isBlank(text)) {
            return "";
        }
        StringBuilder normalized = new StringBuilder();
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.isWhitespace(codePoint) || isPunctuation(codePoint)) {
                continue;
            }
            String value = new String(Character.toChars(codePoint)).toLowerCase();
            normalized.append(normalizeSpeechValue(value));
        }
        return normalized.toString();
    }

    private static boolean isPunctuation(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.CONNECTOR_PUNCTUATION
                || type == Character.DASH_PUNCTUATION
                || type == Character.START_PUNCTUATION
                || type == Character.END_PUNCTUATION
                || type == Character.INITIAL_QUOTE_PUNCTUATION
                || type == Character.FINAL_QUOTE_PUNCTUATION
                || type == Character.OTHER_PUNCTUATION;
    }

    private static String normalizeSpeechValue(String value) {
        if (value.length() != 1) {
            return value;
        }
        char ch = value.charAt(0);
        switch (ch) {
            case '零':
            case '〇':
                return "0";
            case '一':
                return "1";
            case '二':
            case '两':
            case '兩':
                return "2";
            case '三':
                return "3";
            case '四':
                return "4";
            case '五':
                return "5";
            case '六':
                return "6";
            case '七':
                return "7";
            case '八':
                return "8";
            case '九':
                return "9";
            case '團':
                return "团";
            case '隊':
                return "队";
            case '從':
                return "从";
            case '擴':
                return "扩";
            case '產':
                return "产";
            case '漲':
                return "涨";
            case '塊':
                return "块";
            case '對':
                return "对";
            case '著':
                return "着";
            case '這':
                return "这";
            case '個':
                return "个";
            case '問':
                return "问";
            case '題':
                return "题";
            case '數':
                return "数";
            case '據':
                return "据";
            case '裏':
            case '裡':
                return "里";
            case '實':
                return "实";
            case '現':
                return "现";
            case '業':
                return "业";
            case '務':
                return "务";
            case '應':
                return "应";
            case '該':
                return "该";
            case '員':
                return "员";
            case '執':
                return "执";
            case '節':
                return "节";
            case '頂':
                return "顶";
            case '來':
                return "来";
            case '時':
                return "时";
            case '決':
                return "决";
            case '邏':
                return "逻";
            case '輯':
                return "辑";
            case '寫':
                return "写";
            case '檔':
                return "档";
            case '買':
                return "买";
            case '賣':
                return "卖";
            case '讓':
                return "让";
            case '後':
                return "后";
            case '會':
                return "会";
            case '還':
                return "还";
            case '開':
                return "开";
            case '關':
                return "关";
            default:
                return value;
        }
    }

    private static int lcsLength(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int i = 1; i <= left.length(); i++) {
            for (int j = 1; j <= right.length(); j++) {
                if (left.charAt(i - 1) == right.charAt(j - 1)) {
                    current[j] = previous[j - 1] + 1;
                } else {
                    current[j] = Math.max(previous[j], current[j - 1]);
                }
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }

    private static double shiftEarlier(double value, double seconds) {
        return Math.max(0D, value - seconds);
    }

    private static double parseDouble(String value) {
        try {
            return Double.parseDouble(StrUtil.trim(value));
        } catch (Exception ignored) {
            return 0D;
        }
    }

    private static double positive(Double value) {
        return Math.max(0D, defaultDouble(value, 0D));
    }

    private static double defaultDouble(Double value, double defaultValue) {
        return value == null ? defaultValue : value;
    }

    private static double roundStatic(double value) {
        return Math.round(value * 100D) / 100D;
    }

    private File resolvePath(String path) {
        File file = new File(path);
        if (file.isAbsolute()) {
            return file;
        }
        return Paths.get("").toAbsolutePath().resolve(path).normalize().toFile();
    }

    private void markKeywords(TkSubtitleTimeline timeline, List<String> keywords) {
        if (timeline.getSegments() == null) {
            return;
        }
        for (TkSubtitleSegment segment : timeline.getSegments()) {
            if (segment.getWords() == null || segment.getWords().isEmpty()) {
                segment.setWords(buildWords(segment.getText(), segment.getStart(), segment.getEnd(), keywords));
            } else {
                for (TkSubtitleWord word : segment.getWords()) {
                    word.setKeyword(isKeyword(word.getText(), keywords));
                }
                markKeywordPhrases(segment.getWords(), keywords);
            }
        }
    }

    private List<TkSubtitleSegment> buildSegments(List<String> sentences, double duration, List<String> keywords) {
        List<TkSubtitleSegment> segments = new ArrayList<>();
        if (sentences.isEmpty()) {
            return segments;
        }
        duration = Math.max(0.1D, duration);
        int totalWeight = sentences.stream().mapToInt(this::textWeight).sum();
        double cursor = 0D;
        for (int i = 0; i < sentences.size(); i++) {
            String sentence = sentences.get(i);
            int remainingSegments = sentences.size() - i;
            double segmentDuration = i == sentences.size() - 1
                    ? duration - cursor
                    : nextSegmentDuration(duration, cursor, remainingSegments,
                    duration * textWeight(sentence) / Math.max(totalWeight, 1));
            double end = Math.min(duration, cursor + segmentDuration);
            TkSubtitleSegment segment = new TkSubtitleSegment();
            segment.setText(sentence);
            segment.setStart(round(cursor));
            segment.setEnd(round(end));
            segment.setWords(buildWords(sentence, segment.getStart(), segment.getEnd(), keywords));
            segments.add(segment);
            cursor = segment.getEnd();
            if (cursor >= duration && i < sentences.size() - 1) {
                mergeRemainingTextIntoLastSegment(segments, sentences, i + 1, duration, keywords);
                break;
            }
        }
        return segments;
    }

    private double nextSegmentDuration(double totalDuration, double cursor, int remainingSegments, double weightedDuration) {
        double remainingDuration = Math.max(0.01D, totalDuration - cursor);
        double dynamicMinimum = Math.min(MIN_SEGMENT_SECONDS,
                Math.max(MIN_DYNAMIC_SEGMENT_SECONDS, totalDuration / Math.max(remainingSegments, 1)));
        double reserveForRest = dynamicMinimum * Math.max(0, remainingSegments - 1);
        double maxCurrent = Math.max(0.01D, remainingDuration - reserveForRest);
        return Math.min(maxCurrent, Math.max(dynamicMinimum, weightedDuration));
    }

    private void mergeRemainingTextIntoLastSegment(List<TkSubtitleSegment> segments, List<String> sentences,
                                                   int startIndex, double duration, List<String> keywords) {
        if (segments.isEmpty() || startIndex >= sentences.size()) {
            return;
        }
        TkSubtitleSegment last = segments.get(segments.size() - 1);
        StringBuilder text = new StringBuilder(StrUtil.blankToDefault(last.getText(), ""));
        for (int i = startIndex; i < sentences.size(); i++) {
            text.append(sentences.get(i));
        }
        last.setText(text.toString());
        last.setEnd(round(duration));
        last.setWords(buildWords(last.getText(), last.getStart(), last.getEnd(), keywords));
    }

    private List<TkSubtitleWord> buildWords(String sentence, double start, double end, List<String> keywords) {
        List<String> tokens = splitTokens(sentence);
        List<TkSubtitleWord> words = new ArrayList<>();
        if (tokens.isEmpty()) {
            return words;
        }
        double duration = Math.max(0.1D, end - start);
        int totalWeight = tokens.stream().mapToInt(this::textWeight).sum();
        double cursor = start;
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            double tokenEnd = i == tokens.size() - 1
                    ? end
                    : Math.min(end, cursor + duration * textWeight(token) / Math.max(totalWeight, 1));
            words.add(new TkSubtitleWord(token, round(cursor), round(tokenEnd), isKeyword(token, keywords)));
            cursor = tokenEnd;
        }
        markKeywordPhrases(words, keywords);
        return words;
    }

    private List<String> splitSentences(TkGenerationTaskDO task, String scriptText) {
        List<String> sentences = new ArrayList<>();
        if (StrUtil.isBlank(scriptText)) {
            return sentences;
        }
        String normalized = scriptText.replace("\r", "").replace("\n", " ");
        for (String item : normalized.split("(?<=[。！？!?；;])")) {
            String sentence = StrUtil.trim(item);
            if (StrUtil.isNotBlank(sentence)) {
                sentences.addAll(splitReadableChunks(task, sentence));
            }
        }
        if (sentences.isEmpty()) {
            sentences.addAll(splitReadableChunks(task, normalized));
        }
        return sentences;
    }

    private List<String> splitReadableChunks(TkGenerationTaskDO task, String text) {
        String normalized = StrUtil.trim(text);
        if (StrUtil.isBlank(normalized)) {
            return new ArrayList<>();
        }
        if (isMostlyEnglish(normalized)) {
            return splitEnglishReadableChunks(task, normalized);
        }
        return splitCjkReadableChunks(task, normalized);
    }

    private List<String> splitEnglishReadableChunks(TkGenerationTaskDO task, String text) {
        List<String> chunks = new ArrayList<>();
        String[] words = text.trim().replaceAll("\\s+", " ").split(" ");
        StringBuilder chunk = new StringBuilder();
        int wordCount = 0;
        int maxChars = socialCaptionStyle(task) ? SOCIAL_ENGLISH_SEGMENT_CHARS : MAX_ENGLISH_SEGMENT_CHARS;
        int maxWords = socialCaptionStyle(task) ? SOCIAL_ENGLISH_SEGMENT_WORDS : MAX_ENGLISH_SEGMENT_WORDS;
        for (String word : words) {
            if (StrUtil.isBlank(word)) {
                continue;
            }
            int nextLength = chunk.length() == 0 ? word.length() : chunk.length() + 1 + word.length();
            if (chunk.length() > 0
                    && (nextLength > maxChars || wordCount >= maxWords)) {
                chunks.add(chunk.toString());
                chunk.setLength(0);
                wordCount = 0;
            }
            if (chunk.length() > 0) {
                chunk.append(' ');
            }
            chunk.append(word);
            wordCount++;
        }
        if (chunk.length() > 0) {
            chunks.add(chunk.toString());
        }
        return chunks;
    }

    private List<String> splitCjkReadableChunks(TkGenerationTaskDO task, String text) {
        List<String> chunks = new ArrayList<>();
        int maxWeight = socialCaptionStyle(task) ? SOCIAL_CJK_SEGMENT_WEIGHT : MAX_CJK_SEGMENT_WEIGHT;
        for (String clause : splitCjkClauses(text)) {
            chunks.addAll(splitCjkClauseByWeight(clause, maxWeight));
        }
        return chunks;
    }

    private List<String> splitCjkClauses(String text) {
        List<String> clauses = new ArrayList<>();
        StringBuilder clause = new StringBuilder();
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            String value = new String(Character.toChars(codePoint));
            clause.append(value);
            offset += Character.charCount(codePoint);
            if (isCjkSoftPunctuation(value)) {
                addCjkClause(clauses, clause);
            }
        }
        addCjkClause(clauses, clause);
        return clauses;
    }

    private void addCjkClause(List<String> clauses, StringBuilder clause) {
        String value = StrUtil.trim(clause.toString());
        clause.setLength(0);
        if (StrUtil.isBlank(value)) {
            return;
        }
        if (!clauses.isEmpty() && startsWithCjkLeadingPunctuation(value)) {
            int lastIndex = clauses.size() - 1;
            clauses.set(lastIndex, clauses.get(lastIndex) + value);
            return;
        }
        clauses.add(value);
    }

    private List<String> splitCjkClauseByWeight(String text, int maxWeight) {
        List<String> chunks = new ArrayList<>();
        String remaining = StrUtil.trim(text);
        while (StrUtil.isNotBlank(remaining) && displayWeight(remaining) > maxWeight) {
            int splitIndex = chooseCjkSplitIndex(remaining, maxWeight);
            chunks.add(StrUtil.trim(remaining.substring(0, splitIndex)));
            remaining = StrUtil.trim(remaining.substring(splitIndex));
        }
        if (StrUtil.isNotBlank(remaining)) {
            if (!chunks.isEmpty() && startsWithCjkLeadingPunctuation(remaining)) {
                int lastIndex = chunks.size() - 1;
                chunks.set(lastIndex, chunks.get(lastIndex) + remaining);
            } else {
                chunks.add(remaining);
            }
        }
        return chunks;
    }

    private int chooseCjkSplitIndex(String text, int maxWeight) {
        int splitIndex = indexByWeight(text, maxWeight);
        splitIndex = includeFollowingPunctuation(text, splitIndex);
        int protectedIndex = avoidProtectedPhraseSplit(text, splitIndex);
        if (protectedIndex > 0 && protectedIndex != splitIndex) {
            return protectedIndex;
        }
        int punctuationIndex = lastSoftPunctuationBefore(text, splitIndex);
        if (punctuationIndex > 0 && displayWeight(text.substring(0, punctuationIndex)) >= maxWeight / 2) {
            return punctuationIndex;
        }
        return Math.max(1, splitIndex);
    }

    private int avoidProtectedPhraseSplit(String text, int splitIndex) {
        for (String phrase : PROTECTED_CJK_PHRASES) {
            int phraseStart = text.indexOf(phrase);
            while (phraseStart >= 0) {
                int phraseEnd = phraseStart + phrase.length();
                if (splitIndex > phraseStart && splitIndex < phraseEnd) {
                    if (phraseStart > 0) {
                        return phraseStart;
                    }
                    return phraseEnd;
                }
                phraseStart = text.indexOf(phrase, phraseStart + 1);
            }
        }
        return splitIndex;
    }

    private int lastSoftPunctuationBefore(String text, int endExclusive) {
        for (int i = Math.min(endExclusive, text.length()) - 1; i >= 0; i--) {
            if (CJK_SOFT_PUNCTUATION.indexOf(text.charAt(i)) >= 0) {
                return i + 1;
            }
        }
        return -1;
    }

    private int includeFollowingPunctuation(String text, int splitIndex) {
        if (splitIndex < text.length() && CJK_LEADING_PUNCTUATION.indexOf(text.charAt(splitIndex)) >= 0) {
            return splitIndex + 1;
        }
        return splitIndex;
    }

    private int indexByWeight(String text, int maxWeight) {
        int weight = 0;
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            int charWeight = codePoint < 128 ? 1 : 2;
            if (offset > 0 && weight + charWeight > maxWeight) {
                return offset;
            }
            weight += charWeight;
            offset += Character.charCount(codePoint);
        }
        return text.length();
    }

    private int displayWeight(String text) {
        int weight = 0;
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            weight += codePoint < 128 ? 1 : 2;
            offset += Character.charCount(codePoint);
        }
        return weight;
    }

    private boolean isCjkSoftPunctuation(String value) {
        return value.length() == 1 && CJK_SOFT_PUNCTUATION.indexOf(value.charAt(0)) >= 0;
    }

    private boolean startsWithCjkLeadingPunctuation(String value) {
        return StrUtil.isNotBlank(value) && CJK_LEADING_PUNCTUATION.indexOf(value.charAt(0)) >= 0;
    }

    private boolean socialCaptionStyle(TkGenerationTaskDO task) {
        String style = task == null ? "" : StrUtil.blankToDefault(task.getSubtitleStyle(), "");
        return StrUtil.equalsAny(style, "tiktok_large", "neon_pop", "yellow_story", "comment_bubble");
    }

    private boolean isMostlyEnglish(String text) {
        int latin = 0;
        int cjk = 0;
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            if ((codePoint >= 'A' && codePoint <= 'Z') || (codePoint >= 'a' && codePoint <= 'z')) {
                latin++;
            } else if (isCjk(codePoint)) {
                cjk++;
            }
            offset += Character.charCount(codePoint);
        }
        return latin >= cjk;
    }

    private boolean isCjk(int codePoint) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(codePoint);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.HIRAGANA
                || block == Character.UnicodeBlock.KATAKANA
                || block == Character.UnicodeBlock.HANGUL_SYLLABLES;
    }

    private List<String> splitTokens(String text) {
        List<String> tokens = new ArrayList<>();
        StringBuilder ascii = new StringBuilder();
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            String value = new String(Character.toChars(codePoint));
            if (isAsciiWord(codePoint)) {
                ascii.append(value);
            } else {
                flushAscii(tokens, ascii);
                if (!Character.isWhitespace(codePoint)) {
                    tokens.add(value);
                }
            }
            offset += Character.charCount(codePoint);
        }
        flushAscii(tokens, ascii);
        return tokens;
    }

    private List<String> buildSubtitleUnits(String text) {
        List<String> tokens = splitTokens(text);
        List<String> units = new ArrayList<>();
        for (String token : tokens) {
            if (StrUtil.isBlank(token)) {
                continue;
            }
            if (isSubtitlePunctuation(token) && !units.isEmpty()) {
                int lastIndex = units.size() - 1;
                units.set(lastIndex, units.get(lastIndex) + token);
            } else {
                units.add(token);
            }
        }
        return units;
    }

    private boolean isSubtitlePunctuation(String token) {
        if (StrUtil.isBlank(token)) {
            return false;
        }
        for (int offset = 0; offset < token.length();) {
            int codePoint = token.codePointAt(offset);
            if (Character.isLetterOrDigit(codePoint) || isCjk(codePoint)) {
                return false;
            }
            offset += Character.charCount(codePoint);
        }
        return true;
    }

    private void flushAscii(List<String> tokens, StringBuilder ascii) {
        if (ascii.length() > 0) {
            tokens.add(ascii.toString());
            ascii.setLength(0);
        }
    }

    private boolean isAsciiWord(int codePoint) {
        return codePoint < 128 && (Character.isLetterOrDigit(codePoint) || codePoint == '\'' || codePoint == '-' || codePoint == '%');
    }

    private boolean isKeyword(String token, List<String> keywords) {
        for (String keyword : keywords) {
            if (StrUtil.equalsIgnoreCase(token, keyword)) {
                return true;
            }
        }
        return false;
    }

    private void markKeywordPhrases(List<TkSubtitleWord> words, List<String> keywords) {
        StringBuilder fullText = new StringBuilder();
        for (TkSubtitleWord word : words) {
            fullText.append(word.getText());
        }
        String full = fullText.toString();
        for (String keyword : keywords) {
            if (StrUtil.isBlank(keyword) || !StrUtil.containsIgnoreCase(full, keyword)) {
                continue;
            }
            int index = full.toLowerCase().indexOf(keyword.toLowerCase());
            int cursor = 0;
            for (TkSubtitleWord word : words) {
                int next = cursor + word.getText().length();
                if (next > index && cursor < index + keyword.length()) {
                    word.setKeyword(true);
                }
                cursor = next;
            }
        }
    }

    private int textWeight(String text) {
        return Math.max(1, StrUtil.length(text));
    }

    private double probeDuration(File audioFile) {
        try {
            String ffprobe = TkFfmpegExecutableResolver.ffprobe(generationProperties.getFfmpeg().getFfprobePath());
            Process process = new ProcessBuilder(ffprobe, "-v", "error", "-show_entries", "format=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1", audioFile.getAbsolutePath())
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished || process.exitValue() != 0) {
                process.destroyForcibly();
                return 0D;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                return Double.parseDouble(StrUtil.trim(reader.readLine()));
            }
        } catch (Exception ignored) {
            return 0D;
        }
    }

    private double round(double value) {
        return Math.round(value * 100D) / 100D;
    }

    private static class SubtitleQuality {
        private final boolean acceptable;
        private final double audioDuration;
        private final double firstStart;
        private final double lastEnd;
        private final String reason;

        private SubtitleQuality(boolean acceptable, double audioDuration, double firstStart, double lastEnd, String reason) {
            this.acceptable = acceptable;
            this.audioDuration = audioDuration;
            this.firstStart = firstStart;
            this.lastEnd = lastEnd;
            this.reason = reason;
        }
    }

}
