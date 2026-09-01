package cn.iocoder.yudao.module.tk.service.reference;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;

final class TkOpenVideoTranscriptQuality {

    private TkOpenVideoTranscriptQuality() {
    }

    static boolean isUsable(String transcriptText, String segmentsJson) {
        if (StrUtil.isBlank(transcriptText)) {
            return false;
        }
        JsonNode segments;
        try {
            segments = JsonUtils.parseTree(segmentsJson);
        } catch (Exception ignored) {
            return false;
        }
        if (segments == null || !segments.isArray() || segments.size() == 0) {
            return false;
        }
        String compact = transcriptText.replaceAll("\\s+", "");
        if (compact.length() < 2 || hasRepeatedCharacter(compact, 6)) {
            return false;
        }
        return true;
    }

    private static boolean hasRepeatedCharacter(String text, int threshold) {
        int repeated = 1;
        for (int i = 1; i < text.length(); i++) {
            if (text.charAt(i) == text.charAt(i - 1)) {
                repeated++;
                if (repeated >= threshold) {
                    return true;
                }
            } else {
                repeated = 1;
            }
        }
        return false;
    }

}
