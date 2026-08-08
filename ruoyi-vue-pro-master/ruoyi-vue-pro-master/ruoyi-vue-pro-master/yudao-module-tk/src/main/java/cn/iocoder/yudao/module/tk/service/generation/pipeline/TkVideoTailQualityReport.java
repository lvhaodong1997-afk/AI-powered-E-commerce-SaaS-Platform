package cn.iocoder.yudao.module.tk.service.generation.pipeline;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TkVideoTailQualityReport {

    private boolean retryRecommended;
    private boolean lowDynamicTail;
    private boolean videoShorterThanAudio;
    private boolean subtitleAudioMismatch;
    private double videoDuration;
    private double audioDuration;
    private double subtitleEnd;
    private String message;

    static TkVideoTailQualityReport pass(double videoDuration, double audioDuration, double subtitleEnd) {
        return new TkVideoTailQualityReport(false, false, false, false,
                videoDuration, audioDuration, subtitleEnd, "PASS");
    }

}
