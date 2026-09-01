package cn.iocoder.yudao.module.tk.service.reference;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.houbb.opencc4j.util.ZhConverterUtil;

import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TkOpenVideoTranscriptTextNormalizer {

    private static final Pattern ASCII_TOKEN_RUN = Pattern.compile(
            "(?<![A-Za-z0-9])(?:[A-Za-z0-9]\\s+){1,}[A-Za-z0-9](?![A-Za-z0-9])");

    private TkOpenVideoTranscriptTextNormalizer() {
    }

    static String normalizeText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String normalized = ZhConverterUtil.toSimple(text);
        normalized = normalized.replaceAll("(?<=\\p{IsHan})\\s+(?=\\p{IsHan})", "");
        normalized = normalized.replaceAll("(?<=\\p{IsHan})\\s+(?=[A-Za-z0-9])", "");
        normalized = normalized.replaceAll("(?<=[A-Za-z0-9])\\s+(?=\\p{IsHan})", "");
        Matcher matcher = ASCII_TOKEN_RUN.matcher(normalized);
        StringBuffer compacted = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(compacted, matcher.group().replaceAll("\\s+", ""));
        }
        matcher.appendTail(compacted);
        return compacted.toString().replaceAll("[ \\t]{2,}", " ").trim();
    }

    static String normalizeJsonArray(String json) {
        if (json == null || json.trim().isEmpty()) {
            return json;
        }
        try {
            JsonNode root = JsonUtils.parseTree(json);
            normalizeNode(root);
            return JsonUtils.toJsonString(root);
        } catch (Exception ignored) {
            return json;
        }
    }

    private static void normalizeNode(JsonNode node) {
        if (node == null) {
            return;
        }
        if (node.isArray()) {
            ArrayNode array = (ArrayNode) node;
            for (JsonNode child : array) {
                normalizeNode(child);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }
        ObjectNode object = (ObjectNode) node;
        Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if ("text".equals(field.getKey()) && field.getValue().isTextual()) {
                object.put(field.getKey(), normalizeText(field.getValue().asText()));
            } else {
                normalizeNode(field.getValue());
            }
        }
    }

}
