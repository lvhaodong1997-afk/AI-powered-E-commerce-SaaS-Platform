package cn.iocoder.yudao.module.tk.service.reference;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TkOpenVideoTranscriptTextNormalizerTest {

    @Test
    void normalizeTextConvertsTraditionalChineseAndRemovesTranscriptSpaces() {
        assertEquals("BLG首发对战T1，团队回归了。",
                TkOpenVideoTranscriptTextNormalizer.normalizeText("B L G 首 發 對 戰 T 1，團隊回歸了。"));
    }

    @Test
    void normalizeJsonArrayPreservesTimelineAndNormalizesTextFields() {
        String json = "[{\"text\":\"團隊 回歸\",\"start\":0.1,\"end\":1.2,"
                + "\"words\":[{\"text\":\"回 歸\",\"start\":0.1,\"end\":0.8}]}]";

        assertEquals("[{\"text\":\"团队回归\",\"start\":0.1,\"end\":1.2,"
                        + "\"words\":[{\"text\":\"回归\",\"start\":0.1,\"end\":0.8}]}]",
                TkOpenVideoTranscriptTextNormalizer.normalizeJsonArray(json));
    }

}
