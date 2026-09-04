package cn.iocoder.yudao.module.tk.service.reference;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkDeepSeekClient;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class TkTranscriptTextVerifyServiceImpl implements TkTranscriptTextVerifyService {

    private static final double MIN_TEXT_SIMILARITY = 0.45D;
    private static final int MAX_TEXT_GROWTH = 8;
    private static final int VERIFY_BATCH_SIZE = 20;

    @Resource
    private TkDeepSeekClient deepSeekClient;

    @Override
    public TkTranscriptTextVerifyResult verify(String transcriptText, String segmentsJson) {
        if (StrUtil.isBlank(transcriptText)) {
            throw new IllegalArgumentException("ASR 文案不能为空");
        }
        JsonNode originalSegments = parseSegments(segmentsJson);
        List<Map<String, Object>> resultSegments = new ArrayList<>();
        List<String> transcriptParts = new ArrayList<>();
        for (int batchStart = 0; batchStart < originalSegments.size(); batchStart += VERIFY_BATCH_SIZE) {
            int batchEnd = Math.min(originalSegments.size(), batchStart + VERIFY_BATCH_SIZE);
            verifyBatch(transcriptText, originalSegments, batchStart, batchEnd, resultSegments, transcriptParts);
        }
        return new TkTranscriptTextVerifyResult(String.join("\n", transcriptParts),
                JsonUtils.toJsonString(resultSegments));
    }

    private void verifyBatch(String transcriptText, JsonNode originalSegments, int batchStart, int batchEnd,
                             List<Map<String, Object>> resultSegments, List<String> transcriptParts) {
        try {
            verifyBatchOnce(transcriptText, originalSegments, batchStart, batchEnd, resultSegments, transcriptParts);
        } catch (IllegalStateException ex) {
            int batchSize = batchEnd - batchStart;
            if (batchSize <= 1) {
                throw new IllegalStateException("DeepSeek 文案校验失败，已完成拆批重试，片段序号：" + batchStart
                        + "，原因：" + ex.getMessage(), ex);
            }
            int middle = batchStart + batchSize / 2;
            log.warn("DeepSeek 文案校验响应无效，拆分批次重试，batchStart={}, batchEnd={}, reason={}",
                    batchStart, batchEnd, StrUtil.maxLength(StrUtil.blankToDefault(ex.getMessage(), "unknown"), 160));
            verifyBatch(transcriptText, originalSegments, batchStart, middle, resultSegments, transcriptParts);
            verifyBatch(transcriptText, originalSegments, middle, batchEnd, resultSegments, transcriptParts);
        }
    }

    private void verifyBatchOnce(String transcriptText, JsonNode originalSegments, int batchStart, int batchEnd,
                                 List<Map<String, Object>> resultSegments, List<String> transcriptParts) {
        String prompt = buildPrompt(transcriptText, originalSegments, batchStart, batchEnd);
        JsonNode response = parseResponse(deepSeekClient.verifyText(transcriptText, prompt));
        JsonNode verifiedSegments = response.path("segments");
        int batchSize = batchEnd - batchStart;
        if (!verifiedSegments.isArray() || verifiedSegments.size() != batchSize) {
            throw new IllegalStateException("DeepSeek 返回的文案片段数量不一致");
        }

        List<Map<String, Object>> batchResultSegments = new ArrayList<>();
        List<String> batchTranscriptParts = new ArrayList<>();
        for (int batchIndex = 0; batchIndex < batchSize; batchIndex++) {
            JsonNode original = originalSegments.get(batchStart + batchIndex);
            JsonNode verified = verifiedSegments.get(batchIndex);
            validateIndex(verified, batchIndex);
            String originalText = original.path("text").asText("");
            String verifiedText = verified.path("text").asText(null);
            if (verifiedText == null) {
                throw new IllegalStateException("DeepSeek 返回的文案文字为空");
            }
            verifiedText = verifiedText.trim();
            validateTextChange(originalText, verifiedText);

            Map<String, Object> resultSegment = JsonUtils.parseObject(original.toString(), Map.class);
            resultSegment.put("text", verifiedText);
            batchResultSegments.add(new LinkedHashMap<>(resultSegment));
            if (StrUtil.isNotBlank(verifiedText)) {
                batchTranscriptParts.add(verifiedText);
            }
        }
        resultSegments.addAll(batchResultSegments);
        transcriptParts.addAll(batchTranscriptParts);
    }

    private JsonNode parseSegments(String segmentsJson) {
        try {
            JsonNode segments = JsonUtils.parseTree(segmentsJson);
            if (segments == null || !segments.isArray() || segments.size() == 0) {
                throw new IllegalStateException("ASR 未返回有效时间轴");
            }
            return segments;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("ASR 时间轴 JSON 无效", ex);
        }
    }

    private JsonNode parseResponse(String responseText) {
        try {
            JsonNode response = JsonUtils.getObjectMapper().readTree(extractJsonObject(responseText));
            if (response == null || !response.isObject()) {
                throw new IllegalStateException("DeepSeek 校验结果不是 JSON 对象");
            }
            return response;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("DeepSeek 校验结果 JSON 格式错误", ex);
        }
    }

    private String extractJsonObject(String responseText) {
        if (StrUtil.isBlank(responseText)) {
            throw new IllegalStateException("DeepSeek 校验结果为空");
        }
        String content = responseText.replace("\uFEFF", "").trim();
        int objectStart = content.indexOf('{');
        if (objectStart < 0) {
            throw new IllegalStateException("DeepSeek 校验结果不是 JSON 对象");
        }

        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = objectStart; i < content.length(); i++) {
            char current = content.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }
            if (current == '"') {
                inString = true;
            } else if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return content.substring(objectStart, i + 1);
                }
            }
        }
        throw new IllegalStateException("DeepSeek 校验结果 JSON 截断");
    }

    private String buildPrompt(String transcriptText, JsonNode segments, int batchStart, int batchEnd) {
        List<Map<String, Object>> inputSegments = new ArrayList<>();
        for (int i = batchStart; i < batchEnd; i++) {
            JsonNode segment = segments.get(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("index", i - batchStart);
            item.put("text", segment.path("text").asText(""));
            inputSegments.add(item);
        }
        return "你只做文字校验，不做文案优化。保留原文的语气、风格、顺序、事实、数字和口语表达。"
                + "只在上下文足以确定时修正文字；无法确定时原样保留。不要新增、删除、合并或拆分文字片段。"
                + "标点和语义断句是必做项；原文没有标点时，必须根据完整上下文补充必要的中文标点；只有无法判断的位置才原样保留。"
                + "必须返回合法 JSON，格式为 {\"segments\":[{\"index\":0,\"text\":\"校验后的文字\"}]}，"
                + "segments 数量和 index 必须与本批输入完全一致，只返回 index 和 text。"
                + "\n\n完整原文：\n" + transcriptText
                + "\n\n本批分段文字输入：\n" + JsonUtils.toJsonString(inputSegments);
    }

    private void validateIndex(JsonNode verified, int expectedIndex) {
        if (verified == null || !verified.isObject() || !verified.has("index")
                || verified.path("index").asInt(-1) != expectedIndex) {
            throw new IllegalStateException("DeepSeek 返回的时间轴片段顺序不一致");
        }
    }

    private void validateTextChange(String originalText, String verifiedText) {
        if (StrUtil.isBlank(originalText)) {
            if (StrUtil.isNotBlank(verifiedText)) {
                throw new IllegalStateException("DeepSeek 为原始空片段新增了文字");
            }
            return;
        }
        if (StrUtil.isBlank(verifiedText) || verifiedText.contains("```")
                || verifiedText.length() > originalText.length() + Math.max(MAX_TEXT_GROWTH, originalText.length() / 2)) {
            throw new IllegalStateException("DeepSeek 校验结果存在异常扩写");
        }
        String originalCompact = compactForSimilarity(originalText);
        String verifiedCompact = compactForSimilarity(verifiedText);
        if (originalCompact.isEmpty() || verifiedCompact.isEmpty()) {
            return;
        }
        double similarity = longestCommonSubsequence(originalCompact, verifiedCompact)
                * 1.0D / Math.max(originalCompact.length(), verifiedCompact.length());
        if (similarity < MIN_TEXT_SIMILARITY) {
            throw new IllegalStateException("DeepSeek 校验结果与原文差异过大");
        }
    }

    private String compactForSimilarity(String text) {
        return text.replaceAll("[\\p{P}\\p{S}\\s]+", "");
    }

    private int longestCommonSubsequence(String left, String right) {
        int[] previous = new int[right.length() + 1];
        for (int i = 1; i <= left.length(); i++) {
            int[] current = new int[right.length() + 1];
            for (int j = 1; j <= right.length(); j++) {
                current[j] = left.charAt(i - 1) == right.charAt(j - 1)
                        ? previous[j - 1] + 1 : Math.max(previous[j], current[j - 1]);
            }
            previous = current;
        }
        return previous[right.length()];
    }

}
