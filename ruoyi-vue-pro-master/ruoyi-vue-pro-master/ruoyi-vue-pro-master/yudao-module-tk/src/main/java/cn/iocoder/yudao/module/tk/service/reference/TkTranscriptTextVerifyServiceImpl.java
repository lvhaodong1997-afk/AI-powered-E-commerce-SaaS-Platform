package cn.iocoder.yudao.module.tk.service.reference;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.tk.service.generation.pipeline.TkDeepSeekClient;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
@Slf4j
public class TkTranscriptTextVerifyServiceImpl implements TkTranscriptTextVerifyService {

    private static final double MIN_TEXT_SIMILARITY = 0.45D;
    private static final int MAX_TEXT_GROWTH = 8;
    private static final int MIN_PUNCTUATION_CHECK_LENGTH = 6;

    @Resource
    private TkDeepSeekClient deepSeekClient;

    @Override
    public TkTranscriptTextVerifyResult verify(String transcriptText) {
        if (StrUtil.isBlank(transcriptText)) {
            throw new IllegalArgumentException("ASR 文案不能为空");
        }
        String responseText = deepSeekClient.verifyText(transcriptText, buildPrompt(transcriptText));
        JsonNode response = parseResponse(responseText);
        String verifiedText = response.path("text").asText(null);
        if (verifiedText == null) {
            throw new IllegalStateException("DeepSeek 校验结果缺少 text 字段");
        }
        verifiedText = verifiedText.trim();
        validateTextChange(transcriptText, verifiedText);
        return new TkTranscriptTextVerifyResult(verifiedText);
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

    private String buildPrompt(String transcriptText) {
        return "你只做文字校验，不做文案优化。保留原文的语气、风格、顺序、事实、数字和口语表达。"
                + "只在上下文足以确定时修正错别字、同音字、漏字、多字和专有名词；无法确定时原样保留。"
                + "标点和语义断句是必做项；原文没有标点时，必须根据完整上下文补充必要的中文逗号、句号、问号、感叹号等标点。"
                + "不要新增、删除、总结、改写或优化内容。必须返回合法 JSON，格式为 {\"text\":\"校验后的完整文案\"}，只返回 text 字段。"
                + "\n\n完整原文：\n" + transcriptText;
    }

    private void validateTextChange(String originalText, String verifiedText) {
        if (StrUtil.isBlank(verifiedText)) {
            throw new IllegalStateException("DeepSeek 返回的校验文案为空");
        }
        if (verifiedText.contains("```")
                || verifiedText.length() > originalText.length()
                + Math.max(MAX_TEXT_GROWTH, originalText.length() / 2)) {
            throw new IllegalStateException("DeepSeek 校验结果存在异常扩写");
        }
        if (containsChinese(verifiedText) && verifiedText.length() >= MIN_PUNCTUATION_CHECK_LENGTH
                && !containsPunctuation(verifiedText)) {
            throw new IllegalStateException("DeepSeek 校验结果未补充必要标点");
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

    private boolean containsChinese(String text) {
        return text.matches(".*[\\u4e00-\\u9fff].*");
    }

    private boolean containsPunctuation(String text) {
        return text.matches(".*[，。！？；：、,.!?;:…].*");
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
