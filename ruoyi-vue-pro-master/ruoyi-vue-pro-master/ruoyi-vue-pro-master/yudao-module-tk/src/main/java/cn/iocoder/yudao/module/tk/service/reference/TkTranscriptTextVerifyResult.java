package cn.iocoder.yudao.module.tk.service.reference;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TkTranscriptTextVerifyResult {

    private final String transcriptText;
    private final String segmentsJson;

}
